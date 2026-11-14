/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.nodes.MainActivity
import app.nodes.R
import app.nodes.core.data.SettingsRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
 * The session player is what the notification and any other controller see;
 * [createSessionPlayer] decides whether that is the bare ExoPlayer or a Cast
 * wrapper (the gms flavor). The controller drives playback through that
 * player, while speed and skip-silence stay ExoPlayer-only surfaces the
 * service applies itself so a setting survives controller reconnects.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var playbackController: dagger.Lazy<PlaybackController>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var localPlayer: ExoPlayer? = null

    /**
     * Cached copy of the "stop when swiped away" preference. [onTaskRemoved] is
     * a synchronous callback on the main thread, so it cannot read DataStore
     * itself; the settings collector below keeps this in sync instead.
     */
    @Volatile
    private var stopOnTaskClear = false

    // setSkipSilenceEnabled and the notification-provider surface are still
    // unstable Media3 APIs, so the opt-in is declared here instead of at every
    // call site.
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        localPlayer = exoPlayer
        val sessionPlayer = createSessionPlayer(this, exoPlayer)

        // Tapping the media notification re-opens the app. The intent must be
        // immutable on API 31+ and updatable, or Media3 re-fires a stale one.
        val sessionActivity = PendingIntent.getActivity(
            this,
            /* requestCode = */ 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // The notification small icon is the app logo, not a bare exclamation
        // mark: DefaultMediaNotificationProvider falls back to the system
        // default when none is set.
        val notificationProvider = DefaultMediaNotificationProvider(this)
            .apply { setSmallIcon(R.drawable.ic_launcher_monochrome) }
        setMediaNotificationProvider(notificationProvider)

        // Android hides the platform next/previous buttons on single-item
        // timelines (YouTube tracks carry their stream URL per play), so we
        // add explicit media buttons for next, previous and repeat-cycle
        // instead of relying on the hidden platform ones.
        //
        // They must also claim the back and forward SLOTS. DefaultMediaNotificationProvider
        // builds the row as "if the custom layout has a button for SLOT_BACK use it,
        // ELSE IF the platform command is available add the standard previous button"
        // - an unslotted custom button therefore does not replace anything, so the
        // notification ended up with the platform previous plus all three of ours:
        // four skip-ish controls, no obvious play/pause, and the repeat button pushed
        // to the edge. Naming the slots is what makes ours take over those positions.
        val previousButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.player_previous))
            .setSessionCommand(SessionCommand(PREVIOUS_COMMAND, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notification_previous)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val nextButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.player_next))
            .setSessionCommand(SessionCommand(NEXT_COMMAND, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notification_next)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
        // Deliberately unslotted: it belongs after the play/pause pair, not in
        // place of it.
        val repeatButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.player_repeat))
            .setSessionCommand(SessionCommand(REPEAT_CYCLE_COMMAND, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notification_repeat)
            .build()

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .setMediaButtonPreferences(listOf(previousButton, nextButton, repeatButton))
            .build()

        scope.launch {
            settings.settings.collect { current ->
                stopOnTaskClear = current.stopMusicOnTaskClear
                runCatching {
                    exoPlayer.setPlaybackSpeed(current.playbackSpeed)
                    exoPlayer.skipSilenceEnabled = current.skipSilence
                }
            }
        }
    }

    /**
     * The session callbacks. In Media3 the service itself is not the callback;
     * the session receives this object, and the repeat-cycle command is
     * handled here so the notification's media button works. The connection
     * plumbing (DEFAULT_*_COMMANDS, AcceptedResultBuilder) is UnstableApi in
     * Media3 1.11, hence the per-method opt-in.
     */
    private val sessionCallback = object : MediaSession.Callback {

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PREVIOUS_COMMAND, Bundle.EMPTY))
                .add(SessionCommand(NEXT_COMMAND, Bundle.EMPTY))
                .add(SessionCommand(REPEAT_CYCLE_COMMAND, Bundle.EMPTY))
                .build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            extras: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PREVIOUS_COMMAND -> runCatching { playbackController.get().previous() }
                NEXT_COMMAND -> runCatching { playbackController.get().next() }
                REPEAT_CYCLE_COMMAND -> runCatching { playbackController.get().cycleRepeat() }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
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
        localPlayer = null
        super.onDestroy()
    }

    private companion object {
        /** Custom session command for the notification's previous button. */
        const val PREVIOUS_COMMAND = "app.nodes.playback.PREVIOUS"
        /** Custom session command for the notification's next button. */
        const val NEXT_COMMAND = "app.nodes.playback.NEXT"
        /** Custom session command cycled by the notification's repeat button. */
        const val REPEAT_CYCLE_COMMAND = "app.nodes.playback.CYCLE_REPEAT"
    }
}
