/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics

import app.nodes.core.lyrics.providers.NetEaseArtist
import app.nodes.core.lyrics.providers.NetEaseTrack
import app.nodes.core.lyrics.providers.selectNetEaseTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsProviderSelectionTest {

    private val query = LyricsQuery(
        trackId = "demo:1",
        title = "Night Drive",
        artist = "The Fictional Band",
        durationSec = 200,
    )

    @Test
    fun `exact title artist and duration wins`() {
        val candidates = listOf(
            track(id = 1, title = "Another Song", artist = "The Fictional Band", duration = 200),
            track(id = 2, title = "Night Drive", artist = "Some Cover Act", duration = 200),
            track(id = 3, title = "Night Drive (Live)", artist = "The Fictional Band", duration = 201),
            track(id = 4, title = "Night Drive", artist = "The Fictional Band", duration = 200),
        )

        assertEquals(4L, selectNetEaseTrack(candidates, query)?.id)
    }

    @Test
    fun `a candidate more than fifteen seconds off is ignored`() {
        val candidates = listOf(
            track(id = 1, title = "Night Drive", artist = "The Fictional Band", duration = 260),
            track(id = 2, title = "Night Drive", artist = "The Fictional Band", duration = 204),
        )

        assertEquals(2L, selectNetEaseTrack(candidates, query)?.id)
    }

    @Test
    fun `a different title is never accepted`() {
        val candidates = listOf(
            track(id = 1, title = "Morning Walk", artist = "The Fictional Band", duration = 200),
            track(id = 2, title = "Evening Train", artist = "The Fictional Band", duration = 200),
        )

        assertNull(selectNetEaseTrack(candidates, query))
    }

    @Test
    fun `a result without an id is ignored`() {
        val candidates = listOf(
            track(id = null, title = "Night Drive", artist = "The Fictional Band", duration = 200),
        )

        assertNull(selectNetEaseTrack(candidates, query))
    }

    @Test
    fun `placeholder payloads are treated as misses`() {
        assertNull(cleanLyricsText(null))
        assertNull(cleanLyricsText("   "))
        assertNull(cleanLyricsText("..."))
        assertNull(cleanLyricsText("[00:00.000] 纯音乐，请欣赏"))
        assertNull(cleanLyricsText("No lyrics found"))
        assertEquals("A real line\nAnother line", cleanLyricsText(" A real line\nAnother line "))
    }

    private fun track(id: Long?, title: String, artist: String, duration: Long) = NetEaseTrack(
        id = id,
        name = title,
        artists = listOf(NetEaseArtist(name = artist)),
        duration = duration * 1_000L,
    )
}
