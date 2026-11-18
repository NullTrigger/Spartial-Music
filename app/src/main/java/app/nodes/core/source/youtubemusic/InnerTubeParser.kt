/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import app.nodes.core.model.HomeSection
import app.nodes.core.model.Playlist
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads YouTube Music's JSON into the app's models.
 *
 * Kept apart from [InnerTubeClient] so it can be unit tested against saved
 * responses, and written defensively throughout: every value is optional, an
 * unknown renderer is skipped rather than throwing, and a row without a video
 * id is not a song. YouTube changes this shape regularly; a missing field must
 * cost one row, never the whole screen.
 */
internal object InnerTubeParser {

    /** What one playability answer offered. */
    data class StreamPick(
        val status: String,
        val reason: String?,
        val url: String?,
        val ciphered: Boolean,
        val mimeType: String?,
        val bitrate: Int,
    )

    /**
     * An album or playlist page: its header plus the rows under it. The
     * header carries the real title and cover; both are null when the page's
     * layout does not name them, and the caller falls back.
     */
    data class CollectionPage(
        val title: String?,
        val artworkUri: String?,
        val tracks: List<Track>,
    )

    // ------------------------------------------------------------------
    // Home
    // ------------------------------------------------------------------

    /**
     * Every carousel on a browse response, in the order YouTube put it.
     *
     * Home is a list of `musicCarouselShelfRenderer`s; a signed-in home adds
     * song shelves ("Quick picks") next to the album and playlist ones. Shelves
     * without a title or without a single usable row are dropped, because a
     * heading with nothing under it looks like a bug.
     */
    fun homeSections(root: JsonObject): List<HomeSection> {
        val shelves = mutableListOf<Pair<String, JsonObject>>()

        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node["musicCarouselShelfRenderer"] as? JsonObject)
                        ?.let { shelves += "carousel" to it }
                    (node["musicShelfRenderer"] as? JsonObject)
                        ?.let { shelves += "shelf" to it }
                    node.values.forEach(::walk)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)

        val sections = mutableListOf<HomeSection>()
        val usedTitles = mutableSetOf<String>()

        shelves.forEachIndexed { index, (kind, renderer) ->
            val title = if (kind == "carousel") {
                (renderer["header"] as? JsonObject)
                    ?.get("musicCarouselShelfBasicHeaderRenderer")?.obj()
                    ?.get("title")?.text()
            } else {
                (renderer["header"] as? JsonObject)?.let { header ->
                    header["musicHeaderRenderer"]?.obj()?.get("title")?.text()
                        ?: header["title"]?.text()
                } ?: renderer["title"]?.text()
            }?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed

            if (!usedTitles.add(title)) return@forEachIndexed

            val contents = renderer["contents"] as? JsonArray ?: return@forEachIndexed
            val tracks = contents
                .mapNotNull { (it as? JsonObject)?.let(::rowTrack) }
                .distinctBy { it.id }
            if (tracks.isEmpty()) return@forEachIndexed

            sections += HomeSection(
                id = "ytmusic-${slug(title)}-$index",
                title = title,
                tracks = tracks,
            )
        }

        return sections
    }

    /** One home carousel row: a song, or an album/playlist card. */
    private fun rowTrack(row: JsonObject): Track? {
        (row["musicResponsiveListItemRenderer"] as? JsonObject)?.let { song ->
            return songRow(song)
        }
        (row["musicTwoRowItemRenderer"] as? JsonObject)?.let { card ->
            return twoRowCard(card)
        }
        return null
    }

    /**
     * A song row. The video id can live in `playlistItemData`, in the play
     * button's `watchEndpoint`, or in the title's endpoint depending on which
     * shelf produced it.
     */
    fun songRow(row: JsonObject): Track? {
        val videoId = (row["playlistItemData"] as? JsonObject)?.get("videoId")?.string()
            ?: playButtonVideoId(row)
            ?: firstVideoId(row)
            ?: return null

        val columns = (row["flexColumns"] as? JsonArray).orEmpty()
            .mapNotNull {
                (it as? JsonObject)
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.obj()
            }
        val title = columns.firstOrNull()?.get("text")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null

        val parts = subtitleSections(
            columns.getOrNull(1)?.get("text")?.obj()?.get("runs") as? JsonArray,
        )
        val album = parts.firstOrNull { it.isAlbum }
        // Album pages leave the subtitle column empty and never link the
        // artist in the row, so the play button's label ("Song - Artist") is
        // the only place those rows name a performer. "YouTube Music" is not
        // a performer and is never invented as one; a row that names nobody
        // simply has no artist.
        val artist = parts.filter { it.isArtist }.joinToString(", ") { it.text }.ifBlank {
            parts.firstOrNull { !CLOCK.matches(it.text) }?.text
                ?: playLabelArtist(row, title)
                ?: ""
        }
        val clock = parts.lastOrNull { CLOCK.matches(it.text) }?.text
            ?: row["lengthText"]?.text()?.takeIf { CLOCK.matches(it) }
            ?: fixedColumnClock(row)

        return Track(
            id = "ytmusic-$videoId",
            title = title,
            artist = artist,
            album = album?.text,
            albumId = album?.albumId,
            artworkUri = musicThumbnail(row),
            mediaUri = null,
            durationMs = parseClock(clock),
            sourceId = SourceId.YOUTUBE_MUSIC,
            videoId = videoId,
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
        )
    }

    /**
     * A `musicTwoRowItemRenderer`: a square card. With a watch endpoint it is a
     * song or video and plays directly; with an album or playlist browse
     * endpoint it becomes a collection card the UI opens instead of playing.
     * Artist and mood cards are skipped - they have no tracks to show here.
     */
    fun twoRowCard(row: JsonObject): Track? {
        val titleRuns = row["title"]?.runs() ?: return null
        val title = titleRuns.joinToString("") {
            (it as? JsonObject)?.get("text")?.string().orEmpty()
        }
            .trim().takeIf { it.isNotEmpty() } ?: return null

        val endpoint = (titleRuns.firstOrNull() as? JsonObject)
            ?.get("navigationEndpoint")?.obj()
        val browse = endpoint?.get("browseEndpoint")?.obj()
        val watch = endpoint?.get("watchEndpoint")?.obj()

        val videoId = watch?.get("videoId")?.string()
        val browseId = browse?.get("browseId")?.string()
        val pageType = browse?.browsePageType()
        val artwork = musicThumbnail(row)
        val artists = row["subtitle"]?.runs()
            ?.mapNotNull { (it as? JsonObject)?.takeIf { run -> run.runPageType() == PAGE_ARTIST } }
            ?.mapNotNull { it["text"]?.string() }
            ?.joinToString(", ")
            .orEmpty()

        if (videoId != null) {
            return Track(
                id = "ytmusic-$videoId",
                title = title,
                artist = artists.ifBlank { playLabelArtist(row, title) ?: "" },
                artworkUri = artwork,
                durationMs = 0L,
                sourceId = SourceId.YOUTUBE_MUSIC,
                videoId = videoId,
                externalUrl = "https://music.youtube.com/watch?v=$videoId",
            )
        }

        if (browseId != null && pageType in COLLECTION_PAGES) {
            return Track(
                id = "ytmusic-collection-$browseId",
                title = title,
                artist = artists.ifBlank { if (pageType == PAGE_ALBUM) "Album" else "Playlist" },
                artworkUri = artwork,
                durationMs = 0L,
                sourceId = SourceId.YOUTUBE_MUSIC,
                collectionId = browseId,
                externalUrl = "https://music.youtube.com/browse/$browseId",
            )
        }

        return null
    }

    // ------------------------------------------------------------------
    // Search and collections
    // ------------------------------------------------------------------

    /** All song rows in a search answer, in YouTube's order. */
    fun songRows(root: JsonObject, limit: Int): List<Track> {
        val out = mutableListOf<Track>()
        val seen = mutableSetOf<String>()
        walkRenderers(root, "musicResponsiveListItemRenderer") { renderer ->
            songRow(renderer)?.let { track ->
                if (seen.add(track.videoId ?: track.id)) out += track
            }
        }
        return out.take(limit.coerceAtLeast(0))
    }

    /**
     * Tracks on an album or playlist page. Playlist rows are the same song
     * renderer as search; some album layouts use square cards, so both are read.
     */
    fun collectionTracks(root: JsonObject, limit: Int): List<Track> {
        val out = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        walkRenderers(root, "musicResponsiveListItemRenderer") { renderer ->
            songRow(renderer)?.let { track ->
                if (seen.add(track.videoId ?: track.id)) out += track
            }
        }
        walkRenderers(root, "musicTwoRowItemRenderer") { renderer ->
            twoRowCard(renderer)?.takeIf { it.videoId != null }?.let { track ->
                if (seen.add(track.videoId ?: track.id)) out += track
            }
        }

        return out.take(limit.coerceAtLeast(0))
    }

    /**
     * Tracks of a `next` (radio / up-next) answer. The rows are the same song
     * renderers as everywhere else; `playlistPanelVideoRenderer` rows are read
     * too, since some radio layouts answer with them. Defensive throughout: a
     * row without a video id is not a song.
     */
    fun nextTracks(root: JsonObject, limit: Int): List<Track> {
        val out = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        walkRenderers(root, "musicResponsiveListItemRenderer") { renderer ->
            songRow(renderer)?.let { track ->
                if (seen.add(track.videoId ?: track.id)) out += track
            }
        }
        walkRenderers(root, "playlistPanelVideoRenderer") { renderer ->
            panelRow(renderer)?.let { track ->
                if (seen.add(track.videoId ?: track.id)) out += track
            }
        }

        return out.take(limit.coerceAtLeast(0))
    }

    /** One `playlistPanelVideoRenderer` radio row. */
    private fun panelRow(row: JsonObject): Track? {
        val videoId = row["videoId"]?.string() ?: return null
        val title = row["title"]?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = subtitleSections(row["longBylineText"]?.obj()?.get("runs") as? JsonArray)
        val artist = parts.filter { it.isArtist }.joinToString(", ") { it.text }
            .ifBlank { parts.firstOrNull { !CLOCK.matches(it.text) }?.text ?: "" }
        val album = parts.firstOrNull { it.isAlbum }
        return Track(
            id = "ytmusic-$videoId",
            title = title,
            artist = artist,
            album = album?.text,
            albumId = album?.albumId,
            artworkUri = musicThumbnail(row),
            durationMs = parseClock(row["lengthText"]?.text()?.takeIf { CLOCK.matches(it) }),
            sourceId = SourceId.YOUTUBE_MUSIC,
            videoId = videoId,
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
        )
    }

    /**
     * An album or playlist page: the header (its real title and cover) and
     * the tracks. The header has three known shapes - the classic detail
     * header, the responsive header newer pages use, and the newest one that
     * keeps everything in a view model - so all three are read, and a layout
     * with none of them simply costs the title and cover, never the rows.
     */
    fun collectionPage(root: JsonObject, limit: Int): CollectionPage {
        var title: String? = null
        var artwork: String? = null

        // The editable playlist header wraps the classic one, so the walk
        // finds it nested too.
        for (key in listOf("musicDetailHeaderRenderer", "musicResponsiveHeaderRenderer")) {
            walkRenderers(root, key) { header ->
                if (title == null) {
                    title = header["title"]?.text()?.trim()?.takeIf { it.isNotEmpty() }
                }
                if (artwork == null) {
                    artwork = musicThumbnail(header)
                }
            }
        }

        walkRenderers(root, "pageHeaderViewModel") { model ->
            if (title == null) title = dynamicText(model)
            if (artwork == null) artwork = largestImage(model)
        }

        // Album rows carry no thumbnails of their own; without the header's
        // cover every row renders a generated placeholder and the page reads
        // as "the music image never loads", so rows without artwork borrow
        // the page's cover.
        val tracks = collectionTracks(root, limit).map { track ->
            if (track.artworkUri == null && artwork != null) {
                track.copy(artworkUri = artwork)
            } else {
                track
            }
        }

        return CollectionPage(
            title = title,
            artworkUri = artwork,
            tracks = tracks,
        )
    }
    /** Album and playlist cards on a library page. */
    fun libraryPlaylists(root: JsonObject): List<Playlist> {
        val out = mutableListOf<Playlist>()
        val seen = mutableSetOf<String>()
        walkRenderers(root, "musicTwoRowItemRenderer") { row ->
            val titleRuns = row["title"]?.runs() ?: return@walkRenderers
            val title = titleRuns.joinToString("") {
                (it as? JsonObject)?.get("text")?.string().orEmpty()
            }
                .trim().takeIf { it.isNotEmpty() } ?: return@walkRenderers
            val browse = (titleRuns.firstOrNull() as? JsonObject)
                ?.get("navigationEndpoint")?.obj()
                ?.get("browseEndpoint")?.obj() ?: return@walkRenderers
            val browseId = browse["browseId"]?.string() ?: return@walkRenderers
            val pageType = browse.browsePageType()
            if (pageType != PAGE_PLAYLIST && pageType != PAGE_ALBUM) return@walkRenderers
            if (!seen.add(browseId)) return@walkRenderers
            out += Playlist(
                id = browseId,
                title = title,
                artworkUri = musicThumbnail(row),
                sourceId = SourceId.YOUTUBE_MUSIC,
                externalUrl = "https://music.youtube.com/browse/$browseId",
            )
        }
        return out
    }

    /** The signed-in account's name and email from `account/account_menu`. */
    fun account(root: JsonObject): InnerTubeAccount? {
        var header: JsonObject? = null

        fun walk(node: JsonElement) {
            if (header != null) return
            when (node) {
                is JsonObject -> {
                    val found = node["activeAccountHeaderRenderer"] as? JsonObject
                    if (found != null) {
                        header = found
                        return
                    }
                    node.values.forEach(::walk)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)
        val account = header ?: return null
        val name = account["accountName"]?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        return InnerTubeAccount(name = name, email = account["email"]?.text()?.trim())
    }

    /** The suggestions YouTube Music offers for a partly typed query. */
    fun suggestions(root: JsonObject): List<String> {
        val out = mutableListOf<String>()
        // The music endpoint answers with `musicSuggestionRenderer`; the shared
        // search endpoint uses `searchSuggestionRenderer` with the same field.
        for (key in listOf("musicSuggestionRenderer", "searchSuggestionRenderer")) {
            walkRenderers(root, key) { renderer ->
                renderer["suggestion"]?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            }
        }
        return out.distinct()
    }

    /**
     * The caption track of a video, as `[mm:ss.mmm]line` text.
     *
     * `get_transcript` answers with one engagement panel whose cue groups are
     * the timed lines. The musical notes the uploaders pad with are trimmed,
     * and a panel without cues yields null rather than empty text, so the
     * caller can distinguish "no captions" from "captions exist".
     */
    fun transcript(root: JsonObject): String? {
        val cueGroups = root.walkTo("transcriptBodyRenderer")
            ?.get("cueGroups") as? JsonArray ?: return null
        val lines = cueGroups.mapNotNull { group ->
            val renderer = (group as? JsonObject)
                ?.get("transcriptCueGroupRenderer")?.obj() ?: return@mapNotNull null
            val cue = (renderer["cues"] as? JsonArray)
                ?.firstOrNull()?.obj()
                ?.get("transcriptCueRenderer")?.obj() ?: return@mapNotNull null
            val time = (cue["startOffsetMs"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: return@mapNotNull null
            val text = cue["cue"]?.text()?.trim('♪', ' ', '\u2005')
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            "[%02d:%02d.%03d]%s".format(java.util.Locale.US, time / 60_000, time / 1_000 % 60, time % 1_000, text)
        }
        return lines.joinToString("\n").ifEmpty { null }
    }

    /** First nested object under [key], depth-first. */
    private fun JsonObject.walkTo(key: String): JsonObject? {
        this[key]?.obj()?.let { return it }
        for (value in values) {
            when (value) {
                is JsonObject -> value.walkTo(key)?.let { return it }
                is JsonArray -> for (item in value) {
                    (item as? JsonObject)?.walkTo(key)?.let { return it }
                }
                // JsonPrimitive and JsonNull can hold nothing but themselves.
                else -> Unit
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    /**
     * The playability answer and the best audio-only format it offered.
     *
     * `audio/mp4` (AAC) is preferred at equal quality because every Android
     * device decodes it in hardware; `audio/webm` (Opus) is the fallback. A
     * format that only carries a `signatureCipher` is reported as
     * [StreamPick.ciphered] so the caller can try another client identity
     * instead of handing Media3 a URL it cannot fetch.
     */
    fun stream(root: JsonObject, preferHighQuality: Boolean): StreamPick {
        val statusBlock = root["playabilityStatus"] as? JsonObject
        val status = statusBlock?.get("status")?.string() ?: STATUS_UNKNOWN
        val reason = statusBlock?.get("reason")?.string()
        if (status != STATUS_OK) return StreamPick(status, reason, null, false, null, 0)

        val formats = (root["streamingData"] as? JsonObject)
            ?.get("adaptiveFormats") as? JsonArray
        val audio = formats.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { (it["mimeType"]?.string() ?: "").startsWith("audio/") }

        val playable = audio.mapNotNull { format ->
            format.directUrl()?.let { url -> format to url }
        }

        if (playable.isEmpty()) {
            return StreamPick(status, reason, null, audio.isNotEmpty(), null, 0)
        }

        val chosen = if (preferHighQuality) {
            playable.maxWithOrNull(
                compareBy<Pair<JsonObject, String>> { formatScore(it.first) }
                    .thenBy { it.first["bitrate"]?.int() ?: 0 },
            )
        } else {
            val modest = playable.filter { (it.first["bitrate"]?.int() ?: 0) <= MODEST_BITRATE }
            (modest.ifEmpty { playable }).maxWithOrNull(
                compareBy<Pair<JsonObject, String>> { it.first["bitrate"]?.int() ?: 0 },
            )
        } ?: return StreamPick(status, reason, null, false, null, 0)

        return StreamPick(
            status = status,
            reason = reason,
            url = chosen.second,
            ciphered = false,
            mimeType = chosen.first["mimeType"]?.string(),
            bitrate = chosen.first["bitrate"]?.int() ?: 0,
        )
    }

    /** A URL Media3 can fetch, or null when this format needs a signature. */
    private fun JsonObject.directUrl(): String? {
        (this["url"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.startsWith("http") }
            ?.let { return it }

        val cipher = (this["signatureCipher"] ?: this["cipher"])?.string() ?: return null
        val params = parseQueryString(cipher)
        // Without an `s` there is nothing to decipher and the URL is usable as is.
        if (params["s"].isNullOrBlank()) return params["url"]
        return null
    }

    private fun formatScore(format: JsonObject): Int {
        val mime = format["mimeType"]?.string().orEmpty()
        val quality = when (format["audioQuality"]?.string()) {
            "AUDIO_QUALITY_HIGH" -> 3
            "AUDIO_QUALITY_MEDIUM" -> 2
            "AUDIO_QUALITY_LOW" -> 1
            else -> 0
        }
        val container = if (mime.startsWith("audio/mp4")) 1 else 0
        return quality * 10 + container
    }

    /** The visitor id inside `sw.js_data`, which is JSON behind a `)]}'` guard. */
    fun visitorData(body: String): String? {
        val payload = body.substringAfter(")]}'", body).trim()
        val root = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return null
        val candidates = mutableListOf<String>()

        fun collect(node: JsonElement) {
            when (node) {
                is JsonArray -> node.forEach(::collect)
                is JsonPrimitive -> node.contentOrNull?.let(candidates::add)
                else -> Unit
            }
        }

        collect(root)
        return candidates.firstOrNull { VISITOR_DATA_PREFIX.containsMatchIn(it) }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private data class Section(
        val text: String,
        val pageTypes: Set<String>,
        /** Browse id of each page type this section links to, e.g. the album. */
        val browseIds: Map<String, String> = emptyMap(),
    ) {
        val isArtist: Boolean get() = PAGE_ARTIST in pageTypes
        val isAlbum: Boolean get() = PAGE_ALBUM in pageTypes

        /** The album page this part links to, when it links to one. */
        val albumId: String? get() = browseIds[PAGE_ALBUM]
    }

    /**
     * Splits "Artist • Album • 3:42" into its parts and remembers which page
     * each part links to, so the artist is not guessed by position.
     */
    private fun subtitleSections(runs: JsonArray?): List<Section> {
        if (runs == null) return emptyList()
        val sections = mutableListOf<Section>()
        var text = StringBuilder()
        var pageTypes = mutableSetOf<String>()
        var browseIds = mutableMapOf<String, String>()

        fun flush() {
            val value = text.toString().trim()
            if (value.isNotEmpty() && value != "•") {
                sections += Section(value, pageTypes, browseIds)
            }
            text = StringBuilder()
            pageTypes = mutableSetOf()
            browseIds = mutableMapOf()
        }

        for (element in runs) {
            val run = element as? JsonObject ?: continue
            val value = run["text"]?.string().orEmpty()
            if (value.trim() == "•") {
                flush()
                continue
            }
            text.append(value)
            run.runPageType()?.let { pageType ->
                pageTypes += pageType
                run.runBrowseId()?.let { browseIds[pageType] = it }
            }
        }
        flush()
        return sections
    }

    /**
     * The performer named in the play button's accessibility label
     * ("Play Song - Artist"). Album rows carry no artist column and no artist
     * link, so that label is the only place they name one. The known title is
     * stripped from the front so a title containing " - " cannot swallow the
     * artist.
     */
    private fun playLabelArtist(row: JsonObject, title: String): String? {
        val button = (row["overlay"] as? JsonObject)
            ?.get("musicItemThumbnailOverlayRenderer")?.obj()
            ?.get("content")?.obj()
            ?.get("musicPlayButtonRenderer")?.obj() ?: return null
        val label = button["accessibilityPlayData"]?.obj()
            ?.get("accessibilityData")?.obj()
            ?.get("label")?.string() ?: return null
        val tail = label.removePrefix("Play ").removePrefix("Pause ").trim()
        val prefix = "$title - "
        return tail.removePrefix(prefix).takeIf { it != tail && it.isNotBlank() }
    }

    /** The duration in the row's fixed column; album pages keep it there. */
    private fun fixedColumnClock(row: JsonObject): String? {
        val columns = (row["fixedColumns"] as? JsonArray).orEmpty()
        for (column in columns) {
            val text = (column as? JsonObject)
                ?.get("musicResponsiveListItemFixedColumnRenderer")?.obj()
                ?.get("text")?.text()
            if (text != null && CLOCK.matches(text)) return text
        }
        return null
    }

    private fun playButtonVideoId(row: JsonObject): String? {
        val overlay = (row["overlay"] as? JsonObject)
            ?.get("musicItemThumbnailOverlayRenderer")?.obj() ?: return null
        val button = overlay["content"]?.obj()?.get("musicPlayButtonRenderer")?.obj() ?: return null
        return button["playNavigationEndpoint"]?.obj()
            ?.get("watchEndpoint")?.obj()
            ?.get("videoId")?.string()
    }

    private fun firstVideoId(row: JsonObject): String? {
        fun find(node: JsonElement): String? = when (node) {
            is JsonObject -> node["videoId"]?.string()
                ?: node.values.firstNotNullOfOrNull { find(it) }

            is JsonArray -> node.firstNotNullOfOrNull { find(it) }
            else -> null
        }
        return find(row)
    }

    private fun walkRenderers(
        root: JsonObject,
        key: String,
        onRenderer: (JsonObject) -> Unit,
    ) {
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node[key] as? JsonObject)?.let(onRenderer)
                    node.values.forEach(::walk)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
    }

    /**
     * The largest thumbnail on this row, following the music wrapper. YouTube
     * lists thumbnails smallest first, so taking the first one is what made
     * every cover look soft; the display layer then rewrites this URL to the
     * pixels the surface actually needs.
     */
    /**
     * First `dynamicTextViewModel.text.content` under [node]: the title in
     * the newest page-header layout.
     */
    private fun dynamicText(node: JsonElement): String? {
        when (node) {
            is JsonObject -> {
                val content = (node["dynamicTextViewModel"] as? JsonObject)
                    ?.get("text")?.obj()?.get("content")?.string()
                if (!content.isNullOrBlank()) return content.trim()
                for (value in node.values) {
                    dynamicText(value)?.let { return it }
                }
            }

            is JsonArray -> for (value in node) {
                dynamicText(value)?.let { return it }
            }

            else -> Unit
        }
        return null
    }

    /**
     * Largest image under [node], whatever the layout calls its list - the
     * classic `thumbnails` arrays and the view model's `sources` arrays are
     * the same {url, width, height} shape.
     */
    private fun largestImage(node: JsonElement): String? {
        var best: String? = null
        var bestArea = -1

        fun scan(current: JsonElement) {
            when (current) {
                is JsonObject -> {
                    for (key in listOf("thumbnails", "sources")) {
                        val list = current[key] as? JsonArray ?: continue
                        for (element in list) {
                            val obj = element as? JsonObject ?: continue
                            val url = obj["url"]?.string() ?: continue
                            val area = (obj["width"]?.int() ?: 0) * (obj["height"]?.int() ?: 0)
                            if (area > bestArea) {
                                best = url
                                bestArea = area
                            }
                        }
                    }
                    current.values.forEach(::scan)
                }

                is JsonArray -> current.forEach(::scan)
                else -> Unit
            }
        }

        scan(node)
        return best
    }

    private fun musicThumbnail(row: JsonObject): String? {
        for (key in listOf("thumbnail", "thumbnailRenderer")) {
            val outer = row[key] as? JsonObject ?: continue
            val inner = outer["musicThumbnailRenderer"]?.obj() ?: outer
            val thumbs = ((inner["thumbnail"] as? JsonObject)?.get("thumbnails")
                ?: inner["thumbnails"]) as? JsonArray ?: continue
            val sized = thumbs.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val url = obj["url"]?.string() ?: return@mapNotNull null
                val area = (obj["width"]?.int() ?: 0) * (obj["height"]?.int() ?: 0)
                url to area
            }
            val url = sized.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first
                ?: sized.lastOrNull()?.first
            if (url != null) return url
        }
        return null
    }

    /** Page type of a browse endpoint, e.g. `MUSIC_PAGE_TYPE_ALBUM`. */
    private fun JsonObject.browsePageType(): String? =
        (this["browseEndpointContextSupportedConfigs"] as? JsonObject)
            ?.get("browseEndpointContextMusicConfig")?.obj()
            ?.get("pageType")?.string()

    /** Page type a subtitle run links to. */
    private fun JsonObject.runPageType(): String? =
        (this["navigationEndpoint"] as? JsonObject)
            ?.get("browseEndpoint")?.obj()
            ?.browsePageType()

    /** Browse id a subtitle run links to, e.g. the album a song is on. */
    private fun JsonObject.runBrowseId(): String? =
        (this["navigationEndpoint"] as? JsonObject)
            ?.get("browseEndpoint")?.obj()
            ?.get("browseId")?.string()

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private fun JsonElement?.runs(): JsonArray? = (this as? JsonObject)?.get("runs") as? JsonArray

    /** Reads `{"simpleText": "x"}` or `{"runs": [{"text": "x"}, …]}`. */
    private fun JsonElement?.text(): String? {
        val obj = this as? JsonObject
            ?: return (this as? JsonPrimitive)?.contentOrNull
        (obj["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        val runs = obj["runs"] as? JsonArray ?: return null
        return runs
            .mapNotNull { (it as? JsonObject)?.get("text")?.string() }
            .joinToString("")
            .ifBlank { null }
    }

    /** "3:42" / "1:02:03" -> milliseconds. */
    fun parseClock(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        var total = 0L
        for (part in value.trim().split(":")) {
            val number = part.toLongOrNull() ?: return 0L
            total = total * 60 + number
        }
        return total * 1_000L
    }

    private fun parseQueryString(value: String): Map<String, String> =
        value.split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) {
                null
            } else {
                part.substring(0, index) to
                    java.net.URLDecoder.decode(part.substring(index + 1), "UTF-8")
            }
        }.toMap()

    private fun slug(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    const val PAGE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    const val PAGE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    const val PAGE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val STATUS_OK = "OK"
    private const val STATUS_UNKNOWN = "UNKNOWN"
    private const val MODEST_BITRATE = 160_000

    private val COLLECTION_PAGES = setOf(
        PAGE_ALBUM,
        PAGE_PLAYLIST,
        "MUSIC_PAGE_TYPE_PODCAST",
    )

    private val CLOCK = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    private val VISITOR_DATA_PREFIX = Regex("^Cg[t|s]")
}
