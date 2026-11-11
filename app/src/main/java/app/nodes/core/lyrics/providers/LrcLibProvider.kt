/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

/**
 * LRCLIB (lrclib.net) — a public, keyless LRC index that returns both a synced
 * and a plain version of every song it knows.
 *
 * `/api/get` is an exact-match endpoint: title, artist and (if known) duration
 * all have to line up, which is exactly the validation the player wants. When it
 * 404s, the looser `/api/search` results are filtered locally instead of being
 * taken on trust.
 *
 * Terms: https://lrclib.net — the API is free for non-commercial use and asks
 * callers to identify themselves with a User-Agent, which is what [USER_AGENT]
 * is for. See docs/LYRICS.md.
 */
class LrcLibProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = "lrclib"
    override val displayName: String = "LRCLIB"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "LRCLIB request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        exact(query)?.let { return it }
        val candidates = search(query)
        return candidates
            .mapNotNull { track -> track.toPayload(query) }
            .minByOrNull { it.first }
            ?.second
            ?: LyricsPayload.NotFound
    }

    private fun exact(query: LyricsQuery): LyricsPayload? {
        val url = "$BASE/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", query.title)
            .addQueryParameter("artist_name", query.artist)
            .apply { query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) } }
            .apply { if (query.durationSec > 0) addQueryParameter("duration", query.durationSec.toString()) }
            .build()
        val body = get(url.toString()) ?: return null
        val track = runCatching { json.decodeFromString<LrcLibTrack>(body) }.getOrNull() ?: return null
        return track.toPayload(query)?.second
    }

    private fun search(query: LyricsQuery): List<LrcLibTrack> {
        val url = "$BASE/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", query.title)
            .addQueryParameter("artist_name", query.artist)
            .build()
        val body = get(url.toString()) ?: return emptyList()
        return runCatching { json.decodeFromString<List<LrcLibTrack>>(body) }.getOrDefault(emptyList())
    }

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

    /** Ranked payload: lower is a better match. */
    private fun LrcLibTrack.toPayload(query: LyricsQuery): Pair<Int, LyricsPayload>? {
        if (instrumental) return null
        val synced = syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = plainLyrics?.takeIf { it.isNotBlank() }
        val raw = synced ?: plain ?: return null
        val seconds = duration?.toInt() ?: 0
        val drift = if (query.durationSec > 0 && seconds > 0) {
            abs(seconds - query.durationSec)
        } else {
            0
        }
        // Anything more than 15s off is another version of the song.
        if (drift > MAX_DRIFT_SEC) return null
        return drift to LyricsPayload.Found(
            raw = raw,
            syncedHint = synced != null,
            title = trackName,
            artist = artistName,
            durationSec = seconds,
        )
    }

    @Serializable
    private data class LrcLibTrack(
        val id: Long? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    private companion object {
        const val BASE = "https://lrclib.net"
        const val MAX_DRIFT_SEC = 15

        /** LRCLIB asks clients to identify themselves; also keeps us out of bot blocks. */
        const val USER_AGENT = "SpartialMusic/1.0 (https://github.com/NullTrigger/Spartial-Music)"
    }
}
