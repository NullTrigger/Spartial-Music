/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.nodes.R
import app.nodes.core.crash.AppLogger
import app.nodes.core.crash.AppSnapshot
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.PlaybackHandle
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for playback.
 *
 * Two engines, chosen per track by its provider:
 *  - Media3/ExoPlayer via [PlaybackService] for local files and Creative Commons
 *    streams;
 *  - the official YouTube embedded player for YouTube tracks, exposed to the UI
 *    as [PlayerUiState.embeddedVideoId]. No stream URL is ever resolved and
 *    ExoPlayer is stopped, so nothing keeps playing audio the player is not
 *    showing.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SourceRegistry,
    private val library: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()

    /**
     * Tracks whose stream URL has already been re-minted once. A googlevideo URL
     * is short-lived and bound to the request that produced it, so a 403 is
     * fixed by asking the source again - but only once per track per session, so
     * a track YouTube genuinely will not serve shows its error instead of
     * looping.
     */
    private val refreshedStreams = mutableSetOf<String>()

    // MediaController.Listener only reports session events; playback callbacks
    // such as onMediaItemTransition live on Player.Listener, which a
    // MediaController implements.
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLogger.e("Player", "Playback error ${error.errorCodeName}", error)
            val track = _state.value.current
            // ERROR_CODE_IO_BAD_HTTP_STATUS is the CDN answering 403: the URL
            // expired, or was minted for a session it no longer matches. Resolve
            // the track again and reuse the same queue position rather than
            // showing the user a dead stream.
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                track != null &&
                track.sourceId == SourceId.YOUTUBE_MUSIC &&
                refreshedStreams.add(track.id)
            ) {
                _state.update { it.copy(message = "Stream expired, refreshing…") }
                playNow(track, queue.ifEmpty { listOf(track) })
                return
            }
            _state.update { it.copy(message = error.errorCodeName.ifBlank { "Playback error" }) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // A stream that reached READY worked, so allow one future refresh.
            if (playbackState == Player.STATE_READY) {
                _state.value.current?.id?.let(refreshedStreams::remove)
            }
            if (playbackState != Player.STATE_ENDED) return
            if (_state.value.embeddedVideoId != null) return
            val index = queue.indexOfFirst { it.id == _state.value.current?.id }
            val next = queue.getOrNull(index + 1) ?: return
            // Tracks whose URL is minted per play are not in the player's item
            // list, so the player ends instead of advancing; keep the queue going.
            if (next.mediaUri == null) playNow(next, queue)
        }
    }

    init {
        connect()
        // Copy the interesting parts of the player state somewhere the crash
        // handler can read them from a dying thread.
        scope.launch {
            state.collect { current ->
                AppSnapshot.player = buildString {
                    append("track=").append(current.current?.id ?: "none")
                    append(" playing=").append(current.isPlaying)
                    append(" buffering=").append(current.isBuffering)
                    append(" queue=").append(current.queue.size)
                    append(" position=").append(current.positionMs).append("ms")
                    append(" duration=").append(current.durationMs).append("ms")
                    if (current.embeddedVideoId != null) append(" embedded=true")
                }
            }
        }
        scope.launch {
            while (true) {
                // This loop is the one piece of code that runs for the whole
                // time music is playing, so it is also the easiest place for a
                // stale session to end the process. A tick that cannot read the
                // controller is skipped; the next one tries again.
                try {
                    val player = controller
                    if (player != null && player.isPlaying) {
                        _state.update { state ->
                            state.copy(
                                isPlaying = true,
                                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = player.duration.takeIf { it > 0L } ?: state.durationMs,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Session died or is between states; nothing to show this tick.
                    AppLogger.w("Player", "Playback tick skipped", error)
                }
                delay(TICK_MS)
            }
        }
    }

    private fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(listener)
                        syncFromController()
                    }
                    .onFailure { error ->
                        _state.update { it.copy(message = error.message ?: "Player unavailable.") }
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        repeat(20) {
            delay(100)
            controller?.let { return it }
        }
        return controller
    }

    fun playNow(track: Track, playbackQueue: List<Track> = listOf(track)) {
        scope.launch {
            // Resolving a stream is provider and network code. A failure there
            // has to become a message; an uncaught exception on this (main)
            // scope would take the whole process down mid-listen.
            val result = try {
                val source = registry.forTrack(track.sourceId)
                source?.play(track) ?: SourceResult.Failure(SourceErrorType.UNKNOWN)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Player", "Resolving the stream failed", error)
                _state.update { it.copy(message = context.getString(R.string.playback_could_not_start)) }
                return@launch
            }

            // Play history is a nicety: a database problem must never be the
            // reason music does not start.
            runCatching { library.recordPlay(track) }

            val handle = (result as? SourceResult.Success)?.data

            try {
                applyHandle(track, playbackQueue, handle)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Player", "Starting the player failed", error)
                _state.update { it.copy(message = context.getString(R.string.playback_could_not_start)) }
            }
        }
    }

    /**
     * Hands [handle] to the engine that owns it.
     *
     * Split out of [playNow] so every Media3 call sits inside one guarded
     * block: a player rejecting an item becomes a message on the player, not an
     * uncaught exception that closes the app while music is playing.
     */
    private suspend fun applyHandle(
        track: Track,
        playbackQueue: List<Track>,
        handle: PlaybackHandle?,
    ) {
        when (handle) {
            is PlaybackHandle.Stream -> {
                val player = awaitController()
                if (player == null) {
                    _state.update { it.copy(message = "The player is still starting. Try again.") }
                    return
                }
                // The URL was minted for this track a moment ago, so it is
                // attached to the copy that goes into the player. Tracks from
                // the same source still carry no URL and stay out of the
                // player's item list; next() and previous() resolve them
                // through the source instead of Media3 skipping to nothing.
                val resolved = track.copy(mediaUri = handle.url)
                val withUrls = playbackQueue.map { if (it.id == track.id) resolved else it }
                val playable = withUrls.filter { it.mediaUri != null }
                if (playable.isEmpty()) {
                    _state.update { it.copy(message = "Nothing in this list can be streamed.") }
                    return
                }
                val startIndex = playable.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                queue = withUrls
                player.setMediaItems(playable.map { it.toMediaItem() }, startIndex, 0L)
                player.prepare()
                player.play()
                _state.update {
                    it.copy(
                        current = resolved,
                        queue = withUrls,
                        embeddedVideoId = null,
                        isPlaying = true,
                        isBuffering = true,
                        positionMs = 0L,
                        durationMs = track.durationMs,
                        message = null,
                    )
                }
            }

            is PlaybackHandle.EmbeddedVideo -> {
                controller?.pause()
                queue = listOf(track)
                _state.update {
                    it.copy(
                        current = track,
                        queue = listOf(track),
                        embeddedVideoId = handle.videoId,
                        isPlaying = false,
                        isBuffering = false,
                        positionMs = 0L,
                        durationMs = track.durationMs,
                        message = null,
                    )
                }
            }

            is PlaybackHandle.Unavailable -> _state.update { it.copy(message = handle.reason) }

            null -> _state.update {
                it.copy(message = "This track cannot be played from ${track.sourceId.key}.")
            }
        }
    }

    fun togglePlayPause() {
        try {
            if (_state.value.embeddedVideoId != null) {
                _state.update { it.copy(message = "Use the YouTube player controls for this track.") }
                return
            }
            val player = controller ?: return
            if (player.isPlaying) player.pause() else player.play()
            syncFromController()
        } catch (error: Exception) {
            AppLogger.e("Player", "Toggling playback failed", error)
        }
    }

    fun next() {
        try {
            val current = _state.value
            val index = queue.indexOfFirst { it.id == current.current?.id }
            val candidate = queue.getOrNull(index + 1)
            if (candidate != null && (current.embeddedVideoId != null || candidate.mediaUri == null)) {
                playNow(candidate, queue)
                return
            }
            controller?.seekToNextMediaItem()
        } catch (error: Exception) {
            AppLogger.e("Player", "Skipping to the next track failed", error)
        }
    }

    fun previous() {
        try {
            val current = _state.value
            val index = queue.indexOfFirst { it.id == current.current?.id }
            val candidate = queue.getOrNull(index - 1)
            if (candidate != null && (current.embeddedVideoId != null || candidate.mediaUri == null)) {
                playNow(candidate, queue)
                return
            }
            controller?.seekToPreviousMediaItem()
        } catch (error: Exception) {
            AppLogger.e("Player", "Skipping to the previous track failed", error)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            controller?.seekTo(positionMs)
            _state.update { it.copy(positionMs = positionMs) }
        } catch (error: Exception) {
            AppLogger.e("Player", "Seeking failed", error)
        }
    }

    /** Called by Now Playing when the embedded YouTube player is disposed. */
    fun stopEmbedded() {
        if (_state.value.embeddedVideoId == null) return
        _state.update { it.copy(embeddedVideoId = null, isPlaying = false) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun syncFromController() {
        val player = controller ?: return
        // The player's item list can be shorter than the queue, so the media id
        // is the reliable way to find which track is actually playing.
        val track = queue.firstOrNull { it.id == player.currentMediaItem?.mediaId }
            ?: queue.getOrNull(player.currentMediaItemIndex)
        _state.update { state ->
            state.copy(
                current = track ?: state.current,
                queue = queue.ifEmpty { state.queue },
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0L }
                    ?: (track?.durationMs ?: state.durationMs),
            )
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                // The notification and lock screen read this URI themselves, so
                // it has to be a large copy (>= 512px), not the 60px the
                // provider advertised for a list row.
                artworkUri?.let { art ->
                    setArtworkUri(Uri.parse(app.nodes.core.util.upgradeThumbnail(art, 1080) ?: art))
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .apply {
                // googlevideo URLs say what they are (`mime=audio%2Fwebm` for
                // Opus). Passing it tells Media3 which extractor to expect
                // instead of making it guess from a query string with no file
                // extension; local files carry no `mime` and are left alone.
                audioMimeType(mediaUri)?.let { setMimeType(it) }
            }
            .build()
    }

    private fun audioMimeType(url: String?): String? {
        if (url == null) return null
        val declared = runCatching { Uri.parse(url).getQueryParameter("mime") }.getOrNull()
        return declared?.takeIf { it.startsWith("audio/") }
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
