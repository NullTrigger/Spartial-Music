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
import coil.imageLoader
import coil.request.ImageRequest
import app.nodes.R
import app.nodes.core.crash.AppLogger
import app.nodes.core.crash.AppSnapshot
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.LyricsRepository
import app.nodes.core.data.AppSettings
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.PlaybackHandle
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.MusicSource
import app.nodes.core.source.SourceRegistry
import app.nodes.core.util.upgradeThumbnail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SourceRegistry,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val offlineDownloads: OfflineDownloads,
    private val lyrics: LyricsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /**
     * Glossy's "Similar content" list: the provider's own radio answer for the
     * track that is playing, offered under the queue so it can be folded in a
     * track at a time. It is never added on its own - only
     * [playNextSuggestion], [addSuggestionToQueue] and [addAllSuggestions]
     * move an entry into the real queue.
     */
    private val _suggestions = MutableStateFlow<List<Track>>(emptyList())
    val suggestions: StateFlow<List<Track>> = _suggestions.asStateFlow()

    /** The track [suggestions] was fetched for; a new track refetches them. */
    private var suggestionsSeedId: String? = null

    private var suggestionsJob: Job? = null

    /**
     * What the infinite queue's station has already offered this session.
     *
     * The queue keeps everything the station added, so its own id filter
     * covers those; this memory exists for the tracks the user removed. A
     * station that serves them again is the old "same list, again" behaviour
     * with extra steps.
     */
    private val stationOffered = LinkedHashSet<String>()

    /**
     * Where each station seed's radio has been read up to, so the next batch
     * continues the mix instead of replaying its first page.
     */
    private val radioCursors = HashMap<String, String>()

    /** The track whose lyrics were last prefetched, so one attempt per song. */
    private var prefetchedLyricsFor: String? = null

    private var controller: MediaController? = null

    /** Completed the moment the session binding answers, good or bad. */
    private var controllerReady = CompletableDeferred<MediaController?>()

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

    /** Debounce for writing the queue to the database. */
    private var queueSaveJob: Job? = null

    /** The short-loop Automix volume ramp; see [applyAutomix]. */
    private var automixJob: Job? = null

    /** Ids queued with "Play next"; a reshuffle keeps them right after the current track. */
    private val playNextIds = mutableSetOf<String>()

    /** Mirrors "Remember queue" without a suspend read. */
    @Volatile
    private var rememberQueue = true

    /** Position to resume from after a restore, consumed by the first play. */
    private var restoredPositionMs = 0L

    /** True once a restored queue is waiting for the user to press play. */
    @Volatile
    private var restoredPending = false

    /** Last time the playing position was written to the database. */
    private var lastPositionSaveMs = 0L

    private var sleepJob: Job? = null

    /**
     * Tracks whose stream URL has already been re-minted once. A googlevideo URL
     * is short-lived and bound to the request that produced it, so a 403 is
     * fixed by asking the source again - but only once per track per session, so
     * a track YouTube genuinely will not serve shows its error instead of
     * looping.
     */
    private val refreshedStreams = mutableSetOf<String>()

    /**
     * How many tracks in a row have been skipped because they would not play.
     * Reset the moment a stream reaches READY, so a flaky network gets a fresh
     * allowance while a run of dead tracks stops instead of racing through the
     * whole queue.
     */
    private var skipStreak = 0

    // MediaController.Listener only reports session events; playback callbacks
    // such as onMediaItemTransition live on Player.Listener, which a
    // MediaController implements.
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
            // A track that arrived through the preloaded item never went
            // through playNow, so the history entry it would have written is
            // recorded here instead.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val track = _state.value.current
                if (track != null) {
                    scope.launch { runCatching { library.recordPlay(track) } }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLogger.e("Player", "Playback error ${error.errorCodeName}", error)
            val track = _state.value.current
            // ERROR_CODE_IO_BAD_HTTP_STATUS is the CDN answering 403 (a 404 reads
            // the same way): the URL expired, or was minted for a session it no
            // longer matches. Resolve the track again and reuse the same queue
            // position rather than showing the user a dead stream.
            if (track != null &&
                track.sourceId == SourceId.YOUTUBE_MUSIC &&
                error.errorCode in REFRESHABLE_STREAM_ERRORS &&
                refreshedStreams.add(track.id)
            ) {
                _state.update {
                    it.copy(message = context.getString(R.string.playback_stream_expired))
                }
                playNow(track, queue.ifEmpty { listOf(track) }, forceRefresh = true)
                return
            }
            // One track the CDN will not serve must not end the session, and a
            // raw Media3 code is not something to show a listener. Move past it,
            // but only for a few tracks in a row: a whole queue of dead streams
            // stops with a message instead of flicking through every row.
            val next = queue.getOrNull(queuePosition + 1)
            if (next != null && skipStreak < MAX_CONSECUTIVE_SKIPS) {
                skipStreak++
                _state.update {
                    it.copy(message = context.getString(R.string.playback_track_skipped))
                }
                playNow(next, queue, forceRefresh = true)
                return
            }
            _state.update {
                it.copy(message = context.getString(R.string.playback_could_not_start))
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // A stream that reached READY worked, so allow one future refresh.
            if (playbackState == Player.STATE_READY) {
                _state.value.current?.id?.let(refreshedStreams::remove)
                // A stream that reached READY played, so the next failure is a
                // new problem rather than part of a skip streak.
                skipStreak = 0
            }
            if (playbackState != Player.STATE_ENDED) return
            if (_state.value.embeddedVideoId != null) return
            // The next track is normally preloaded, so this is the end of the
            // timeline, not every track boundary: it fires when the queue runs
            // out, when repeat-all wraps, and when nothing could be preloaded
            // (a refused stream). Walk the canonical queue either way rather
            // than trusting the player's item list.
            advanceAfterEnd()
        }
    }

    init {
        connect()
        startAutomixLoop()
        // A timer set in an earlier session is an absolute timestamp, so it
        // survives the process being killed instead of restarting on launch.
        scope.launch { scheduleSleep(settings.current().sleepTimerEndsAt) }
        // Queue edits happen on the main thread; cache the preference so
        // playNext() and addToQueue() can honour it without suspending.
        scope.launch {
            settings.settings.collect { current ->
                preventDuplicates = current.queuePreventDuplicates
                rememberQueue = current.rememberQueue
                if (!current.rememberQueue) {
                    // Turning the setting off clears what was saved; the queue
                    // on screen is untouched.
                    runCatching { library.clearSavedQueue() }
                }
                _state.update { it.copy(infiniteQueue = current.autoplaySimilar) }
            }
        }
        // The similar list follows whatever is playing: one radio call per new
        // track, cancelled if the user skips again before it answers. Nothing
        // is fetched while paused; the queue screen can ask for the list.
        scope.launch {
            state.map { it.current?.id to it.isPlaying }
                .distinctUntilChanged()
                .collect { (_, playing) -> if (playing) refreshSuggestions() }
        }
        // Glossy resolves the next track's lyrics ahead of time, so the lyrics
        // screen opens on them instead of on a spinner. Same here: a moment
        // into a song, the queue's next track is fetched in the background,
        // and a skip cancels the attempt.
        scope.launch {
            state.map { it.current?.id }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id == null) return@collectLatest
                    delay(LYRICS_PREFETCH_DELAY_MS)
                    prefetchNextLyrics()
                }
        }
        // Restore the queue from the last session, paused. Nothing plays and no
        // stream URL is fetched until the user presses play.
        restoreQueue()
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
                        trimPlayedItems(player)
                        // The position is written about every 15 seconds while
                        // playing, so a force-stop loses at most that much.
                        if (System.currentTimeMillis() - lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS) {
                            lastPositionSaveMs = System.currentTimeMillis()
                            saveQueueNow()
                        }
                        // Infinite queue tops up in the background while the
                        // music keeps playing; the end-of-queue path remains as
                        // a safety net.
                        maybeExtendQueueEarly()
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
     * The volume ramp behind Automix. One player, so this is a real
     * crossfade: the end of a track fades out before it stops and the next one
     * fades in over the same [AppSettings.crossfadeSeconds]. A single player
     * cannot overlap two decoders, so the mix happens across the transition,
     * not on top of it - which is also what keeps it cheap on low-end devices.
     * Volume is restored to full whenever Automix is off or the track is
     * outside its fade window, so nothing can leave the player quiet.
     *
     * This runs on its own short loop ([AUTOMIX_TICK_MS]) rather than the
     * half-second UI tick: a fade redrawn twice a second is heard as a stair
     * step, and this is only a float assignment per step.
     */
    private fun applyAutomix(player: Player, settings: AppSettings) {
        if (!settings.automix || settings.crossfadeSeconds <= 0) {
            if (player.volume != 1f) player.volume = 1f
            return
        }
        val fadeMs = settings.crossfadeSeconds * 1_000L
        // A stream reports C.TIME_UNSET until its container is parsed; the
        // provider's own duration is the right answer until then, and without
        // it the tail never faded at all.
        val duration = player.duration.takeIf { it > 0L }
            ?: _state.value.durationMs.takeIf { it > 0L }
            ?: return
        val position = player.currentPosition.coerceIn(0L, duration)
        val remaining = duration - position
        val hasNext = player.hasNextMediaItem() || queuePosition < queue.lastIndex
        val volume = when {
            // Head of a track: fade in.
            position < fadeMs -> position.toFloat() / fadeMs
            // Tail of a track that has a successor: fade out.
            remaining < fadeMs && hasNext -> remaining.toFloat() / fadeMs
            else -> 1f
        }
        player.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * The fade loop. Kept apart from the UI tick so the ramp is smooth without
     * making the whole player state update ten times a second.
     */
    private fun startAutomixLoop() {
        if (automixJob?.isActive == true) return
        automixJob = scope.launch {
            while (true) {
                val player = controller
                // Between tracks the player has no duration yet; the ramp is a
                // no-op there, and the next tick picks it up.
                if (player != null && player.isPlaying) {
                    runCatching { applyAutomix(player, settings.current()) }
                    delay(AUTOMIX_TICK_MS)
                } else {
                    // Nothing to ramp: a paused or stopped player only needs a
                    // cheap poll. Ten wake-ups a second while the app sits idle
                    // cost battery on a slow device for no audio at all.
                    delay(AUTOMIX_IDLE_TICK_MS)
                }
            }
        }
    }

    /**
     * Drops already-played items from the player's timeline. Preloading
     * appends the next track, so without this the item list grows for the
     * whole session and Media3 keeps a dead URL per track alive.
     */
    private fun trimPlayedItems(player: Player) {
        runCatching {
            while (player.currentMediaItemIndex > 0) player.removeMediaItem(0)
        }
    }

    /**
     * Ask the source behind the next queue entry to warm whatever [playNow]
     * will need for it - for YouTube Music that is the stream URL round-trip,
     * which is most of the wait when skipping ahead - and then hand the
     * resolved item to the player so it is already buffered when this track
     * ends. Fired once per track, as soon as playback is comfortably
     * established, and never for a track the player already holds.
     *
     * Preloading is what makes Automix audible and the transition gapless: a
     * player whose timeline holds only the current track ends (STATE_ENDED)
     * instead of moving on, so the volume ramp had nothing to fade into and
     * the next URL was still being minted when the fade finished.
     *
     * The cover goes out as well: on the player screen the background, the
     * palette and the hero all want the next track's art the moment it starts,
     * and the row that was tapped has usually been scrolled far behind.
     */
    private fun prefetchUpcoming() {
        val state = _state.value
        if (state.isBuffering || state.embeddedVideoId != null) return
        if (state.positionMs < PREFETCH_AFTER_MS) return
        // Repeat-one replays the same item, so a queued successor would be
        // played instead of repeating.
        if (state.repeatMode == RepeatMode.ONE) return
        val upcoming = queue.getOrNull(queuePosition + 1) ?: return
        if (upcoming.id == prefetchedTrackId || upcoming.id == state.current?.id) return
        prefetchedTrackId = upcoming.id
        prefetchArtwork(upcoming.artworkUri)
        scope.launch {
            val source = registry.forTrack(upcoming.sourceId)
            // Only streams without a URL need the network round trip; a local
            // file already has one, but is still preloaded below so the
            // player owns that transition too.
            if (upcoming.mediaUri == null) {
                try {
                    source?.prefetch(upcoming)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Prefetching is an optimisation; a failure just means the
                    // next skip takes the normal route.
                    AppLogger.w("Player", "Stream prefetch failed", error)
                }
            }
            preload(upcoming, source)
        }
    }

    /**
     * Resolves [track] and appends it to the player's timeline, so Media3 owns
     * the transition: it buffers the next stream while this one plays and
     * moves on without a gap. The queue entry is updated with the minted URL so
     * [syncFromController] can still map the media id back to it.
     *
     * Best-effort throughout: any failure leaves the normal end-of-track path
     * (resolve, then start) in charge.
     */
    private suspend fun preload(track: Track, source: MusicSource?) {
        if (source == null) return
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        // Something already follows the current item: that transition belongs
        // to the player, and appending here would put this track out of order
        // at the end of the timeline.
        if (player.currentMediaItemIndex != player.mediaItemCount - 1) return
        // Already queued, or the user moved on.
        for (index in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(index).mediaId == track.id) return
        }
        val handle = try {
            (runCatching { source.play(track) }.getOrNull() as? SourceResult.Success)?.data
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return
        }
        val stream = handle as? PlaybackHandle.Stream ?: return
        // The suspension above may have outlived this queue position: only
        // attach the item when it is still the entry that follows the current
        // one, and the player was not reset meanwhile.
        if (controller !== player) return
        val state = _state.value
        if (state.embeddedVideoId != null) return
        if (queue.getOrNull(queuePosition + 1)?.id != track.id) return
        if (player.currentMediaItem?.mediaId == track.id) return

        val resolved = track.copy(mediaUri = stream.url)
        queue = queue.map { if (it.id == track.id) resolved else it }
        offlineDownloads.rememberStream(stream.url, stream.userAgent)
        _state.update { current ->
            current.copy(queue = queue)
        }
        runCatching { player.addMediaItem(resolved.toMediaItem()) }
    }

    /**
     * Puts the cover for [artworkUri] into the shared image disk cache at a
     * size big enough for the player, so the next track's hero and palette are
     * warm before it plays. Best-effort: a failure only costs the normal load.
     */
    private fun prefetchArtwork(artworkUri: String?) {
        // The same rewrite the screen applies, or the disk cache key would not
        // match what Artwork later asks for and the download would be wasted.
        val url = upgradeThumbnail(artworkUri, PREFETCH_ARTWORK_PX) ?: return
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(PREFETCH_ARTWORK_PX)
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun connect() {
        if (controller != null) return
        val ready = controllerReady
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(listener)
                        syncFromController()
                        ready.complete(mediaController)
                    }
                    .onFailure { error ->
                        _state.update { it.copy(message = error.message ?: "Player unavailable.") }
                        ready.complete(null)
                    }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * The controller, as soon as it is bound.
     *
     * Waiting is event-driven: the old poll-every-100ms loop only noticed the
     * binding a polling interval after the fact, which was dead air added to
     * every start. A binding that failed once is retried with a fresh
     * handshake; the wait itself is capped, because a session that cannot
     * start must tell the user instead of hanging.
     */
    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        if (controllerReady.isCompleted) controllerReady = CompletableDeferred()
        connect()
        return withTimeoutOrNull(CONTROLLER_WAIT_MS) { controllerReady.await() } ?: controller
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playNow(
        track: Track,
        playbackQueue: List<Track> = listOf(track),
        forceRefresh: Boolean = false,
        startPositionMs: Long = 0L,
    ) {
        // A new start is a new station: what the last one already offered says
        // nothing about this one.
        stationOffered.clear()
        scope.launch {
            // A restored queue resumes where the last session stopped, but
            // only when the restored track is the one being started.
            val resumeAt = if (restoredPending && track.id == _state.value.current?.id) {
                restoredPositionMs
            } else {
                startPositionMs
            }
            restoredPending = false
            restoredPositionMs = 0L
            // A new start re-plans what is ahead of it.
            prefetchedTrackId = null
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
            var offlineCacheKey: String? = null
            val result = try {
                val source = registry.forTrack(track.sourceId)
                if (forceRefresh) runCatching { source?.invalidateStream(track) }
                val offlineDownload = if (track.sourceId == SourceId.YOUTUBE_MUSIC) {
                    runCatching { offlineDownloads.completedDownload(track.id) }.getOrNull()
                } else {
                    null
                }
                if (offlineDownload != null) {
                    offlineCacheKey = track.id
                    SourceResult.Success(
                        PlaybackHandle.Stream(offlineDownload.request.uri.toString()),
                    )
                } else {
                    val playResult = source?.play(track) ?: SourceResult.Failure(SourceErrorType.UNKNOWN)
                    ((playResult as? SourceResult.Success)?.data as? PlaybackHandle.Stream)?.let { stream ->
                        offlineDownloads.rememberStream(stream.url, stream.userAgent)
                    }
                    playResult
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e("Player", "Resolving the stream failed", error)
                fail(context.getString(R.string.playback_could_not_start))
                return@launch
            }

            // Play history is a nicety: a database problem must never be the
            // reason music does not start - and the write must not sit in
            // front of the player either. It is handed to its own coroutine
            // so the tap's critical path ends at the player start.
            scope.launch { runCatching { library.recordPlay(track) } }

            val handle = (result as? SourceResult.Success)?.data

            try {
                val reason = applyHandle(
                    track = track,
                    playbackQueue = playbackQueue,
                    handle = handle,
                    startPositionMs = resumeAt,
                    offlineCacheKey = offlineCacheKey,
                )
                if (reason != null) {
                    fail(reason)
                }
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
        startPositionMs: Long = 0L,
        offlineCacheKey: String? = null,
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
                player.setMediaItems(
                    playable.map { item ->
                        item.toMediaItem(
                            offlineCacheKey = offlineCacheKey?.takeIf { key -> key == item.id },
                        )
                    },
                    startIndex,
                    startPositionMs.coerceAtLeast(0L),
                )
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
        // "Play next" rows keep their slot on a reshuffle.
        if (afterCurrent) {
            playNextIds.add(track.id)
            if (playNextIds.size > PLAY_NEXT_KEPT) {
                playNextIds.remove(playNextIds.first())
            }
        }
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
        scheduleQueueSave()
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
                scheduleQueueSave()
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
            // A restored queue starts on this press, at the saved position.
            // Until now nothing was resolved and no URL was fetched.
            if (restoredPending) {
                val track = _state.value.current ?: queue.getOrNull(queuePosition)
                if (track != null) {
                    playNow(track, queue)
                    return
                }
            }
            val player = controller ?: return
            if (player.isPlaying) {
                player.pause()
                // Position is saved on every pause, so a force-stop after this
                // loses nothing.
                saveQueueNow()
            } else {
                player.play()
            }
            syncFromController()
        } catch (error: Exception) {
            AppLogger.e("Player", "Toggling playback failed", error)
        }
    }

    /**
     * Called when playback stops for good (task removed, service stopping),
     * while the position can still be read. Synchronous-safe: the write is
     * fire-and-forget off the main thread.
     */
    fun persistOnStop() {
        saveQueueNow()
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
        scheduleQueueSave()
    }

    /**
     * Deals the upcoming tracks out again without interrupting the playing
     * song. "Play next" entries keep their slot right after it, in the order
     * they were queued. Turns shuffle on if it was off (the pre-shuffle order
     * is kept, so turning shuffle off still restores what the user chose).
     */
    fun reshuffle() {
        val upcoming = queue.drop(queuePosition + 1)
        if (upcoming.isEmpty()) return
        if (unshuffledQueue == null) unshuffledQueue = queue
        val byId = HashMap<String, ArrayDeque<Track>>()
        upcoming.forEach { byId.getOrPut(it.id) { ArrayDeque() }.addLast(it) }
        val dealt = QueueEdits.reshuffled(upcoming.map { it.id }, playNextIds, kotlin.random.Random.Default)
            .mapNotNull { id -> byId[id]?.removeFirstOrNull() }
        publishQueue(queue.take(queuePosition + 1) + dealt)
        _state.update {
            it.copy(shuffleOn = true, message = context.getString(R.string.player_reshuffled))
        }
        scheduleQueueSave()
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
        // The quick toggle and the saved setting are the same switch.
        scope.launch { settings.setAutoplaySimilar(enabled) }
        // When switching it on, top the queue up straight away instead of
        // waiting for the current track to end. Playback is never interrupted.
        if (enabled) {
            extendQueue(playFirst = false)
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
            extendQueue(playFirst = true)
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
     * Appends the next handful of tracks to the queue: the playing track's own
     * radio, continued from where the last batch stopped - minus what is
     * queued, what played in the last 50 tracks and the artists or albums the
     * batch already took, so a queue started from one song does not turn into
     * that song's artist. Only when the radio cannot answer does the local
     * library stand in; the provider's front page never does. Nothing is ever
     * invented: "queue ended" is reported only when every step comes back
     * empty.
     *
     * @param playFirst when true (the end-of-queue safety net) the first new
     *        track is started; otherwise playback continues untouched.
     */
    private fun extendQueue(playFirst: Boolean) {
        if (extendingQueue) return
        extendingQueue = true
        _state.update { it.copy(isFindingMore = true) }
        scope.launch {
            try {
                val extension = fetchMoreTracks()
                if (extension.isEmpty()) {
                    _state.update { it.copy(isFindingMore = false) }
                    if (playFirst) {
                        _state.update {
                            it.copy(message = context.getString(R.string.player_queue_ended))
                        }
                    }
                    return@launch
                }
                trimPlayedHistory()
                publishQueue(queue + extension)
                _state.update { it.copy(isFindingMore = false) }
                if (playFirst) {
                    moveTo(queuePosition + 1)
                }
            } finally {
                extendingQueue = false
            }
        }
    }

    /** Early top-up so playback never pauses: at most two tracks still queued. */
    private fun maybeExtendQueueEarly() {
        if (!_state.value.infiniteQueue) return
        if (queue.size - queuePosition - 1 > 2) return
        extendQueue(playFirst = false)
    }

    // ------------------------------------------------------------------
    // Similar content (Glossy's queue-add mechanism)
    // ------------------------------------------------------------------

    /**
     * Asks for the similar list now, for a paused queue that wants its shelf
     * before playback starts. Cheap when the list is already loaded.
     */
    fun requestSuggestions() = refreshSuggestions()

    /** Folds [track] into the queue right after the current one. */
    fun playNextSuggestion(track: Track) {
        dropSuggestion(track)
        playNext(track)
    }

    /** Appends [track] to the end of the queue. */
    fun addSuggestionToQueue(track: Track) {
        dropSuggestion(track)
        addToQueue(track)
    }

    /**
     * Appends every remaining suggestion in list order, as one deliberate
     * action rather than a burst of separate edits.
     */
    fun addAllSuggestions() {
        val all = _suggestions.value
        if (all.isEmpty()) return
        val queued = queue.mapTo(HashSet()) { it.id }
        _suggestions.value = emptyList()
        all.filterNot { it.id in queued }.forEach(::addToQueue)
    }

    /** Drops the suggestion the user just acted on, like Glossy's automix rows. */
    private fun dropSuggestion(track: Track) {
        _suggestions.update { list -> list.filterNot { it.id == track.id } }
    }

    /**
     * Refetches the similar list for whatever is playing. The radio answer is
     * the provider's own judgement of "more like this" - the same `next`
     * request Glossy builds its queue from - so it is used as it comes back,
     * minus what is already queued. An empty or failed answer clears the list
     * rather than offering something unrelated.
     */
    private fun refreshSuggestions() {
        suggestionsJob?.cancel()
        val current = _state.value.current
        if (current == null) {
            suggestionsSeedId = null
            _suggestions.value = emptyList()
            return
        }
        if (current.id == suggestionsSeedId && _suggestions.value.isNotEmpty()) return
        suggestionsSeedId = current.id
        suggestionsJob = scope.launch {
            val radio = radioSuggestions(current, SUGGESTION_COUNT * 2)
            val queued = queue.mapTo(HashSet()) { it.id }
            val picked = QueueEdits.varyByName(
                radio.filter {
                    it.id != current.id &&
                        it.id !in queued &&
                        (it.videoId != null || it.mediaUri != null)
                }.distinctBy { it.id },
                takenNames = emptySet(),
                limit = SUGGESTION_COUNT,
            )
            // A newer track may have started while this call was in flight.
            if (suggestionsSeedId == current.id) _suggestions.value = picked
        }
    }

    /**
     * The provider's radio answer for [track]: its own "up next" list. Shared
     * by the queue, the Reel feed and the similar list, so every place the
     * player grows uses the same mechanism. A source that cannot answer, or a
     * dead network, is an empty list - never an exception for the caller.
     */
    suspend fun radioSuggestions(track: Track, limit: Int): List<Track> = runCatching {
        (registry.forTrack(track.sourceId)?.getRelated(track, limit) as? SourceResult.Success)
            ?.data.orEmpty()
    }.getOrDefault(emptyList())

    /**
     * The station's radio, paged forward.
     *
     * A radio is a playlist with a continuation token, not a fixed list. Asking
     * only for the first page means every batch after the first sees the same
     * twenty-five songs - all of them already queued - and the queue has to
     * fall back to something else. The cursor is kept per seed and advanced on
     * every call, so a long session keeps receiving new tracks from the same
     * mix; when the radio is finally exhausted the plain first page stands in.
     */
    private suspend fun stationRadio(seed: Track, limit: Int): List<Track> {
        val source = registry.forTrack(seed.sourceId) ?: return emptyList()
        val key = seed.videoId ?: seed.id
        repeat(RADIO_PAGE_TRIES) { attempt ->
            val page = runCatching { source.radioPage(seed, radioCursors[key], limit) }.getOrNull()
                ?: return radioSuggestions(seed, limit)
            val next = page.continuation
            if (next == null) {
                radioCursors.remove(key)
            } else {
                if (radioCursors.size >= RADIO_CURSOR_MEMORY) radioCursors.clear()
                radioCursors[key] = next
            }
            if (page.tracks.isNotEmpty()) return page.tracks
            // A page with a token but no rows is a skipped segment; the next
            // page usually has rows. One retry, then the seed is given up.
            if (next == null || attempt == RADIO_PAGE_TRIES - 1) return emptyList()
        }
        return emptyList()
    }

    /**
     * The next batch, a station rather than a single radio call.
     *
     * The batch is the provider's radio and nothing else: two seeds - the
     * playing track and, when it is a different artist, the one before it -
     * are asked for their radios, and the answers are blended round-robin.
     *
     * The home feed's picks used to be mixed in as "discovery", three slots in
     * ten. That is what put the account's trending rails (devotional videos,
     * regional film songs) inside a queue started from one Punjabi track: the
     * feed's first rows are the provider's front page, not this song's
     * neighbours. Discovery is gone; a radio answer is the queue.
     *
     * Variety is enforced on top: no song name twice, no artist the queue just
     * ended on, one song per artist and per album in a batch. Already queued,
     * recently played and already-offered tracks never come back. Only when
     * both radios are dry does the local fallback stand in - the queue's own
     * echo, then the library - and even that is shaped by the queue's mood,
     * because a queue grown from a Malayalam song should not be taken over by
     * an unrelated pool.
     */
    private suspend fun fetchMoreTracks(): List<Track> {
        val queuedIds = queue.mapTo(HashSet()) { it.id }
        val recentlyPlayedIds = runCatching {
            library.recentlyPlayedIds(RECENT_EXCLUDE_COUNT)
        }.getOrDefault(emptySet())
        val offeredIds = queuedIds + stationOffered
        val excludedIds = offeredIds + recentlyPlayedIds
        val names = queue.mapTo(HashSet()) { QueueEdits.titleKey(it) }
        val seed = _state.value.current ?: queue.lastOrNull()

        val stations = stationSeeds().map { stationRadio(it, STATION_RADIO_WINDOW) }
        // The artists the queue just played are out for this batch: a station
        // grown from one song otherwise opens with that same artist again.
        val tailArtists = queue.takeLast(ARTIST_TAIL)
            .map { QueueEdits.artistKey(it) }
            .filter { it.isNotEmpty() }
            .toHashSet()
        val station = QueueEdits.stationStream(
            stations = stations,
            excludedIds = excludedIds,
            takenNames = names,
            limit = EXTEND_COUNT,
            takenArtists = tailArtists,
        )
        if (station.isNotEmpty()) {
            rememberStationOffered(station)
            return station
        }

        val radio = stations.flatten()
        val mood = moodFor(seed)
        val pool = localTrackPool()
        // The local echo of what the queue is already playing, then the pool.
        // The provider's front page never stands in here.
        val echo = QueueEdits.echo(queue, pool, EXTEND_COUNT)
        // A wide net first, then the mood picks its share from it: shaping a
        // shortlist of ten would only reshuffle what the fallbacks happened to
        // return first.
        val candidates = QueueEdits.selectExtension(
            related = QueueEdits.varyByName(radio + echo, names, EXTEND_CANDIDATES),
            localPool = QueueEdits.varyByName(pool, names, EXTEND_CANDIDATES),
            queuedIds = excludedIds,
            recentlyPlayedIds = recentlyPlayedIds,
            limit = EXTEND_CANDIDATES,
        )
        val varied = QueueEdits.varyByArtist(candidates, tailArtists, EXTEND_CANDIDATES)
        val picked = QueueEdits.shapeByMood(varied, mood, EXTEND_COUNT)
        if (picked.isNotEmpty()) {
            rememberStationOffered(picked)
            return picked
        }
        // Last step: a shuffle of the whole local library, still minus what is
        // queued, offered or just played.
        val last = QueueEdits.shapeByMood(
            QueueEdits.varyByArtist(
                QueueEdits.varyByName(
                    pool.shuffled().filter { it.id !in excludedIds },
                    names,
                    EXTEND_CANDIDATES,
                ),
                tailArtists,
                EXTEND_CANDIDATES,
            ),
            mood,
            EXTEND_COUNT,
        )
        rememberStationOffered(last)
        return last
    }

    /**
     * The seeds the station grows from: what is playing, then the track before
     * it when it is a different artist, so the two radios' listeners meet
     * instead of one radio being appended to itself.
     */
    private fun stationSeeds(): List<Track> {
        val current = _state.value.current ?: queue.lastOrNull() ?: return emptyList()
        val seeds = ArrayList<Track>(STATION_SEED_COUNT)
        if (current.videoId != null || current.mediaUri != null) seeds += current
        var index = queuePosition - 1
        val floor = queuePosition - STATION_SEED_LOOKBACK
        while (index >= floor && index >= 0 && seeds.size < STATION_SEED_COUNT) {
            val candidate = queue.getOrNull(index)
            index--
            if (candidate == null || candidate.id == current.id) continue
            if (candidate.videoId == null && candidate.mediaUri == null) continue
            if (seeds.any { it.artist.equals(candidate.artist, ignoreCase = true) }) continue
            seeds += candidate
        }
        return seeds
    }

    /**
     * Resolves the next queue track's lyrics in the background, so changing song
     * finds them already cached. One attempt per track: a provider that missed
     * is not asked again on every play of the same song.
     */
    private suspend fun prefetchNextLyrics() {
        val next = queue.getOrNull(queuePosition + 1) ?: return
        if (next.id == prefetchedLyricsFor) return
        prefetchedLyricsFor = next.id
        try {
            // A cache hit costs nothing; only a cold track is fetched.
            if (lyrics.cachedFor(next) == null) lyrics.lyricsFor(next)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.w("Playback", "Prefetching lyrics for ${next.id} failed", error)
        }
    }

    /** Remembers the station's picks so, once removed, they never return. */
    private fun rememberStationOffered(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        tracks.mapTo(stationOffered) { it.id }
        while (stationOffered.size > STATION_MEMORY) {
            val oldest = stationOffered.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
    }

    /**
     * The listening mood around [seed]: what is playing plus the tail of the
     * queue, since what the user queued last is the best statement of what
     * they want next. Purely local inference; see [MoodProfile].
     */
    private fun moodFor(seed: Track?): MoodProfile =
        MoodProfile.of(seed, queue.drop(queuePosition + 1).takeLast(MOOD_TAIL))

    /** The device's own catalogue, in preference order. */
    private suspend fun localTrackPool(): List<Track> = buildList {
        runCatching { library.recentlyPlayed.first() }.getOrNull()?.let(::addAll)
        runCatching { library.likedTracks.first() }.getOrNull()?.let(::addAll)
        runCatching { library.allTracks.first() }.getOrNull()?.let(::addAll)
    }

    /** The queue cannot grow forever: at most 100 played tracks stay behind. */
    private fun trimPlayedHistory() {
        val drop = queuePosition - MAX_PLAYED_BEHIND
        if (drop <= 0) return
        publishQueue(queue.drop(drop))
    }

    // ------------------------------------------------------------------
    // Queue persistence
    // ------------------------------------------------------------------

    /** Debounced save: one write shortly after the last change. */
    private fun scheduleQueueSave() {
        queueSaveJob?.cancel()
        queueSaveJob = scope.launch {
            delay(QUEUE_SAVE_DEBOUNCE_MS)
            saveQueueNow()
        }
    }

    /**
     * Writes the queue and its position now. Called on the debounce, on pause
     * and stop, and periodically while playing - never on the UI thread's
     * critical path, and never able to disturb playback.
     */
    fun saveQueueNow() {
        if (!rememberQueue) return
        val snapshot = QueueEdits.Snapshot(
            trackIds = queue.map { it.id },
            originalPositions = originalOrderPositions(),
            currentIndex = queuePosition.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
            positionMs = controller?.currentPosition?.takeIf { it > 0L }
                ?: _state.value.positionMs,
            shuffleEnabled = _state.value.shuffleOn,
        )
        scope.launch {
            runCatching {
                library.saveQueue(
                    tracks = queue,
                    currentIndex = snapshot.currentIndex,
                    positionMs = snapshot.positionMs,
                    shuffleEnabled = snapshot.shuffleEnabled,
                    originalPositions = snapshot.originalPositions,
                )
            }.onFailure { error ->
                AppLogger.w("Player", "Saving the queue failed", error)
            }
        }
    }

    /** Where each queue entry sat before shuffle; identity while shuffle is off. */
    private fun originalOrderPositions(): List<Int> {
        val original = unshuffledQueue ?: return queue.indices.toList()
        val positions = HashMap<String, ArrayDeque<Int>>()
        original.forEachIndexed { index, track ->
            positions.getOrPut(track.id) { ArrayDeque() }.addLast(index)
        }
        return queue.map { track -> positions[track.id]?.removeFirstOrNull() ?: 0 }
    }

    /**
     * Loads the saved queue back, paused. Nothing plays and no stream URL is
     * fetched until the user presses play; a track that no longer resolves is
     * skipped at load time instead of crashing later.
     */
    private fun restoreQueue() {
        scope.launch {
            val current = settings.current()
            if (!current.rememberQueue) return@launch
            val saved = runCatching { library.savedQueue() }.getOrNull() ?: return@launch
            val tracks = saved.tracks
            if (tracks.isEmpty()) return@launch
            queue = tracks
            queuePosition = saved.currentIndex.coerceIn(0, tracks.lastIndex)
            restoredPositionMs = saved.positionMs
            restoredPending = true
            // Shuffle restores with the queue when the user asked for it; the
            // pre-shuffle order travels along so turning shuffle off works.
            val shuffle = saved.shuffleEnabled && current.rememberShuffle
            unshuffledQueue = if (shuffle) {
                tracks.indices
                    .sortedBy { saved.originalPositions.getOrNull(it) ?: it }
                    .map { tracks[it] }
            } else {
                null
            }
            _state.update {
                it.copy(
                    current = tracks.getOrNull(queuePosition),
                    queue = tracks,
                    queueIndex = queuePosition,
                    isPlaying = false,
                    isBuffering = false,
                    positionMs = saved.positionMs,
                    durationMs = tracks.getOrNull(queuePosition)?.durationMs ?: 0L,
                    shuffleOn = shuffle,
                )
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
        saveQueueNow()
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

    private fun Track.toMediaItem(offlineCacheKey: String? = null): MediaItem {
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
            .apply {
                offlineCacheKey?.let(::setCustomCacheKey)
            }
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

        /**
         * How often the Automix ramp is applied. Ten steps a second is smooth
         * to the ear and is one float assignment per step, so it costs less
         * than the state update the 500ms tick already does.
         */
        const val AUTOMIX_TICK_MS = 100L

        /** Idle poll for the same loop: the ramp only exists while playing. */
        private const val AUTOMIX_IDLE_TICK_MS = 750L

        /**
         * How far into a song we wait before warming the next track's stream
         * and queueing it in the player. Two seconds is past the point where
         * a start is still at risk, and leaves minutes of runway on every
         * normal track.
         */
        const val PREFETCH_AFTER_MS = 2_000L

        /** Cover size warmed ahead of the next track: enough for the player hero. */
        const val PREFETCH_ARTWORK_PX = 640

        /** Tracks appended at a time while the infinite queue is on. */
        const val EXTEND_COUNT = 10

        /** How long a song plays before the next track's lyrics are prefetched. */
        const val LYRICS_PREFETCH_DELAY_MS = 2_000L

        /** Rows offered in the queue's "Similar content" list. */
        const val SUGGESTION_COUNT = 12

        /** Candidate tracks gathered per batch before the mood shapes them. */
        const val EXTEND_CANDIDATES = EXTEND_COUNT * 3

        /** Radios blended into one station batch: the track and the one before it. */
        const val STATION_SEED_COUNT = 2

        /** How far back the second station seed may sit in the queue. */
        const val STATION_SEED_LOOKBACK = 4

        /** Radio window asked of each station seed. */
        const val STATION_RADIO_WINDOW = 25

        /** Pages tried per seed before the station treats its radio as dry. */
        const val RADIO_PAGE_TRIES = 2

        /** Radio cursors remembered at once; clearing only restarts a mix. */
        const val RADIO_CURSOR_MEMORY = 200

        /**
         * How many of the queue's last tracks have their artists kept out of
         * the next batch, so a station never restarts on the same artist.
         */
        const val ARTIST_TAIL = 2

        /** Bounded station memory, in track ids. */
        const val STATION_MEMORY = 400

        /** Queue tail read for the mood, beyond what is playing. */
        const val MOOD_TAIL = 5

        /** How long to wait for the session binding before giving up. */
        const val CONTROLLER_WAIT_MS = 2_000L

        /** Queue writes are debounced this long after a burst of edits. */
        const val QUEUE_SAVE_DEBOUNCE_MS = 500L

        /** How often the playing position is written while playing. */
        const val POSITION_SAVE_INTERVAL_MS = 15_000L

        /** Recently played tracks that never re-enter the infinite queue. */
        const val RECENT_EXCLUDE_COUNT = 50

        /** How many played tracks stay behind the current one at most. */
        const val MAX_PLAYED_BEHIND = 100

        /** How many "Play next" ids are remembered for reshuffles. */
        const val PLAY_NEXT_KEPT = 32

        /**
         * Tracks skipped in a row before playback stops. Enough to walk past a
         * couple of dead streams, too few to race through a queue that is all
         * refused.
         */
        const val MAX_CONSECUTIVE_SKIPS = 3

        /**
         * Playback errors a fresh stream URL can fix: the CDN's 403/404 for a
         * URL that expired or was minted for another session.
         */
        val REFRESHABLE_STREAM_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        )
    }
}
