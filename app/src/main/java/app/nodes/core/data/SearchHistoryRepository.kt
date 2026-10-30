/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recent search terms, kept on device only (never sent to a server) so the
 * search field can offer a starting point. Cleared with the rest of the cache.
 */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    val recent: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_RECENT]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun remember(term: String) {
        val clean = term.trim()
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) })
                .take(MAX_ENTRIES)
            prefs[KEY_RECENT] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun clear() = dataStore.edit { it.remove(KEY_RECENT) }

    private companion object {
        const val MAX_ENTRIES = 8
        const val SEPARATOR = "\u001F"
        val KEY_RECENT = stringPreferencesKey("recent_searches")
    }
}
