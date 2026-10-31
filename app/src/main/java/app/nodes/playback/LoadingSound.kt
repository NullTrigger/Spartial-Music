/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.nodes.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The short chime that plays while the Home feed is loading, then stops.
 *
 * The asset is `res/raw/loading`, named without an extension in code so it can be
 * a WebM/Opus file, a WAV or anything else Media3 reads: the container is
 * sniffed and no MIME type is hard-coded here, so replacing the file is the only
 * change needed.
 *
 * One chime per process: [playOnce] is a no-op after the first call, so a feed
 * refresh or a source change does not turn into a jingle loop. The player is
 * released by [stop] as soon as the feed settles, which keeps a second decoder
 * from living alongside the music player.
 */
@Singleton
class LoadingSound @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null
    private var played = false

    fun playOnce() {
        if (played) return
        played = true
        val engine = player ?: ExoPlayer.Builder(context).build().also { player = it }
        engine.setMediaItem(MediaItem.fromUri(assetUri()))
        engine.prepare()
        engine.play()
    }

    /** Release the chime player; safe to call when nothing is playing. */
    fun stop() {
        player?.release()
        player = null
    }

    private fun assetUri(): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.loading}")
}
