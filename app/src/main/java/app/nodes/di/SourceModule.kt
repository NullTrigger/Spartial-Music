/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.di

import app.nodes.core.source.MusicSource
import app.nodes.core.source.jamendo.JamendoSource
import app.nodes.core.source.local.LocalSource
import app.nodes.core.source.youtube.YouTubeOfficialSource
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {

    @Binds
    @IntoSet
    abstract fun bindLocalSource(source: LocalSource): MusicSource

    @Binds
    @IntoSet
    abstract fun bindJamendoSource(source: JamendoSource): MusicSource

    @Binds
    @IntoSet
    abstract fun bindYouTubeSource(source: YouTubeOfficialSource): MusicSource

    @Binds
    @IntoSet
    abstract fun bindYouTubeMusicSource(source: YouTubeMusicSource): MusicSource
}
