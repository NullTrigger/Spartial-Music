/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Track> = emptyList(),
    val error: SourceErrorType? = null,
    val searched: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val history: SearchHistoryRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val recentQueries = history.recent

    init {
        viewModelScope.launch {
            _query
                .debounce(350)
                .distinctUntilChanged()
                .collect { term -> runSearch(term) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        _state.update { it.copy(query = value, error = null) }
    }

    fun submit() {
        viewModelScope.launch {
            runSearch(_query.value)
            history.remember(_query.value)
        }
    }

    fun useRecent(term: String) {
        _query.value = term
        _state.update { it.copy(query = term) }
        submit()
    }

    private suspend fun runSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(loading = false, results = emptyList(), error = null, searched = false) }
            return
        }
        _state.update { it.copy(loading = true, searched = true) }
        val source = registry.primary()
        when (val result = source.search(trimmed, 30)) {
            is SourceResult.Success -> _state.update {
                it.copy(loading = false, results = result.data, error = null, searched = true)
            }

            is SourceResult.Failure -> _state.update {
                it.copy(loading = false, results = emptyList(), error = result.type, searched = true)
            }
        }
    }
}
