/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.HomeFeed
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.core.source.youtube.YouTubeSignIn
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val feed: HomeFeed = HomeFeed(),
    val recents: List<Track> = emptyList(),
    val error: SourceErrorType? = null,
    val sourceName: String = "",
    val sourceId: SourceId = SourceId.LOCAL,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val signIn: YouTubeSignIn,
    private val musicAuth: InnerTubeAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** The signed-in YouTube Music account, if any; drives the avatar initial. */
    val account: StateFlow<InnerTubeAccount?> = musicAuth.account

    init {
        viewModelScope.launch {
            settings.settings
                .map { it.primarySource }
                .distinctUntilChanged()
                .collectLatest { sourceId -> load(sourceId) }
        }
        // Signing in or out adds or removes the personal rails, so reload rather
        // than making the user restart the app to see their own music.
        viewModelScope.launch {
            signIn.state
                .drop(1)
                .collect { load(_state.value.sourceId) }
        }
        // The same for the YouTube Music cookie: signing in makes Home personal,
        // signing out returns it to the anonymous shelves.
        viewModelScope.launch {
            musicAuth.account
                .drop(1)
                .collect { load(_state.value.sourceId) }
        }
        // "Recents" is the local listening history, independent of the active
        // provider: it stays useful while a network source is offline.
        viewModelScope.launch {
            library.recentlyPlayed.collect { recents ->
                _state.update { it.copy(recents = recents) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(_state.value.sourceId) }
    }

    private suspend fun load(sourceId: SourceId) {
        _state.update { it.copy(loading = true, error = null, sourceId = sourceId) }
        val source = registry.byId(sourceId) ?: registry.primary()
        val recent = _state.value.recents.ifEmpty { library.recentlyPlayed.first() }

        when (val result = source.getHome()) {
            is SourceResult.Success -> {
                val feed = result.data
                _state.update {
                    it.copy(
                        loading = false,
                        sourceName = source.displayName,
                        error = null,
                        feed = feed.copy(
                            continuePlaying = recent.ifEmpty { feed.continuePlaying },
                        ),
                    )
                }
            }

            is SourceResult.Failure -> {
                _state.update {
                    it.copy(
                        loading = false,
                        error = result.type,
                        sourceName = source.displayName,
                    )
                }
            }
        }
    }
}
