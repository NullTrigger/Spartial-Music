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
 * Suggestions are derived from the same Home feed the provider already builds,
 * plus the local listening history. No extra provider call and no new profile
 * is created: the recommendation is explainable ("because you played X") and
 * stays inside the app.
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
                val feed = result.data
                val hero = feed.hero.map { it.track }
                val shelved = feed.sections.flatMap { it.tracks }
                val all = (hero + shelved + feed.continuePlaying)
                    .distinctBy { it.id }
                val seed = recents.firstOrNull()
                val top = (hero + shelved).distinctBy { it.id }.take(8)
                val topIds = top.mapTo(HashSet()) { it.id }
                val related = if (seed != null) {
                    all.filter { track ->
                        track.id != seed.id &&
                            track.artist.equals(seed.artist, ignoreCase = true)
                    }
                } else {
                    emptyList()
                }
                val because = (related + all)
                    .distinctBy { it.id }
                    .filterNot { it.id in topIds }
                    .take(6)
                val becauseIds = because.mapTo(HashSet()) { it.id }
                val fresh = all
                    .filterNot { it.id in topIds || it.id in becauseIds }
                    .take(10)
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        sourceName = source.displayName,
                        topPicks = top,
                        because = because,
                        becauseSeed = seed?.title ?: seed?.artist,
                        fresh = fresh,
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
