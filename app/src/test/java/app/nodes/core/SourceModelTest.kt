/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core

import app.nodes.core.data.db.toEntity
import app.nodes.core.data.db.toTrack
import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import app.nodes.core.source.DemoCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceModelTest {

    @Test
    fun sourceIdRoundTripsThroughKey() {
        SourceId.entries.forEach { id ->
            assertEquals(id, SourceId.fromKey(id.key))
        }
    }

    @Test
    fun unknownSourceKeyFallsBackToLocal() {
        assertEquals(SourceId.LOCAL, SourceId.fromKey("myspace"))
    }

    @Test
    fun trackSurvivesEntityRoundTrip() {
        val track = Track(
            id = "youtube-abc",
            title = "A title",
            artist = "An artist",
            album = "An album",
            artworkUri = "https://example.com/art.jpg",
            mediaUri = null,
            durationMs = 123_000L,
            sourceId = SourceId.YOUTUBE,
            videoId = "abc",
            externalUrl = "https://www.youtube.com/watch?v=abc",
        )
        val restored = track.toEntity(now = 0L).toTrack()

        assertEquals(track.id, restored.id)
        assertEquals(track.sourceId, restored.sourceId)
        assertEquals(track.videoId, restored.videoId)
        assertNull(restored.mediaUri)
    }

    @Test
    fun demoCatalogueHasNoStreamUrlsAndNoRealArtists() {
        assertTrue(DemoCatalog.tracks.isNotEmpty())
        DemoCatalog.tracks.forEach { track ->
            assertNull(track.mediaUri)
            assertTrue(track.artist.isNotBlank())
        }
    }

    @Test
    fun demoSearchIsCaseInsensitive() {
        val results = DemoCatalog.search("midnight")
        assertEquals(1, results.size)
        assertEquals("Midnight Static", results.first().title)
    }
}
