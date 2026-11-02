/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.nodes.core.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Standard Media3 session service. It only ever plays URLs that a
 * [app.nodes.core.source.MusicSource] resolved legally - YouTube tracks are
 * never handed to ExoPlayer, they stay in the official embedded player.
 *
 * Playback speed and skip-silence live on the ExoPlayer instance and are not
 * reachable through a MediaController, so the service watches the settings and
 * applies them itself; that way a setting survives the controller reconnecting
 * and applies to the next track as well.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    /**
     * Cached copy of the "stop when swiped away" preference. [onTaskRemoved] is
     * a synchronous callback on the main thread, so it cannot read DataStore
     * itself; the settings collector below keeps this in sync instead.
     */
    @Volatile
    private var stopOnTaskClear = false

    // setSkipSilenceEnabled is still an unstable Media3 surface, so the opt-in
    // is declared here instead of at every call site.
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        scope.launch {
            settings.settings.collect { current ->
                stopOnTaskClear = current.stopMusicOnTaskClear
                runCatching {
                    player.setPlaybackSpeed(current.playbackSpeed)
                    player.skipSilenceEnabled = current.skipSilence
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (stopOnTaskClear) {
            // Opted in: the swiped-away app must not keep a media session (or
            // its notification) alive in the background. Stop the player first
            // so Media3 tears the notification down from a stopped state, then
            // let the service pause everything and finish.
            mediaSession?.player?.stop()
            pauseAllPlayersAndStopSelf()
            return
        }
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
