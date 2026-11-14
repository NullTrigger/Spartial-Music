/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * BetterLyrics (`lyrics-api.boidu.dev`): one keyed-by-title endpoint that
 * answers with an Apple-style TTML document, the richest format in general
 * circulation - syllable timing, background vocals and duet voices included.
 *
 * The endpoint does its own matching server-side from title, artist and
 * duration, so the payload carries no metadata of its own; the query's own
 * identity is echoed back for the mismatch guard, which is honest because a
 * wrong-song answer here is the endpoint's mistake and no client field could
 * have caught it.
 */
class BetterLyricsProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = "better_lyrics"
    override val displayName: String = "BetterLyrics"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "BetterLyrics request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val title = query.title.trim()
        val artist = query.artist.trim()
        if (title.isBlank() || artist.isBlank()) return LyricsPayload.NotFound

        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                if (query.durationSec > 0) addQueryParameter("d", query.durationSec.toString())
                query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("al", it) }
            }
            .build()
        val body = get(url.toString()) ?: return LyricsPayload.NotFound
        val envelope = runCatching { json.decodeFromString<TTMLResponse>(body) }.getOrNull()
        val ttml = envelope?.ttml?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return LyricsPayload.NotFound

        val lines = Ttml.parse(ttml)
        if (lines.isEmpty()) return LyricsPayload.NotFound

        return LyricsPayload.Found(
            raw = Ttml.toEnhancedLrc(lines),
            syncedHint = true,
            title = title,
            artist = artist,
            durationSec = query.durationSec,
        )
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

    @Serializable
    private data class TTMLResponse(val ttml: String? = null)

    private companion object {
        const val BASE = "https://lyrics-api.boidu.dev"
        const val USER_AGENT =
            "SpartialMusic/1.0 (https://github.com/NullTrigger/Spartial-Music)"
    }
}
