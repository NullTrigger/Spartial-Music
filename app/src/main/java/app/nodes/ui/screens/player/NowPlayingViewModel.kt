/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.crash.AppLogger
import app.nodes.core.data.LibraryRepository
import app.nodes.core.data.LyricsRepository
import app.nodes.core.data.LyricsResult
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Drives the screen-awake flag while Now Playing is open. */
    val keepScreenOn: StateFlow<Boolean> = settings.settings
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Whether lyrics follow the current line. The player can still pause
     * following for the rest of a song when the user scrolls by hand, but the
     * preference is the starting point for every track.
     */
    val lyricsAutoScroll: StateFlow<Boolean> = settings.settings
        .map { it.lyricsAutoScroll }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _lyrics = MutableStateFlow(LyricsResult())
    val lyrics: StateFlow<LyricsResult> = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

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
            _lyricsLoading.value = false
            _liked.value = false
            return
        }
        _lyrics.value = LyricsResult()
        _lyricsLoading.value = true
        likedJob = viewModelScope.launch {
            // A flow failure here used to reach the default uncaught-exception
            // handler and kill the process: the library is not worth that.
            try {
                library.isLiked(track.id).collect { _liked.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.w("Player", "Reading the liked flag failed", error)
                _liked.value = false
            }
        }
        lyricsJob = viewModelScope.launch {
            // A lyrics lookup is up to five sequential provider calls. Started
            // at the same instant as the tap, it competes with the stream
            // resolution for the same connection and is a large part of the
            // delay people describe as "the song takes time to load". The
            // spinner is raised immediately so opening Lyrics still looks
            // busy; only the network work waits.
            delay(LYRICS_START_DELAY_MS)
            try {
                _lyrics.value = resolveLyrics(track)
            } finally {
                _lyricsLoading.value = false
            }
        }
    }

    /**
     * Lyrics are resolved shortly after a track starts playing - after a short
     * pause so the stream gets the network first. Providers, the disk cache and
     * the parser all live outside this app's control, so a failure is answered
     * with "no lyrics" instead of an exception on the main-scoped coroutine -
     * which would close the app mid-playback.
     */
    private suspend fun resolveLyrics(track: Track, refresh: Boolean = false): LyricsResult =
        try {
            repository.lyricsFor(track, refresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // An ExceptionInInitializerError from the parser is an Error; see
            // LyricsRepository for the crash this guard is here to prevent.
            AppLogger.e("Lyrics", "Showing lyrics failed for ${track.id}", error)
            LyricsResult()
        }

    /** "Refresh lyrics": steps over every cache and asks the providers again. */
    fun refreshLyrics() {
        val track = current ?: return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _lyricsLoading.value = true
            try {
                _lyrics.value = resolveLyrics(track, refresh = true)
            } finally {
                _lyricsLoading.value = false
            }
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            _liked.value = library.toggleLike(track)
        }
    }

    private companion object {
        /** How long the stream is allowed to have the network to itself. */
        const val LYRICS_START_DELAY_MS = 900L
    }
}
