/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import app.nodes.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchSuggestionsTest {

    @Test
    fun `matches recent searches and ranks newest first`() {
        val tracks = listOf(
            Track(id = "older", title = "Moon River", artist = "Nina"),
            Track(id = "newer", title = "Quiet Moon", artist = "Arlo"),
            Track(id = "unrelated", title = "Open Road", artist = "Mira"),
        )

        val suggestions = searchHistorySuggestions(
            recentQueries = listOf("quiet moon", "moon river"),
            cachedTracks = tracks,
        )

        assertEquals(listOf("newer", "older"), suggestions.map { it.id })
    }

    @Test
    fun `requires all query words and omits already shown tracks`() {
        val tracks = listOf(
            Track(id = "full-match", title = "Silver Fields", artist = "Aster"),
            Track(id = "partial", title = "Silver Days", artist = "Aster"),
        )

        val suggestions = searchHistorySuggestions(
            recentQueries = listOf("silver fields"),
            cachedTracks = tracks,
            excludedIds = setOf("full-match"),
        )

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `keeps local matches first and fills with discovery one artist at a time`() {
        val matches = listOf(Track(id = "local", title = "Silver Fields", artist = "Aster"))
        val discovery = listOf(
            Track(id = "a1", title = "Aster One", artist = "Aster"),
            Track(id = "a2", title = "Aster Two", artist = "Aster"),
            Track(id = "b1", title = "Birch One", artist = "Birch"),
        )

        val merged = mergeSearchSuggestions(matches, discovery, limit = 4)

        assertEquals(listOf("local", "a1", "b1", "a2"), merged.map { it.id })
    }

    @Test
    fun `drops discovery that Home already shows`() {
        val merged = mergeSearchSuggestions(
            matches = emptyList(),
            discovery = listOf(
                Track(id = "dup", title = "Dup", artist = "Aster"),
                Track(id = "ok", title = "Ok", artist = "Birch"),
            ),
            excludedIds = setOf("dup"),
        )

        assertEquals(listOf("ok"), merged.map { it.id })
    }
}
