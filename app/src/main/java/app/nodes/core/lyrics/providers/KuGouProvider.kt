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
import kotlin.math.abs

/**
 * KuGou's public lyric endpoints: a song search, a lyric search (by hash or
 * keyword) and a base64 lyric download. Especially strong for Asian and
 * international tracks the Western indexes do not know.
 *
 * Wire behaviour ported from the KuGou module Glossy ships (itself from
 * ViMusic): the same endpoints, parameters and scoring. The responses here are
 * line-synced LRC, which is what [app.nodes.core.lyrics.LyricsParser] reads.
 */
class KuGouProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : LyricsProvider {

    override val id: String = "kugou"
    override val displayName: String = "KuGou"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(query) }
            .getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                LyricsPayload.Failed(error.message ?: "KuGou request failed")
            }
    }

    private fun fetchBlocking(query: LyricsQuery): LyricsPayload {
        val keyword = Keyword(
            title = normalizeTitle(query.title),
            artist = normalizeArtist(query.artist),
            album = query.album,
        )
        val durationSec = query.durationSec.takeIf { it > 0 } ?: -1

        // Best song first, then the keyword index if that dead-ends - the same
        // two-step order the reference implementation uses.
        val candidate = bestCandidate(searchSongs(keyword), durationSec, keyword)
            ?: searchLyricsByKeyword(keyword, durationSec).candidates.firstOrNull()
            ?: return LyricsPayload.NotFound

        val raw = download(candidate.id, candidate.accesskey)
            ?: return LyricsPayload.NotFound
        val lrc = normalize(raw)

        val payloadTitle = candidate.songName ?: keyword.title
        val payloadArtist = candidate.artistName ?: keyword.artist
        return LyricsPayload.Found(
            raw = lrc,
            syncedHint = true,
            title = payloadTitle,
            artist = payloadArtist,
            durationSec = (candidate.durationSec ?: 0),
        )
    }

    // ---- network ----------------------------------------------------------

    private fun searchSongs(keyword: Keyword): SearchSongResponse? {
        val url = "https://mobileservice.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
            .addQueryParameter("version", "9108")
            .addQueryParameter("plat", "0")
            .addQueryParameter("pagesize", PAGE_SIZE.toString())
            .addQueryParameter("showtype", "0")
            .addQueryParameter("keyword", searchQuery(keyword))
            .build()
        val body = get(url.toString()) ?: return null
        return runCatching { json.decodeFromString<SearchSongResponse>(body) }.getOrNull()
    }

    private fun searchLyricsByKeyword(keyword: Keyword, durationSec: Int): SearchLyricsResponse {
        val url = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "pc")
            .addQueryParameter("keyword", searchQuery(keyword))
            .apply { if (durationSec > 0) addQueryParameter("duration", (durationSec * 1000).toString()) }
            .build()
        return get(url.toString())
            ?.let { body -> runCatching { json.decodeFromString<SearchLyricsResponse>(body) }.getOrNull() }
            ?: SearchLyricsResponse(emptyList())
    }

    private fun searchLyricsByHash(hash: String): SearchLyricsResponse {
        val url = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("man", "yes")
            .addQueryParameter("client", "pc")
            .addQueryParameter("hash", hash)
            .build()
        return get(url.toString())
            ?.let { body -> runCatching { json.decodeFromString<SearchLyricsResponse>(body) }.getOrNull() }
            ?: SearchLyricsResponse(emptyList())
    }

    /** base64 body holding the LRC document. */
    private fun download(id: Long, accessKey: String): String? {
        val url = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8")
            .addQueryParameter("client", "pc")
            .addQueryParameter("ver", "1")
            .addQueryParameter("id", id.toString())
            .addQueryParameter("accesskey", accessKey)
            .build()
        val body = get(url.toString()) ?: return null
        val response = runCatching { json.decodeFromString<DownloadLyricsResponse>(body) }.getOrNull()
            ?: return null
        return runCatching {
            java.util.Base64.getDecoder().decode(response.content).decodeToString()
        }.getOrNull()
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

    // ---- matching ---------------------------------------------------------

    private fun bestCandidate(
        songs: SearchSongResponse?,
        durationSec: Int,
        keyword: Keyword,
    ): Candidate? {
        val best = songs?.data?.info?.maxByOrNull { song ->
            score(song.songname.takeIf { it.isNotEmpty() } ?: song.filename,
                song.singername.takeIf { it.isNotEmpty() } ?: song.filename,
                song.duration, durationSec, keyword)
        } ?: return null

        if (durationSec == -1 || abs(best.duration - durationSec) <= DURATION_TOLERANCE) {
            // Hash lookup returns richer metadata, so prefer it when it lands.
            searchLyricsByHash(best.hash).candidates.firstOrNull()?.let { hashHit ->
                return Candidate(
                    id = hashHit.id,
                    accesskey = hashHit.accesskey,
                    durationSec = hashHit.duration.toInt(),
                    songName = best.songname.ifBlank { null },
                    artistName = best.singername.ifBlank { null },
                )
            }
        }
        return null
    }

    private fun score(title: String, artist: String, durationSec: Int, querySec: Int, keyword: Keyword): Double {
        var score = 0.0
        score += similarity(keyword.title, title)
        score += similarity(keyword.artist, artist)
        if (querySec > 0) {
            val diff = abs(durationSec - querySec)
            if (diff <= DURATION_TOLERANCE) score += 1.0
            score -= diff * 0.01
        }
        return score
    }

    private fun similarity(a: String, b: String): Double {
        val s1 = a.trim().lowercase()
        val s2 = b.trim().lowercase()
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1.contains(s2) || s2.contains(s1)) return 0.8
        return 1.0 - (levenshtein(s1, s2).toDouble() / maxOf(s1.length, s2.length))
    }

    private fun levenshtein(a: String, b: String): Int {
        val matrix = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in a.indices) matrix[i + 1][0] = i + 1
        for (j in b.indices) matrix[0][j + 1] = j + 1
        for (i in a.indices) for (j in b.indices) {
            matrix[i + 1][j + 1] = minOf(
                matrix[i][j + 1] + 1,
                matrix[i + 1][j] + 1,
                matrix[i][j] + if (a[i] == b[j]) 0 else 1,
            )
        }
        return matrix[a.length][b.length]
    }

    // ---- text shaping ------------------------------------------------------

    private fun searchQuery(keyword: Keyword): String = buildString {
        append(keyword.title)
        append(" - ")
        append(keyword.artist)
        if (!keyword.album.isNullOrBlank()) {
            append(' ')
            append(keyword.album)
        }
    }

    private fun normalizeTitle(title: String) = title
        .replace(BRACKETED, "")
        .replace("（.*）".toRegex(), "")
        .replace("「.*」".toRegex(), "")
        .replace("『.*』".toRegex(), "")
        .replace("《.*》".toRegex(), "")
        .replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String) = artist
        .replace(", ", "、")
        .replace(" & ", "、")
        .replace(".", "")
        .replace("和", "、")
        .replace(BRACKETED, "")

    /**
     * Strips the credit header/footer ("作词 :", "Composer :") the downloads
     * carry, using the same accepted/banned line shapes as the reference.
     */
    private fun normalize(raw: String): String {
        val lines = raw.lines().filter { ACCEPTED.matches(it) }
        var headCut = 0
        for (i in minOf(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
            if (BANNED.matches(lines[i])) {
                headCut = i + 1
                break
            }
        }
        var tailCut = 0
        for (i in minOf(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
            if (BANNED.matches(lines[lines.lastIndex - i])) {
                tailCut = i + 1
                break
            }
        }
        return lines.drop(headCut).dropLast(tailCut).joinToString("\n")
    }

    private data class Keyword(val title: String, val artist: String, val album: String?)

    /** Normalised candidate: the lyric id plus what the song search knew. */
    private data class Candidate(
        val id: Long,
        val accesskey: String,
        val durationSec: Int?,
        val songName: String?,
        val artistName: String?,
    )

    @Serializable
    private data class SearchSongResponse(val data: Data = Data()) {
        @Serializable
        data class Data(val info: List<Info> = emptyList())

        @Serializable
        data class Info(
            val duration: Int = 0,
            val hash: String = "",
            val songname: String = "",
            val singername: String = "",
            val filename: String = "",
        )
    }

    @Serializable
    private data class SearchLyricsResponse(val candidates: List<WireCandidate> = emptyList()) {
        @Serializable
        data class WireCandidate(
            val id: Long = 0,
            val duration: Long = 0,
            val accesskey: String = "",
        )
    }

    @Serializable
    private data class DownloadLyricsResponse(val content: String)

    private companion object {
        const val PAGE_SIZE = 8
        const val HEAD_CUT_LIMIT = 30
        const val DURATION_TOLERANCE = 8
        const val USER_AGENT =
            "SpartialMusic/1.0 (https://github.com/NullTrigger/Spartial-Music)"

        private val ACCEPTED = Regex("""^\[(\d\d):(\d\d)\.(\d{2,3})\].*""")
        private val BANNED = Regex(""".+].+[:：].+""")
        private val BRACKETED = Regex("""\(.*\)""")
    }
}
