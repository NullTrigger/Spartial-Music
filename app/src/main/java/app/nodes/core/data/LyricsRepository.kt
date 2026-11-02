/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import android.content.Context
import app.nodes.core.crash.AppLogger
import app.nodes.core.lyrics.LyricsParser
import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsProviders
import app.nodes.core.lyrics.LyricsQuery
import app.nodes.core.model.LyricLine
import app.nodes.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** What the player gets back for a track. Empty [lines] means "nothing found". */
data class LyricsResult(
    val lines: List<LyricLine> = emptyList(),
    /** Provider name shown in the lyrics header, when the setting is on. */
    val provider: String? = null,
    val synced: Boolean = false,
    val fromCache: Boolean = false,
)

/**
 * Resolves lyrics for a track, in the order the user arranged in Settings.
 *
 * Providers are tried one at a time with a short budget each, because the point
 * of the order is that a provider the user moved up gets asked first. A synced
 * result wins immediately; when "prefer synced" is on, a plain result is held
 * while the remaining providers are asked, and only used if none of them can
 * time the song.
 *
 * Results live in memory and on disk (`files/lyrics_cache`), keyed by track, so
 * replaying a song costs nothing. A miss is cached too, briefly, so an
 * instrumental does not re-ask every provider on every start. Nothing is
 * uploaded anywhere; the payload is the provider's own text.
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val json: Json,
    private val providers: Set<@JvmSuppressWildcards LyricsProvider>,
) {
    private val memory = ConcurrentHashMap<String, Resolved>()

    suspend fun lyricsFor(track: Track, refresh: Boolean = false): LyricsResult {
        val key = cacheKey(track)

        if (!refresh) {
            memory[key]?.let { return it.toResult(fromCache = true) }
            val cached = try {
                readCache(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppLogger.e("Lyrics", "Reading the lyrics cache failed", error)
                null
            }
            if (cached != null) {
                memory[key] = cached
                return cached.toResult(fromCache = true)
            }
        }

        val resolved = try {
            resolve(
                LyricsQuery(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSec = (track.durationMs / 1_000L).toInt(),
                    videoId = track.videoId,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Throwable, not Exception: a static initialiser failure arrives as
            // ExceptionInInitializerError, which is an Error. Catching only
            // Exception is exactly how 0.1.0 died when a lyrics regex was
            // rejected by Android's regex engine.
            //
            // This runs while a track is playing. A provider, a settings read or
            // the parser failing must cost the lyrics, nothing more - but it is
            // recorded, because "no lyrics" and "the provider crashed" look
            // identical from the player.
            AppLogger.w("Lyrics", "Lookup failed for ${track.id}", error)
            Resolved()
        }
        memory[key] = resolved
        writeCache(key, resolved)
        return resolved.toResult()
    }

    /** Drops every cached payload; the user's own `.lrc` files are untouched. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        memory.clear()
        cacheDir().listFiles()?.forEach { it.delete() }
    }

    private suspend fun resolve(query: LyricsQuery): Resolved {
        val prefs = settings.current()
        val ordered = orderedProviders(prefs)
        if (ordered.isEmpty()) return Resolved()

        var plainFallback: Resolved? = null
        val synced = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            for (provider in ordered) {
                val payload = withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                    safeFetch(provider, query)
                } ?: continue
                if (payload !is LyricsPayload.Found) continue
                if (!matches(payload, query)) continue

                val parsed = LyricsParser.parse(payload.raw)
                if (parsed.lines.isEmpty()) continue

                val resolved = Resolved(
                    raw = payload.raw,
                    providerId = provider.id,
                    displayName = provider.displayName,
                    lines = parsed.lines,
                    synced = parsed.synced,
                )
                if (parsed.synced || !prefs.lyricsPreferSynced) {
                    return@withTimeoutOrNull resolved
                }
                if (plainFallback == null) plainFallback = resolved
            }
            null
        }
        return synced ?: plainFallback ?: Resolved()
    }

    private suspend fun safeFetch(provider: LyricsProvider, query: LyricsQuery): LyricsPayload =
        runCatching { provider.fetch(query) }.getOrElse { error ->
            // A cancelled lookup must stay cancelled, or the timeout would be
            // swallowed and the next provider asked with the clock still running.
            if (error is kotlinx.coroutines.CancellationException) throw error
            AppLogger.w("Lyrics", "Provider ${provider.id} failed", error)
            LyricsPayload.Failed(error.message ?: "Request failed")
        }

    /**
     * Refuses an answer that is clearly another song.
     *
     * Providers match loosely, and a same-named cover or a live version would
     * otherwise be shown under this track's title. A provider that returns no
     * names cannot be judged and is allowed through.
     */
    private fun matches(payload: LyricsPayload.Found, query: LyricsQuery): Boolean {
        if (payload.durationSec > 0 && query.durationSec > 0 &&
            abs(payload.durationSec - query.durationSec) > MAX_DRIFT_SEC
        ) {
            return false
        }
        if (!payload.title.isNullOrBlank()) {
            val wanted = tokens(query.title)
            val answered = tokens(payload.title)
            if (wanted.isNotEmpty() && answered.isNotEmpty() && wanted.intersect(answered).isEmpty()) {
                return false
            }
        }
        if (!payload.artist.isNullOrBlank()) {
            val wanted = query.artist.lowercase(Locale.ROOT).trim()
            val answered = payload.artist.lowercase(Locale.ROOT).trim()
            val namesOverlap = wanted.isNotBlank() && answered.isNotBlank() &&
                (wanted.contains(answered) || answered.contains(wanted))
            if (!namesOverlap &&
                tokens(query.artist).intersect(tokens(payload.artist)).isEmpty()
            ) {
                return false
            }
        }
        return true
    }

    private fun tokens(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && it !in IGNORED_TOKENS }
            .toSet()
    }

    private fun orderedProviders(prefs: AppSettings): List<LyricsProvider> {
        val byId = providers.associateBy { it.id }
        val stored = prefs.lyricsProviderOrder
        val order = stored + LyricsProviders.KNOWN.filterNot { it in stored }
        return order
            .distinct()
            .filterNot { it in prefs.lyricsDisabledProviders }
            .mapNotNull { byId[it] }
    }

    // ---- disk cache -------------------------------------------------------

    private suspend fun readCache(key: String): Resolved? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.isFile) return@withContext null
        val cached = runCatching { json.decodeFromString<CachedLyrics>(file.readText()) }
            .getOrNull() ?: return@withContext null
        val ttl = if (cached.notFound) NOT_FOUND_TTL_MS else FOUND_TTL_MS
        if (System.currentTimeMillis() - cached.savedAt > ttl) return@withContext null
        if (cached.notFound) return@withContext Resolved()

        val parsed = LyricsParser.parse(cached.raw)
        if (parsed.lines.isEmpty()) return@withContext null
        Resolved(
            raw = cached.raw,
            providerId = cached.providerId,
            displayName = providers.firstOrNull { it.id == cached.providerId }?.displayName,
            lines = parsed.lines,
            synced = parsed.synced,
        )
    }

    private suspend fun writeCache(key: String, resolved: Resolved) = withContext(Dispatchers.IO) {
        val entry = if (resolved.lines.isEmpty()) {
            CachedLyrics(notFound = true, savedAt = System.currentTimeMillis())
        } else {
            CachedLyrics(
                raw = resolved.raw,
                providerId = resolved.providerId,
                savedAt = System.currentTimeMillis(),
            )
        }
        runCatching { fileFor(key).writeText(json.encodeToString(entry)) }
        Unit
    }

    private fun cacheDir(): File = File(context.filesDir, "lyrics_cache").apply {
        if (!exists()) mkdirs()
    }

    private fun fileFor(key: String): File = File(cacheDir(), "$key.json")

    private fun cacheKey(track: Track): String = buildString {
        append(track.sourceId.key)
        append('-')
        append(track.videoId ?: track.id)
        append('-')
        append(track.title)
    }
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(80)
        .ifEmpty { "track" }

    private data class Resolved(
        val raw: String = "",
        val providerId: String = "",
        val displayName: String? = null,
        val lines: List<LyricLine> = emptyList(),
        val synced: Boolean = false,
    ) {
        fun toResult(fromCache: Boolean = false) = LyricsResult(
            lines = lines,
            provider = displayName,
            synced = synced,
            fromCache = fromCache,
        )
    }

    @Serializable
    private data class CachedLyrics(
        val raw: String = "",
        val providerId: String = "",
        val notFound: Boolean = false,
        val savedAt: Long = 0L,
    )

    companion object {
        /** One provider's budget; enough for a cold CDN, short enough to skip. */
        private const val PER_PROVIDER_TIMEOUT_MS = 6_000L

        /** The whole lookup, however many providers are enabled. */
        private const val TOTAL_TIMEOUT_MS = 12_000L

        /** Anything further off than this is another version of the song. */
        private const val MAX_DRIFT_SEC = 15

        private const val FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val NOT_FOUND_TTL_MS = 24L * 60 * 60 * 1000

        private val IGNORED_TOKENS = setOf("the", "and", "feat", "ft", "official", "video")

        private val LINE_STAMP = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /**
         * Parses one `[mm:ss.xx] text` line, returning null for anything that is
         * not timed. Kept as a public entry point because the duration tests and
         * older call sites use it; [LyricsParser] handles whole documents.
         */
        fun parseLine(line: String): LyricLine? {
            val trimmed = line.trim()
            val match = LINE_STAMP.find(trimmed) ?: return null
            val minutes = match.groupValues[1].toLongOrNull() ?: return null
            val seconds = match.groupValues[2].toLongOrNull() ?: return null
            val fractionRaw = match.groupValues[3]
            val fraction = when (fractionRaw.length) {
                0 -> 0L
                1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100
                2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10
                else -> fractionRaw.take(3).toLongOrNull() ?: 0L
            }
            return LyricLine(
                timeMs = minutes * 60_000L + seconds * 1_000L + fraction,
                text = trimmed.substring(match.range.last + 1).trim(),
            )
        }
    }
}
