/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.LyricsRepository
import app.nodes.core.data.LyricsResult
import app.nodes.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lyrics and "liked" state for the Now Playing screen.
 *
 * Playback commands live on [PlayerViewModel]; this one resolves lyrics through
 * [LyricsRepository] — local file first, then whichever providers the user
 * enabled — and mirrors the favourite flag.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: LyricsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _lyrics = MutableStateFlow(LyricsResult())
    val lyrics: StateFlow<LyricsResult> = _lyrics.asStateFlow()

    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()

    private var likedJob: Job? = null
    private var lyricsJob: Job? = null
    private var current: Track? = null

    /** Called by the screen whenever the playing track changes. */
    fun onTrackChanged(track: Track?) {
        current = track
        likedJob?.cancel()
        lyricsJob?.cancel()
        if (track == null) {
            _lyrics.value = LyricsResult()
            _liked.value = false
            return
        }
        likedJob = viewModelScope.launch {
            // A flow failure here used to reach the default uncaught-exception
            // handler and kill the process: the library is not worth that.
            try {
                library.isLiked(track.id).collect { _liked.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _liked.value = false
            }
        }
        lyricsJob = viewModelScope.launch {
            _lyrics.value = resolveLyrics(track)
        }
    }

    /**
     * Lyrics are looked up the moment a track starts playing. Providers, the
     * disk cache and the parser all live outside this app's control, so a
     * failure is answered with "no lyrics" instead of an exception on the
     * main-scoped coroutine - which would close the app mid-playback.
     */
    private suspend fun resolveLyrics(track: Track, refresh: Boolean = false): LyricsResult =
        try {
            repository.lyricsFor(track, refresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LyricsResult()
        }

    /** "Refresh lyrics": steps over every cache and asks the providers again. */
    fun refreshLyrics() {
        val track = current ?: return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _lyrics.value = resolveLyrics(track, refresh = true)
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            _liked.value = library.toggleLike(track)
        }
    }
}
