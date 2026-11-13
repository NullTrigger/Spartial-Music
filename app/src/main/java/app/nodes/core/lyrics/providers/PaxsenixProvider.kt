/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

/**
 * Paxsenix (`lyrics.paxsenix.org`): a community proxy for Apple Music's rich
 * syllable-synced lyrics.
 *
 * Two hops, both ported from the Paxsenix module in the Glossy tree:
 *
 *  1. The song is searched on Apple's own catalogue. That needs the web
 *     player's JWT, which is scraped from the page's bundle - [AppleToken]
 *     fetches it once, keeps it until Apple rejects it (401), then scrapes a
 *     fresh one. No account, no purchase: the same token the web player sends.
 *  2. The chosen track id is handed to the proxy, which answers with TTML when
 *     Apple has one, and the proxy's own line/word formats when it does not.
 */
class PaxsenixProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = "paxsenix"
    override val displayName: String = "Paxsenix (Apple Music)"

    private val appleToken = AppleToken()

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "Paxsenix request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val title = cleanTitle(query.title)
        val artist = cleanArtist(query.artist)
        if (title.isBlank()) return LyricsPayload.NotFound

        val songs = search("$title $artist").ifEmpty { search(title) }
        val ranked = songs
            .map { it to score(it, title, artist, query.durationSec) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(10)
        if (ranked.isEmpty()) return LyricsPayload.NotFound

        // Try the best matches until one returns lyrics. Word timing (quality
        // 3) is the ceiling, so stop as soon as it is reached.
        var best: String? = null
        var bestQuality = 0
        var bestMatch: AppleSong? = null
        for ((song, _) in ranked) {
            val payload = fetchLyrics(song.id) ?: continue
            val quality = payload.quality
            if (quality > bestQuality) {
                bestQuality = quality
                best = payload.lrc
                bestMatch = song
            }
            if (bestQuality == 3) break
        }
        val lrc = best ?: return LyricsPayload.NotFound
        return LyricsPayload.Found(
            raw = lrc,
            syncedHint = bestQuality >= 2,
            title = bestMatch?.trackName ?: title,
            artist = bestMatch?.artistName ?: artist,
            durationSec = bestMatch?.durationSec ?: query.durationSec,
        )
    }

    // ---- Apple catalogue search -------------------------------------------

    private fun search(term: String): List<AppleSong> {
        val token = appleToken.token() ?: return emptyList()
        return searchWithToken(token, term).ifEmpty {
            // A stale token answers 401; one refresh is worth trying.
            appleToken.clear()
            searchWithToken(appleToken.token() ?: return emptyList(), term)
        }
    }

    private fun searchWithToken(token: String, term: String): List<AppleSong> {
        val url = APPLE_CATALOG.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("term", term)
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "25")
            .addQueryParameter("l", "en-US")
            .addQueryParameter("platform", "web")
            .build()
        val body = appleGet(url.toString(), token) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<AppleSearchResponse>(body) }.getOrNull()
            ?: return emptyList()
        return parsed.results?.songs?.data.orEmpty().mapNotNull { song ->
            val attr = song.attributes ?: return@mapNotNull null
            AppleSong(
                id = song.id,
                trackName = attr.name ?: return@mapNotNull null,
                artistName = attr.artistName.orEmpty(),
                albumName = attr.albumName,
                durationSec = attr.durationInMillis?.let { it / 1000 },
            )
        }
    }

    private fun appleGet(url: String, token: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", BROWSER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun score(song: AppleSong, title: String, artist: String, querySec: Int): Double {
        var score = 0.0
        song.durationSec?.let { d ->
            if (querySec > 0) {
                when (abs(d - querySec)) {
                    in 0..2 -> score += 100.0
                    in 3..5 -> score += 50.0
                    in 6..10 -> score += 10.0
                    else -> score -= 50.0
                }
            }
        }
        val resultTitle = song.trackName.lowercase().replace(CLEANUP, "").trim()
        val wantedTitle = title.lowercase().trim()
        when {
            resultTitle == wantedTitle -> score += 80.0
            resultTitle.contains(wantedTitle) || wantedTitle.contains(resultTitle) -> score += 40.0
        }
        val resultArtist = song.artistName.lowercase()
        if (resultArtist.contains(artist.lowercase())) score += 50.0
        return score
    }

    // ---- proxy lyrics -----------------------------------------------------

    /** Returns the payload and its quality: 1 plain, 2 line-synced, 3 words. */
    private fun fetchLyrics(id: String): Timed? {
        val url = PROXY_BASE.toHttpUrl().newBuilder()
            .addPathSegment("apple-music")
            .addPathSegment("lyrics")
            .addQueryParameter("id", id)
            .build()
        val body = get(url.toString()) ?: return null
        val response = runCatching { json.decodeFromString<LyricsResponse>(body) }.getOrNull()
            ?: return null

        response.ttmlContent?.takeIf { it.isNotBlank() }?.let { ttml ->
            val lines = Ttml.parse(ttml)
            if (lines.isNotEmpty()) {
                return Timed(Ttml.toEnhancedLrc(lines), if (Ttml.isWordSynced(lines)) 3 else 2)
            }
        }
        response.elrcMultiPerson?.takeIf { it.isNotBlank() }?.let { return Timed(it, 2) }
        response.elrc?.takeIf { it.isNotBlank() }?.let { return Timed(it, 2) }
        response.plain?.takeIf { it.isNotBlank() }?.let { return Timed(it, 1) }

        if (response.content.isEmpty()) return null
        // Line arrays carry per-line timestamps; word arrays carry syllables.
        val words = response.content.firstOrNull()?.text?.firstOrNull()?.timestamp != null
        return Timed(
            response.content.joinToString("\n") { line ->
                formatStamp(line.timestamp) + line.text.joinToString(" ") { it.text }
            },
            if (words) 3 else 2,
        )
    }

    private data class Timed(val lrc: String, val quality: Int)

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun formatStamp(ms: Long): String {
        val total = (ms / 10).coerceAtLeast(0)
        return "[%02d:%02d.%02d]".format(Locale.US, total / 6000, total % 6000 / 100, total % 100)
    }

    // ---- title cleaning -----------------------------------------------------

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        TITLE_JUNK.forEach { cleaned = cleaned.replace(it, "") }
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        val trimmed = artist.trim()
        for (separator in ARTIST_SEPARATORS) {
            if (trimmed.contains(separator, ignoreCase = true)) {
                return trimmed.split(Regex(Regex.escape(separator)), limit = 2)[0].trim()
            }
        }
        return trimmed
    }

    /** The web player's JWT, scraped once and reused until Apple expires it. */
    private inner class AppleToken {

        private val mutex = Mutex()
        private var cached: String? = null

        fun token(): String? = cached ?: mutex.withLock {
            cached ?: scrape()?.also { cached = it }
        }

        fun clear() {
            cached = null
        }

        /**
         * The bundle URL changes with every Apple deploy, so the page is read
         * for the current `index~<hash>.js` path and the token (a JWS: three
         * base64url segments) is taken from the bundle text.
         */
        private fun scrape(): String? {
            val page = get("https://beta.music.apple.com") ?: return null
            val jsPath = INDEX_JS.find(page)?.value ?: return null
            val bundle = get("https://beta.music.apple.com$jsPath") ?: return null
            return TOKEN.find(bundle)?.value
        }
    }

    // ---- wire shapes -------------------------------------------------------

    @Serializable
    private data class AppleSearchResponse(val results: Results? = null) {
        @Serializable
        data class Results(val songs: Songs? = null)

        @Serializable
        data class Songs(val data: List<Song> = emptyList())

        @Serializable
        data class Song(val id: String, val attributes: Attributes? = null)

        @Serializable
        data class Attributes(
            val name: String? = null,
            val artistName: String? = null,
            val albumName: String? = null,
            val durationInMillis: Long? = null,
        )
    }

    @Serializable
    private data class LyricsResponse(
        val type: String? = null,
        val ttmlContent: String? = null,
        val elrcMultiPerson: String? = null,
        val elrc: String? = null,
        val plain: String? = null,
        val content: List<Line> = emptyList(),
    ) {
        @Serializable
        data class Line(
            val timestamp: Long = 0,
            val background: Boolean = false,
            val oppositeTurn: Boolean = false,
            val text: List<Word> = emptyList(),
        )

        @Serializable
        data class Word(val text: String, val timestamp: Long = 0, val endtime: Long = 0)
    }

    private data class AppleSong(
        val id: String,
        val trackName: String,
        val artistName: String,
        val albumName: String?,
        val durationSec: Int?,
    )

    private companion object {
        const val PROXY_BASE = "https://lyrics.paxsenix.org"
        const val APPLE_CATALOG = "https://amp-api.music.apple.com/v1/catalog/us"
        const val USER_AGENT =
            "SpartialMusic/1.0 (https://github.com/NullTrigger/Spartial-Music)"
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0"

        private val INDEX_JS = Regex("""/assets/index~[^/\s"']+\.js""")
        private val TOKEN = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
        private val CLEANUP = Regex("""\s*\(.*?\)|\s*\[.*?]""")

        private val TITLE_JUNK = listOf(
            Regex("""\s*\(.*?(official|video|audio|lyrics?|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\[.*?(official|video|audio|lyrics?|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?]""", RegexOption.IGNORE_CASE),
            Regex("""\s*【.*?】"""),
            Regex("""\s*\|.*$"""),
            Regex("""\s*-\s*(official|video|audio|lyrics?|visualizer).*$""", RegexOption.IGNORE_CASE),
            Regex("""\s*\((?:feat|ft)\..*?\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*(?:feat|ft)\..*$""", RegexOption.IGNORE_CASE),
        )

        private val ARTIST_SEPARATORS = listOf(
            " & ", " and ", ", ", " x ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ",
        )

        @Suppress("unused")
    }
}
