/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.newreleases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.model.Playlist
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.source.SourceRegistry
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewReleasesUiState(
    val loading: Boolean = true,
    val releases: List<Playlist> = emptyList(),
    val error: SourceErrorType? = null,
)

/**
 * New releases come from YouTube Music's own page, which lists albums and EPs
 * as cards rather than songs - so the page is a grid of collections, each one
 * opening like any other album.
 */
@HiltViewModel
class NewReleasesViewModel @Inject constructor(
    private val registry: SourceRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(NewReleasesUiState())
    val state: StateFlow<NewReleasesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val source = registry.byId(SourceId.YOUTUBE_MUSIC) as? YouTubeMusicSource
            if (source == null) {
                _state.update {
                    it.copy(loading = false, error = SourceErrorType.UNKNOWN)
                }
                return@launch
            }
            when (val result = source.newReleases()) {
                is SourceResult.Success -> _state.update {
                    it.copy(loading = false, releases = result.data, error = null)
                }

                is SourceResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.type)
                }
            }
        }
    }
}
