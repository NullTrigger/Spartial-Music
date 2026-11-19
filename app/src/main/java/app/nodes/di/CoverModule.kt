/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.di

import app.nodes.core.cover.AnimatedCoverProvider
import app.nodes.core.cover.UserLoopProvider
import app.nodes.core.cover.YouTubeMusicCoverProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Animated-cover providers, bound as a set the [app.nodes.core.cover.CoverResolver]
 * orders and tries. Both flavours contribute the same two classes; the foss
 * and gms source sets decide what YouTubeMusicCoverProvider actually does
 * (today: null in both, see its doc comment).
 */
@Module
@InstallIn(SingletonComponent::class)
object CoverModule {

    @Provides
    @IntoSet
    fun userLoopProvider(provider: UserLoopProvider): AnimatedCoverProvider = provider

    @Provides
    @IntoSet
    fun youTubeMusicCoverProvider(provider: YouTubeMusicCoverProvider): AnimatedCoverProvider =
        provider
}
