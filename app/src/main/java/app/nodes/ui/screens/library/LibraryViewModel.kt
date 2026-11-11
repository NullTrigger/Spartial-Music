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

/** An artist derived from the cached song table; no extra provider call. */
data class LibraryArtist(
    val name: String,
    val artworkUri: String?,
    val trackCount: Int,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val likedIds: Set<String> = emptySet(),
    val allTracks: List<Track> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val artists: List<LibraryArtist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = playlists.isEmpty() &&
            likedTracks.isEmpty() &&
            allTracks.isEmpty() &&
            recentlyPlayed.isEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val youTubeMusic: YouTubeMusicSource,
    auth: InnerTubeAuth,
) : ViewModel() {

    private val remotePlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    private val remoteLiked = MutableStateFlow<List<Track>>(emptyList())

    /**
     * One snapshot of everything the Library screen shows. The remote lists are
     * best effort: when signed out, or when a fetch fails, the local library
     * stays on screen instead of emptying it.
     */
    val state: StateFlow<LibraryUiState> = combine(
        combine(library.playlists, remotePlaylists) { local, remote -> local + remote },
        combine(library.likedTracks, remoteLiked) { local, remote ->
            (local + remote).distinctBy { it.id }
        },
        library.recentlyPlayed,
        library.allTracks,
    ) { playlists, liked, recent, all ->
        LibraryUiState(
            loading = false,
            playlists = playlists,
            likedTracks = liked,
            likedIds = liked.mapTo(HashSet()) { it.id },
            allTracks = all,
            recentlyPlayed = recent,
            artists = artistGroups(all),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    init {
        viewModelScope.launch {
            auth.account.collect { account ->
                if (account == null) {
                    remotePlaylists.value = emptyList()
                    remoteLiked.value = emptyList()
                } else {
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

private fun artistGroups(tracks: List<Track>): List<LibraryArtist> =
    tracks
        .filter { it.artist.isNotBlank() }
        .groupBy { it.artist }
        .map { (name, rows) ->
            LibraryArtist(
                name = name,
                artworkUri = rows.firstNotNullOfOrNull { it.artworkUri },
                trackCount = rows.size,
            )
        }
        .sortedByDescending { it.trackCount }
