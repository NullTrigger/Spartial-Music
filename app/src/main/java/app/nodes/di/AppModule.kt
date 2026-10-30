/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.nodes.core.data.db.LibraryDao
import app.nodes.core.data.db.NodesDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): NodesDatabase =
        Room.databaseBuilder(context, NodesDatabase::class.java, NodesDatabase.NAME)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideTrackDao(database: NodesDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideLibraryDao(database: NodesDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideRemoteCacheDao(database: NodesDatabase): RemoteCacheDao = database.remoteCacheDao()
}
