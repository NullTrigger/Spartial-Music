/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Playlist
import app.nodes.core.model.Track
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val youTubeMusic: YouTubeMusicSource,
    auth: InnerTubeAuth,
) : ViewModel() {

    private val remotePlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    private val remoteLiked = MutableStateFlow<List<Track>>(emptyList())

    /** Local playlists plus, when signed in, the account's YouTube Music ones. */
    val playlists: StateFlow<List<Playlist>> =
        combine(library.playlists, remotePlaylists) { local, remote -> local + remote }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Locally liked tracks plus, when signed in, the account's liked songs. */
    val likedTracks: StateFlow<List<Track>> =
        combine(library.likedTracks, remoteLiked) { local, remote ->
            (local + remote).distinctBy { it.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            auth.account.collect { account ->
                if (account == null) {
                    remotePlaylists.value = emptyList()
                    remoteLiked.value = emptyList()
                } else {
                    // Both are best effort: a failed fetch leaves the local
                    // library on screen instead of emptying it.
                    remotePlaylists.value = youTubeMusic.userPlaylists()
                    remoteLiked.value = youTubeMusic.likedSongs()
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.createPlaylist(name.trim()) }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.toggleLike(track) }
    }
}
