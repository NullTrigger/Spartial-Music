/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core

import app.nodes.core.data.LyricsRepository
import app.nodes.core.source.youtube.parseIso8601Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationParsingTest {

    @Test
    fun parsesMinutesAndSeconds() {
        assertEquals(222_000L, parseIso8601Duration("PT3M42S"))
    }

    @Test
    fun parsesHours() {
        assertEquals(3_723_000L, parseIso8601Duration("PT1H2M3S"))
    }

    @Test
    fun parsesSecondsOnly() {
        assertEquals(45_000L, parseIso8601Duration("PT45S"))
    }

    @Test
    fun toleratesGarbage() {
        assertEquals(0L, parseIso8601Duration(""))
        assertEquals(0L, parseIso8601Duration("not-a-duration"))
    }

    @Test
    fun parsesLyricTimestamps() {
        val line = LyricsRepository.parseLine("[01:23.45] Hello world")
        assertEquals(83_450L, line?.timeMs)
        assertEquals("Hello world", line?.text)
    }

    @Test
    fun ignoresLinesWithoutTimestamp() {
        assertNull(LyricsRepository.parseLine("just a lyric"))
    }

    @Test
    fun parsesThreeDigitMilliseconds() {
        assertEquals(1_005L, LyricsRepository.parseLine("[00:01.005] tick")?.timeMs)
    }
}
