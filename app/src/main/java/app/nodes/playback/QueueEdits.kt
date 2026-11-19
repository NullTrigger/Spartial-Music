/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.data.db.QueueItemEntity
import app.nodes.core.data.db.QueueStateEntity
import app.nodes.core.model.Track

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
     * Picks the tracks to append to a queue of one, so a single tap becomes a
     * listening session instead of four minutes of music and silence.
     *
     * [candidates] is expected in preference order (listening history first);
     * ranking is stable, so ties keep that order. Another track from the same
     * album wins, then the same artist, then everything else - minus what the
     * queue already holds, so nothing appears twice.
     */
    fun suggest(queue: List<Track>, candidates: List<Track>, limit: Int): List<Track> {
        if (limit <= 0) return emptyList()
        val seed = queue.firstOrNull() ?: return emptyList()
        val excluded = queue.mapTo(HashSet()) { it.id }
        return candidates
            .distinctBy { it.id }
            .filter { it.id !in excluded }
            .sortedByDescending { affinity(seed, it) }
            .take(limit)
    }

    /** Higher means a better fit for the queue's seed track. */
    private fun affinity(seed: Track, candidate: Track): Int = when {
        !seed.album.isNullOrBlank() && candidate.album.equals(seed.album, ignoreCase = true) -> 3
        candidate.artist.isNotBlank() &&
            candidate.artist.equals(seed.artist, ignoreCase = true) -> 2
        else -> 1
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
