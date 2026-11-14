/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsProviders
import app.nodes.core.lyrics.LyricsQuery
import app.nodes.core.lyrics.cleanLyricsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import kotlin.math.abs

/**
 * NetEase Cloud Music's public web lyric endpoints.
 *
 * The search call returns song ids; the lyric call returns an LRC document, so
 * this provider supplies line timing and is tried before the plain-text
 * Lyrics.ovh. The endpoints are the ones the web player uses, not a documented
 * public API: they can change without notice and are rate-limited by NetEase.
 * See docs/LYRICS.md for the terms note.
 */
class NetEaseProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = LyricsProviders.NETEASE
    override val displayName: String = "NetEase Cloud Music"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "NetEase request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val best = selectNetEaseTrack(search(query), query) ?: return LyricsPayload.NotFound
        val id = best.id ?: return LyricsPayload.NotFound
        val raw = lyric(id) ?: return LyricsPayload.NotFound

        return LyricsPayload.Found(
            raw = raw,
            syncedHint = true,
            title = best.name,
            artist = best.artists.mapNotNull { it.name }.joinToString(", ").ifBlank { null },
            durationSec = ((best.duration ?: 0L) / 1_000L).toInt(),
        )
    }

    private fun search(query: LyricsQuery): List<NetEaseTrack> {
        val term = listOf(query.title, query.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val url = "$BASE/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", term)
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "10")
            .addQueryParameter("offset", "0")
            .build()
        val body = get(url.toString()) ?: return emptyList()
        val parsed = runCatching { json.decodeFromString<NetEaseSearchResponse>(body) }
            .getOrNull()
            ?: return emptyList()
        if (parsed.code != null && parsed.code != 200) return emptyList()
        return parsed.result?.songs.orEmpty()
    }

    private fun lyric(id: Long): String? {
        val url = "$BASE/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", id.toString())
            .addQueryParameter("lv", "1")
            .addQueryParameter("kv", "1")
            .addQueryParameter("tv", "-1")
            .build()
        val body = get(url.toString()) ?: return null
        val parsed = runCatching { json.decodeFromString<NetEaseLyricResponse>(body) }
            .getOrNull()
            ?: return null
        return cleanLyricsText(parsed.lrc?.lyric)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private companion object {
        const val BASE = "https://music.163.com"
        const val REFERER = "https://music.163.com/"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Picks the search hit whose title, artist and duration best match the track.
 *
 * Search returns covers and live versions next to the studio recording, so the
 * first row is not automatically right. A title overlap is required when the
 * query has a title; the score then prefers an exact title, a matching artist
 * and a close duration. Kept top-level and internal so it can be unit-tested
 * without a network call.
 */
internal fun selectNetEaseTrack(songs: List<NetEaseTrack>, query: LyricsQuery): NetEaseTrack? {
    val wantedTitle = tokens(query.title)
    val candidates = songs.filter { song ->
        song.id != null &&
            durationMatches(song.duration, query.durationSec) &&
            (wantedTitle.isEmpty() || tokens(song.name).intersect(wantedTitle).isNotEmpty())
    }
    if (candidates.isEmpty()) return null
    return candidates.maxByOrNull { song -> score(song, query) }
}

private fun durationMatches(durationMs: Long?, querySeconds: Int): Boolean {
    if (durationMs == null || durationMs <= 0L || querySeconds <= 0) return true
    return abs(durationMs / 1_000L - querySeconds.toLong()) <= MAX_DRIFT_SEC
}

private fun score(song: NetEaseTrack, query: LyricsQuery): Int {
    var score = 0
    if (normalize(song.name) == normalize(query.title)) score += 10
    else if (tokens(song.name).intersect(tokens(query.title)).isNotEmpty()) score += 5

    val artist = normalize(song.artists.mapNotNull { it.name }.joinToString(" "))
    val wantedArtist = normalize(query.artist)
    if (artist.isNotEmpty() && wantedArtist.isNotEmpty()) {
        if (artist.contains(wantedArtist) || wantedArtist.contains(artist)) score += 4
        else if (tokens(artist).intersect(tokens(wantedArtist)).isNotEmpty()) score += 2
    }

    val seconds = (song.duration ?: 0L) / 1_000L
    if (seconds > 0L && query.durationSec > 0) {
        val drift = abs(seconds - query.durationSec.toLong())
        if (drift <= 3L) score += 3 else if (drift <= MAX_DRIFT_SEC) score += 1
    }
    return score
}

private fun normalize(text: String?): String =
    text.orEmpty()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private fun tokens(text: String?): Set<String> =
    normalize(text)
        .split(' ')
        .filter { it.isNotBlank() && it !in IGNORED_TOKENS }
        .toSet()

private const val MAX_DRIFT_SEC = 15L

private val IGNORED_TOKENS = setOf("the", "and", "feat", "ft", "official", "video")

@Serializable
internal data class NetEaseSearchResponse(
    val code: Int? = null,
    val result: NetEaseResult? = null,
)

@Serializable
internal data class NetEaseResult(
    val songs: List<NetEaseTrack> = emptyList(),
)

@Serializable
internal data class NetEaseTrack(
    val id: Long? = null,
    val name: String? = null,
    val artists: List<NetEaseArtist> = emptyList(),
    val album: NetEaseAlbum? = null,
    val duration: Long? = null,
)

@Serializable
internal data class NetEaseArtist(
    val name: String? = null,
)

@Serializable
internal data class NetEaseAlbum(
    val name: String? = null,
)

@Serializable
internal data class NetEaseLyricResponse(
    val code: Int? = null,
    val lrc: NetEaseLrc? = null,
)

@Serializable
internal data class NetEaseLrc(
    val lyric: String? = null,
)
