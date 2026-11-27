/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.equalizer

import android.content.Intent
import androidx.lifecycle.ViewModel
import app.nodes.playback.EqualizerController
import app.nodes.playback.EqualizerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin adapter over [EqualizerController]: the controller owns the effects
 * and the persistence, the screen only renders state and forwards edits.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: EqualizerController,
) : ViewModel() {

    val state: StateFlow<EqualizerUiState> = controller.state

    fun setEnabled(enabled: Boolean) = controller.setEnabled(enabled)

    fun setPreset(presetId: String) = controller.setPreset(presetId)

    /** Moves one band by [deltaDb] relative to wherever it currently is. */
    fun adjustBandLevel(index: Int, deltaDb: Float) {
        val current = state.value.bandLevelsDb.getOrElse(index) { 0f }
        controller.setBandLevel(index, current + deltaDb)
    }

    fun setBassBoost(strength: Int) = controller.setBassBoost(strength)

    fun setVirtualizer(strength: Int) = controller.setVirtualizer(strength)

    fun reset() = controller.reset()

    /** Null when the device ships without a system audio-effect panel. */
    fun systemEqualizerIntent(): Intent? = controller.systemEqualizerIntent()
}
