/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source

import app.nodes.core.model.HomeFeed
import app.nodes.core.model.PlaybackHandle
import app.nodes.core.model.PlaylistDetail
import app.nodes.core.model.SourceAvailability
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track

/**
 * The one contract every music provider implements.
 *
 * Rules that apply to all implementations:
 *  - Mode 1 YouTube tracks resolve to [PlaybackHandle.EmbeddedVideo] and are
 *    played by the official IFrame player; the opt-in InnerTube source returns
 *    [PlaybackHandle.Stream] instead. Neither downloads media.
 *  - Never add a third unofficial API without a maintainer decision. See
 *    YOUTUBE.md and PRIVACY.md.
 *  - Report configuration problems through [availability] instead of throwing,
 *    so the UI can explain the fix.
 */
interface MusicSource {

    val id: SourceId

    val displayName: String

    /** Whether this provider is usable right now (key present, permission granted). */
    suspend fun availability(): SourceAvailability

    /** Free text search over the provider's legal catalogue. */
    suspend fun search(query: String, limit: Int = 25): SourceResult<List<Track>>

    /** Content for the Home screen. */
    suspend fun getHome(): SourceResult<HomeFeed>

    /** Charts / most popular. */
    suspend fun getTrending(limit: Int = 25): SourceResult<List<Track>>

    /** Resolve a playlist (provider playlist id). */
    suspend fun getPlaylist(playlistId: String): SourceResult<PlaylistDetail>

    /** Decide how this track should be played. */
    suspend fun play(track: Track): SourceResult<PlaybackHandle>

    /**
     * Drop any cached stream URL for [track]. A 403 from the CDN means the URL
     * went stale; the next [play] must mint a fresh one. Sources that resolve
     * nothing have nothing to drop.
     */
    fun invalidateStream(track: Track) = Unit

    /**
     * Do the slow network work [play] would need, ahead of time, so the track
     * starts fast when it is reached. Must never throw; the default does
     * nothing.
     */
    suspend fun prefetch(track: Track) = Unit
}
