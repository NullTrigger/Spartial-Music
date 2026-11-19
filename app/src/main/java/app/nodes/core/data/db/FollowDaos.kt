/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Follow graph, local only. See `followed_artists` in Entities.kt. */
@Dao
interface FollowedArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: FollowedArtistEntity): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM followed_artists
            WHERE artistId = :artistId AND sourceId = :sourceId
        )
        """,
    )
    fun isFollowing(artistId: String, sourceId: String): Flow<Boolean>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM followed_artists
            WHERE artistId = :artistId AND sourceId = :sourceId
        )
        """,
    )
    suspend fun exists(artistId: String, sourceId: String): Boolean

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun observeAll(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC LIMIT :limit")
    suspend fun mostRecent(limit: Int): List<FollowedArtistEntity>

    @Query("DELETE FROM followed_artists WHERE artistId = :artistId AND sourceId = :sourceId")
    suspend fun delete(artistId: String, sourceId: String)
}

/** Likes saved locally that could not be pushed to YouTube Music yet. */
@Dao
interface PendingLikeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(row: PendingLikeEntity)

    @Query("SELECT * FROM pending_like_ops ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingLikeEntity>

    @Query("UPDATE pending_like_ops SET attempts = :attempts WHERE trackId = :trackId")
    suspend fun bumpAttempts(trackId: String, attempts: Int)

    @Query("DELETE FROM pending_like_ops WHERE trackId = :trackId")
    suspend fun remove(trackId: String)

    @Query("DELETE FROM pending_like_ops WHERE attempts >= :cap")
    suspend fun dropExhausted(cap: Int)
}
