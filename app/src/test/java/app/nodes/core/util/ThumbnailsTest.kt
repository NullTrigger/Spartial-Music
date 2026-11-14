/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailsTest {

    @Test
    fun `google size suffix is rewritten to the requested pixels`() {
        val url = "https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            upgradeThumbnail(url, 544),
        )
    }

    @Test
    fun `the square shorthand keeps its shape`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=s1080",
            upgradeThumbnail("https://lh3.googleusercontent.com/abc=s120", 1080),
        )
    }

    @Test
    fun `extra flags survive the rewrite`() {
        assertEquals(
            "https://yt3.ggpht.com/x=w768-h768-p-k-no-nu",
            upgradeThumbnail("https://yt3.ggpht.com/x=w120-h120-p-k-no-nu", 768),
        )
    }

    @Test
    fun `sizes are clamped to a sane range`() {
        val small = upgradeThumbnail("https://lh3.googleusercontent.com/a=w60-h60", 16)
        assertTrue(small!!.endsWith("=w128-h128"))

        val huge = upgradeThumbnail("https://lh3.googleusercontent.com/a=w60-h60", 9000)
        assertTrue(huge!!.endsWith("=w2048-h2048"))
    }

    @Test
    fun `urls without a size suffix are untouched`() {
        val url = "https://cdn.example.org/art/cover.jpg"
        assertEquals(url, upgradeThumbnail(url, 544))
    }

    @Test
    fun `other hosts are left alone`() {
        val url = "https://images.jamendo.com/?id=42&size=200"
        assertEquals(url, upgradeThumbnail(url, 544))
    }

    @Test
    fun `a youtube video thumbnail asks for the largest frame first`() {
        val url = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            ),
            thumbnailCandidates(url, 1080),
        )
    }

    @Test
    fun `maxres is not rewritten again`() {
        val url = "https://i.ytimg.com/vi/abc/maxresdefault.jpg"
        assertEquals(url, upgradeThumbnail(url, 1080))
        assertEquals(listOf(url), thumbnailCandidates(url, 1080))
    }

    @Test
    fun `a missing url has no candidates`() {
        assertNull(upgradeThumbnail(null, 544))
        assertNull(upgradeThumbnail("  ", 544))
        assertTrue(thumbnailCandidates(null, 544).isEmpty())
    }

    @Test
    fun `a host with a port is still recognised`() {
        assertEquals(
            "http://lh3.googleusercontent.com:443/a=w256-h256",
            upgradeThumbnail("http://lh3.googleusercontent.com:443/a=w60-h60", 256),
        )
    }
}
