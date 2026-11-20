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
        assertEquals(1, feed.count { it.id == "dup" })
        assertTrue("s2" !in feed.map { it.id })
        assertEquals(feed.size, feed.map { it.id }.distinct().size)
    }

    @Test
    fun `batch removes same recording across source ids`() {
        val first = Track(id = "jamendo:1", title = "Blue Sky", artist = "Aster")
        val echo = Track(id = "youtube:2", title = "BLUE SKY", artist = "Aster")
        val other = Track(id = "jamendo:3", title = "Open Road", artist = "Mira")

        val batch = ReelFeed.batch(listOf(first, echo, other), emptySet(), 10)

        assertEquals(listOf("jamendo:1", "jamendo:3"), batch.map { it.id })
    }

    @Test
    fun `personalize favors recent search and new tracks by listened artists`() {
        val recent = Track(id = "played", title = "Quiet River", artist = "Aster")
        val searched = Track(id = "search-hit", title = "Quiet Fields", artist = "Mira")
        val artistMatch = Track(id = "artist-hit", title = "Open Road", artist = "Aster")
        val unrelated = Track(id = "other", title = "Bright Lights", artist = "Noor")

        val ranked = ReelFeed.personalize(
            candidates = listOf(unrelated, artistMatch, searched),
            recentQueries = listOf("quiet fields"),
            recentlyPlayed = listOf(recent),
            liked = emptyList(),
        )

        assertEquals(listOf("search-hit", "artist-hit", "other"), ranked.map { it.id })
    }

    @Test
    fun `batch excludes the queue and the recent fifty and caps the size`() {
        val candidates = (1..30).map { track("t$it") } + track("queued") + track("recent")
        val batch = ReelFeed.batch(candidates, setOf("queued", "recent"), 10)
        assertEquals(10, batch.size)
        assertTrue(batch.none { it.id == "queued" || it.id == "recent" })
    }
}
