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
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * LyricsPlus: a community lyrics aggregator with several volunteer-run
 * mirrors, answering line-synced and word-synced lines with per-line metadata.
 *
 * The mirrors come and go, so they are tried in order with the last one that
 * answered tried first next time - the same resilience the reference client
 * (`LyricsPlusProvider` in the Glossy tree) implements. A word-synced line
 * carries a `syllabus` array, which is converted into this app's inline
 * enhanced-LRC dialect.
 */
class LyricsPlusProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = "lyrics_plus"
    override val displayName: String = "LyricsPlus"

    @Volatile
    private var lastWorkingServer: String? = null

    private fun prioritisedServers(): List<String> {
        val last = lastWorkingServer
        return if (last != null && last in SERVERS) {
            listOf(last) + SERVERS.filter { it != last }
        } else {
            SERVERS
        }
    }

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "LyricsPlus request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val title = query.title.trim()
        val artist = query.artist.trim()
        if (title.isBlank() || artist.isBlank()) return LyricsPayload.NotFound

        // Each mirror is asked in turn; the first one with lyrics wins and is
        // remembered so a later lookup does not re-walk the dead ones.
        for (server in prioritisedServers()) {
            val response = fetchFromServer(server, title, artist, query)
                ?.takeIf { it.lyrics.isNotEmpty() } ?: continue
            lastWorkingServer = server
            return toPayload(response, query)
        }
        return LyricsPayload.NotFound
    }

    private fun fetchFromServer(
        server: String,
        title: String,
        artist: String,
        query: LyricsQuery,
    ): Response? {
        val url = (server.trimEnd('/') + "/v2/lyrics/get").toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                // Seconds: dividing the millisecond duration again by 1000 sent
                // "0" for every real song, so the server had nothing to match on.
                if (query.durationSec > 0) addQueryParameter("duration", query.durationSec.toString())
                query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album", it) }
            }
            .build()
        val body = get(url.toString()) ?: return null
        return runCatching { json.decodeFromString<Response>(body) }.getOrNull()
    }

    private fun toPayload(response: Response, query: LyricsQuery): LyricsPayload {
        val metadataTitle = response.metadata?.title
        val synced = response.lyrics.all { line -> line.syllabus.isNullOrEmpty() } == false
        val raw = buildString {
            response.lyrics.forEach { line ->
                append(formatStamp(line.time))
                val words = line.syllabus.orEmpty()
                if (words.size > 1) {
                    words.forEach { word ->
                        append(formatWordTag(word.time))
                        append(word.text).append(' ')
                    }
                } else {
                    append(line.text)
                }
                append('\n')
            }
        }.trim()
        if (raw.isEmpty()) return LyricsPayload.NotFound

        return LyricsPayload.Found(
            raw = raw,
            syncedHint = synced,
            // The server's own title when it has one; otherwise the query's,
            // which is what the endpoint matched on.
            title = metadataTitle ?: query.title,
            artist = query.artist,
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

    private fun formatStamp(ms: Long): String {
        val total = (ms / 10).coerceAtLeast(0)
        val minutes = total / 6000
        val rest = total % 6000
        return "[%02d:%02d.%02d]".format(Locale.US, minutes, rest / 100, rest % 100)
    }


    /** `<m:ss.xx>`, the tag shape [app.nodes.core.lyrics.LyricsParser] reads. */
    private fun formatWordTag(ms: Long): String {
        val total = (ms / 10).coerceAtLeast(0)
        return "<%d:%05.2f>".format(Locale.US, total / 6000, total % 6000 / 100.0)
    }

    @Serializable
    private data class Response(
        val lyrics: List<Line> = emptyList(),
        val metadata: Metadata? = null,
    )

    @Serializable
    private data class Metadata(val title: String? = null)

    @Serializable
    private data class Line(
        val time: Long = 0,
        val duration: Long = 0,
        val text: String = "",
        val syllabus: List<Syllable>? = null,
    )

    @Serializable
    private data class Syllable(
        val time: Long = 0,
        val duration: Long = 0,
        val text: String = "",
    )

    private companion object {
        const val USER_AGENT =
            "SpartialMusic/1.0 (https://github.com/NullTrigger/Spartial-Music)"

        /**
         * The community mirrors, in the order the reference client lists them.
         * They are volunteers; expect some to be down at any time.
         */
        val SERVERS = listOf(
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus-seven.vercel.app",
        )
    }
}
