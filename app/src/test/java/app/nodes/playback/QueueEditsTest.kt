/*
 * Copyright (C) 2026 The Spartial Music Authors.
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

    private fun song(
        id: String,
        artist: String = "Demo",
        album: String? = null,
    ) = Track(id = id, title = id, artist = artist, album = album)

    @Test
    fun suggestionsRankTheSameAlbumFirstThenTheSameArtist() {
        val seed = song("seed", artist = "Midnight Signals", album = "Night Drive")
        val candidates = listOf(
            song("other"),
            song("same-artist", artist = "Midnight Signals"),
            song("same-album", artist = "Midnight Signals", album = "Night Drive"),
        )

        val result = QueueEdits.suggest(
            queue = listOf(seed),
            candidates = candidates,
            limit = 3,
        )

        assertEquals(listOf("same-album", "same-artist", "other"), result.map { it.id })
    }

    @Test
    fun suggestionsSkipWhatTheQueueAlreadyHolds() {
        val queue = listOf(song("seed"), song("already"))

        val result = QueueEdits.suggest(
            queue = queue,
            candidates = listOf(song("seed"), song("already"), song("fresh")),
            limit = 5,
        )

        assertEquals(listOf("fresh"), result.map { it.id })
    }

    @Test
    fun suggestionsRespectTheLimitAndKeepCandidateOrderInTies() {
        val result = QueueEdits.suggest(
            queue = listOf(song("seed")),
            candidates = listOf(song("one"), song("two"), song("three")),
            limit = 2,
        )

        assertEquals(listOf("one", "two"), result.map { it.id })
    }
}
