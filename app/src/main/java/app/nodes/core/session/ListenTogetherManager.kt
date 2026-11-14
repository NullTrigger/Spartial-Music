/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.session

import app.nodes.core.crash.AppLogger
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import app.nodes.playback.PlaybackController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Someone waiting for the host to let them in. */
data class PendingJoiner(val userId: String, val username: String)

/** What the Sessions screen shows. */
data class RoomUiState(
    val status: RoomStatus = RoomStatus.DISCONNECTED,
    val code: String = "",
    val isHost: Boolean = false,
    val members: List<String> = emptyList(),
    val pending: List<PendingJoiner> = emptyList(),
    val notice: String? = null,
)

/**
 * Turns room events into player commands, and this device's player into room
 * events.
 *
 * Three rules hold the whole thing together:
 *
 *  - **Only the host broadcasts.** The server answers a non-host
 *    `playback_action` with `not_host`, so broadcasting from a guest would only
 *    surface errors the user can do nothing about.
 *  - **Applying a remote change never re-broadcasts it.** [applyingRemote]
 *    suppresses the host watcher while a remote command is carried out; without
 *    it a host that receives its own relayed action would send it straight back
 *    and the room would oscillate.
 *  - **Position is corrected against the server clock.** The ping replies carry
 *    it, and a room that drifts is a room nobody listens in.
 *
 * The protocol lives in [ListenTogetherClient] and the field numbers in
 * [ListenTogetherProtocol]; this class only knows about playback.
 */
@Singleton
class ListenTogetherManager @Inject constructor(
    private val playback: PlaybackController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    private var client: ListenTogetherClient? = null
    private var eventsJob: Job? = null
    private var statusJob: Job? = null
    private var hostJob: Job? = null

    private var myUserId = ""
    private var hostId = ""
    private var displayName = DEFAULT_NAME
    private var applyingRemote = false
    private var roomTrack: Track? = null

    fun create(serverUrl: String, username: String) {
        displayName = username.trim().ifBlank { DEFAULT_NAME }
        open(serverUrl)
        _state.update { it.copy(notice = null) }
        client?.createRoom(displayName)
    }

    fun join(serverUrl: String, code: String, username: String) {
        displayName = username.trim().ifBlank { DEFAULT_NAME }
        open(serverUrl)
        _state.update { it.copy(notice = null) }
        client?.joinRoom(code, displayName)
    }

    fun approveJoin(userId: String) = client?.approveJoin(userId)

    fun rejectJoin(userId: String) = client?.rejectJoin(userId, null)

    /**
     * Remembers what to call this device in the room. The server only reads the
     * name when a room is created or joined, so a change mid-session applies on
     * the next one rather than pretending to rename a live member.
     */
    fun announceName(value: String) {
        displayName = value.trim().ifBlank { DEFAULT_NAME }
    }

    fun reportNotice(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun leave() {
        client?.leaveRoom()
        reset()
        _state.value = RoomUiState()
    }

    /** Called when the Sessions screen leaves the composition. */
    fun onScreenClosed() {
        // The room survives: a listener who navigates to a playlist mid-song
        // must not be dropped. Only the socket is closed if we were never in a
        // room at all.
        if (_state.value.status == RoomStatus.CONNECTING) reset()
    }

    private fun open(serverUrl: String) {
        if (client != null) return
        val newClient = ListenTogetherClient(serverUrl)
        client = newClient
        eventsJob = scope.launch { newClient.events.collect { handle(it) } }
        statusJob = scope.launch {
            newClient.status.collect { status -> _state.update { it.copy(status = status) } }
        }
    }

    private fun reset() {
        eventsJob?.cancel()
        statusJob?.cancel()
        hostJob?.cancel()
        eventsJob = null
        statusJob = null
        hostJob = null
        client?.close()
        client = null
        myUserId = ""
        hostId = ""
        roomTrack = null
        applyingRemote = false
    }

    private fun handle(event: RoomEvent) {
        when (event) {
            RoomEvent.Connected -> Unit

            is RoomEvent.Created -> {
                myUserId = event.userId
                hostId = event.userId
                // The host's own name is not echoed back by the server, so the
                // member list starts empty and the UI labels the first row
                // "You".
                _state.update { it.copy(status = RoomStatus.IN_ROOM, code = event.code, isHost = true, members = emptyList()) }
                startHosting()
            }

            is RoomEvent.Joined -> {
                myUserId = event.userId
                hostId = event.state.hostId
                applyState(event.state)
                _state.update {
                    it.copy(
                        status = RoomStatus.IN_ROOM,
                        code = event.code,
                        isHost = event.userId == event.state.hostId,
                        members = event.state.users.map(RemoteUser::username),
                    )
                }
                // A host who reconnects into a room they still own has to
                // resume broadcasting, or the room freezes on their last action.
                if (_state.value.isHost) startHosting()
            }

            is RoomEvent.JoinRequest -> _state.update {
                it.copy(pending = (it.pending + PendingJoiner(event.userId, event.username)).distinctBy(PendingJoiner::userId))
            }

            is RoomEvent.UserJoined -> _state.update {
                it.copy(
                    members = (it.members + event.username).distinct(),
                    pending = it.pending.filterNot { joiner -> joiner.userId == event.userId },
                )
            }

            is RoomEvent.UserLeft -> _state.update {
                it.copy(
                    members = it.members - event.username,
                    pending = it.pending.filterNot { joiner -> joiner.userId == event.userId },
                )
            }

            is RoomEvent.Playback -> applySync(event.event)

            is RoomEvent.State -> applySync(event.event)

            is RoomEvent.Rejected -> {
                reset()
                _state.value = RoomUiState(notice = event.reason.ifBlank { "Join declined" })
            }

            is RoomEvent.Failed -> {
                AppLogger.w(TAG, "Server error ${event.code}: ${event.message}")
                _state.update { it.copy(notice = friendly(event.code, event.message)) }
            }

            RoomEvent.Left -> {
                reset()
                _state.value = RoomUiState(notice = "Left the session")
            }
        }
    }

    /** The server's short codes are not user-facing; explain them instead. */
    private fun friendly(code: String, message: String): String = when (code) {
        "room_not_found" -> "No session with that code. Check it and try again."
        "room_full" -> "That session is full."
        "host_not_allowed" -> "This server only lets approved accounts host."
        "not_host" -> "Only the host controls playback in this session."
        "already_in_room" -> "You are already in a session."
        else -> message.ifBlank { code }
    }

    /** Applies the room's state on join, before any delta has arrived. */
    private fun applyState(state: RoomState) {
        val track = state.currentTrack?.let(::toTrack) ?: return
        roomTrack = track
        applyingRemote = true
        scope.launch {
            playback.playNow(track, listOf(track))
            delay(SETTLE_MS)
            if (state.isPlaying) {
                playback.seekTo(estimatedPosition(state.positionMs, state.lastUpdateMs))
            } else {
                pauseIfPlaying()
            }
            applyingRemote = false
        }
    }

    private fun applySync(event: SyncEvent) {
        // Ignore our own echoes and the empty state frames a reconnect brings.
        if (event.action.isBlank() && event.track == null) return
        when (event.action) {
            ListenTogetherActions.CHANGE_TRACK -> applyTrack(event)
            ListenTogetherActions.PLAY -> applyTrack(event, resume = true)
            ListenTogetherActions.PAUSE -> {
                applyingRemote = true
                pauseIfPlaying()
                applyingRemote = false
            }

            ListenTogetherActions.SEEK -> {
                applyingRemote = true
                playback.seekTo(estimatedPosition(event.positionMs, event.serverTimeMs))
                applyingRemote = false
            }

            ListenTogetherActions.SKIP_NEXT -> remoteStep(next = true)
            ListenTogetherActions.SKIP_PREV -> remoteStep(next = false)
        }
    }

    private fun applyTrack(event: SyncEvent, resume: Boolean = false) {
        val track = event.track?.let(::toTrack) ?: roomTrack ?: return
        if (event.track != null) roomTrack = track
        applyingRemote = true
        scope.launch {
            val sameTrack = playback.state.value.current?.id == track.id
            if (sameTrack && resume) {
                playback.togglePlayPause()
            } else {
                playback.playNow(track, listOf(track))
                delay(SETTLE_MS)
                playback.seekTo(estimatedPosition(event.positionMs, event.serverTimeMs))
            }
            applyingRemote = false
        }
    }

    private fun remoteStep(next: Boolean) {
        applyingRemote = true
        if (next) playback.next() else playback.previous()
        applyingRemote = false
    }

    /** `togglePlayPause` flips, so it can only be called when playing. */
    private fun pauseIfPlaying() {
        if (playback.state.value.isPlaying) playback.togglePlayPause()
    }

    /**
     * The room's position, advanced to now.
     *
     * `sync_playback` stamps the moment the server saw the action; adding the
     * offset between the server clock and this device's clock is what keeps a
     * guest from starting every track a fraction of a second behind.
     */
    private fun estimatedPosition(positionMs: Long, stampedAtMs: Long): Long {
        if (stampedAtMs <= 0L) return positionMs.coerceAtLeast(0L)
        val offset = client?.clockOffsetMs?.value ?: 0L
        val serverNow = System.currentTimeMillis() + offset
        return (positionMs + (serverNow - stampedAtMs).coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /**
     * Watches this device's player and reports the changes that matter.
     *
     * Started only on becoming host. It compares the fields it cares about
     * rather than reacting to every emission, because the controller publishes
     * a new position twice a second and broadcasting that would flood the
     * socket.
     */
    private fun startHosting() {
        if (hostJob != null) return
        hostJob = scope.launch {
            var lastTrackId: String? = null
            var lastPlaying: Boolean? = null
            playback.state.collect { player ->
                if (applyingRemote) return@collect
                val current = player.current ?: return@collect
                when {
                    lastTrackId != current.id -> {
                        lastTrackId = current.id
                        lastPlaying = player.isPlaying
                        broadcast(ListenTogetherActions.CHANGE_TRACK, current, player.positionMs)
                    }

                    lastPlaying != player.isPlaying -> {
                        lastPlaying = player.isPlaying
                        broadcast(
                            if (player.isPlaying) ListenTogetherActions.PLAY else ListenTogetherActions.PAUSE,
                            current,
                            player.positionMs,
                        )
                    }
                }
            }
        }
    }

    private fun broadcast(action: String, track: Track, positionMs: Long) {
        // The ids are the authority, not the UI flag: `host_changed` can move
        // the room while this screen is showing something else.
        if (myUserId.isBlank() || myUserId != hostId) return
        client?.sendPlayback(
            ListenTogetherPayloads.playbackAction(
                action = action,
                trackId = track.id,
                positionMs = positionMs,
                track = toRemote(track),
            ),
        )
    }

    /**
     * Rebuilds a playable [Track] from what the room announced.
     *
     * The id carries the provider: `ytmusic-<videoId>` names the same recording
     * on every device, so a guest can play it through InnerTube with no lookup.
     * A song from this phone's own files has no meaning on another one, and is
     * deliberately not guessed at - it resolves to a track the player reports
     * as unavailable rather than silently playing something else.
     */
    private fun toTrack(remote: RemoteTrack): Track {
        val videoId = remote.id.removePrefix(YTMUSIC_PREFIX).takeIf {
            remote.id.startsWith(YTMUSIC_PREFIX) && it.isNotBlank()
        }
        return Track(
            id = remote.id,
            title = remote.title,
            artist = remote.artist,
            album = remote.album.ifBlank { null },
            artworkUri = remote.thumbnail.ifBlank { null },
            durationMs = remote.durationMs,
            sourceId = if (videoId != null) SourceId.YOUTUBE_MUSIC else SourceId.LOCAL,
            videoId = videoId,
        )
    }

    private fun toRemote(track: Track): RemoteTrack = RemoteTrack(
        id = track.id,
        title = track.title,
        artist = track.artist,
        album = track.album.orEmpty(),
        durationMs = track.durationMs,
        thumbnail = track.artworkUri.orEmpty(),
    )

    private companion object {
        const val TAG = "ListenTogether"
        const val YTMUSIC_PREFIX = "ytmusic-"
        const val DEFAULT_NAME = "Guest"

        /** How long a new stream is given before the position is corrected. */
        const val SETTLE_MS = 400L
    }
}
