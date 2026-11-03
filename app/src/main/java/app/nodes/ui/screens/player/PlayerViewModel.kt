/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Track
import app.nodes.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin command surface over [PlaybackController], so every screen can start
 * playback without knowing which engine ends up playing.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlaybackController,
    private val library: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<app.nodes.playback.PlayerUiState> = player.state

    private val _liked = MutableStateFlow(false)
    val liked: StateFlow<Boolean> = _liked.asStateFlow()

    fun play(track: Track, queue: List<Track> = listOf(track)) = player.playNow(track, queue)

    fun playNext(track: Track) = player.playNext(track)

    fun addToQueue(track: Track) = player.addToQueue(track)

    fun playQueueIndex(index: Int) = player.playQueueIndex(index)

    fun removeFromQueue(index: Int) = player.removeFromQueue(index)

    fun togglePlayPause() = player.togglePlayPause()

    fun cycleRepeat(): app.nodes.playback.RepeatMode = player.cycleRepeat()

    fun toggleShuffle() = player.toggleShuffle()

    fun toggleInfiniteQueue() = player.toggleInfiniteQueue()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun stopEmbedded() = player.stopEmbedded()

    fun clearMessage() = player.clearMessage()

    fun observeLiked(trackId: String): Flow<Boolean> = library.isLiked(trackId)

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            _liked.value = library.toggleLike(track)
        }
    }
}
