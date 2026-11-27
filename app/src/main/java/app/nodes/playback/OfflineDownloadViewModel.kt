/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import androidx.lifecycle.ViewModel
import app.nodes.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OfflineDownloadViewModel @Inject constructor(
    val downloads: OfflineDownloads,
) : ViewModel() {
    /** Fire-and-forget: the save keeps going after this screen is gone. */
    fun enqueue(track: Track) {
        downloads.enqueueInBackground(track)
    }

    fun remove(trackId: String) = downloads.remove(trackId)
}
