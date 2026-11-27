/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.nodes.core.data.db.FollowedArtistDao
import app.nodes.core.data.db.LibraryDao
import app.nodes.core.data.db.PendingLikeDao
import app.nodes.core.data.db.QueueDao
import app.nodes.core.data.db.SpartialDatabase
import app.nodes.core.data.db.RemoteCacheDao
import app.nodes.core.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.nodesSettings: DataStore<Preferences> by preferencesDataStore(name = "nodes_settings")

/** v2 adds tracks.albumId so "View album" can reopen the real album page. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN albumId TEXT")
    }
}

/**
 * v3 makes (playlistId, trackId) unique: one track appears at most once per
 * playlist. Rows written before the constraint existed are deduplicated -
 * the earliest copy keeps its place - before the unique index is created.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM playlist_tracks WHERE rowid NOT IN (
                SELECT MIN(rowid) FROM playlist_tracks GROUP BY playlistId, trackId
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_trackId` " +
                "ON `playlist_tracks` (`playlistId`, `trackId`)",
        )
    }
}

/**
 * v4 adds the persisted play queue: one row per queued track (with the
 * pre-shuffle position so shuffle can be undone) and a single row of queue
 * state. Nothing existing is touched.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_items` (
                `position` INTEGER NOT NULL,
                `trackId` TEXT NOT NULL,
                `originalPosition` INTEGER NOT NULL,
                PRIMARY KEY(`position`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `queue_state` (
                `id` INTEGER NOT NULL,
                `currentIndex` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `shuffleEnabled` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * v5 adds pinning: `user_playlists.pinnedAt` records when (or whether) the
 * user pinned a playlist to the library's Pins row. Existing rows get NULL,
 * which means "not pinned"; nothing existing is touched.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `user_playlists` ADD COLUMN `pinnedAt` INTEGER")
    }
}

/**
 * v6 adds the Reel's social graph, both tables in one step:
 * `followed_artists` (local-first follows) and `pending_like_ops` (likes the
 * user saved locally that YouTube Music has not accepted yet). Nothing
 * existing is touched.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `followed_artists` (
                `artistId` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `imageUrl` TEXT,
                `followedAt` INTEGER NOT NULL,
                PRIMARY KEY(`artistId`, `sourceId`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_followed_artists_followedAt` ON `followed_artists` (`followedAt`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_like_ops` (
                `trackId` TEXT NOT NULL,
                `videoId` TEXT NOT NULL,
                `attempts` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`trackId`)
            )
            """.trimIndent(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideMedia3DatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideOfflineDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache = SimpleCache(
        context.filesDir.resolve("media3_downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.nodesSettings

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpartialDatabase =
        Room.databaseBuilder(context, SpartialDatabase::class.java, SpartialDatabase.NAME)
            // Every version has a real migration. The cache is never wiped to
            // pick up a schema change: likes, history and playlists live in
            // the same database (development_guide.md).
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideTrackDao(database: SpartialDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideLibraryDao(database: SpartialDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideRemoteCacheDao(database: SpartialDatabase): RemoteCacheDao = database.remoteCacheDao()

    @Provides
    fun provideQueueDao(database: SpartialDatabase): QueueDao = database.queueDao()

    @Provides
    fun provideFollowedArtistDao(database: SpartialDatabase): FollowedArtistDao =
        database.followedArtistDao()

    @Provides
    fun providePendingLikeDao(database: SpartialDatabase): PendingLikeDao =
        database.pendingLikeDao()
}
