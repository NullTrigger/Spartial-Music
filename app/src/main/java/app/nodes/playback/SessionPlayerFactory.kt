/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * The player that backs the [MediaSession] — a hook point for flavor builds.
 *
 * The FOSS build plays locally only, so the ExoPlayer instance itself backs
 * the session. The gms flavor replaces this file with one that wraps the local
 * player in [androidx.media3.cast.CastPlayer], which routes the queue to a
 * Cast receiver when the user transfers it from the Output Switcher.
 */
internal fun createSessionPlayer(context: Context, localPlayer: ExoPlayer): Player = localPlayer
