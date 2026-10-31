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
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.PlaybackHandle
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
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

    // MediaController.Listener only reports session events; playback callbacks
    // such as onMediaItemTransition live on Player.Listener, which a
    // MediaController implements.
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(message = error.errorCodeName.ifBlank { "Playback error" }) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
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
        scope.launch {
            while (true) {
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
            val source = registry.forTrack(track.sourceId)
            val result = source?.play(track) ?: SourceResult.Failure(SourceErrorType.UNKNOWN)
            library.recordPlay(track)
            val handle = (result as? SourceResult.Success)?.data

            when (handle) {
                is PlaybackHandle.Stream -> {
                    val player = awaitController()
                    if (player == null) {
                        _state.update { it.copy(message = "The player is still starting. Try again.") }
                        return@launch
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
                        return@launch
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
    }

    fun togglePlayPause() {
        if (_state.value.embeddedVideoId != null) {
            _state.update { it.copy(message = "Use the YouTube player controls for this track.") }
            return
        }
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
        syncFromController()
    }

    fun next() {
        val current = _state.value
        val index = queue.indexOfFirst { it.id == current.current?.id }
        val candidate = queue.getOrNull(index + 1)
        if (candidate != null && (current.embeddedVideoId != null || candidate.mediaUri == null)) {
            playNow(candidate, queue)
            return
        }
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val current = _state.value
        val index = queue.indexOfFirst { it.id == current.current?.id }
        val candidate = queue.getOrNull(index - 1)
        if (candidate != null && (current.embeddedVideoId != null || candidate.mediaUri == null)) {
            playNow(candidate, queue)
            return
        }
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
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
            .apply { artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private companion object {
        const val TICK_MS = 500L
    }
}
