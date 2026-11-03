/*
 * Copyright (C) 2026 The Nodes Authors.
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

/**
 * Lyrics.ovh — a free, keyless plain-text lyrics API.
 *
 * It answers `/v1/{artist}/{title}` with one `lyrics` string and no timestamps,
 * so it is the last provider in the default order: it is useful when every
 * timed index misses, not a replacement for them. Terms and rate limits live at
 * https://lyrics.ovh — see docs/LYRICS.md.
 */
class LyricsOvhProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = LyricsProviders.LYRICS_OVH
    override val displayName: String = "Lyrics.ovh"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "Lyrics.ovh request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val artist = query.artist.trim()
        val title = query.title.trim()
        if (artist.isEmpty() || title.isEmpty()) return LyricsPayload.NotFound

        val url = "$BASE/v1".toHttpUrl().newBuilder()
            .addPathSegment(artist)
            .addPathSegment(title)
            .build()
        val body = get(url.toString()) ?: return LyricsPayload.NotFound
        val parsed = runCatching { json.decodeFromString<LyricsOvhResponse>(body) }
            .getOrNull()
            ?: return LyricsPayload.NotFound
        val raw = cleanLyricsText(parsed.lyrics) ?: return LyricsPayload.NotFound

        return LyricsPayload.Found(
            raw = raw,
            syncedHint = false,
            title = title,
            artist = artist,
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
    private data class LyricsOvhResponse(
        val lyrics: String? = null,
    )

    private companion object {
        const val BASE = "https://api.lyrics.ovh"
        const val USER_AGENT = "Nodes/1.0 (https://github.com/nodes-app/nodes)"
    }
}
