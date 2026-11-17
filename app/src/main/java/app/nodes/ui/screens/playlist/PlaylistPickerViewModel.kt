/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.AddToPlaylistResult
import app.nodes.core.model.Playlist
import app.nodes.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The one-shot outcome shown in the app-level notice after a picker action. */
sealed interface PlaylistNotice {
    val playlistName: String

    /** Undo is offered for adds: the row can be taken straight back out. */
    val canUndo: Boolean get() = false

    data class Added(
        val playlistId: String,
        val trackId: String,
        override val playlistName: String,
    ) : PlaylistNotice {
        override val canUndo: Boolean get() = true
    }

    data class AlreadyExists(override val playlistName: String) : PlaylistNotice

    data object Failed : PlaylistNotice {
        override val playlistName: String get() = ""
    }
}

/**
 * Serves the add-to-playlist picker and the notice it leaves behind. The
 * notice outlives the dialog so its Undo action still works after dismissal.
 */
@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(
    private val library: LibraryRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = library.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<PlaylistNotice?>(null)
    val notice: StateFlow<PlaylistNotice?> = _notice.asStateFlow()

    /** Playlists already holding [trackId], so the picker can mark them. */
    fun containingPlaylists(trackId: String): Flow<Set<String>> =
        library.observePlaylistsContaining(trackId).map { rows ->
            rows.mapTo(HashSet()) { it.id }
        }

    fun add(track: Track, playlist: Playlist) {
        viewModelScope.launch {
            _notice.value = when (library.addToPlaylist(playlist.id, track)) {
                is AddToPlaylistResult.Added ->
                    PlaylistNotice.Added(playlist.id, track.id, playlist.title)

                is AddToPlaylistResult.AlreadyExists ->
                    PlaylistNotice.AlreadyExists(playlist.title)

                is AddToPlaylistResult.Error -> PlaylistNotice.Failed
            }
        }
    }

    fun createAndAdd(name: String, track: Track) {
        viewModelScope.launch {
            val playlist = runCatching { library.createPlaylistAndAdd(name.trim(), track) }
                .getOrNull()
            _notice.value = if (playlist != null) {
                PlaylistNotice.Added(playlist.id, track.id, playlist.title)
            } else {
                PlaylistNotice.Failed
            }
        }
    }

    /** Takes an added row back out, the undo behind "Added to X". */
    fun undoAdd(notice: PlaylistNotice.Added) {
        viewModelScope.launch {
            library.removeFromPlaylist(notice.playlistId, notice.trackId)
            _notice.value = null
        }
    }

    fun clearNotice() {
        _notice.value = null
    }
}
