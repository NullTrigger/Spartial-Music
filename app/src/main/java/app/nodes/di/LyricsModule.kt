/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.di

import android.content.Context
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.providers.LocalLyricsProvider
import app.nodes.core.lyrics.providers.LrcLibProvider
import app.nodes.core.lyrics.providers.LyricsOvhProvider
import app.nodes.core.lyrics.providers.NetEaseProvider
import app.nodes.core.lyrics.providers.SimpMusicProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Every [LyricsProvider] the app ships, as one injected set.
 *
 * Adding a provider means one class and one line here; the repository resolves
 * order and enablement from settings, so nothing else has to change.
 */
@Module
@InstallIn(SingletonComponent::class)
object LyricsModule {

    @Provides
    @IntoSet
    fun provideLocalProvider(@ApplicationContext context: Context): LyricsProvider =
        LocalLyricsProvider(context)

    @Provides
    @IntoSet
    fun provideLrcLibProvider(client: OkHttpClient, json: Json): LyricsProvider =
        LrcLibProvider(client, json)

    @Provides
    @IntoSet
    fun provideSimpMusicProvider(client: OkHttpClient, json: Json): LyricsProvider =
        SimpMusicProvider(client, json)

    @Provides
    @IntoSet
    fun provideNetEaseProvider(client: OkHttpClient, json: Json): LyricsProvider =
        NetEaseProvider(client, json)

    @Provides
    @IntoSet
    fun provideLyricsOvhProvider(client: OkHttpClient, json: Json): LyricsProvider =
        LyricsOvhProvider(client, json)
}
