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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

/**
 * SimpMusic's public lyrics API.
 *
 * It is keyed by the YouTube video id, which makes it the best match for tracks
 * that came from YouTube Music, and it falls back to a text search for anything
 * else. Terms and the API description live at https://api-lyrics.simpmusic.org —
 * see docs/LYRICS.md.
 */
class SimpMusicProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = "simp_music"
    override val displayName: String = "SimpMusic"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "SimpMusic request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val byId = query.videoId
            ?.takeIf { it.isNotBlank() }
            ?.let { get("$BASE$it") }
            .orEmpty()
        val candidates = byId.ifEmpty { get("${BASE}search?q=${encode(query.title, query.artist)}") }
        val best = candidates
            .filter { it.hasText() }
            .let { list ->
                if (query.durationSec > 0) {
                    list.filter { track ->
                        val seconds = track.durationSeconds ?: 0
                        seconds == 0 || abs(seconds - query.durationSec) <= MAX_DRIFT_SEC
                    }
                } else {
                    list
                }
            }
            .minByOrNull { track ->
                val seconds = track.durationSeconds ?: 0
                if (query.durationSec > 0 && seconds > 0) abs(seconds - query.durationSec) else 0
            }
            ?: return LyricsPayload.NotFound

        val synced = best.syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = best.plainLyrics?.takeIf { it.isNotBlank() }
        val raw = synced ?: plain ?: return LyricsPayload.NotFound
        return LyricsPayload.Found(
            raw = raw,
            syncedHint = synced != null,
            title = best.songTitle,
            artist = best.artistName,
            durationSec = best.durationSeconds ?: 0,
        )
    }

    private fun get(url: String): List<SimpMusicTrack> {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val parsed = json.decodeFromString<SimpMusicResponse>(body)
                if (parsed.success) parsed.data else emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(title: String, artist: String): String =
        java.net.URLEncoder.encode("$title $artist", "UTF-8")

    private fun SimpMusicTrack.hasText(): Boolean =
        !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    @Serializable
    private data class SimpMusicResponse(
        val success: Boolean = false,
        val data: List<SimpMusicTrack> = emptyList(),
    )

    @Serializable
    private data class SimpMusicTrack(
        val id: String? = null,
        val videoId: String? = null,
        @SerialName("songTitle") val songTitle: String? = null,
        @SerialName("artistName") val artistName: String? = null,
        @SerialName("albumName") val albumName: String? = null,
        @SerialName("durationSeconds") val durationSeconds: Int? = null,
        val syncedLyrics: String? = null,
        @SerialName("plainLyric") val plainLyrics: String? = null,
        val richSyncLyrics: String? = null,
    )

    private companion object {
        const val BASE = "https://api-lyrics.simpmusic.org/v1/"
        const val MAX_DRIFT_SEC = 15
        const val USER_AGENT = "Nodes/1.0 (https://github.com/nodes-app/nodes)"
    }
}
