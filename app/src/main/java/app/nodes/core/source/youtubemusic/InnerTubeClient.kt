/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What YouTube answered when asked for a stream.
 *
 * [Refused] carries a short, per-client account of why nothing was served, so the
 * player can tell the user something more useful than "it failed" and a bug
 * report carries the diagnosis without a log.
 */
sealed interface InnerTubeStream {
    data class Playable(val url: String, val client: String) : InnerTubeStream

    data class Refused(val detail: String) : InnerTubeStream
}

/**
 * One of YouTube's own app identities, as used on the private InnerTube API.
 *
 * YouTube decides what audio it will hand out partly by which app is asking, and
 * the answers change over time: the identity that serves a track this month can
 * return "unplayable" next month. Working players carry several and try them in
 * order, so these profiles are a list rather than a constant. The values are the
 * ones Metrolist and Glossy ship (see THIRD_PARTY_LICENSES.md).
 */
internal data class InnerTubeClientProfile(
    val name: String,
    val version: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val includeUserAgentInContext: Boolean = false,
    val embedded: Boolean = false,
)

/**
 * Client for YouTube's private InnerTube JSON API (the `youtubei` endpoints).
 *
 * This is the **unofficial** interface used by YouTube's own clients, not a
 * published API. It is not covered by the YouTube API Services Terms, it can
 * change or start requiring attestation tokens without notice, and Google may
 * rate-limit or block traffic that uses it. It exists in Nodes because the
 * maintainer chose to add it on top of the documented Data API path; see
 * YOUTUBE.md and THIRD_PARTY_LICENSES.md.
 *
 * Deliberate limits, so this stays a player and not a ripper:
 *  - search and playlist metadata only; no recommendations engine, no tracking;
 *  - playback asks the clients that answer with a plain `url` field, so no
 *    signature deciphering, no PoToken minting and no attestation code lives
 *    here. Clients that only answer with `signatureCipher` are skipped, and a
 *    client that starts requiring a token simply drops out of the chain;
 *  - the URL handed to Media3 is the one YouTube returned for this request, with
 *    nothing rewritten beyond reading it out of the response;
 *  - nothing is downloaded: the reachability probe reads a single byte, and
 *    nothing is written to disk except the artwork Coil caches as usual.
 */
@Singleton
class InnerTubeClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /**
     * Songs matching [query], ranked as YouTube Music ranks them.
     *
     * The `params` value is YouTube Music's own "songs" filter. Without it the
     * endpoint answers as plain YouTube search, and the results fill up with
     * ordinary videos - gameplay clips, reviews, vlogs - which is not content a
     * music player should offer. Only music shelf items are read back out of the
     * response as well, so a plain `videoRenderer` can never leak into results.
     */
    suspend fun search(query: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", contextFor(WEB_PROFILE))
            put("query", query)
            put("params", MUSIC_SONGS_FILTER)
        }
        val root = post("search", payload, WEB_PROFILE.userAgent)
        musicTracks(root).take(limit.coerceIn(1, 50))
    }

    /** Contents of a YouTube playlist (any playlist id, no `VL` prefix needed). */
    suspend fun playlist(playlistId: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", contextFor(WEB_PROFILE))
            put("browseId", if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
        }
        val root = post("browse", payload, WEB_PROFILE.userAgent)
        rendererTracks(root).take(limit.coerceIn(1, 100))
    }

    /**
     * Resolves a directly playable audio URL for [videoId].
     *
     * The clients in [PLAYBACK_CLIENTS] are asked in turn, newest and best first;
     * the first answer that is both `OK` and actually fetchable wins. Each URL is
     * short-lived and bound to the client that minted it, so it must be used
     * immediately and never cached.
     */
    suspend fun audioUrl(videoId: String, preferHighQuality: Boolean): InnerTubeStream =
        withContext(Dispatchers.IO) {
            val notes = mutableListOf<String>()

            for (profile in PLAYBACK_CLIENTS) {
                val attempt = try {
                    requestStream(profile, videoId, preferHighQuality)
                } catch (error: IOException) {
                    notes += "${profile.name}: network error"
                    continue
                }

                if (attempt.status != STATUS_OK) {
                    val reason = attempt.reason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    notes += "${profile.name}: ${attempt.status.humanStatus()}$reason"
                    continue
                }

                val url = attempt.url
                if (url == null) {
                    notes += "${profile.name}: no plain audio stream"
                    continue
                }

                if (!isReachable(url, profile.userAgent)) {
                    notes += "${profile.name}: stream rejected by the CDN"
                    continue
                }

                return@withContext InnerTubeStream.Playable(url, profile.name)
            }

            InnerTubeStream.Refused(notes.joinToString("; "))
        }

    // ---------------------------------------------------------------------

    /** What one client answered: the playability status and, if offered, a URL. */
    private data class Attempt(val status: String, val reason: String?, val url: String?)

    private fun requestStream(
        profile: InnerTubeClientProfile,
        videoId: String,
        preferHighQuality: Boolean,
    ): Attempt {
        val payload = buildJsonObject {
            put("context", contextFor(profile))
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val root = post("player", payload, profile.userAgent)

        val statusBlock = root["playabilityStatus"]?.obj()
        val status = statusBlock?.get("status")?.string() ?: "UNKNOWN"
        val reason = statusBlock?.get("reason")?.string()
        if (status != STATUS_OK) return Attempt(status, reason, null)

        val formats = (root["streamingData"]?.obj()?.get("adaptiveFormats") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

        return Attempt(status, reason, pickAudioFormat(formats, preferHighQuality))
    }

    /**
     * The audio URL to use, or null when this client only offered ciphered
     * formats. Plain `url` fields are the whole reason this client is in the
     * chain; a `signatureCipher` entry would need a JavaScript player to solve
     * and is intentionally not handled.
     */
    private fun pickAudioFormat(formats: List<JsonObject>, preferHighQuality: Boolean): String? {
        val audio = formats.filter { format ->
            (format["mimeType"]?.string() ?: "").startsWith("audio/") &&
                format["url"]?.string() != null
        }
        if (audio.isEmpty()) return null

        val chosen = if (preferHighQuality) {
            audio.maxWithOrNull(
                compareBy<JsonObject> { qualityScore(it) }
                    .thenBy { it["bitrate"]?.int() ?: 0 },
            )
        } else {
            // A modest bitrate saves data without sounding worse on a phone
            // speaker; fall back to the lowest the track offers.
            val modest = audio.filter { (it["bitrate"]?.int() ?: 0) <= MODEST_BITRATE }
            (modest.ifEmpty { audio }).maxWithOrNull(
                compareBy<JsonObject> { it["bitrate"]?.int() ?: 0 },
            )
        }
        return chosen?.get("url")?.string()
    }

    private fun qualityScore(format: JsonObject): Int = when (format["audioQuality"]?.string()) {
        "AUDIO_QUALITY_HIGH" -> 3
        "AUDIO_QUALITY_MEDIUM" -> 2
        "AUDIO_QUALITY_LOW" -> 1
        else -> 0
    }

    /**
     * Whether the CDN will actually serve [url] to [userAgent].
     *
     * The probe is a ranged GET for a single byte, because googlevideo answers
     * HEAD with 403 and would reject every candidate if it were used instead.
     * Media3 would otherwise take the URL and fail later, mid-playback, with the
     * player already showing the track as playing.
     */
    private fun isReachable(url: String, userAgent: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        client.newCall(request).execute().use { response -> response.isSuccessful }
    }.getOrDefault(false)

    private fun contextFor(profile: InnerTubeClientProfile): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", profile.name)
            put("clientVersion", profile.version)
            if (profile.includeUserAgentInContext) put("userAgent", profile.userAgent)
            profile.osName?.let { put("osName", it) }
            profile.osVersion?.let { put("osVersion", it) }
            profile.deviceMake?.let { put("deviceMake", it) }
            profile.deviceModel?.let { put("deviceModel", it) }
            profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
            put("hl", "en")
            put("gl", "US")
        }
        if (profile.embedded) {
            putJsonObject("thirdParty") { put("embedUrl", EMBED_URL) }
        }
    }

    private fun post(path: String, payload: JsonObject, userAgent: String): JsonObject {
        val request = Request.Builder()
            .url("$BASE_URL/$path?key=$PUBLIC_CLIENT_KEY&prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("InnerTube $path returned HTTP ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            return json.parseToJsonElement(text).jsonObject
        }
    }

    /**
     * Reads only the rows YouTube Music puts on a music shelf.
     *
     * A music result is a `musicResponsiveListItemRenderer`, whose title, artist,
     * album and clock live in `flexColumns` rather than in the flat fields a
     * `videoRenderer` has. Reading that shape is what makes the filter visible in
     * the UI: a row that is not music has no such renderer and is skipped.
     */
    private fun musicTracks(root: JsonElement): List<Track> {
        val out = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node["musicResponsiveListItemRenderer"] as? JsonObject)
                        ?.toMusicTrack()
                        ?.let { track -> if (seen.add(track.id)) out += track }
                    node.values.forEach(::walk)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)
        return out
    }

    private fun JsonObject.toMusicTrack(): Track? {
        val videoId = (this["playlistItemData"] as? JsonObject)?.get("videoId")?.string()
            ?: firstVideoId()
            ?: return null

        val columns = (this["flexColumns"] as? JsonArray)
            ?.mapNotNull {
                (it as? JsonObject)?.get("musicResponsiveListItemFlexColumnRenderer")?.obj()
            }
            .orEmpty()
        val title = columns.firstOrNull()?.get("text")?.text() ?: return null

        // "Artist \u2022 Album \u2022 3:42": the first two runs link to a page and
        // say which one, the clock is the only run with no page type at all.
        val subtitle = (columns.getOrNull(1)?.get("text")?.obj()?.get("runs") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
        val artist = subtitle.firstOrNull { it.musicPageType() == "MUSIC_PAGE_TYPE_ARTIST" }
            ?.get("text")?.string()
            ?: subtitle.firstOrNull()?.get("text")?.string()
            ?: "YouTube Music"
        val album = subtitle.firstOrNull { it.musicPageType() == "MUSIC_PAGE_TYPE_ALBUM" }
            ?.get("text")?.string()
        val clock = subtitle.lastOrNull {
            it["text"]?.string()?.let(CLOCK::matches) == true
        }?.get("text")?.string()

        val artwork = (this["thumbnail"]?.obj()?.get("musicThumbnailRenderer")?.obj()
            ?.get("thumbnail")?.obj()?.get("thumbnails") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("url")?.string() }
            ?.lastOrNull()

        return Track(
            id = "ytmusic-$videoId",
            title = title,
            artist = artist,
            album = album,
            artworkUri = artwork,
            mediaUri = null,
            durationMs = parseClock(clock),
            sourceId = SourceId.YOUTUBE_MUSIC,
            videoId = videoId,
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
        )
    }

    /** The page type a subtitle run links to, e.g. `MUSIC_PAGE_TYPE_ALBUM`. */
    private fun JsonObject.musicPageType(): String? =
        (this["navigationEndpoint"] as? JsonObject)
            ?.get("browseEndpoint")?.obj()
            ?.get("browseEndpointContextSupportedConfigs")?.obj()
            ?.get("browseEndpointContextMusicConfig")?.obj()
            ?.get("pageType")?.string()

    /** Any `videoId` beneath this renderer, for rows without `playlistItemData`. */
    private fun JsonObject.firstVideoId(): String? {
        fun find(node: JsonElement): String? = when (node) {
            is JsonObject -> node["videoId"]?.string()
                ?: node.values.firstNotNullOfOrNull { find(it) }

            is JsonArray -> node.firstNotNullOfOrNull { find(it) }
            else -> null
        }
        return find(this)
    }

    /** Walks the response for video-shaped nodes, whatever page wrapped them. */
    private fun rendererTracks(root: JsonElement): List<Track> {
        val out = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    for (key in VIDEO_KEYS) {
                        (node[key] as? JsonObject)?.toTrack()?.let { track ->
                            if (seen.add(track.id)) out += track
                        }
                    }
                    node.values.forEach(::walk)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)
        return out
    }

    private fun JsonObject.toTrack(): Track? {
        val videoId = this["videoId"]?.string() ?: return null
        val title = this["title"]?.text() ?: return null
        val artist = this["ownerText"]?.text()
            ?: this["longBylineText"]?.text()
            ?: this["shortBylineText"]?.text()
            ?: "YouTube Music"
        val artwork = (this["thumbnail"]?.obj()?.get("thumbnails") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("url")?.string() }
            ?.lastOrNull()
        return Track(
            id = "ytmusic-$videoId",
            title = title,
            artist = artist,
            album = null,
            artworkUri = artwork,
            mediaUri = null,
            durationMs = parseClock(this["lengthText"]?.text()),
            sourceId = SourceId.YOUTUBE_MUSIC,
            videoId = videoId,
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
        )
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    /** Reads `{"simpleText": "x"}` or `{"runs": [{"text": "x"}, …]}`. */
    private fun JsonElement?.text(): String? {
        val obj = this as? JsonObject ?: return (this as? JsonPrimitive)?.contentOrNull
        (obj["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        val runs = obj["runs"] as? JsonArray ?: return null
        return runs
            .mapNotNull { (it as? JsonObject)?.get("text")?.string() }
            .joinToString("")
            .ifBlank { null }
    }

    /** "3:42" / "1:02:03" -> milliseconds. */
    private fun parseClock(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        var total = 0L
        for (part in value.trim().split(":")) {
            val number = part.toLongOrNull() ?: return 0L
            total = total * 60 + number
        }
        return total * 1_000L
    }

    /** "UNPLAYABLE" -> "unplayable", so the failure line reads like a sentence. */
    private fun String.humanStatus(): String = lowercase().replace('_', ' ')

    private companion object {
        const val BASE_URL = "https://www.youtube.com/youtubei/v1"
        const val STATUS_OK = "OK"
        const val EMBED_URL = "https://www.reddit.com/"
        const val MODEST_BITRATE = 160_000

        /** The public key YouTube's own web client ships with; not a secret. */
        const val PUBLIC_CLIENT_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** "3:42" / "1:02:03", the only subtitle run with no page type. */
        val CLOCK = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

        /**
         * YouTube Music's "songs" search filter (the `params` a music search
         * carries). It restricts the answer to songs, which is why an ordinary
         * video - a gameplay clip, for instance - no longer appears.
         */
        const val MUSIC_SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

        /** Used for search and playlists, where any answer will do. */
        val WEB_PROFILE = InnerTubeClientProfile(
            name = "WEB",
            version = "2.20260114.08.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) " +
                "Gecko/20100101 Firefox/140.0",
        )

        /**
         * Playback clients, tried in this order.
         *
         * These are the identities that still answer with plain `url` fields
         * instead of a ciphered one. WEB_REMIX, WEB_CREATOR and the TV clients
         * are deliberately absent: they now require a PoToken and signature
         * solving, which this project does not ship. A track that only those
         * clients would serve is reported as unavailable rather than worked
         * around.
         */
        val PLAYBACK_CLIENTS = listOf(
            InnerTubeClientProfile(
                name = "VISIONOS",
                version = "0.1",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
                osName = "visionOS",
                osVersion = "1.3.21O771",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
            ),
            InnerTubeClientProfile(
                name = "ANDROID_VR",
                version = "1.65.10",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                    "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = 32,
                includeUserAgentInContext = true,
            ),
            InnerTubeClientProfile(
                name = "ANDROID_VR",
                version = "1.43.32",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
                    "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; " +
                    "Cronet/107.0.5284.2)",
                osName = "Android",
                osVersion = "12",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = 32,
                includeUserAgentInContext = true,
            ),
            InnerTubeClientProfile(
                name = "IOS",
                version = "21.03.1",
                userAgent = "com.google.ios.youtube/21.03.1 " +
                    "(iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
                osVersion = "18.2.22C152",
            ),
            InnerTubeClientProfile(
                name = "IOS",
                version = "21.03.3",
                userAgent = "com.google.ios.youtube/21.03.3 " +
                    "(iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
                osName = "iPadOS",
                osVersion = "17.7.10.21H450",
                deviceMake = "Apple",
                deviceModel = "iPad7,6",
            ),
        )

        /** Node names that carry a playable video in search, browse and playlists. */
        val VIDEO_KEYS = listOf(
            "videoRenderer",
            "compactVideoRenderer",
            "playlistVideoRenderer",
            "gridVideoRenderer",
        )
    }
}
