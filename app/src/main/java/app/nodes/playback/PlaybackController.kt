/*
 * Copyright (C) 2026 The Spartial Music Authors.
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
import app.nodes.core.data.SettingsRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 *
 * The queue is the list of tracks the user is listening through, in the order
 * they chose. It follows Glossy's model rather than a provider's list: Play next
 * inserts after the current track, Add to queue appends, and neither replaces
 * the queue with the collection a track happened to be tapped from.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SourceRegistry,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()

    /**
     * Index in [queue] of the entry the player is on. Kept separately from
     * `indexOfFirst { it.id == current.id }` so a deliberately repeated track
     * advances to the next copy instead of jumping back to the first one.
     */
    private var queuePosition: Int = 0

    /** Mirrors the "keep the queue unique" preference without a suspend read. */
    @Volatile
    private var preventDuplicates = true

    /**
     * The queue order before shuffle, so toggling shuffle off restores what the
     * user chose. Null while shuffle is off.
     */
    private var unshuffledQueue: List<Track>? = null

    /** True while the infinite queue is already fetching more tracks. */
    @Volatile
    private var extendingQueue = false

    /** Id of the queue entry whose stream URL was already warmed. */
    private var prefetchedTrackId: String? = null

    private var sleepJob: Job? = null

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
                playNow(track, queue.ifEmpty { listOf(track) }, forceRefresh = true)
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
            // Tracks whose URL is minted per play are not all in the player's
            // item list, so the player ends instead of advancing. Walk the
            // canonical queue instead of trusting the player's timeline.
            advanceAfterEnd()
        }
    }

    init {
        connect()
        // A timer set in an earlier session is an absolute timestamp, so it
        // survives the process being killed instead of restarting on launch.
        scope.launch { scheduleSleep(settings.current().sleepTimerEndsAt) }
        // Queue edits happen on the main thread; cache the preference so
        // playNext() and addToQueue() can honour it without suspending.
        scope.launch {
            settings.settings.collect { preventDuplicates = it.queuePreventDuplicates }
        }
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
                        prefetchUpcoming()
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

    /**
     * Ask the source behind the next queue entry to warm whatever [playNow]
     * will need for it - for YouTube Music that is the stream URL round-trip,
     * which is most of the wait when skipping ahead. Fired once per track,
     * shortly after playback is comfortably established, and never for a track
     * the player already holds a resolved URL for.
     */
    private fun prefetchUpcoming() {
        val state = _state.value
        if (state.isBuffering || state.embeddedVideoId != null) return
        if (state.positionMs < PREFETCH_AFTER_MS) return
        val upcoming = queue.getOrNull(queuePosition + 1) ?: return
        if (upcoming.mediaUri != null) return
        if (upcoming.id == prefetchedTrackId || upcoming.id == state.current?.id) return
        prefetchedTrackId = upcoming.id
        scope.launch {
            try {
                registry.forTrack(upcoming.sourceId)?.prefetch(upcoming)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Prefetching is an optimisation; a failure just means the
                // next skip takes the normal route.
                AppLogger.w("Player", "Stream prefetch failed", error)
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

    fun playNow(track: Track, playbackQueue: List<Track> = listOf(track), forceRefresh: Boolean = false) {
        scope.launch {
            // Minting a stream costs at least one network round trip, and the
            // tap that started it would otherwise appear to do nothing until
            // that answer arrives. Show the tapped track immediately with the
            // buffering ring, and put the previous state back if the stream
            // cannot be started.
            val before = _state.value
            val optimistic = track.id != before.current?.id
            if (optimistic) {
                _state.value = before.copy(
                    current = track,
                    queue = playbackQueue.ifEmpty { listOf(track) },
                    isPlaying = false,
                    isBuffering = true,
                    positionMs = 0L,
                    durationMs = track.durationMs,
                    message = null,
                )
            }
            fun fail(reason: String) {
                _state.value = before.copy(message = reason)
            }

            // Resolving a stream is provider and network code. A failure there
            // has to become a message; an uncaught exception on this (main)
            // scope would take the whole process down mid-listen.
            val result = try {
                val source = registry.forTrack(track.sourceId)
                if (forceRefresh) runCatching { source?.invalidateStream(track) }
                source?.play(track) ?: SourceResult.Failure(SourceErrorType.UNKNOWN)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Player", "Resolving the stream failed", error)
                fail(context.getString(R.string.playback_could_not_start))
                return@launch
            }

            // Play history is a nicety: a database problem must never be the
            // reason music does not start.
            runCatching { library.recordPlay(track) }

            val handle = (result as? SourceResult.Success)?.data

            try {
                val reason = applyHandle(track, playbackQueue, handle)
                if (reason != null) fail(reason)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Player", "Starting the player failed", error)
                fail(context.getString(R.string.playback_could_not_start))
            }
        }
    }

    /**
     * Hands [handle] to the engine that owns it.
     *
     * Split out of [playNow] so every Media3 call sits inside one guarded
     * block: a player rejecting an item becomes a message on the player, not an
     * uncaught exception that closes the app while music is playing.
     *
     * @return null when playback started, otherwise the reason to show.
     */
    private suspend fun applyHandle(
        track: Track,
        playbackQueue: List<Track>,
        handle: PlaybackHandle?,
    ): String? {
        return when (handle) {
            is PlaybackHandle.Stream -> {
                val player = awaitController()
                if (player == null) {
                    return "The player is still starting. Try again."
                }
                // The URL was minted for this track a moment ago, so it is
                // attached to the copy that goes into the player. Tracks from
                // the same source still carry no URL and stay out of the
                // player's item list; next() and previous() resolve them
                // through the source instead of Media3 skipping to nothing.
                val resolved = track.copy(mediaUri = handle.url)
                val withUrls = playbackQueue.map { if (it.id == track.id) resolved else it }
                // Media3 identifies an item by media id, so a queue that
                // deliberately repeats a track cannot be represented in its
                // timeline. In that case play just the current item and let
                // moveTo() walk the canonical queue; otherwise every resolved
                // track can travel in the player for gapless playback.
                val repeatsTrack = withUrls.map { it.id }.distinct().size != withUrls.size
                val playable = if (repeatsTrack) {
                    listOf(resolved)
                } else {
                    withUrls.filter { it.mediaUri != null }
                }
                if (playable.isEmpty()) {
                    return "Nothing in this list can be streamed."
                }
                val startIndex = playable.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                queue = withUrls
                queuePosition = positionOf(track, withUrls)
                player.setMediaItems(playable.map { it.toMediaItem() }, startIndex, 0L)
                player.prepare()
                player.play()
                _state.update {
                    it.copy(
                        current = resolved,
                        queue = withUrls,
                        queueIndex = queuePosition,
                        embeddedVideoId = null,
                        isPlaying = true,
                        isBuffering = true,
                        positionMs = 0L,
                        durationMs = track.durationMs,
                        message = null,
                    )
                }
                null
            }

            is PlaybackHandle.EmbeddedVideo -> {
                controller?.pause()
                // Keep the collection the track was opened from so Next and
                // Previous still walk it; the embedded player only ever shows
                // one video at a time.
                val nextQueue = playbackQueue.ifEmpty { listOf(track) }
                queue = nextQueue
                queuePosition = positionOf(track, nextQueue)
                _state.update {
                    it.copy(
                        current = track,
                        queue = nextQueue,
                        queueIndex = queuePosition,
                        embeddedVideoId = handle.videoId,
                        isPlaying = false,
                        isBuffering = false,
                        positionMs = 0L,
                        durationMs = track.durationMs,
                        message = null,
                    )
                }
                null
            }

            is PlaybackHandle.Unavailable -> handle.reason

            null -> "This track cannot be played from ${track.sourceId.key}."
        }
    }

    /**
     * Glossy-style "play next": insert directly after whatever is playing
     * instead of replacing the queue with the list the track came from. The
     * queue is the order the user built, not a provider's ordering.
     */
    fun playNext(track: Track) = insertQueue(track, afterCurrent = true)

    /** Glossy-style "add to queue": append to the end of the current queue. */
    fun addToQueue(track: Track) = insertQueue(track, afterCurrent = false)

    /** Jump to a row of the queue without disturbing the rows around it. */
    fun playQueueIndex(index: Int) {
        if (index !in queue.indices || index == queuePosition) return
        moveTo(index)
    }

    /**
     * Removes a queued track. The playing row is intentionally not removable:
     * deleting it would leave the player pointing at a vanished item. Skip
     * first, then remove it from its new (non-current) position.
     */
    fun removeFromQueue(index: Int) {
        val item = queue.getOrNull(index) ?: return
        if (item.id == _state.value.current?.id) return
        val next = queue.toMutableList()
        next.removeAt(index)
        if (index < queuePosition) queuePosition--
        publishQueue(next)
    }

    /**
     * Reorders one queue entry. The playing entry keeps playing; its position
     * pointer is adjusted so next/previous still walk the reordered list.
     */
    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val next = queue.toMutableList()
        val moved = next.removeAt(from)
        next.add(to, moved)
        queuePosition = when {
            from == queuePosition -> to
            from < queuePosition && to >= queuePosition -> queuePosition - 1
            from > queuePosition && to <= queuePosition -> queuePosition + 1
            else -> queuePosition
        }.coerceIn(0, next.lastIndex)
        queue = next
        _state.update { it.copy(queue = next, queueIndex = queuePosition) }
        reconcilePlayerQueue()
    }

    private fun insertQueue(track: Track, afterCurrent: Boolean) {
        val current = _state.value.current
        if (current == null || queue.isEmpty()) {
            playNow(track, listOf(track))
            return
        }
        if (track.id == current.id) {
            _state.update { it.copy(message = context.getString(R.string.player_already_playing)) }
            return
        }
        val next = QueueEdits.insert(
            queue = queue,
            playingId = current.id,
            track = track,
            afterCurrent = afterCurrent,
            preventDuplicates = preventDuplicates,
        )
        publishQueue(next)
        _state.update {
            it.copy(
                message = context.getString(
                    if (afterCurrent) R.string.player_playing_next else R.string.player_added_to_queue,
                    track.title,
                ),
            )
        }
    }

    private fun publishQueue(next: List<Track>) {
        val currentId = _state.value.current?.id
        queue = next
        if (currentId != null) {
            val forward = (queuePosition.coerceAtLeast(0) until next.size)
                .firstOrNull { next[it].id == currentId }
            queuePosition = forward ?: next.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        }
        _state.update { it.copy(queue = next, queueIndex = queuePosition) }
        reconcilePlayerQueue()
    }

    /**
     * Moves to the queue entry at [index]. A track already in the player's item
     * list is reused, so skipping does not re-mint a stream that is still valid
     * and does not reload a local file; everything else goes through [playNow].
     */
    private fun moveTo(index: Int) {
        val track = queue.getOrNull(index) ?: return
        val current = _state.value
        if (index == queuePosition && track.id == current.current?.id) return
        queuePosition = index
        _state.update { it.copy(queueIndex = index) }
        val player = controller
        val playerIndex = if (player != null &&
            current.embeddedVideoId == null &&
            track.id != current.current?.id
        ) {
            (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == track.id
            }
        } else {
            null
        }
        if (player != null && playerIndex != null) {
            try {
                player.seekTo(playerIndex, 0L)
                if (player.playbackState == Player.STATE_IDLE ||
                    player.playbackState == Player.STATE_ENDED
                ) {
                    player.prepare()
                }
                player.play()
                syncFromController()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Player", "Skipping inside the queue failed", error)
                playNow(track, queue)
            }
        } else {
            playNow(track, queue)
        }
    }

    /**
     * Index of [track] in [list], starting from the last known queue position.
     * Forward first (the normal case: playback advanced), then backwards (the
     * user pressed Previous), and only then the first copy.
     */
    private fun positionOf(track: Track, list: List<Track>): Int {
        if (list.isEmpty()) return 0
        val hint = queuePosition.coerceIn(0, list.lastIndex)
        if (list[hint].id == track.id) return hint
        val forward = (hint until list.size).firstOrNull { list[it].id == track.id }
        if (forward != null) return forward
        val backward = (hint - 1 downTo 0).firstOrNull { list[it].id == track.id }
        return backward ?: list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    }

    /**
     * Mirrors the canonical queue into the player's item list after an edit.
     *
     * Media3 identifies items by media id, so a queue that deliberately holds
     * the same track twice cannot be represented; those are left to [moveTo]
     * and skipped here. Removing and inserting items does not interrupt the
     * track that is currently playing.
     */
    private fun reconcilePlayerQueue() {
        val player = controller ?: return
        val currentId = _state.value.current?.id ?: return
        if (_state.value.embeddedVideoId != null) return
        val desired = queue.filter { it.mediaUri != null }
        if (desired.isEmpty() || desired.none { it.id == currentId }) return
        if (desired.map { it.id }.distinct().size != desired.size) return
        val canEdit = runCatching {
            player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        }.getOrDefault(false)
        if (!canEdit) return
        try {
            for (index in player.mediaItemCount - 1 downTo 0) {
                val id = player.getMediaItemAt(index).mediaId
                if (desired.none { it.id == id }) player.removeMediaItem(index)
            }
            desired.forEachIndexed { index, track ->
                val existing = (0 until player.mediaItemCount).firstOrNull {
                    player.getMediaItemAt(it).mediaId == track.id
                }
                when {
                    existing == null ->
                        player.addMediaItems(
                            index.coerceAtMost(player.mediaItemCount),
                            listOf(track.toMediaItem()),
                        )

                    existing != index -> player.moveMediaItem(existing, index)
                }
            }
        } catch (error: Exception) {
            AppLogger.w("Player", "Updating the player queue failed", error)
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
            moveTo(queuePosition + 1)
        } catch (error: Exception) {
            AppLogger.e("Player", "Skipping to the next track failed", error)
        }
    }

    fun previous() {
        try {
            val current = _state.value
            // Standard behaviour, and what Glossy does: after a few seconds
            // Previous restarts the song instead of leaving it.
            if (current.positionMs > 3_000L || queuePosition <= 0) {
                seekTo(0L)
                return
            }
            moveTo(queuePosition - 1)
        } catch (error: Exception) {
            AppLogger.e("Player", "Skipping to the previous track failed", error)
        }
    }

    /** Cycles Repeat off -> this track -> off. */
    fun cycleRepeat(): RepeatMode {
        // off -> all -> one -> off: all three states the transport shows.
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update {
            it.copy(
                repeatMode = next,
                message = context.getString(
                    when (next) {
                        RepeatMode.OFF -> R.string.player_repeat_off
                        RepeatMode.ONE -> R.string.player_repeat_one
                        RepeatMode.ALL -> R.string.player_repeat_all
                    },
                ),
            )
        }
        return next
    }

    /**
     * Shuffles the part of the queue after the playing track. The playing track
     * keeps its place, so audio does not restart mid-song; toggling again
     * restores the exact order the user chose.
     */
    fun toggleShuffle() {
        val orderBefore = _state.value.shuffleOn
        if (!orderBefore) {
            unshuffledQueue = queue
            val keep = queue.take(queuePosition + 1)
            val rest = queue.drop(queuePosition + 1).shuffled()
            publishQueue(keep + rest)
            _state.update { it.copy(shuffleOn = true, message = context.getString(R.string.player_shuffle_on)) }
        } else {
            val restored = unshuffledQueue ?: queue
            unshuffledQueue = null
            publishQueue(restored)
            _state.update { it.copy(shuffleOn = false, message = context.getString(R.string.player_shuffle_off)) }
        }
    }

    /** Infinite queue: a queue that fills itself again with related listening. */
    fun toggleInfiniteQueue() {
        val enabled = !_state.value.infiniteQueue
        _state.update {
            it.copy(
                infiniteQueue = enabled,
                message = context.getString(
                    if (enabled) R.string.player_infinite_on else R.string.player_infinite_off,
                ),
            )
        }
        // When switching it on, top the queue up straight away instead of
        // waiting for the current track to end.
        if (enabled) {
            extendQueueAndContinue()
        }
    }

    /**
     * Called when a track ends. Repeat one restarts it; everything else walks
     * the canonical queue, and when the queue runs out, Repeat all loops from
     * the top and the infinite queue refills itself first.
     */
    private fun advanceAfterEnd() {
        if (_state.value.repeatMode == RepeatMode.ONE) {
            replayCurrent()
            return
        }
        val next = queuePosition + 1
        if (next <= queue.lastIndex) {
            moveTo(next)
            return
        }
        if (_state.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            moveTo(0)
            return
        }
        if (_state.value.infiniteQueue) {
            extendQueueAndContinue()
            return
        }
        // Nothing follows: the ended track stays on screen, paused, so the
        // user can restart it rather than finding the app silent.
        _state.update { it.copy(isPlaying = false) }
    }

    /**
     * Repeat one must also work for the single-item timelines, so a track that
     * is not in the player's item list is started again through the source
     * instead of a seek that would land on nothing.
     */
    private fun replayCurrent() {
        val track = _state.value.current ?: return
        try {
            val player = controller
            val index = player?.let { p ->
                (0 until p.mediaItemCount).firstOrNull { p.getMediaItemAt(it).mediaId == track.id }
            }
            if (player != null && index != null) {
                player.seekTo(index, 0L)
                player.prepare()
                player.play()
                syncFromController()
            } else {
                playNow(track, queue)
            }
        } catch (error: Exception) {
            AppLogger.e("Player", "Repeating the current track failed", error)
            playNow(track, queue)
        }
    }

    /**
     * Appends the next handful of tracks to the queue and plays the first of
     * them. The candidates are the user's own listens and likes, minus what the
     * queue already holds - still only local data, nothing network-suggested.
     */
    private fun extendQueueAndContinue() {
        if (extendingQueue) return
        extendingQueue = true
        scope.launch {
            try {
                val known = queue.map { it.id }.toSet()
                val candidates = buildList {
                    runCatching { library.recentlyPlayed.first() }.getOrNull()?.let(::addAll)
                    runCatching { library.likedTracks.first() }.getOrNull()?.let(::addAll)
                }.filter { it.id !in known }.distinctBy { it.id }
                if (candidates.isEmpty()) {
                    _state.update { it.copy(message = context.getString(R.string.player_queue_ended)) }
                    return@launch
                }
                val extension = candidates.take(EXTEND_COUNT)
                publishQueue(queue + extension)
                _state.update { it.copy(message = context.getString(R.string.player_queue_extended, extension.size)) }
                moveTo(queuePosition + 1)
            } finally {
                extendingQueue = false
            }
        }
    }

    /**
     * Pauses playback once the timer runs out. Passing 0 (or less) cancels it.
     * The deadline is persisted so a timer keeps running while the app is away.
     */
    fun setSleepTimer(minutes: Int) {
        val endsAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        scope.launch { settings.setSleepTimerEndsAt(endsAt) }
        scheduleSleep(endsAt)
    }

    private fun scheduleSleep(endsAt: Long) {
        sleepJob?.cancel()
        if (endsAt <= 0L) {
            _state.update { it.copy(sleepTimerEndsAt = 0L) }
            return
        }
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            finishSleepTimer()
            return
        }
        _state.update { it.copy(sleepTimerEndsAt = endsAt) }
        sleepJob = scope.launch {
            delay(remaining)
            finishSleepTimer()
        }
    }

    private fun finishSleepTimer() {
        runCatching { controller?.pause() }
        scope.launch { settings.setSleepTimerEndsAt(0L) }
        _state.update {
            it.copy(
                sleepTimerEndsAt = 0L,
                isPlaying = false,
                message = context.getString(R.string.player_sleep_timer_done),
            )
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
        if (track != null) queuePosition = positionOf(track, queue)
        _state.update { state ->
            state.copy(
                current = track ?: state.current,
                queue = queue.ifEmpty { state.queue },
                queueIndex = queuePosition,
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

        /** How far into a song we wait before warming the next track's stream. */
        const val PREFETCH_AFTER_MS = 10_000L

        /** Tracks appended at a time while the infinite queue is on. */
        const val EXTEND_COUNT = 10
    }
}
