/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SongSort { RECENT, TITLE, ARTIST }

data class SongsUiState(
    val loading: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val likedIds: Set<String> = emptySet(),
    val total: Int = 0,
    val query: String = "",
    val sort: SongSort = SongSort.RECENT,
)

/** The full local song list, filtered and sorted without another query. */
@HiltViewModel
class SongsViewModel @Inject constructor(
    private val library: LibraryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SongSort.RECENT)

    val state: StateFlow<SongsUiState> = combine(
        library.allTracks,
        library.likedTracks,
        query,
        sort,
    ) { all, liked, term, order ->
        val likedIds = liked.mapTo(HashSet()) { it.id }
        val needle = term.trim()
        val filtered = if (needle.isEmpty()) {
            all
        } else {
            all.filter { track ->
                track.title.contains(needle, ignoreCase = true) ||
                    track.artist.contains(needle, ignoreCase = true) ||
                    track.album?.contains(needle, ignoreCase = true) == true
            }
        }
        val sorted = when (order) {
            SongSort.RECENT -> filtered
            SongSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> filtered.sortedWith(
                compareBy({ it.artist.lowercase() }, { it.title.lowercase() }),
            )
        }
        SongsUiState(
            loading = false,
            tracks = sorted,
            likedIds = likedIds,
            total = all.size,
            query = term,
            sort = order,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongsUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: SongSort) {
        sort.value = value
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.toggleLike(track) }
    }
}
