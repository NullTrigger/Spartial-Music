/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.LyricsRepository
import app.nodes.core.model.LyricLine
import app.nodes.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lyrics and "liked" state for the Now Playing screen.
 *
 * Playback commands live on [PlayerViewModel]; this one only loads the local
 * `.lrc` sidecar for the current track and mirrors the favourite flag.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val lyrics: LyricsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _lines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lines: StateFlow<List<LyricLine>> = _lines.asStateFlow()

    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()

    private var likedJob: Job? = null

    /** Called by the screen whenever the playing track changes. */
    fun onTrackChanged(track: Track?) {
        likedJob?.cancel()
        if (track == null) {
            _lines.value = emptyList()
            _liked.value = false
            return
        }
        likedJob = viewModelScope.launch {
            library.isLiked(track.id).collect { _liked.value = it }
        }
        viewModelScope.launch {
            _lines.value = lyrics.lyricsFor(track)
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            _liked.value = library.toggleLike(track)
        }
    }
}
