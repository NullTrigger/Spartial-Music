/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * The player that backs the [androidx.media3.session.MediaSession].
 *
 * The FOSS build plays locally only, so the ExoPlayer instance itself backs
 * the session. The gms flavour has its own SessionPlayerFactory (same path in
 * `src/gms/java`) that wraps the local player in a CastPlayer instead; both
 * are needed because every variant resolves this symbol.
 */
internal fun createSessionPlayer(context: Context, localPlayer: ExoPlayer): Player = localPlayer
