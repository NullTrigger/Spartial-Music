/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.session

import app.nodes.core.crash.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Where the room is, as far as the UI is concerned. */
enum class RoomStatus { DISCONNECTED, CONNECTING, IN_ROOM }

/** One thing the server told us, already decoded. */
sealed interface RoomEvent {
    /** The socket is up; nothing else has happened yet. */
    data object Connected : RoomEvent
    data class Created(val code: String, val userId: String, val token: String) : RoomEvent
    data class Joined(val code: String, val userId: String, val state: RoomState) : RoomEvent
    data class JoinRequest(val userId: String, val username: String) : RoomEvent
    data class UserJoined(val userId: String, val username: String) : RoomEvent
    data class UserLeft(val userId: String, val username: String) : RoomEvent
    data class Playback(val event: SyncEvent) : RoomEvent
    data class State(val event: SyncEvent) : RoomEvent
    data class Rejected(val reason: String) : RoomEvent
    data class Failed(val code: String, val message: String) : RoomEvent
    /** We were removed from the room by the host, or the socket died. */
    data object Left : RoomEvent
}

/**
 * WebSocket client for the Listen Together room server.
 *
 * The transport is the Metrolist protocol: binary protobuf inside a gzip
 * envelope, over one long-lived socket. OkHttp already ships in the app for the
 * lyrics providers and speaks WebSocket natively, so this adds no dependency.
 *
 * Nothing here knows about playback - it moves bytes and reports what the
 * server said. [ListenTogetherManager] is what turns those events into player
 * commands, which keeps the protocol testable without an Android context.
 */
class ListenTogetherClient(
    private val serverUrl: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val http = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var keepAlive: Job? = null

    private val _status = MutableStateFlow(RoomStatus.DISCONNECTED)
    val status: StateFlow<RoomStatus> = _status.asStateFlow()

    /**
     * Events are replayed to a late subscriber exactly once, because the
     * manager is created per screen and a join approval that arrives while no
     * one is listening must not be lost - it carries the room code and token.
     */
    private val _events = MutableSharedFlow<RoomEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RoomEvent> = _events.asSharedFlow()

    /** Server time minus local time at the last ping, for position estimates. */
    private val _clockOffsetMs = MutableStateFlow(0L)
    val clockOffsetMs: StateFlow<Long> = _clockOffsetMs.asStateFlow()

    var userId: String = ""
        private set
    var token: String = ""
        private set
    var roomCode: String = ""
        private set

    fun connect() {
        if (socket != null) return
        _status.value = RoomStatus.CONNECTING
        val request = Request.Builder().url(serverUrl).build()
        socket = http.newWebSocket(request, listener)
        startKeepAlive()
    }

    /** Sends `create_room`; the answer is [RoomEvent.Created]. */
    fun createRoom(username: String) {
        connect()
        send(ListenTogetherMessages.CREATE_ROOM, ListenTogetherPayloads.createRoom(username))
    }

    /** Sends `join_room`; the host must approve before [RoomEvent.Joined] arrives. */
    fun joinRoom(code: String, username: String) {
        connect()
        send(
            ListenTogetherMessages.JOIN_ROOM,
            ListenTogetherPayloads.joinRoom(code.trim().uppercase(), username),
        )
    }

    fun approveJoin(userId: String) =
        send(ListenTogetherMessages.APPROVE_JOIN, ListenTogetherPayloads.approveJoin(userId))

    fun rejectJoin(userId: String, reason: String? = null) =
        send(ListenTogetherMessages.REJECT_JOIN, ListenTogetherPayloads.rejectJoin(userId, reason))

    fun bufferReady(trackId: String) =
        send(ListenTogetherMessages.BUFFER_READY, ListenTogetherPayloads.bufferReady(trackId))

    fun requestSync() = send(ListenTogetherMessages.REQUEST_SYNC, ByteArray(0))

    /**
     * Reports one local playback change to the room. Only the host's actions
     * move the room; the server answers any other sender with `not_host`, so
     * [ListenTogetherManager] gates this before it is called.
     */
    fun sendPlayback(payload: ByteArray) =
        send(ListenTogetherMessages.PLAYBACK_ACTION, payload)

    fun leaveRoom() {
        send(ListenTogetherMessages.LEAVE_ROOM, ByteArray(0))
        close()
        _status.value = RoomStatus.DISCONNECTED
        scope.launch { _events.emit(RoomEvent.Left) }
    }

    fun close() {
        keepAlive?.cancel()
        keepAlive = null
        socket?.close(NORMAL_CLOSE, null)
        socket = null
    }

    private fun send(type: String, payload: ByteArray) {
        val ws = socket
        if (ws == null) {
            AppLogger.w(LISTEN_TOGETHER_TAG, "Dropped $type: no socket")
            return
        }
        // OkHttp's WebSocket API takes an okio ByteString, not a ByteArray.
        runCatching { ws.send(Envelope.encode(type, payload).toByteString()) }
            .onFailure { error -> AppLogger.w(LISTEN_TOGETHER_TAG, "Send $type failed", error) }
    }

    /**
     * Application-level pings do more than keep the socket warm: the reply
     * carries the server clock, which is what lets a guest place the room's
     * position between the host's updates instead of drifting.
     */
    private fun startKeepAlive() {
        keepAlive?.cancel()
        keepAlive = scope.launch {
            var sequence = 0L
            while (true) {
                delay(PING_PERIOD_MS)
                if (socket == null) break
                val sent = System.currentTimeMillis()
                send(ListenTogetherMessages.PING, ListenTogetherPayloads.ping(sent, sequence))
                sequence++
            }
        }
    }

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            _status.value = RoomStatus.CONNECTING
            scope.launch { _events.emit(RoomEvent.Connected) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handle(bytes.toByteArray())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSE, null)
            socket = null
            keepAlive?.cancel()
            _status.value = RoomStatus.DISCONNECTED
            scope.launch { _events.emit(RoomEvent.Left) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            AppLogger.w(LISTEN_TOGETHER_TAG, "Socket failure", t)
            socket = null
            keepAlive?.cancel()
            _status.value = RoomStatus.DISCONNECTED
            scope.launch { _events.emit(RoomEvent.Failed("socket", t.message ?: "Connection lost")) }
        }
    }

    private fun handle(bytes: ByteArray) {
        val (type, payload) = runCatching { Envelope.decode(bytes) }.getOrElse { error ->
            AppLogger.e(LISTEN_TOGETHER_TAG, "Undecodable frame (${bytes.size}B)", error)
            return
        }
        val event = decode(type, payload) ?: return
        scope.launch { _events.emit(event) }
    }

    /**
     * @return the event to publish, or null for traffic that only updates the
     *   clock or that this build does not act on. Frame types the app ignores
     *   must not reach the manager as a fake "state" message.
     */
    private fun decode(type: String, payload: ByteArray): RoomEvent? {
        val fields = PbFields(payload)
        return when (type) {
            ListenTogetherMessages.ROOM_CREATED -> ListenTogetherPayloads.RoomCreated.decode(payload).let {
                userId = it.userId
                token = it.token
                roomCode = it.code
                _status.value = RoomStatus.IN_ROOM
                RoomEvent.Created(it.code, it.userId, it.token)
            }

            ListenTogetherMessages.JOIN_APPROVED -> ListenTogetherPayloads.JoinApproved.decode(payload).let {
                userId = it.userId
                token = it.token
                roomCode = it.code
                _status.value = RoomStatus.IN_ROOM
                RoomEvent.Joined(it.code, it.userId, it.state)
            }

            ListenTogetherMessages.JOIN_REQUEST ->
                ListenTogetherPayloads.JoinRequest.decode(payload).let {
                    RoomEvent.JoinRequest(it.userId, it.username)
                }

            ListenTogetherMessages.USER_JOINED ->
                ListenTogetherPayloads.UserChange.decode(payload).let {
                    RoomEvent.UserJoined(it.userId, it.username)
                }

            ListenTogetherMessages.USER_LEFT ->
                ListenTogetherPayloads.UserChange.decode(payload).let {
                    RoomEvent.UserLeft(it.userId, it.username)
                }

            ListenTogetherMessages.SYNC_PLAYBACK -> RoomEvent.Playback(SyncEvent.decodePlayback(payload))

            // `sync_state` reuses the same field slots the playback delta uses
            // for the parts this app reads, so one decoder serves both.
            ListenTogetherMessages.SYNC_STATE -> RoomEvent.State(SyncEvent.decodeState(payload))

            ListenTogetherMessages.JOIN_REJECTED -> RoomEvent.Rejected(fields.string(2))

            ListenTogetherMessages.KICKED -> RoomEvent.Rejected(fields.string(1))

            ListenTogetherMessages.ERROR ->
                ListenTogetherPayloads.Failure.decode(payload).let {
                    RoomEvent.Failed(it.code, it.message)
                }

            ListenTogetherMessages.PONG -> {
                // {1 client_time, 2 received, 3 server_time, 4 sequence}
                val clientTime = fields.number(1)
                val serverTime = fields.number(3)
                if (clientTime > 0L && serverTime > 0L) {
                    val roundTrip = System.currentTimeMillis() - clientTime
                    _clockOffsetMs.value = serverTime - (clientTime + roundTrip / 2)
                }
                null
            }

            else -> {
                AppLogger.w(LISTEN_TOGETHER_TAG, "Ignoring unknown message '$type'")
                null
            }
        }
    }

    private companion object {
        const val LISTEN_TOGETHER_TAG = "ListenTogether"
        const val NORMAL_CLOSE = 1000

        /** OkHttp-level pings keep the socket alive through idle NAT. */
        const val PING_INTERVAL_SECONDS = 20L

        /** Application-level pings, which also carry the server clock. */
        const val PING_PERIOD_MS = 25_000L
    }
}
