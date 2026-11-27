/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.cover

import app.nodes.core.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolver order, failure skipping and the in-memory LRU. */
class CoverResolverTest {

    private class FakeProvider(
        override val id: String,
        private val answer: (Track) -> AnimatedCover?,
    ) : AnimatedCoverProvider {
        var calls = 0
        override suspend fun find(track: Track): AnimatedCover? {
            calls++
            return answer(track)
        }
    }

    private fun cover(url: String, provider: String) = AnimatedCover(url, provider)

    @Test
    fun `user loop wins over the youtube provider regardless of set order`() = runTest {
        val user = FakeProvider(CoverProviderIds.USER_LOOP) { cover("file://loop", CoverProviderIds.USER_LOOP) }
        val youTube = FakeProvider(CoverProviderIds.YOUTUBE_MUSIC) { cover("https://x", it.id) }
        val resolver = CoverResolver(linkedSetOf(youTube, user))
        val found = resolver.find(Track(id = "t1", title = "Signal Bloom", artist = "A"))
        assertEquals("file://loop", found?.url)
        assertEquals(CoverProviderIds.USER_LOOP, found?.providerId)
        assertEquals(0, youTube.calls)
    }

    @Test
    fun `null when every provider misses`() = runTest {
        val resolver = CoverResolver(
            setOf(
                FakeProvider(CoverProviderIds.USER_LOOP) { null },
                FakeProvider(CoverProviderIds.YOUTUBE_MUSIC) { null },
            ),
        )
        assertNull(resolver.find(Track(id = "t2", title = "Late Night Mix", artist = "A")))
    }

    @Test
    fun `a throwing provider is skipped and the next one answers`() = runTest {
        val broken = FakeProvider(CoverProviderIds.USER_LOOP) { error("boom") }
        val working = FakeProvider(CoverProviderIds.YOUTUBE_MUSIC) { cover("https://ok", it.id) }
        val resolver = CoverResolver(linkedSetOf(broken, working))
        assertEquals("https://ok", resolver.find(Track(id = "t3", title = "x", artist = "A"))?.url)
    }

    @Test
    fun `the lru keeps at most the cap and answers from cache in between`() = runTest {
        val counting = FakeProvider(CoverProviderIds.USER_LOOP) { cover("u-${it.id}", it.id) }
        val resolver = CoverResolver(setOf(counting))
        repeat(70) { i -> resolver.find(Track(id = "t$i", title = "x", artist = "A")) }
        // One call per distinct track; the eldest entries fell out of the
        // cache, the newest 50 did not re-ask.
        assertEquals(70, counting.calls)
        resolver.find(Track(id = "t69", title = "x", artist = "A"))
        assertEquals(70, counting.calls)
        resolver.find(Track(id = "t0", title = "x", artist = "A"))
        assertEquals(71, counting.calls)
    }
}
