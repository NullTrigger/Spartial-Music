/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which group of a mixed search the results list shows. */
enum class SearchFilter {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS,
}

/** An album derived from the song results; no extra provider call is made. */
data class SearchAlbum(
    val name: String,
    val artist: String,
    val artworkUri: String?,
    val trackCount: Int,
    /** The provider's album page id when the rows name one; null keeps the name-only fallback. */
    val albumId: String? = null,
) {
    /** Stable list key: two releases can share a title but not an id. */
    val key: String get() = albumId ?: "name:$name|$artist"
}

/** An artist derived from the song results. */
data class SearchArtist(
    val name: String,
    val artworkUri: String?,
    val trackCount: Int,
)

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Track> = emptyList(),
    val albums: List<SearchAlbum> = emptyList(),
    val artists: List<SearchArtist> = emptyList(),
    val error: SourceErrorType? = null,
    val searched: Boolean = false,
    val filter: SearchFilter = SearchFilter.ALL,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val registry: SourceRegistry,
    private val history: SearchHistoryRepository,
    private val youTubeMusic: YouTubeMusicSource,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    val recentQueries = history.recent

    /** Ids are enough to show a filled star without loading each track twice. */
    val likedIds: StateFlow<Set<String>> = library.likedTracks
        .map { tracks -> tracks.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        // Results follow the text field, not the keyboard's search key: a short
        // pause is enough to know the user stopped typing. collectLatest drops a
        // slower earlier response so it cannot overwrite a newer one.
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { term -> runSearch(term) }
        }
        viewModelScope.launch {
            _query
                .debounce(200)
                .distinctUntilChanged()
                .collectLatest { term ->
                    _suggestions.value = if (term.trim().length < 2) {
                        emptyList()
                    } else {
                        youTubeMusic.suggestions(term.trim())
                    }
                }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        _state.update { it.copy(query = value, error = null) }
    }

    fun setFilter(filter: SearchFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.toggleLike(track) }
    }

    fun submit() {
        val term = _query.value.trim()
        if (term.isEmpty()) return
        viewModelScope.launch {
            _suggestions.value = emptyList()
            runSearch(term)
            history.remember(term)
        }
    }

    fun retry() {
        val term = _query.value.trim()
        if (term.isNotEmpty()) viewModelScope.launch { runSearch(term) }
    }

    fun useSuggestion(term: String) {
        _suggestions.value = emptyList()
        useRecent(term)
    }

    fun useRecent(term: String) {
        _query.value = term
        _state.update { it.copy(query = term) }
        submit()
    }

    private suspend fun runSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    loading = false,
                    results = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    error = null,
                    searched = false,
                )
            }
            return
        }
        _state.update { it.copy(loading = true, searched = true) }
        val source = registry.primary()
        val result = source.search(trimmed, 30)
        // The field may have moved on while the request was in flight. Showing
        // the old answer under the new text is worse than showing nothing.
        if (trimmed != _query.value.trim()) return
        when (result) {
            is SourceResult.Success -> {
                // Record what was fetched: album and artist pages are a Room
                // query over music the app has already seen.
                runCatching { library.cacheTracks(result.data) }
                _state.update {
                    it.copy(
                        loading = false,
                        results = result.data,
                        albums = albumGroups(result.data),
                        artists = artistGroups(result.data),
                        error = null,
                        searched = true,
                    )
                }
            }

            is SourceResult.Failure -> _state.update {
                it.copy(
                    loading = false,
                    results = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    error = result.type,
                    searched = true,
                )
            }
        }
    }
}

/**
 * Album cards derived from the song results.
 *
 * Rows are grouped by the provider's album id when the row carries one, and
 * only fall back to the album name plus artist otherwise. Grouping by name
 * alone merged unrelated releases that share a title (a search for "One Love"
 * showed one card claiming five tracks, and opening it showed the single track
 * of whichever album the id happened to come from), so the count on the card
 * now counts the same album the card opens.
 */
internal fun albumGroups(tracks: List<Track>): List<SearchAlbum> =
    tracks
        .filter { !it.album.isNullOrBlank() }
        .groupBy { track ->
            track.albumId ?: "name:${track.album.orEmpty()}|${track.artist}"
        }
        .map { (_, rows) ->
            SearchAlbum(
                name = rows.first().album.orEmpty(),
                artist = rows.first().artist,
                artworkUri = rows.firstNotNullOfOrNull { it.artworkUri },
                trackCount = rows.size,
                albumId = rows.firstNotNullOfOrNull { it.albumId },
            )
        }
        .sortedByDescending { it.trackCount }

private fun artistGroups(tracks: List<Track>): List<SearchArtist> =
    tracks
        .filter { it.artist.isNotBlank() }
        .groupBy { it.artist }
        .map { (name, rows) ->
            SearchArtist(
                name = name,
                artworkUri = rows.firstNotNullOfOrNull { it.artworkUri },
                trackCount = rows.size,
            )
        }
        .sortedByDescending { it.trackCount }
