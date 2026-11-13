/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics

import app.nodes.core.lyrics.providers.Ttml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between the TTML providers and [LyricsParser]: whatever
 * [Ttml.toEnhancedLrc] emits must be readable as timed lines with word tags.
 */
class TtmlTest {

    private val ttml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:ttp="http://www.w3.org/ns/ttml#parameter">
          <body>
            <div begin="00:00:01.000">
              <p begin="00:00:01.000" end="00:00:04.000">
                <span begin="00:00:01.000">Word1</span>
                <span begin="00:00:02.000"> word2</span>
              </p>
              <p begin="0:05.500" end="0:07.500">Second line</p>
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun `timed words become parser-readable word tags`() {
        val lines = Ttml.parse(ttml)
        assertEquals(2, lines.size)

        val lrc = Ttml.toEnhancedLrc(lines)
        val first = lrc.lineSequence().first()

        // The tag shape LyricsParser.WORD_TAG accepts: <m:ss.xx>.
        assertTrue("tag shape: $first", first.contains(Regex("""<\d{1,2}:\d{2}\.\d{1,3}>""")))
        assertTrue(first.startsWith("[00:01.00]"))
        assertTrue(first.contains("Word1"))
    }

    @Test
    fun `plain lines stay plain`() {
        val lines = Ttml.parse(ttml)
        assertEquals("Second line", lines[1].text)

        val lrc = Ttml.toEnhancedLrc(lines)
        // The first line is word-synced; the second has no spans, so it is text.
        val second = lrc.lineSequence().elementAt(1)
        assertTrue(second.startsWith("[00:05.50]"))
        assertFalse(second.contains('<'))
    }

    @Test
    fun `clock values in both shapes parse`() {
        assertEquals(75.5, Ttml.parseTime("1:15.500"), 0.001)
        assertEquals(3725.25, Ttml.parseTime("01:02:05.250"), 0.001)
        assertEquals(3.2, Ttml.parseTime("3.2s"), 0.001)
        assertEquals(1.5, Ttml.parseTime("1,5"), 0.001)
    }

    @Test
    fun `malformed xml yields nothing rather than throwing`() {
        assertTrue(Ttml.parse("").isEmpty())
        // A doctype is refused, not fetched.
        assertTrue(Ttml.parse("<!DOCTYPE tt><tt/>").isEmpty())
        // An unparseable timestamp is skipped, never emitted as NaN.
        assertTrue(Ttml.parse("<tt><p begin='xx'>hi</p></tt>").isEmpty())
    }
}
