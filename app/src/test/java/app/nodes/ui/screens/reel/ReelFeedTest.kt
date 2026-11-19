/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Reel's feed math, plainly: dedupe, exclusions, interleaving. */
class ReelFeedTest {

    private fun track(id: String) = Track(id = id, title = id, artist = "A")

    @Test
    fun `interleave takes one per artist in turn and dedupes`() {
        val a = listOf(track("a1"), track("a2"), track("a3"))
        val b = listOf(track("b1"), track("b2"))
        val feed = ReelFeed.interleaveByArtist(listOf(a, b), emptySet(), 10)
        assertEquals(listOf("a1", "b1", "a2", "b2", "a3"), feed.map { it.id })
    }

    @Test
    fun `excluded ids never appear and the same id is never repeated`() {
        val x = listOf(track("s1"), track("dup"), track("dup"))
        val y = listOf(track("dup"), track("s2"))
        val feed = ReelFeed.interleaveByArtist(listOf(x, y), setOf("s2"), 10)
        assertEquals(2, feed.count { it.id == "dup" })
        assertTrue("s2" !in feed.map { it.id })
        assertEquals(feed.size, feed.map { it.id }.distinct().size)
    }

    @Test
    fun `batch excludes the queue and the recent fifty and caps the size`() {
        val candidates = (1..30).map { track("t$it") } + track("queued") + track("recent")
        val batch = ReelFeed.batch(candidates, setOf("queued", "recent"), 10)
        assertEquals(10, batch.size)
        assertTrue(batch.none { it.id == "queued" || it.id == "recent" })
    }
}
