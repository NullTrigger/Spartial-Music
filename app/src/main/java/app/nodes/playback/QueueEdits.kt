/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

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
}
