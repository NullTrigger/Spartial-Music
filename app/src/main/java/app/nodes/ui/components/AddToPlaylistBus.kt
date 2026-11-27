/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import app.nodes.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Add to playlist" is offered from every track surface - rows, the queue, the
 * player, the mini player - none of which can host the picker dialog. This
 * tiny bus is the contract, in the same spirit as [ScrollTopBus]: a surface
 * requests an add for one track, and the app shell owns the dialog and its
 * result notice.
 */
object AddToPlaylistBus {

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    fun request(track: Track) {
        _track.value = track
    }

    fun clear() {
        _track.value = null
    }
}
