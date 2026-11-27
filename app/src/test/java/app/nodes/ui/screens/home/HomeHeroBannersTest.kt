/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroBannersTest {

    private fun track(id: String, videoId: String? = "video-$id") =
        Track(id = id, title = id, artist = "Artist", videoId = videoId)

    @Test
    fun `queue suggestions lead the hero and never repeat a banner`() {
        val banners = listOf(track("b1"), track("b2"), track("b3"), track("b4"))
        val upNext = listOf(track("u1"), track("u2"), track("b2"))

        val hero = heroWithUpNext(banners = banners, upNext = upNext, limit = 5)

        // The queue's picks first, then the provider's banners in their own
        // order - and b2, which both sides offer, drops out of both.
        assertEquals(listOf("u1", "u2", "b1", "b3", "b4"), hero.map { it.id })
    }

    @Test
    fun `unplayable suggestions and duplicates are dropped`() {
        val banners = listOf(track("b1"))
        val upNext = listOf(track("collection", videoId = null), track("u1"), track("u1"))

        val hero = heroWithUpNext(banners = banners, upNext = upNext)

        assertEquals(listOf("u1", "b1"), hero.map { it.id })
    }

    @Test
    fun `an empty queue leaves the provider banners untouched`() {
        val banners = listOf(track("b1"), track("b2"))

        assertEquals(banners, heroWithUpNext(banners = banners, upNext = emptyList()))
    }
}
