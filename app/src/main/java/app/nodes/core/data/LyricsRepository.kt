/*
 * Copyright (C) 2026 The Spartial Music Authors.
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
import kotlin.coroutines.coroutineContext
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
 * The order decides who *wins*, not who is asked. Like Glossy, the top of the
 * order is raced first - the three sources most likely to answer run at once
 * with a short budget each, so a common song resolves in a few hundred
 * milliseconds instead of waiting for the slowest provider in the list - and
 * the rest is only asked when that tier cannot time the song. The winner is
 * picked by rank: a timed answer from the head of a tier wins outright, any
 * other answer is held for a short settle window so a better-ranked one can
 * still turn up. The local `.lrc` sidecar is a file read, so it is answered
 * before any request is spent; when "prefer synced" is on, a plain result is
 * only used when nothing in the order can time the song.
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

    suspend fun lyricsFor(
        track: Track,
        refresh: Boolean = false,
        preferProvider: String? = null,
    ): LyricsResult {
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
                    title = cleanTitle(track.title),
                    artist = cleanArtist(track.artist),
                    album = track.album?.let(::cleanTitle),
                    durationSec = (track.durationMs / 1_000L).toInt(),
                    videoId = track.videoId,
                ),
                preferProvider,
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

    /**
     * What the caches already say, without asking any provider. Null when
     * nothing is cached, so the caller can show a cached answer instantly and
     * only wait when there is genuinely nothing to show yet.
     */
    suspend fun cachedFor(track: Track): LyricsResult? {
        val key = cacheKey(track)
        memory[key]?.let { return it.toResult(fromCache = true) }
        val cached = try {
            readCache(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.e("Lyrics", "Reading the lyrics cache failed", error)
            null
        } ?: return null
        memory[key] = cached
        return cached.toResult(fromCache = true)
    }

    /** Provider ids and names for the player's lyrics menu, in lookup order. */
    suspend fun providerOptions(): List<Pair<String, String>> =
        orderedProviders(settings.current()).map { it.id to it.displayName }

    /** Drops every cached payload; the user's own `.lrc` files are untouched. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        memory.clear()
        cacheDir().listFiles()?.forEach { it.delete() }
    }

    private suspend fun resolve(query: LyricsQuery, preferProvider: String? = null): Resolved {
        val prefs = settings.current()
        // "Change provider" asks one source first for this song; the rest of
        // the order stays as arranged, so a miss still walks the list.
        val ordered = orderedProviders(prefs).let { list ->
            if (preferProvider == null) {
                list
            } else {
                list.sortedByDescending { it.id == preferProvider }
            }
        }
        if (ordered.isEmpty()) return Resolved()

        // A plain answer is only usable when nothing in the order can time
        // the song; it is held while the rest is asked, exactly as the
        // "Prefer synced lyrics" setting promises.
        var plainFallback: Resolved? = null

        // The user's own .lrc is a file read, not a request: answer it before
        // any network call is spent. It is the one source the race skips.
        val local = ordered.firstOrNull()?.takeIf { it.id == LyricsProviders.LOCAL }
        if (local != null) {
            val resolved = withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                safeFetch(local, query)
            }?.let { evaluate(local, it, query) }
            if (resolved != null) {
                if (resolved.synced || !prefs.lyricsPreferSynced) return resolved
                plainFallback = resolved
            }
        }
        val remote = if (local == null) ordered else ordered.drop(1)
        if (remote.isEmpty()) return plainFallback ?: Resolved()

        val preferSynced = prefs.lyricsPreferSynced
        // Glossy's shape: the top of the order is raced first, so a song the
        // common providers know resolves in a few hundred milliseconds instead
        // of waiting for the slowest provider in the whole list; the rest is
        // only asked when that tier cannot time the song. The outer budget is
        // the whole lookup, both tiers included.
        val best = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            val firstTier = raceProviders(remote.take(TIER_SIZE), query, preferSynced)
            if (firstTier != null && (firstTier.synced || !preferSynced)) {
                firstTier
            } else {
                val rest = if (remote.size > TIER_SIZE) {
                    raceProviders(remote.drop(TIER_SIZE), query, preferSynced)
                } else {
                    null
                }
                when {
                    firstTier == null -> rest
                    rest != null && rest.synced -> rest
                    else -> firstTier
                }
            }
        }
        val plain = plainFallback
        return when {
            best == null -> plain ?: Resolved()
            // Timed lyrics always beat the user's own untimed sidecar.
            best.synced -> best
            // Otherwise the sidecar outranks a plain network answer.
            plain != null -> plain
            else -> best
        }
    }

    /**
     * Races one tier of providers concurrently, but the winner is chosen by
     * *rank*, not by who answers fastest: [providers] is already in the user's
     * order, a timed answer from its head wins outright, and any other usable
     * answer is held for [SYNCED_PREFERENCE_WINDOW_MS] so a better-ranked
     * answer can still turn up. Racing by raw speed alone is what made moving
     * a provider up or down in Settings meaningless.
     */
    private suspend fun raceProviders(
        providers: List<LyricsProvider>,
        query: LyricsQuery,
        preferSynced: Boolean,
    ): Resolved? {
        if (providers.isEmpty()) return null
        // The channel carries answers as they land; rank is the provider's
        // position in this tier, so the consumer can tell a lucky fast answer
        // from a preferred one.
        val answers = Channel<Pair<Int, Resolved>>(Channel.BUFFERED)
        val fetchJob = Job(coroutineContext[Job])
        val fetchScope = CoroutineScope(coroutineContext + fetchJob)
        val jobs = providers.mapIndexed { rank, provider ->
            fetchScope.launch(Dispatchers.IO) {
                val payload = withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                    safeFetch(provider, query)
                } ?: return@launch
                val resolved = evaluate(provider, payload, query) ?: return@launch
                answers.trySend(rank to resolved)
            }
        }
        // Closing the channel when the last provider has answered is what ends
        // the wait early: nothing better can arrive after that.
        val monitor = fetchScope.launch {
            jobs.forEach { it.join() }
            answers.close()
        }
        try {
            var best: Pair<Int, Resolved>? = null
            var plain: Pair<Int, Resolved>? = null
            var settleAt = Long.MAX_VALUE
            while (true) {
                val remaining = settleAt - System.currentTimeMillis()
                val next = if (settleAt == Long.MAX_VALUE) {
                    // Nothing worth waiting for has arrived yet: block until
                    // an answer does, or every provider has had its budget.
                    answers.receiveCatching().getOrNull() ?: break
                } else {
                    withTimeoutOrNull(remaining.coerceAtLeast(1L)) {
                        answers.receiveCatching().getOrNull()
                    } ?: break
                }
                val (rank, resolved) = next
                // A timed answer is always worth showing; with the preference
                // off, the best-ranked answer of any kind is.
                if (resolved.synced || !preferSynced) {
                    if (best == null || rank < best.first) {
                        best = rank to resolved
                        if (rank == 0) break
                        settleAt = minOf(
                            settleAt,
                            System.currentTimeMillis() + SYNCED_PREFERENCE_WINDOW_MS,
                        )
                    }
                } else if (plain == null || rank < plain.first) {
                    plain = rank to resolved
                    if (settleAt == Long.MAX_VALUE) {
                        settleAt = System.currentTimeMillis() + SYNCED_PREFERENCE_WINDOW_MS
                    }
                }
            }
            return best?.second ?: plain?.second
        } finally {
            monitor.cancel()
            fetchJob.cancel()
        }
    }

    /**
     * The answer as a usable result, or null when it cannot be shown: wrong
     * recording, nothing parsed, or a provider that missed.
     */
    private fun evaluate(
        provider: LyricsProvider,
        payload: LyricsPayload,
        query: LyricsQuery,
    ): Resolved? {
        if (payload !is LyricsPayload.Found) return null
        // The user's own sidecar is explicitly trusted. Network results must
        // identify the exact recording or are skipped.
        if (provider.id != LyricsProviders.LOCAL &&
            !isVerifiedLyricsMatch(payload, query)
        ) {
            return null
        }
        val parsed = LyricsParser.parse(payload.raw)
        if (parsed.lines.isEmpty()) return null
        return Resolved(
            raw = payload.raw,
            providerId = provider.id,
            displayName = provider.displayName,
            lines = parsed.lines,
            synced = parsed.synced,
        )
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
     * Strips the decoration YouTube Music uploads carry - "(Official Audio)",
     * "[Lyrics]", "- Topic" channels - before the title and artist are asked
     * of a lyrics provider. Left in, that decoration breaks LRCLIB's
     * exact-match endpoint and lets a loose provider lock onto a lyric video
     * of a different recording, which is where "the lyrics are wrong" comes
     * from. Falls back to the original text whenever cleaning would leave
     * nothing behind.
     */
    internal fun cleanTitle(title: String): String {
        var t = BRACKETED_JUNK.replace(title, " ")
        t = TRAILING_JUNK.replace(t, " ")
        t = t.replace(WS_RUN, " ").trim()
        return t.ifBlank { title.trim() }
    }

    internal fun cleanArtist(artist: String): String {
        val t = ARTIST_CHANNEL_SUFFIX.replace(artist, "").trim()
        return t.ifBlank { artist.trim() }
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
        // v4: network providers now have to pass the exact title+artist+duration
        // guard in isVerifiedLyricsMatch. Bumping the version discards the
        // payloads the older loose matcher stored, so a song that previously
        // showed another song's lyrics is looked up again from scratch.
        append("v4-")
        append(track.sourceId.key)
        append('-')
        append(track.videoId ?: track.id)
        append('-')
        append(cleanTitle(track.title))
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
        /**
         * The lyrics-mismatch guard.
         *
         * A provider answer is only trusted when it names the same recording:
         * after folding accents, case and punctuation, every word the provider
         * gives for the title and the artist must be one of the track's words -
         * and it must give at least one of each. That accepts the credit
         * differences real indexes are full of ("Kalyani" for a "Kalyani,
         * Ranjini" duet, "Song" for "Song (feat. X)"), while an answer that
         * *adds* words - "Toxic (Live)", "… Tribute Band" - names another
         * recording and is dropped. When both sides know a length, it must be
         * within [MAX_DRIFT_SEC]; a provider that reports a video id must
         * report the same one. This is what stands between the player and the
         * "wrong lyrics" people report, so it stays strict in that direction.
         */
        internal fun isVerifiedLyricsMatch(payload: LyricsPayload.Found, query: LyricsQuery): Boolean {
            val title = identityTokens(query.title)
            val answerTitle = identityTokens(payload.title)
            val artist = identityTokens(query.artist)
            val answerArtist = identityTokens(payload.artist)
            if (title.isEmpty() || artist.isEmpty()) return false
            if (answerTitle.isEmpty() || answerArtist.isEmpty()) return false
            if (!title.containsAll(answerTitle) || !artist.containsAll(answerArtist)) return false
            if (payload.durationSec > 0 && query.durationSec > 0 &&
                abs(payload.durationSec - query.durationSec) > MAX_DRIFT_SEC
            ) {
                return false
            }
            return query.videoId.isNullOrBlank() || payload.videoId.isNullOrBlank() ||
                query.videoId == payload.videoId
        }

        /** Accent- and punctuation-folded word set used by the guard above. */
        private fun identityTokens(text: String?): Set<String> {
            if (text.isNullOrBlank()) return emptySet()
            return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 1 && it !in IGNORED_TOKENS }
                .toSet()
        }

        /**
         * One provider's budget, taken from Glossy: enough for a cold CDN,
         * short enough that a dead provider costs the tier a moment instead of
         * the whole lookup. Two tiers fit inside [TOTAL_TIMEOUT_MS].
         */
        private const val PER_PROVIDER_TIMEOUT_MS = 3_500L

        /**
         * How many of the user's providers are raced first.
         *
         * The point is latency, not coverage: the sources that almost always
         * answer are the ones near the top of the order, so asking three of
         * them is what turns a lookup into a few hundred milliseconds. The
         * remaining providers are only asked when none of the three could time
         * the song.
         */
        private const val TIER_SIZE = 3

        /**
         * How long a usable answer is held back while a better-ranked one gets
         * a chance to arrive.
         *
         * The winner is picked by rank, so the top of the order has to be
         * allowed to be slow; but not for long. A timed answer from the head of
         * the tier wins outright, everything else waits this window. Glossy's
         * number.
         */
        private const val SYNCED_PREFERENCE_WINDOW_MS = 1_500L

        /**
         * The whole lookup, both tiers included.
         *
         * A synced hit returns earlier, and the settle window usually ends the
         * wait long before this; the ceiling only binds when every provider is
         * slow or silent.
         */
        private const val TOTAL_TIMEOUT_MS = 12_000L

        /** Anything further off than this is another version of the song. */
        private const val MAX_DRIFT_SEC = 15

        private const val FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val NOT_FOUND_TTL_MS = 24L * 60 * 60 * 1000

        private val IGNORED_TOKENS = setOf("the", "and", "feat", "ft", "official", "video")

        /**
         * Bracketed junk like "(Official Music Video)", "[Lyrics]",
         * "(Visualizer)", "(Remastered 2014)". Only brackets whose content
         * names a release decoration are removed; "(feat. X)" stays, because
         * the featured artist is part of the song a provider indexes.
         */
        private val BRACKETED_JUNK = Regex(
            "[\\[{(][^\\])}]*?(?:official|lyrics?|visuali[sz]er|remastered|remaster|audio\\s*version|mv|hq|4k)[^\\])}]*?[\\])}]",
            RegexOption.IGNORE_CASE,
        )

        /** A trailing " - official audio", " | lyrics", ": audio" tail. */
        private val TRAILING_JUNK = Regex(
            "\\s*[-–—|:]\\s*(?:official(?:\\s+\\w+)*|lyrics?|audio|video|visuali[sz]er|remastered|remaster)\\s*$",
            RegexOption.IGNORE_CASE,
        )

        /** YouTube Music channel suffixes: "Ella Langley - Topic". */
        private val ARTIST_CHANNEL_SUFFIX = Regex(
            "\\s*[-–—]?\\s*(?:topic|vevo)\\s*$",
            RegexOption.IGNORE_CASE,
        )

        private val WS_RUN = Regex("\\s+")

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
