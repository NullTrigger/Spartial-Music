/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * The gms build session player: the local ExoPlayer wrapped in a
 * [CastPlayer], so one [Player] serves both this device and Cast receivers.
 *
 * The wrap is the Output Switcher transfer path documented at
 * developer.android.com/media/media3/cast/create-castplayer: when the user
 * picks a Cast device from the system dialog, this player keeps the queue and
 * routes the stream to the receiver instead of the local output.
 *
 * If Play services cannot provide a Cast context on the device, the plain
 * local player is used, so playback never depends on Cast succeeding.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createSessionPlayer(context: Context, localPlayer: ExoPlayer): Player =
    runCatching {
        CastPlayer.Builder(context)
            .setLocalPlayer(localPlayer)
            .build()
    }.getOrDefault(localPlayer)
