/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data.db

import androidx.room.Entity
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
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "sortOrder"])
data class PlaylistTrackEntity(
    val playlistId: String,
    val sortOrder: Int,
    val trackId: String,
)

/** Raw provider payloads, with a timestamp so we can respect a TTL. */
@Entity(tableName = "remote_cache")
data class RemoteCacheEntity(
    @PrimaryKey val key: String,
    val body: String,
    val fetchedAt: Long,
)

fun TrackEntity.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
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
    artworkUri = artworkUri,
    mediaUri = mediaUri,
    durationMs = durationMs,
    videoId = videoId,
    externalUrl = externalUrl,
    licenseNote = licenseNote,
    updatedAt = now,
)
