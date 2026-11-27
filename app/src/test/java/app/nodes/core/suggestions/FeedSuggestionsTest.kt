/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.suggestions

import app.nodes.core.model.HomeFeed
import app.nodes.core.model.HomeSection
import app.nodes.core.model.HeroSlide
import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one suggestion mechanism: a home feed shaped into top picks, the seed's
 * echo and variety. Pure, no Android and no provider.
 */
class FeedSuggestionsTest {

    private fun track(id: String, artist: String = "Artist") =
        Track(id = id, title = id, artist = artist, videoId = "vid-$id")

    private fun card(id: String) =
        Track(id = id, title = id, artist = "Artist", collectionId = "VL$id")

    @Test
    fun `the feed is shaped into top picks, the seed's echo and fresh around the seed`() {
        val hero = listOf(track("h1", "Solo"), track("h2"))
        val shelf = listOf(
            track("s1", "Solo"),
            track("s2"),
            track("s3"),
            track("s4"),
            track("s5"),
            track("s6"),
            track("s7"),
            track("s8"),
            track("s9"),
        )
        val continuePlaying = listOf(
            track("c1", "Solo"),
            track("c2"),
            track("c3"),
            track("c4"),
            track("c5"),
        )
        val feed = HomeFeed(
            hero = hero.map { HeroSlide(track = it) },
            sections = listOf(HomeSection(id = "rail", title = "Rail", tracks = shelf)),
            continuePlaying = continuePlaying,
        )
        val seed = track("seed", "Solo")

        val shaped = FeedSuggestions.shape(feed, seed)

        // Top picks are the hero and the rails, in feed order, capped at 8.
        assertEquals(
            listOf("h1", "h2", "s1", "s2", "s3", "s4", "s5", "s6"),
            shaped.topPicks.map { it.id },
        )
        // The echo of the seed comes first, then feed filler, capped at 6.
        assertEquals(
            listOf("c1", "s7", "s8", "s9", "c2", "c3"),
            shaped.because.map { it.id },
        )
        assertEquals("Solo", shaped.becauseSeed?.artist)
        // What is neither top pick nor on the because shelf is variety.
        assertEquals(listOf("c4", "c5"), shaped.fresh.map { it.id })
        // The queue and the Reel append in this order.
        assertEquals("c1", shaped.ordered.first().id)
        assertEquals("c5", shaped.ordered.last().id)
    }

    @Test
    fun `collection cards and duplicates are dropped, and no seed means no echo`() {
        val feed = HomeFeed(
            hero = listOf(HeroSlide(track = track("h1")), HeroSlide(track = card("card"))),
            sections = listOf(
                HomeSection(id = "rail", title = "Rail", tracks = listOf(track("h1"), track("s1"))),
            ),
        )

        val shaped = FeedSuggestions.shape(feed, null)

        assertEquals(listOf("h1", "s1"), shaped.topPicks.map { it.id })
        assertTrue(shaped.because.isEmpty())
        assertEquals(null, shaped.becauseSeed)
        assertTrue(shaped.fresh.isEmpty())
    }
}
