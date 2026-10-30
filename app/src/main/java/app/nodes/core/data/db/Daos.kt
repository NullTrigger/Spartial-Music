/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE sourceId = :sourceId ORDER BY updatedAt DESC LIMIT :limit")
    fun bySource(sourceId: String, limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}

@Dao
interface LibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(liked: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun unlike(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_tracks WHERE trackId = :trackId)")
    fun isLiked(trackId: String): Flow<Boolean>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN liked_tracks l ON t.id = l.trackId
        ORDER BY l.likedAt DESC
        """,
    )
    fun likedTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlay(history: PlayHistoryEntity)

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN play_history h ON t.id = h.trackId
        GROUP BY t.id
        ORDER BY MAX(h.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun recentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM user_playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(cross: PlaylistTrackEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextSortOrder(playlistId: String): Int

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks p ON t.id = p.trackId
        WHERE p.playlistId = :playlistId
        ORDER BY p.sortOrder ASC
        """,
    )
    suspend fun tracksInPlaylist(playlistId: String): List<TrackEntity>

    @Query("DELETE FROM user_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)

    @Transaction
    suspend fun addToPlaylist(playlistId: String, trackId: String) {
        insertPlaylistTrack(PlaylistTrackEntity(playlistId, nextSortOrder(playlistId), trackId))
    }
}

@Dao
interface RemoteCacheDao {

    @Query("SELECT * FROM remote_cache WHERE key = :key")
    suspend fun get(key: String): RemoteCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: RemoteCacheEntity)

    @Query("DELETE FROM remote_cache")
    suspend fun clear()

    @Query("DELETE FROM remote_cache WHERE fetchedAt < :olderThan")
    suspend fun clearOlderThan(olderThan: Long)
}
