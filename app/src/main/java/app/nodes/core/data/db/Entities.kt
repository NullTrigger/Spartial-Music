/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track

/**
 * Canonical, local track table. Remote results are cached here so the library
 * (likes, playlists, history) keeps working offline.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val artworkUri: String?,
    val mediaUri: String?,
    val durationMs: Long,
    val videoId: String?,
    val externalUrl: String?,
    val licenseNote: String?,
    val updatedAt: Long,
)

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAt: Long,
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val trackId: String,
    val playedAt: Long,
)

@Entity(tableName = "user_playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** When the user pinned this playlist; null means unpinned. */
    @ColumnInfo(defaultValue = "NULL")
    val pinnedAt: Long? = null,
)

/**
 * A playlist row. One track appears at most once per playlist: the unique
 * (playlistId, trackId) index is the invariant, added in schema v3.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "sortOrder"],
    indices = [Index(value = ["playlistId", "trackId"], unique = true)],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val sortOrder: Int,
    val trackId: String,
)

/**
 * An artist the user follows. The composite primary key is the unique
 * (artistId, sourceId) index: one follow per artist per source. Local-first -
 * a follow is only ever a row in this table.
 */
@Entity(
    tableName = "followed_artists",
    primaryKeys = ["artistId", "sourceId"],
    indices = [Index("followedAt")],
)
data class FollowedArtistEntity(
    val artistId: String,
    val sourceId: String,
    val name: String,
    val imageUrl: String?,
    val followedAt: Long,
)

/**
 * A like the user saved locally that YouTube Music has not accepted yet.
 * Retried on app start and when the Reel opens, with an attempt cap so a
 * refused track cannot loop forever.
 */
@Entity(tableName = "pending_like_ops", primaryKeys = ["trackId"])
data class PendingLikeEntity(
    val trackId: String,
    val videoId: String,
    val attempts: Int,
    val updatedAt: Long,
)

/** Raw provider payloads, with a timestamp so we can respect a TTL. */
@Entity(tableName = "remote_cache")
data class RemoteCacheEntity(
    @PrimaryKey val key: String,
    val body: String,
    val fetchedAt: Long,
)

/**
 * One row of the persisted play queue. [position] is the play order;
 * [originalPosition] is where the row sat before shuffle, so turning shuffle
 * off can restore the order the user chose. Both are 0-based and dense.
 */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
    val originalPosition: Int,
)

/** The single row of queue state that travels with [QueueItemEntity]. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
)

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artworkUri = artworkUri,
    mediaUri = mediaUri,
    durationMs = durationMs,
    sourceId = SourceId.fromKey(sourceId),
    videoId = videoId,
    externalUrl = externalUrl,
    licenseNote = licenseNote,
)

fun Track.toEntity(now: Long = System.currentTimeMillis()): TrackEntity = TrackEntity(
    id = id,
    sourceId = sourceId.key,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artworkUri = artworkUri,
    mediaUri = mediaUri,
    durationMs = durationMs,
    videoId = videoId,
    externalUrl = externalUrl,
    licenseNote = licenseNote,
    updatedAt = now,
)
