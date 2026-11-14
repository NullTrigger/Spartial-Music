/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.model

/**
 * Aggregated listening numbers computed on device from the local play history
 * (`play_history`). Nothing here touches the network - the whole screen is a
 * Room query (PRIVACY.md).
 *
 * [durationMs] is an estimate: a recorded play counts the track's full length,
 * whether or not it was heard to the end, because the history only records
 * that playback started.
 */
data class ListeningStats(
    val plays: Int = 0,
    val durationMs: Long = 0L,
    val topTracks: List<TrackStat> = emptyList(),
    val topArtists: List<ArtistStat> = emptyList(),
) {
    /** [durationMs] as whole minutes, for the big Replay-style headline. */
    val minutes: Long get() = durationMs / 60_000L
}

/** One ranked row on the "Top tracks" list. */
data class TrackStat(
    val track: Track,
    val plays: Int,
)

/** One ranked card on the "Top artists" rail. */
data class ArtistStat(
    val artist: String,
    val plays: Int,
    val durationMs: Long = 0L,
)
