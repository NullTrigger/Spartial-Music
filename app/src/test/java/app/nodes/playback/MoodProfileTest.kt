/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Language inference from a track's own text: named words beat scripts, Latin
 * names nothing, and only a positively different language is OTHER.
 */
class MoodProfileTest {

    private fun track(
        id: String,
        title: String = id,
        artist: String = "Artist",
        album: String? = null,
    ) = Track(id = id, title = title, artist = artist, album = album)

    @Test
    fun `a language named in the words wins over the script`() {
        // Devanagari script alone would say Hindi; the word says Bhojpuri.
        val bhojpuri = track(
            "b1",
            title = "\u0932\u094b\u0932\u093f\u092a\u094b\u092a \u0932\u0917\u0947\u0932\u0942 (Bhojpuri Song 2025)",
        )
        assertEquals("bhojpuri", MoodProfile.clusterOf(bhojpuri))
    }

    @Test
    fun `script decides when no language is named`() {
        val malayalam = track("m1", title = "\u0d2e\u0d4b\u0d39\u0d2e\u0d41\u0d23\u0d4d\u0d1f\u0d41")
        assertEquals("malayalam", MoodProfile.clusterOf(malayalam))

        val hindi = track("h1", title = "\u0928\u0915\u094b \u0926\u093f\u0932 \u0926\u0947")
        assertEquals("hindi", MoodProfile.clusterOf(hindi))
    }

    @Test
    fun `latin text names no language`() {
        // English and romanised Tamil are one song at a time indistinguishable.
        assertEquals(MoodProfile.UNSTATED, MoodProfile.clusterOf(track("l1", title = "Kammaniye")))
        assertEquals(MoodProfile.UNSTATED, MoodProfile.clusterOf(track("l2", title = "Shape of You")))
    }

    @Test
    fun `language words match whole words only`() {
        // "melodia" must not become Odia.
        assertEquals(MoodProfile.UNSTATED, MoodProfile.clusterOf(track("o1", title = "Melodia")))
    }

    @Test
    fun `the mood is what is playing plus the queue tail`() {
        val seed = track("s", title = "\u0d2e\u0d4b\u0d39\u0d2e\u0d41\u0d23\u0d4d\u0d1f\u0d41", artist = "Kalyani")
        val recent = listOf(
            track("r1", title = "\u0d28\u0d3f\u0d7c\u0d2e\u0d3e\u0d26\u0d3f", artist = "Kalyani"),
            track("r2", title = "Enthinu Pulari", artist = "Someone Else", album = "Malayalam Hits"),
        )
        val mood = MoodProfile.of(seed, recent)

        assertEquals(setOf("malayalam"), mood.clusters)
        assertEquals(setOf("kalyani", "someone else"), mood.artists)
    }

    @Test
    fun `a stray track in the queue cannot join the mood and grow its share`() {
        val seed = track("s", title = "\u0d2e\u0d4b\u0d39\u0d2e\u0d41\u0d23\u0d4d\u0d1f\u0d41", artist = "Kalyani")
        val tail = listOf(
            track("b1", title = "Lollipop Lagelu (Bhojpuri Song)", artist = "A"),
            track("b2", title = "Bhojpuri Hit", artist = "B"),
            track("m1", title = "\u0d28\u0d3f\u0d7c\u0d2e\u0d3e\u0d26\u0d3f", artist = "Kalyani"),
            track("m2", title = "\u0d2a\u0d41\u0d34\u0d4d\u0d34\u0d3f", artist = "Kalyani"),
        )
        val mood = MoodProfile.of(seed, tail)

        // Two Bhojpuri tracks out of five do not carry the room.
        assertEquals(setOf("malayalam"), mood.clusters)
        assertEquals(
            MoodProfile.Match.OTHER,
            mood.matchOf(track("b3", title = "Another Bhojpuri Song", artist = "C")),
        )

        // But a tail that speaks one language throughout is that language.
        val settled = MoodProfile.of(
            seed,
            listOf(
                track("x1", title = "Bhojpuri Hit 1", artist = "A"),
                track("x2", title = "Bhojpuri Hit 2", artist = "B"),
                track("x3", title = "Bhojpuri Hit 3", artist = "C"),
            ),
        )
        assertEquals(setOf("malayalam", "bhojpuri"), settled.clusters)
    }

    @Test
    fun `match keeps artist and language, holds back named strangers`() {
        val mood = MoodProfile.of(seed = track("s", title = "\u0d2e\u0d4b\u0d39\u0d2e\u0d41\u0d23\u0d4d\u0d1f\u0d41", artist = "Kalyani"), recent = emptyList())

        // Same language, different artist: in.
        assertEquals(
            MoodProfile.Match.SAME,
            mood.matchOf(track("m2", title = "\u0d28\u0d3f\u0d7c\u0d2e\u0d3e\u0d26\u0d3f", artist = "Another Singer")),
        )
        // Same artist, whatever the language: in — the user asked for this voice.
        assertEquals(
            MoodProfile.Match.SAME,
            mood.matchOf(track("m3", title = "Some Hindi Song", artist = "Kalyani")),
        )
        // Latin-script, no language named: not punished.
        assertEquals(
            MoodProfile.Match.NEUTRAL,
            mood.matchOf(track("l1", title = "Kammaniye", artist = "Stranger")),
        )
        // A track that names another language outright: held back.
        assertEquals(
            MoodProfile.Match.OTHER,
            mood.matchOf(track("b1", title = "Lollipop Lagelu (Bhojpuri Song)", artist = "Stranger")),
        )
    }
}
