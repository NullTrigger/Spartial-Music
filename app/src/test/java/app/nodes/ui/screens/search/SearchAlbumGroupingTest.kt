/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.search

import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Albums rail is derived from song results, so its cards must group the
 * same album the card opens. Grouping by title alone merged unrelated releases
 * that happen to share a name, which is how a card could claim five tracks and
 * open a one-track album.
 */
class SearchAlbumGroupingTest {

    private fun song(title: String, artist: String, album: String, albumId: String?) = Track(
        id = "ytmusic-$title-$artist",
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
    )

    @Test
    fun `songs of one album stay on one card`() {
        val albums = albumGroups(
            listOf(
                song("Intro", "Shubh", "One Love", "MPRE1"),
                song("One Love", "Shubh", "One Love", "MPRE1"),
                song("Outro", "Shubh", "One Love", "MPRE1"),
            ),
        )

        assertEquals(1, albums.size)
        assertEquals(3, albums.single().trackCount)
        assertEquals("MPRE1", albums.single().albumId)
    }

    @Test
    fun `the same title on different albums makes separate cards`() {
        val albums = albumGroups(
            listOf(
                song("One Love", "Shubh", "One Love", "MPRE1"),
                song("One Love", "Blue", "One Love", "MPRE2"),
                song("One Love", "Other", "One Love", null),
            ),
        )

        assertEquals(3, albums.size)
        assertEquals(listOf(1, 1, 1), albums.map { it.trackCount })
        assertEquals(3, albums.map { it.key }.distinct().size)
    }

    @Test
    fun `rows without an album id fall back to name and artist`() {
        val albums = albumGroups(
            listOf(
                song("One Love", "Shubh", "One Love", null),
                song("One Love (Live)", "Shubh", "One Love", null),
            ),
        )

        assertEquals(1, albums.size)
        assertEquals(2, albums.single().trackCount)
        assertEquals(null, albums.single().albumId)
    }
}
