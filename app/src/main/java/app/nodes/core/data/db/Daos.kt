/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Totals for one stats window. COUNT(*) always yields exactly one row. */
data class PlayTotalsRow(val plays: Int, val durationMs: Long)

/** A track from the cache joined with how often it was played in a window. */
data class TrackPlayRow(
    @Embedded val track: TrackEntity,
    val plays: Int,
)

/** Plays and estimated minutes grouped by artist name, one rank row each. */
data class ArtistPlayRow(val artist: String, val plays: Int, val durationMs: Long)

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

    /** Every cached track, newest first. */
    @Query("SELECT * FROM tracks ORDER BY updatedAt DESC")
    fun allTracks(): Flow<List<TrackEntity>>

    /** Everything cached under one artist name, newest first. */
    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY updatedAt DESC")
    fun byArtist(artist: String): Flow<List<TrackEntity>>

    /** Everything cached under one album name, newest first. */
    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY updatedAt DESC")
    fun byAlbum(album: String): Flow<List<TrackEntity>>

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

    // Listening stats: pure aggregations over play_history joined with the
    // cached tracks. Every window is inclusive of [since], in epoch millis.

    @Query(
        """
        SELECT COUNT(*) AS plays, COALESCE(SUM(t.durationMs), 0) AS durationMs
        FROM play_history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.playedAt >= :since
        """,
    )
    fun playTotals(since: Long): Flow<PlayTotalsRow>

    @Query(
        """
        SELECT t.*, COUNT(*) AS plays
        FROM play_history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.playedAt >= :since
        GROUP BY t.id
        ORDER BY plays DESC, MAX(h.playedAt) DESC
        LIMIT :limit
        """,
    )
    fun topTracks(since: Long, limit: Int): Flow<List<TrackPlayRow>>

    @Query(
        """
        SELECT t.artist AS artist, COUNT(*) AS plays,
               COALESCE(SUM(t.durationMs), 0) AS durationMs
        FROM play_history h
        INNER JOIN tracks t ON t.id = h.trackId
        WHERE h.playedAt >= :since
        GROUP BY t.artist
        ORDER BY plays DESC
        LIMIT :limit
        """,
    )
    fun topArtists(since: Long, limit: Int): Flow<List<ArtistPlayRow>>

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
