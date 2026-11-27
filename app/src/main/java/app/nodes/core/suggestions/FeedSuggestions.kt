/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.suggestions

import app.nodes.core.model.HomeFeed
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.MusicSource

/**
 * The one suggestion mechanism, shared by the For You screen, the queue
 * top-up and the Reel feed.
 *
 * Suggestions are derived from the same Home feed the provider already
 * builds, plus the local listening history. No extra provider call and no new
 * profile is created: the recommendation is explainable ("because you played
 * X") and stays inside the app. Three shelves come out of one feed:
 *
 *  - [Shaped.topPicks]: what the provider puts up front (hero and rails);
 *  - [Shaped.because]: the seed's artist first, the echo of what is playing or
 *    what played last, then other feed tracks to fill the shelf;
 *  - [Shaped.fresh]: the rest of the feed, for variety.
 *
 * [Shaped.ordered] appends the echo first and then the editorial picks and
 * the variety, which is the order the queue and the Reel keep the music going
 * in.
 */
internal object FeedSuggestions {

    private const val TOP_PICKS = 8
    private const val BECAUSE = 6
    private const val FRESH = 10

    /** How long one home feed is reused before it is fetched again. */
    private const val FEED_TTL_MS = 10 * 60 * 1000L

    private val cache = HashMap<SourceId, Pair<Long, HomeFeed>>()

    data class Shaped(
        /** Every playable row the feed offered, in feed order. */
        val pool: List<Track>,
        val topPicks: List<Track>,
        val because: List<Track>,
        val becauseSeed: Track?,
        val fresh: List<Track>,
    ) {
        /** What to append next: the seed's echo first, then the picks, then variety. */
        val ordered: List<Track>
            get() = because + topPicks + fresh
    }

    /**
     * Shapes a home feed around [seed] - usually what is playing, or what
     * played last. Without a seed there is no "because" shelf: its first
     * tracks echo the seed, the rest is feed filler, exactly what the For You
     * screen has always shown.
     */
    fun shape(feed: HomeFeed, seed: Track?): Shaped {
        // Album and playlist cards in a shelf are not playable rows; every
        // suggestion plays immediately, so they are dropped rather than
        // offered and then refused.
        val hero = feed.hero.map { it.track }.filter { it.isPlayable() }
        val shelved = feed.sections.flatMap { it.tracks }.filter { it.isPlayable() }
        val pool = (hero + shelved + feed.continuePlaying)
            .filter { it.isPlayable() }
            .distinctBy { it.id }
        val top = (hero + shelved).distinctBy { it.id }.take(TOP_PICKS)
        val topIds = top.mapTo(HashSet()) { it.id }
        val related = if (seed != null) {
            pool.filter { track ->
                track.id != seed.id &&
                    track.artist.equals(seed.artist, ignoreCase = true)
            }
        } else {
            emptyList()
        }
        val because = (related + pool)
            .distinctBy { it.id }
            .filterNot { it.id in topIds }
            .take(BECAUSE)
        val becauseIds = because.mapTo(HashSet()) { it.id }
        val fresh = pool
            .filterNot { it.id in topIds || it.id in becauseIds }
            .take(FRESH)
        return Shaped(
            pool = pool,
            topPicks = top,
            because = because,
            becauseSeed = seed,
            fresh = fresh,
        )
    }

    /**
     * The provider's home feed shaped around [seed], or null when the provider
     * cannot answer (offline, missing key). Callers fall back to the device's
     * own catalogue then. One feed is reused for [FEED_TTL_MS], so a queue
     * growing track by track does not fetch it per track.
     */
    suspend fun shaped(source: MusicSource, seed: Track?): Shaped? {
        val feed = feed(source) ?: return null
        return shape(feed, seed)
    }

    private suspend fun feed(source: MusicSource): HomeFeed? {
        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[source.id] }
        if (cached != null && now - cached.first < FEED_TTL_MS) return cached.second
        val feed = when (val result = runCatching { source.getHome() }.getOrNull()) {
            is SourceResult.Success -> result.data
            else -> null
        } ?: return null
        synchronized(cache) { cache[source.id] = now to feed }
        return feed
    }
}

/**
 * A row the player can actually start: a video id for a provider stream, or a
 * local file. Album and playlist cards in a home shelf have neither.
 */
internal fun Track.isPlayable(): Boolean = videoId != null || mediaUri != null
