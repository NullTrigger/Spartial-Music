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
}
