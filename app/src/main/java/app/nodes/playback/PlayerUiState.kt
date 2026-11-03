/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.model.Track

/** Where playback goes after a track ends; queues are walked by the controller. */
enum class RepeatMode {
    OFF,
    /** The whole queue again from the top. */
    ALL,
    /** The current track again, forever. */
    ONE,
}

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    /**
     * Position of [current] in [queue]. This is not always the first matching
     * id: a queue may legitimately hold the same track twice when "keep the
     * queue unique" is off, and then the second copy must be highlighted.
     */
    val queueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /**
     * Non-null while a YouTube track is loaded. The Now Playing screen must keep
     * the official player visible; audio stops when it is disposed.
     */
    val embeddedVideoId: String? = null,
    /** One-shot user-facing message (quota, missing key, unavailable track). */
    val message: String? = null,
    /** Epoch millis when the sleep timer fires; 0 when it is off. */
    val sleepTimerEndsAt: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Shuffle of the queue tail; the playing track and the played prefix stay. */
    val shuffleOn: Boolean = false,
    /**
     * Glossy's "infinite queue": as the queue runs out, more tracks from the
     * listening history and likes are appended automatically. Session-only.
     */
    val infiniteQueue: Boolean = false,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
