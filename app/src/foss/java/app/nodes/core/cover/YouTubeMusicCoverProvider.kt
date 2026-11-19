/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import app.nodes.core.model.Track
import javax.inject.Inject

/**
 * YouTube Music animated covers, foss flavour.
 *
 * The InnerTube data the app already parses (see InnerTubeParser) carries
 * static thumbnails only - there is no animated or looping cover field in any
 * response the app reads, and this project does not guess request shapes or
 * add new endpoints for a cosmetic feature. Until a looping cover turns up in
 * data the app already fetches, this provider returns null and the Reel shows
 * the generated cover.
 */
class YouTubeMusicCoverProvider @Inject constructor() : AnimatedCoverProvider {
    override val id: String = CoverProviderIds.YOUTUBE_MUSIC

    override suspend fun find(track: Track): AnimatedCover? = null
}
