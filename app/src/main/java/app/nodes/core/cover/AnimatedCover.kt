/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import app.nodes.core.model.Track

/**
 * A short looping video to play behind the Reel in place of the static cover.
 *
 * [url] is either an `https` streaming URL from a provider or an absolute
 * path to a file the user picked. Nothing is ever written to disk except
 * user-picked files - remote covers are streamed and forgotten.
 */
data class AnimatedCover(
    val url: String,
    val providerId: String,
    val isLocalFile: Boolean = false,
)

/**
 * Finds an animated cover for a track, or null. Providers must be cheap:
 * [find] runs while the previous page is still on screen, and the resolver
 * caches the answer per track id for the session.
 *
 * Policy (AGENTS.md): a provider may only use a public or official API, or
 * files the user supplies. Never a reverse-engineered private endpoint,
 * another service's login cookie or token, or scraped pages.
 */
interface AnimatedCoverProvider {
    val id: String

    suspend fun find(track: Track): AnimatedCover?
}

object CoverProviderIds {
    const val USER_LOOP = "user-loop"
    const val YOUTUBE_MUSIC = "youtubemusic"
}
