/*
 * Copyright (C) 2026 The Nodes Authors.
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
        RemoteCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NodesDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun libraryDao(): LibraryDao
    abstract fun remoteCacheDao(): RemoteCacheDao

    companion object {
        const val NAME = "nodes.db"
    }
}
