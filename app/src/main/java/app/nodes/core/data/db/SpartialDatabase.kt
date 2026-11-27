/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        LikedTrackEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FollowedArtistEntity::class,
        PendingLikeEntity::class,
        RemoteCacheEntity::class,
        QueueItemEntity::class,
        QueueStateEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class SpartialDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun libraryDao(): LibraryDao
    abstract fun remoteCacheDao(): RemoteCacheDao
    abstract fun queueDao(): QueueDao

    abstract fun followedArtistDao(): FollowedArtistDao

    abstract fun pendingLikeDao(): PendingLikeDao

    companion object {
        const val NAME = "nodes.db"
    }
}
