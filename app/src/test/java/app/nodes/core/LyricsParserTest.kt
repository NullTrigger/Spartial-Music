/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core

import app.nodes.core.lyrics.LyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun `plain lrc becomes timed lines`() {
        val parsed = LyricsParser.parse("[00:12.50] Hello\n[00:20.00] World")

        assertTrue(parsed.synced)
        assertEquals(2, parsed.lines.size)
        assertEquals(12_500L, parsed.lines[0].timeMs)
        assertEquals("Hello", parsed.lines[0].text)
        // The end comes from the next line, so a highlight can have a duration.
        assertEquals(20_000L, parsed.lines[0].endMs)
        assertEquals(20_000L, parsed.lines[1].timeMs)
    }

    @Test
    fun `one line can carry several stamps`() {
        val parsed = LyricsParser.parse("[00:05.00][01:05.00] Chorus")

        assertEquals(2, parsed.lines.size)
        assertEquals("Chorus", parsed.lines[0].text)
        assertEquals(65_000L, parsed.lines[1].timeMs)
    }

    @Test
    fun `word timings are kept from enhanced lrc`() {
        val parsed = LyricsParser.parse("[00:01.00]v1: <00:01.00>Hello <00:01.60>world")

        assertTrue(parsed.synced)
        val line = parsed.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_600L, line.words[0].endMs)
        assertEquals(1_600L, line.words[1].startMs)
    }

    @Test
    fun `metadata and credit lines are dropped`() {
        val parsed = LyricsParser.parse(
            "[ar:Somebody]\n[offset:0]\nSynced by somebody\n[00:01.00] Real line",
        )

        val line = parsed.lines.single()
        assertEquals("Real line", line.text)
        assertEquals(1_000L, line.timeMs)
    }

    @Test
    fun `untimed text stays unsynced`() {
        val parsed = LyricsParser.parse("first line\nsecond line")

        assertFalse(parsed.synced)
        assertEquals(listOf("first line", "second line"), parsed.lines.map { it.text })
    }

    @Test
    fun `blank payload has nothing to show`() {
        val parsed = LyricsParser.parse("   ")

        assertFalse(parsed.synced)
        assertTrue(parsed.lines.isEmpty())
    }
}
