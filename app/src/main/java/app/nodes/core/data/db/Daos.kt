/*
 * Copyright (C) 2026 The Spartial Music Authors.
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
data class ArtistPlayRow(
    val artist: String,
    val plays: Int,
    val durationMs: Long,
    /** Cover of the artist's most recently played track; null when none is known. */
    val artworkUri: String?,
)

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
               COALESCE(SUM(t.durationMs), 0) AS durationMs,
               (SELECT t2.artworkUri FROM tracks t2
                INNER JOIN play_history h2 ON t2.id = h2.trackId
                WHERE t2.artist = t.artist AND h2.playedAt >= :since
                ORDER BY h2.playedAt DESC LIMIT 1) AS artworkUri
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

    /**
     * The cover a local playlist shows: the first song's artwork, in playlist
     * order. Null when the playlist is empty or its first song has no cover,
     * which leaves the generated placeholder in place.
     */
    @Query(
        """
        SELECT t.artworkUri FROM tracks t
        INNER JOIN playlist_tracks p ON t.id = p.trackId
        WHERE p.playlistId = :playlistId
        ORDER BY p.sortOrder ASC
        LIMIT 1
        """,
    )
    suspend fun firstArtwork(playlistId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(cross: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(rows: List<PlaylistTrackEntity>)

    /** Caches a track from inside a playlist transaction (same database). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextSortOrder(playlistId: String): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_tracks
            WHERE playlistId = :playlistId AND trackId = :trackId
        )
        """,
    )
    suspend fun playlistContains(playlistId: String, trackId: String): Boolean

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: String, trackId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun playlistTrackRows(playlistId: String): List<PlaylistTrackEntity>

    @Query(
        """
        SELECT p.* FROM user_playlists p
        INNER JOIN playlist_tracks pt ON pt.playlistId = p.id
        WHERE pt.trackId = :trackId
        ORDER BY p.createdAt DESC
        """,
    )
    fun playlistsContaining(trackId: String): Flow<List<PlaylistEntity>>

    @Query("UPDATE user_playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String, now: Long)

    /** Pins a playlist to the library's Pins row (or unpins when [at] is null). */
    @Query("UPDATE user_playlists SET pinnedAt = :at WHERE id = :id")
    suspend fun setPinned(id: String, at: Long?)

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

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recentHistory(limit: Int): List<PlayHistoryEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM play_history
            WHERE trackId = :trackId AND playedAt = :playedAt
        )
        """,
    )
    suspend fun historyRowExists(trackId: String, playedAt: Long): Boolean

    /**
     * Adds one track to a playlist as a single transaction: the cached track
     * row and the cross row commit together, so a playlist can never point at
     * a track that is not in the cache. The unique (playlistId, trackId) index
     * is the invariant; the existence check turns it into a friendly result.
     *
     * @return true when the track was added, false when it was already there.
     */
    @Transaction
    suspend fun addToPlaylist(playlistId: String, track: TrackEntity): Boolean {
        if (playlistContains(playlistId, track.id)) return false
        insertTrack(track)
        insertPlaylistTrack(PlaylistTrackEntity(playlistId, nextSortOrder(playlistId), track.id))
        return true
    }

    /** Creates a playlist that starts with one track, in one transaction. */
    @Transaction
    suspend fun createPlaylistWithFirstTrack(playlist: PlaylistEntity, track: TrackEntity) {
        upsertPlaylist(playlist)
        insertTrack(track)
        insertPlaylistTrack(PlaylistTrackEntity(playlist.id, 0, track.id))
    }

    /** Rewrites one playlist's order in one transaction (drag reorder). */
    @Transaction
    suspend fun replacePlaylistTracks(playlistId: String, rows: List<PlaylistTrackEntity>) {
        deletePlaylistTracks(playlistId)
        insertPlaylistTracks(rows)
    }

    /** Deletes a playlist and its rows in one transaction. */
    @Transaction
    suspend fun deletePlaylistCompletely(playlistId: String) {
        deletePlaylistTracks(playlistId)
        deletePlaylist(playlistId)
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

/** The persisted play queue: one row per queued track plus a single state row. */
@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun items(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM queue_items")
    suspend fun clearItems()

    @Query("DELETE FROM queue_state")
    suspend fun clearState()

    /**
     * Replaces the whole saved queue in one transaction. The queued tracks are
     * upserted into `tracks` first, so a restored queue can never point at a
     * row that is not there.
     */
    @Transaction
    suspend fun replaceQueue(
        tracks: List<TrackEntity>,
        items: List<QueueItemEntity>,
        state: QueueStateEntity,
    ) {
        upsertTracks(tracks)
        clearItems()
        clearState()
        insertItems(items)
        insertState(state)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<QueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertState(state: QueueStateEntity)

    /** Drops the saved queue without touching the tracks themselves. */
    @Transaction
    suspend fun clearQueue() {
        clearItems()
        clearState()
    }
}
