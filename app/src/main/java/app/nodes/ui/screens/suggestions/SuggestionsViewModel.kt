/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.core.suggestions.FeedSuggestions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SuggestionsUiState(
    val loading: Boolean = true,
    val error: SourceErrorType? = null,
    val sourceName: String = "",
    val topPicks: List<Track> = emptyList(),
    val because: List<Track> = emptyList(),
    val becauseSeed: String? = null,
    val fresh: List<Track> = emptyList(),
) {
    val isEmpty: Boolean
        get() = topPicks.isEmpty() && because.isEmpty() && fresh.isEmpty()
}

/**
 * The For You screen. The shelves come from [FeedSuggestions], the one
 * suggestion mechanism this app uses: the provider's home feed shaped around
 * what played last, so the queue and the Reel suggest the same music.
 */
@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SuggestionsUiState())
    val state: StateFlow<SuggestionsUiState> = _state.asStateFlow()

    private var sourceId: SourceId = SourceId.LOCAL

    init {
        viewModelScope.launch {
            settings.settings
                .map { it.primarySource }
                .distinctUntilChanged()
                .collectLatest { id ->
                    sourceId = id
                    load()
                }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val source = registry.byId(sourceId) ?: registry.primary()
        val recents = runCatching { library.recentlyPlayed.first() }.getOrDefault(emptyList())
        when (val result = source.getHome()) {
            is SourceResult.Success -> {
                val shaped = FeedSuggestions.shape(result.data, recents.firstOrNull())
                // Record what was fetched: album and artist pages are a Room
                // query over music the app has already seen.
                runCatching { library.cacheTracks(shaped.pool) }
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        sourceName = source.displayName,
                        topPicks = shaped.topPicks,
                        because = shaped.because,
                        becauseSeed = shaped.becauseSeed?.title ?: shaped.becauseSeed?.artist,
                        fresh = shaped.fresh,
                    )
                }
            }

            is SourceResult.Failure -> _state.update {
                it.copy(
                    loading = false,
                    error = result.type,
                    sourceName = source.displayName,
                )
            }
        }
    }
}
