/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrendingUiState(
    val loading: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val error: SourceErrorType? = null,
    val sourceName: String = "",
    val sourceId: SourceId = SourceId.LOCAL,
)

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendingUiState())
    val state: StateFlow<TrendingUiState> = _state.asStateFlow()

    val likedIds: StateFlow<Set<String>> = library.likedTracks
        .map { tracks -> tracks.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            settings.settings.map { it.primarySource }.distinctUntilChanged()
                .collectLatest { load(it) }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(_state.value.sourceId) }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.toggleLike(track) }
    }

    private suspend fun load(sourceId: SourceId) {
        _state.update { it.copy(loading = true, error = null, sourceId = sourceId) }
        val source = registry.byId(sourceId) ?: registry.primary()
        when (val result = source.getTrending(30)) {
            is SourceResult.Success -> _state.update {
                it.copy(
                    loading = false,
                    tracks = result.data,
                    error = if (result.data.isEmpty()) SourceErrorType.NOT_FOUND else null,
                    sourceName = source.displayName,
                )
            }

            is SourceResult.Failure -> _state.update {
                it.copy(loading = false, error = result.type, sourceName = source.displayName)
            }
        }
    }
}
