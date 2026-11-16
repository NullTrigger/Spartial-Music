/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.nodes.core.data.db.LibraryDao
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
            // Explicit first: the cache may be wiped only when no migration
            // exists, never to pick up a new column - likes and history live
            // in the same database.
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideTrackDao(database: SpartialDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideLibraryDao(database: SpartialDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideRemoteCacheDao(database: SpartialDatabase): RemoteCacheDao = database.remoteCacheDao()
}
