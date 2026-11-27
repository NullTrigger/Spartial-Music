/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import app.nodes.core.model.Track
import javax.inject.Inject

/**
 * Play-build stub: the Google Play flavour ships no animated-cover code at
 * all beyond this always-null provider, so no cover URL parsing or video
 * loading from YouTube exists in that APK. User-picked loops still work.
 */
class YouTubeMusicCoverProvider @Inject constructor() : AnimatedCoverProvider {
    override val id: String = CoverProviderIds.YOUTUBE_MUSIC

    override suspend fun find(track: Track): AnimatedCover? = null
}
