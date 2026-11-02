/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEditsTest {

    private fun track(id: String) = Track(id = id, title = id, artist = "Demo")

    @Test
    fun addToQueueAppendsWithoutReordering() {
        val queue = listOf(track("a"), track("b"), track("c"))

        val result = QueueEdits.insert(
            queue = queue,
            playingId = "a",
            track = track("d"),
            afterCurrent = false,
            preventDuplicates = true,
        )

        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
        assertEquals(listOf("a", "b", "c"), queue.map { it.id })
    }

    @Test
    fun playNextInsertsImmediatelyAfterThePlayingTrack() {
        val queue = listOf(track("a"), track("b"), track("c"))

        val result = QueueEdits.insert(
            queue = queue,
            playingId = "b",
            track = track("d"),
            afterCurrent = true,
            preventDuplicates = true,
        )

        assertEquals(listOf("a", "b", "d", "c"), result.map { it.id })
    }

    @Test
    fun duplicateIsMovedInsteadOfListedTwice() {
        val queue = listOf(track("a"), track("b"), track("c"))

        val result = QueueEdits.insert(
            queue = queue,
            playingId = "a",
            track = track("c"),
            afterCurrent = true,
            preventDuplicates = true,
        )

        assertEquals(listOf("a", "c", "b"), result.map { it.id })
        assertEquals(1, result.count { it.id == "c" })
    }

    @Test
    fun duplicatesAreKeptWhenThePreferenceIsOff() {
        val queue = listOf(track("a"), track("b"))

        val result = QueueEdits.insert(
            queue = queue,
            playingId = "a",
            track = track("b"),
            afterCurrent = false,
            preventDuplicates = false,
        )

        assertEquals(listOf("a", "b", "b"), result.map { it.id })
    }

    @Test
    fun playNextWithAnUnknownCurrentTrackFallsBackToTheEnd() {
        val queue = listOf(track("a"), track("b"))

        val result = QueueEdits.insert(
            queue = queue,
            playingId = null,
            track = track("c"),
            afterCurrent = true,
            preventDuplicates = true,
        )

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }
}
