/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import app.nodes.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks every registered [AnimatedCoverProvider] in a fixed order and keeps
 * the first answer. Results - including "nothing found" - live in a small
 * in-memory LRU so swiping back never re-asks; nothing is written to disk
 * here, only [UserLoopStore] touches storage, and only with user files.
 *
 * A provider that throws is skipped: one broken source must never take the
 * cover (or the Reel) down.
 */
@Singleton
class CoverResolver @Inject constructor(
    private val providers: Set<AnimatedCoverProvider>,
) {
    /** Order the design fixes: the user's own file first, then YouTube Music. */
    private val ordered: List<AnimatedCoverProvider> = providers.sortedBy { provider ->
        when (provider.id) {
            CoverProviderIds.USER_LOOP -> 0
            CoverProviderIds.YOUTUBE_MUSIC -> 1
            else -> 2
        }
    }

    private val cache = object : LinkedHashMap<String, AnimatedCover?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnimatedCover?>) =
            size > maxCacheEntries
    }

    suspend fun find(track: Track): AnimatedCover? {
        val key = track.id
        synchronized(cache) {
            if (cache.containsKey(key)) return cache[key]
        }
        var result: AnimatedCover? = null
        for (provider in ordered) {
            val found = runCatching { provider.find(track) }.getOrNull()
            if (found != null) {
                result = found
                break
            }
        }
        synchronized(cache) { cache[key] = result }
        return result
    }

    /** Drop everything (e.g. after the user sets or removes a loop). */
    fun invalidate(trackId: String) {
        synchronized(cache) { cache.remove(trackId) }
    }

    internal companion object {
        /** About 50 tracks remembered; the Reel never needs more. */
        const val maxCacheEntries = 50
    }
}
