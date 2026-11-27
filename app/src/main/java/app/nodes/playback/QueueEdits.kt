/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.data.db.QueueItemEntity
import app.nodes.core.data.db.QueueStateEntity
import app.nodes.core.model.Track
import java.text.Normalizer
import java.util.Locale

/**
 * Pure queue edits, kept out of [PlaybackController] so they can be unit tested
 * without a Media3 session.
 *
 * The model is Glossy's: a track tapped in a list starts a new queue, while
 * "Play next" inserts after the playing entry and "Add to queue" appends. The
 * queue is never rebuilt from the collection a track came from, which is what
 * made the old behaviour look like the same list being added again in order.
 */
internal object QueueEdits {

    /**
     * Inserts [track] into [queue] without disturbing the order of the rest.
     *
     * With [preventDuplicates] the older copy of the same track is dropped first
     * (Glossy's `PreventDuplicateTracksInQueue`) so the new request wins and the
     * queue does not grow a second identical row.
     */
    fun insert(
        queue: List<Track>,
        playingId: String?,
        track: Track,
        afterCurrent: Boolean,
        preventDuplicates: Boolean,
    ): List<Track> {
        val next = if (preventDuplicates) {
            queue.filterNot { it.id == track.id }.toMutableList()
        } else {
            queue.toMutableList()
        }
        if (afterCurrent) {
            val currentIndex = next.indexOfFirst { it.id == playingId }
            next.add(if (currentIndex >= 0) currentIndex + 1 else next.size, track)
        } else {
            next.add(track)
        }
        return next
    }

    /**
     * Tracks that echo the queue: the same artist or album as something it
     * already holds, so a single tap becomes a listening session that still
     * sounds like the song it started from.
     *
     * [candidates] is expected in preference order (listening history first);
     * the score is stable, so ties keep that order. Same album wins over same
     * artist, and a candidate that shares neither is dropped rather than
     * appended as filler: the queue's suggestions must echo the music, not
     * whatever earlier searches left cached on the device.
     */
    fun echo(queue: List<Track>, candidates: List<Track>, limit: Int): List<Track> {
        if (limit <= 0 || queue.isEmpty()) return emptyList()
        val artists = queue.mapNotNullTo(HashSet()) { it.artist.keyOrNull() }
        val albums = queue.mapNotNullTo(HashSet()) { it.album?.keyOrNull() }
        val excluded = queue.mapTo(HashSet()) { it.id }
        return candidates
            .distinctBy { it.id }
            .filter { it.id !in excluded }
            .sortedByDescending { affinity(it, artists, albums) }
            .filter { affinity(it, artists, albums) > 0 }
            .take(limit)
    }

    /** Same album beats same artist; no link to the queue scores nothing. */
    private fun affinity(track: Track, artists: Set<String>, albums: Set<String>): Int {
        val album = track.album?.keyOrNull()
        if (album != null && album in albums) return 2
        return if (track.artist.keyOrNull() in artists) 1 else 0
    }

    private fun String.keyOrNull(): String? = trim().lowercase().takeIf { it.isNotEmpty() }

    /** The artist a queue compares for variety: trimmed and case-folded. */
    fun artistKey(track: Track): String = track.artist.keyOrNull().orEmpty()

    // ------------------------------------------------------------------
    // Name variety
    // ------------------------------------------------------------------

    /**
     * The song name behind a listing. "Fa Fa Fa (Live)", "FA FA FA [Remix]",
     * "Fa-fa-fa - Remastered" and "Fa Fa Fa" are one name: pressings and
     * suffixes do not make a song a different song.
     */
    fun titleKey(track: Track): String {
        val base = track.title
            .substringBefore('(')
            .substringBefore('[')
            .substringBefore(" - ")
        return Normalizer.normalize(base, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * Keeps the first track per song name and drops the names in [takenNames]
     * (what the queue already holds), stopping at [limit]. Up-next answers
     * lead with the played song's own pressings, and a queue that appends
     * them fills with one name over and over; auto-filled additions must read
     * like a listening session instead.
     */
    fun varyByName(candidates: List<Track>, takenNames: Set<String>, limit: Int): List<Track> {
        if (limit <= 0) return emptyList()
        val seen = HashSet(takenNames)
        val out = mutableListOf<Track>()
        for (track in candidates) {
            if (out.size >= limit) break
            if (!seen.add(titleKey(track))) continue
            out += track
        }
        return out
    }

    // ------------------------------------------------------------------
    // Persistence (pure: entities in, entities out, no Android)
    // ------------------------------------------------------------------

    /**
     * A queue as it is written to the database. [trackIds] is the play order;
     * [originalPositions] holds the pre-shuffle index of each entry, so the
     * chosen order survives a restart and can be restored when shuffle is
     * turned off.
     */
    data class Snapshot(
        val trackIds: List<String>,
        val originalPositions: List<Int>,
        val currentIndex: Int,
        val positionMs: Long,
        val shuffleEnabled: Boolean,
    )

    fun toRows(snapshot: Snapshot): Pair<List<QueueItemEntity>, QueueStateEntity> =
        snapshot.trackIds.mapIndexed { index, id ->
            QueueItemEntity(
                position = index,
                trackId = id,
                originalPosition = snapshot.originalPositions.getOrNull(index) ?: index,
            )
        } to QueueStateEntity(
            id = 0,
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            shuffleEnabled = snapshot.shuffleEnabled,
        )

    /**
     * Reads rows back. Skips rows whose play slot collapsed (corrupt or
     * hand-edited data) rather than throwing; the orders stay parallel lists.
     */
    fun fromRows(items: List<QueueItemEntity>, state: QueueStateEntity): Snapshot =
        Snapshot(
            trackIds = items.sortedBy { it.position }.map { it.trackId },
            originalPositions = items.sortedBy { it.position }.map { it.originalPosition },
            currentIndex = state.currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            positionMs = state.positionMs,
            shuffleEnabled = state.shuffleEnabled,
        )

    // ------------------------------------------------------------------
    // Shuffle
    // ------------------------------------------------------------------

    /**
     * A random order of [upcoming] that never repeats an entry before the
     * whole list has played: it is a permutation, not independent picks.
     */
    fun shuffledOrder(upcoming: List<String>, random: kotlin.random.Random): List<String> =
        upcoming.shuffled(random)

    /**
     * Reshuffles the upcoming tracks without touching what is playing.
     * "Play next" entries ([playNextIds]) keep their slot right after the
     * current track, in the order they were queued; everything after them is
     * dealt out freshly.
     */
    fun reshuffled(
        upcoming: List<String>,
        playNextIds: Set<String>,
        random: kotlin.random.Random,
    ): List<String> {
        val mustStay = upcoming.filter { it in playNextIds }
        val rest = upcoming.filterNot { it in playNextIds }.shuffled(random)
        return mustStay + rest
    }

    // ------------------------------------------------------------------
    // Infinite queue
    // ------------------------------------------------------------------

    /** One mood-foreign track per this many: the "a little variety" share. */
    private const val MOOD_VARIETY_DIVISOR = 10

    /**
     * Shapes a batch of candidates to the queue's [mood], so an infinite
     * queue grown from one song keeps growing in that song's language.
     *
     * Three groups, each keeping its own preference order: tracks that share
     * the mood's language or artist lead, tracks with no language signal
     * (Latin-script titles: English and romanised Malayalam are
     * indistinguishable) follow, and only a track that positively names a
     * *different* language is held back to a small minority of slots — one in
     * [MOOD_VARIETY_DIVISOR], dropped in mid-batch so it reads as variety
     * rather than as a change of mind. When the mood's own pool runs dry the
     * rest fills anyway: a full queue beats a pure one.
     */
    fun shapeByMood(
        candidates: List<Track>,
        mood: MoodProfile,
        limit: Int,
        varietySlots: Int = maxOf(1, limit / MOOD_VARIETY_DIVISOR),
    ): List<Track> {
        if (limit <= 0) return emptyList()
        val main = ArrayDeque<Track>()
        val other = ArrayDeque<Track>()
        for (track in candidates) {
            if (mood.matchOf(track) == MoodProfile.Match.OTHER) other.addLast(track) else main.addLast(track)
        }
        val varietyEvery = (limit / maxOf(1, varietySlots)).coerceAtLeast(2)
        val out = ArrayList<Track>(minOf(limit, candidates.size))
        var usedVariety = 0
        for (slot in 0 until limit) {
            val varietyDue = usedVariety < varietySlots &&
                other.isNotEmpty() &&
                slot % varietyEvery == varietyEvery / 2
            val track = when {
                varietyDue -> { usedVariety++; other.removeFirst() }
                main.isNotEmpty() -> main.removeFirst()
                other.isNotEmpty() -> other.removeFirst()
                else -> break
            }
            out += track
        }
        return out
    }

    /**
     * Blends the radio answers of several seeds into one stream: one track
     * from each station per round, so a batch reads like a station rather than
     * one radio's top ten. Tracks already queued or played, ids listed in
     * [excludedIds] and song names the queue already holds are dropped; the
     * first track per name wins.
     *
     * Variety is enforced, not hoped for: [takenArtists] - the artists the
     * queue already ended on - start at [perArtist] and are skipped entirely,
     * and no artist or album may take more than its cap inside one batch. A
     * radio answer for one song is mostly that song's artist and album, which
     * is why a queue started from one track used to keep sounding like it.
     */
    fun stationStream(
        stations: List<List<Track>>,
        excludedIds: Set<String>,
        takenNames: Set<String>,
        limit: Int,
        takenArtists: Set<String> = emptySet(),
        perArtist: Int = 1,
        perAlbum: Int = 1,
    ): List<Track> {
        if (limit <= 0 || stations.isEmpty()) return emptyList()
        val queues = stations.map { ArrayDeque(it) }
        val seenIds = HashSet<String>()
        val seenNames = HashSet(takenNames)
        val artists = HashMap<String, Int>()
        takenArtists.filter { it.isNotBlank() }.forEach { artists[it] = perArtist }
        val albums = HashMap<String, Int>()
        val out = ArrayList<Track>(limit)
        var added = true
        while (out.size < limit && added) {
            added = false
            for (station in queues) {
                if (out.size >= limit) break
                while (station.isNotEmpty()) {
                    val track = station.removeFirst()
                    if (track.id in excludedIds || !seenIds.add(track.id)) continue
                    if (!seenNames.add(titleKey(track))) continue
                    val artist = artistKey(track)
                    if (artist.isNotEmpty() && (artists[artist] ?: 0) >= perArtist) continue
                    val album = track.album?.keyOrNull().orEmpty()
                    if (album.isNotEmpty() && (albums[album] ?: 0) >= perAlbum) continue
                    if (artist.isNotEmpty()) artists[artist] = (artists[artist] ?: 0) + 1
                    if (album.isNotEmpty()) albums[album] = (albums[album] ?: 0) + 1
                    out += track
                    added = true
                    break
                }
            }
        }
        return out
    }

    /**
     * One song per artist, minus the artists the queue already ended on. Used
     * on the batches that are not built by [stationStream], so every path that
     * grows the queue obeys the same variety rule.
     */
    fun varyByArtist(candidates: List<Track>, takenArtists: Set<String>, limit: Int): List<Track> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        takenArtists.filterTo(seen) { it.isNotBlank() }
        val out = ArrayList<Track>(minOf(limit, candidates.size))
        for (track in candidates) {
            if (out.size >= limit) break
            val artist = artistKey(track)
            if (artist.isNotEmpty() && !seen.add(artist)) continue
            out += track
        }
        return out
    }

    /**
     * The next batch for an infinite queue, in preference order:
     * source-related tracks first, then the local candidate pool. Anything
     * already queued or played in the last [recentlyPlayedIds] is excluded,
     * and nothing appears twice.
     *
     * Returns fewer than [limit] or none at all only when every step runs dry.
     */
    fun selectExtension(
        related: List<Track>,
        localPool: List<Track>,
        queuedIds: Set<String>,
        recentlyPlayedIds: Set<String>,
        limit: Int,
    ): List<Track> {
        if (limit <= 0) return emptyList()
        val excluded = queuedIds + recentlyPlayedIds
        val out = mutableListOf<Track>()
        val seen = HashSet<String>()
        for (track in related + localPool) {
            if (out.size >= limit) break
            if (track.id in excluded || !seen.add(track.id)) continue
            out += track
        }
        return out
    }
}
