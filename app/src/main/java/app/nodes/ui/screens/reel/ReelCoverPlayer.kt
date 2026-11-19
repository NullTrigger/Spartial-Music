/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.nodes.core.cover.AnimatedCover
import app.nodes.core.crash.AppLogger
import java.io.File
import kotlin.math.sqrt

/**
 * The one ExoPlayer instance the Reel uses to loop a cover video.
 *
 * Deliberately deaf and disposable: audio is disabled through track
 * selection as well as volume, audio focus is never requested, the plain
 * Media3 data source stack is used with no cache (remote covers are streamed
 * and never written to disk; user loops are files the picker already gave
 * this app), and the player exists only while its page is visible. The
 * screen owns the instance and releases it on page change, screen exit and
 * ON_STOP; a failure is remembered per track for the session so a broken
 * video cannot start a retry loop, and the generated cover shows instead.
 */
class ReelCoverPlayer private constructor(
    private val player: ExoPlayer,
) {
    /** True once the first frame has been drawn; drives the crossfade. */
    var isReady = false
        private set

    private var onFirstFrame: (() -> Unit)? = null
    private var onError: (() -> Unit)? = null

    companion object {
        private const val TAG = "CoverPlayer"

        /**
         * @param maxPixels the screen's pixel budget; the video is decoded no
         *   larger than this. 0 disables the cap.
         */
        fun create(
            context: Context,
            maxPixels: Int,
            onFirstFrame: () -> Unit,
            onError: () -> Unit,
        ): ReelCoverPlayer {
            val side = if (maxPixels > 0) sqrt(maxPixels.toDouble()).toInt() else 0
            val player = ExoPlayer.Builder(context).build().apply {
                volume = 0f
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    // No audio renderer at all: the cover can never interrupt
                    // or duck the music player.
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .apply {
                        // Decode no larger than the screen.
                        if (side > 0) setMaxVideoSize(side, side)
                    }
                    .build()
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_UNKNOWN).build(),
                    /* handleAudioFocus = */ false,
                )
            }
            return ReelCoverPlayer(player).also {
                it.onFirstFrame = onFirstFrame
                it.onError = onError
                it.attachListeners()
            }
        }
    }

    private fun attachListeners() {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isReady = true
                onFirstFrame?.invoke()
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.w(TAG, "Cover video failed: ${error.errorCodeName}")
                onError?.invoke()
            }
        })
    }

    fun load(cover: AnimatedCover) {
        val uri = if (cover.isLocalFile) Uri.fromFile(File(cover.url)) else Uri.parse(cover.url)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    /** Visible + foreground + settings all agree: spin; otherwise idle. */
    fun setPlaying(playing: Boolean) {
        player.playWhenReady = playing
    }

    fun attach(view: TextureView) {
        player.setVideoTextureView(view)
    }

    fun release() {
        runCatching { player.release() }
            .onFailure { AppLogger.w(TAG, "Cover release failed", it) }
        isReady = false
    }
}
