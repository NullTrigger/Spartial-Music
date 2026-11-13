/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.session

/**
 * Message and payload shapes of the Listen Together protocol.
 *
 * Every field number here is taken from the worker this client talks to
 * (`metroserver-worker/src/protocol.js` in the Glossy repository) and from the
 * Metrolist reference server both of them are ported from. The two must agree
 * exactly: a wrong field number is not an error, it silently reads as an empty
 * string or a zero and the room desynchronises.
 */
object ListenTogetherMessages {

    // Client -> server
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val BUFFER_READY = "buffer_ready"
    const val PING = "ping"
    const val REQUEST_SYNC = "request_sync"

    // Server -> client
    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val SYNC_STATE = "sync_state"
    const val BUFFER_WAIT = "buffer_wait"
    const val BUFFER_COMPLETE = "buffer_complete"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val ERROR = "error"
    const val PONG = "pong"
}

/** The playback actions this app sends. The server owns the full list. */
object ListenTogetherActions {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
    const val CHANGE_TRACK = "change_track"
    const val QUEUE_ADD = "queue_add"
}

/** A song, as the room sees it. Field numbers match the worker's `Track`. */
data class RemoteTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val thumbnail: String = "",
) {

    fun encode(): ByteArray = Pb.concat(
        Pb.string(1, id),
        Pb.string(2, title),
        Pb.string(3, artist),
        Pb.string(4, album),
        Pb.int(5, durationMs),
        Pb.string(6, thumbnail),
    )

    companion object {
        fun decode(data: ByteArray?): RemoteTrack {
            // Kotlin has no isNullOrEmpty() for primitive arrays.
            if (data == null || data.isEmpty()) return RemoteTrack()
            val f = PbFields(data)
            return RemoteTrack(
                id = f.string(1),
                title = f.string(2),
                artist = f.string(3),
                album = f.string(4),
                durationMs = f.number(5),
                thumbnail = f.string(6),
            )
        }
    }
}

/** One person in the room. Field numbers match the worker's `Member`. */
data class RemoteUser(
    val userId: String = "",
    val username: String = "",
    val isHost: Boolean = false,
    val isConnected: Boolean = true,
) {

    companion object {
        fun decode(data: ByteArray): RemoteUser {
            val f = PbFields(data)
            return RemoteUser(
                userId = f.string(1),
                username = f.string(2),
                isHost = f.boolean(3),
                isConnected = f.boolean(4),
            )
        }
    }
}

/**
 * The whole room, as sent on join approval. Field numbers match
 * `Room.stateMessage()`.
 */
data class RoomState(
    val roomCode: String = "",
    val hostId: String = "",
    val users: List<RemoteUser> = emptyList(),
    val currentTrack: RemoteTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val lastUpdateMs: Long = 0L,
    val volume: Float = 1f,
    val queue: List<RemoteTrack> = emptyList(),
    val revision: Long = 0L,
) {

    companion object {
        fun decode(data: ByteArray?): RoomState {
            if (data == null || data.isEmpty()) return RoomState()
            val f = PbFields(data)
            return RoomState(
                roomCode = f.string(1),
                hostId = f.string(2),
                users = f.repeated(3).map { RemoteUser.decode(it) },
                currentTrack = RemoteTrack.decode(f.raw(4)),
                isPlaying = f.boolean(5),
                positionMs = f.number(6),
                lastUpdateMs = f.number(7),
                volume = f.float(8),
                queue = f.repeated(9).map { RemoteTrack.decode(it) },
                revision = f.number(10),
            )
        }
    }
}

/**
 * A `sync_playback` or `sync_state` delta.
 *
 * `sync_playback` carries the server's clock in field 9 and its revision in
 * field 10, and repeats the server time in field 11; `sync_state` uses the
 * shorter `Room.syncStateMessage()` numbering, so the two are decoded apart
 * rather than guessed at.
 */
data class SyncEvent(
    val action: String = "",
    val trackId: String = "",
    val positionMs: Long = 0L,
    val track: RemoteTrack? = null,
    val insertNext: Boolean = false,
    val queue: List<RemoteTrack> = emptyList(),
    val queueTitle: String = "",
    val volume: Float = 0f,
    val serverTimeMs: Long = 0L,
    val revision: Long = 0L,
) {

    companion object {

        fun decodePlayback(data: ByteArray): SyncEvent {
            val f = PbFields(data)
            return SyncEvent(
                action = f.string(1),
                trackId = f.string(2),
                positionMs = f.number(3),
                track = f.raw(4)?.let { RemoteTrack.decode(it) },
                insertNext = f.boolean(5),
                queue = f.repeated(6).map { RemoteTrack.decode(it) },
                queueTitle = f.string(7),
                volume = f.float(8),
                serverTimeMs = f.number(9),
                revision = f.number(10),
            )
        }

        fun decodeState(data: ByteArray): SyncEvent {
            val f = PbFields(data)
            return SyncEvent(
                track = f.raw(1)?.let { RemoteTrack.decode(it) },
                positionMs = f.number(3),
                queue = f.repeated(5).map { RemoteTrack.decode(it) },
                volume = f.float(6),
                revision = f.number(7),
                serverTimeMs = f.number(4),
            ).let { it.copy(action = if (f.boolean(2)) ListenTogetherActions.PLAY else ListenTogetherActions.PAUSE) }
        }
    }
}

/** Payload builders for the messages this client sends. */
internal object ListenTogetherPayloads {

    fun createRoom(username: String): ByteArray = Pb.string(1, username)

    fun joinRoom(code: String, username: String): ByteArray =
        Pb.concat(Pb.string(1, code), Pb.string(2, username))

    fun approveJoin(userId: String): ByteArray = Pb.string(1, userId)

    fun rejectJoin(userId: String, reason: String?): ByteArray =
        Pb.concat(Pb.string(1, userId), Pb.string(2, reason))

    fun bufferReady(trackId: String): ByteArray = Pb.string(1, trackId)

    fun ping(clientTimeMs: Long, sequence: Long): ByteArray =
        Pb.concat(Pb.int(1, clientTimeMs), Pb.int(2, sequence))

    /**
     * `playback_action`: {1 action, 2 track_id, 3 position, 4 track_info,
     * 5 insert_next, 6 queue, 7 queue_title, 8 volume}.
     *
     * Written as an ordered list so a field number can never drift from its
     * slot the way a long positional argument list invites.
     */
    fun playbackAction(
        action: String,
        trackId: String? = null,
        positionMs: Long? = null,
        track: RemoteTrack? = null,
        insertNext: Boolean = false,
        queue: List<RemoteTrack> = emptyList(),
        queueTitle: String? = null,
        volume: Float? = null,
    ): ByteArray {
        val parts = mutableListOf(
            Pb.string(1, action),
            Pb.string(2, trackId),
            if (positionMs != null) Pb.int(3, positionMs) else ByteArray(0),
            Pb.message(4, track?.encode()),
            Pb.bool(5, insertNext),
        )
        queue.forEach { parts += Pb.message(6, it.encode()) }
        parts += Pb.string(7, queueTitle)
        parts += if (volume != null) Pb.float(8, volume) else ByteArray(0)
        return Pb.concat(*parts.toTypedArray())
    }

    /** Fields of `room_created`: {1 code, 2 user_id, 3 token}. */
    data class RoomCreated(val code: String, val userId: String, val token: String) {
        companion object {
            fun decode(data: ByteArray) = PbFields(data).let {
                RoomCreated(it.string(1), it.string(2), it.string(3))
            }
        }
    }

    /** Fields of `join_approved`: {1 room_code, 2 user_id, 3 token, 4 state}. */
    data class JoinApproved(
        val code: String,
        val userId: String,
        val token: String,
        val state: RoomState,
    ) {
        companion object {
            fun decode(data: ByteArray) = PbFields(data).let {
                JoinApproved(it.string(1), it.string(2), it.string(3), RoomState.decode(it.raw(4)))
            }
        }
    }

    /** Fields of `join_request`: {1 user_id, 2 username}. */
    data class JoinRequest(val userId: String, val username: String) {
        companion object {
            fun decode(data: ByteArray) = PbFields(data).let { JoinRequest(it.string(1), it.string(2)) }
        }
    }

    /** Fields of `user_joined` / `user_left`: {1 user_id, 2 username}. */
    data class UserChange(val userId: String, val username: String) {
        companion object {
            fun decode(data: ByteArray) = PbFields(data).let { UserChange(it.string(1), it.string(2)) }
        }
    }

    /** Fields of `error`: {1 code, 2 message}. */
    data class Failure(val code: String, val message: String) {
        companion object {
            fun decode(data: ByteArray) = PbFields(data).let { Failure(it.string(1), it.string(2)) }
        }
    }
}
