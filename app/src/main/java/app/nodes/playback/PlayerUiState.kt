/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.model.Track

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
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
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
