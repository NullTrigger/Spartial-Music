/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.EqualizerPresets
import app.nodes.core.model.EqualizerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** One band of the device's equalizer, straight from the platform effect. */
data class EqualizerBand(
    val index: Int,
    /** Center frequency in millihertz, as the platform reports it. */
    val centerFreqMilliHz: Int,
    val minLevelDb: Float,
    val maxLevelDb: Float,
)

/**
 * Everything the equalizer screen renders. Effects are only ever attached to
 * a live ExoPlayer audio session; until one exists the state still carries the
 * persisted levels, so the screen can show and edit them and they simply apply
 * when playback starts.
 */
data class EqualizerUiState(
    val enabled: Boolean = true,
    val presetId: String = EqualizerPresets.FLAT,
    val bands: List<EqualizerBand> = emptyList(),
    val bandLevelsDb: List<Float> = emptyList(),
    /** 0..1000; 0 means the effect is off. */
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val bassBoostSupported: Boolean = false,
    val virtualizerSupported: Boolean = false,
    /** Effects are attached to a live audio session right now. */
    val sessionAttached: Boolean = false,
    /** The platform refused the effects on this device. */
    val effectsUnavailable: Boolean = false,
)

/**
 * Owns the platform audio effects (`Equalizer`, `BassBoost`, `Virtualizer`)
 * and the persistence behind them. [PlaybackService] reports the player's
 * audio session through [onAudioSessionChanged]; everything here is wrapped in
 * `runCatching`, because audio effects are a courtesy on Android - some
 * devices and some routes (Bluetooth SCO, cast) reject them - and a refused
 * effect must degrade to "EQ unavailable" rather than crash a player.
 *
 * Audio-effect session intents are broadcast open/close around the session so
 * system-wide effects (and the system equalizer panel) know what is going on.
 */
@Singleton
class EqualizerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    // Serializes every attach/detach/mutate path; effect handles are not
    // thread-safe and the service and UI touch this from different threads.
    private val mutex = Mutex()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSessionId: Int? = null

    init {
        // Device bands are read once from the global mix - without enabling or
        // configuring anything - so the sliders exist before the first track
        // plays. The probe is released immediately; some devices refuse it and
        // bands are then discovered on the player session instead.
        scope.launch {
            val probed = probeBands()
            _state.updateIf { current ->
                current.copy(bands = if (current.bands.isEmpty()) probed else current.bands)
            }
        }
        scope.launch {
            settings.equalizerSettings.collect { stored ->
                _state.updateIf { current -> merge(current, stored) }
                mutex.withLock { applyToEffects() }
            }
        }
    }

    /**
     * Called by [PlaybackService] whenever the player's audio session changes
     * (`Player.Listener.onAudioSessionIdChanged`). Recreates the effects on
     * the new session and re-applies the persisted configuration.
     */
    fun onAudioSessionChanged(newSessionId: Int) {
        scope.launch {
            mutex.withLock {
                if (attachedSessionId == newSessionId && equalizer != null) return@withLock
                detachEffects(closeBroadcast = true)
                attachedSessionId = newSessionId
                withContext(Dispatchers.Default) {
                    equalizer = runCatching { Equalizer(EFFECT_PRIORITY, newSessionId) }.getOrNull()
                    bassBoost = runCatching { BassBoost(EFFECT_PRIORITY, newSessionId) }.getOrNull()
                    virtualizer = runCatching { Virtualizer(EFFECT_PRIORITY, newSessionId) }.getOrNull()
                }
                val eq = equalizer
                if (eq == null) {
                    _state.updateIf { it.copy(sessionAttached = false, effectsUnavailable = true) }
                    return@withLock
                }
                val sessionBands = runCatching { readBands(eq) }.getOrDefault(emptyList())
                _state.updateIf {
                    it.copy(
                        bands = if (sessionBands.isNotEmpty()) sessionBands else it.bands,
                        bassBoostSupported = runCatching { bassBoost?.strengthSupported == true }
                            .getOrDefault(false),
                        virtualizerSupported = runCatching { virtualizer?.strengthSupported == true }
                            .getOrDefault(false),
                        sessionAttached = true,
                        effectsUnavailable = false,
                    )
                }
                applyToEffects()
                broadcastSession(newSessionId, open = true)
            }
        }
    }

    /** Service teardown: drop the effects and tell the system the session is done. */
    fun release() {
        scope.launch {
            mutex.withLock {
                detachEffects(closeBroadcast = true)
                attachedSessionId = null
                _state.updateIf { it.copy(sessionAttached = false) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) = updateSettings { it.copy(enabled = enabled) }

    /** Applies a preset curve to whatever bands the device has. */
    fun setPreset(presetId: String) = updateSettings { stored ->
        val levels = if (presetId == EqualizerPresets.CUSTOM) {
            stored.bandLevelsDb
        } else {
            levelsForBands(presetId)
        }
        stored.copy(presetId = presetId, bandLevelsDb = levels)
    }

    /** One band moved by hand; the preset becomes Custom from here on. */
    fun setBandLevel(index: Int, levelDb: Float) = updateSettings { stored ->
        val bands = _state.value.bands
        if (index !in bands.indices) return@updateSettings stored
        val base = stored.bandLevelsDb.ifEmpty { levelsForBands(stored.presetId) }
        val levels = buildList {
            repeat(bands.size) { i -> add(base.getOrElse(i) { 0f }) }
        }.toMutableList()
        val band = bands[index]
        levels[index] = levelDb.coerceIn(band.minLevelDb, band.maxLevelDb)
        stored.copy(presetId = EqualizerPresets.CUSTOM, bandLevelsDb = levels)
    }

    fun setBassBoost(strength: Int) =
        updateSettings { it.copy(bassBoostStrength = strength.coerceIn(0, MAX_STRENGTH)) }

    fun setVirtualizer(strength: Int) =
        updateSettings { it.copy(virtualizerStrength = strength.coerceIn(0, MAX_STRENGTH)) }

    /** Flat preset, effects at zero. */
    fun reset() = updateSettings { EqualizerSettings() }

    /**
     * The system's own audio-effect panel, or null when the device ships
     * without one - the screen hides the entry rather than fail on tap.
     */
    fun systemEqualizerIntent(): Intent? {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, attachedSessionId ?: 0)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    private fun updateSettings(transform: (EqualizerSettings) -> EqualizerSettings) {
        scope.launch {
            val stored = settings.equalizerSettings.first()
            settings.setEqualizerSettings(transform(stored))
        }
    }

    /** Stored settings folded onto the live state: bands decide level count. */
    private fun merge(current: EqualizerUiState, stored: EqualizerSettings): EqualizerUiState {
        val levels = when {
            stored.bandLevelsDb.isNotEmpty() -> stored.bandLevelsDb
            current.bands.isNotEmpty() -> levelsForBands(stored.presetId)
            else -> emptyList()
        }
        return current.copy(
            enabled = stored.enabled,
            presetId = stored.presetId,
            bandLevelsDb = levels,
            bassBoostStrength = stored.bassBoostStrength,
            virtualizerStrength = stored.virtualizerStrength,
        )
    }

    private fun levelsForBands(presetId: String): List<Float> =
        EqualizerPresets.levelsFor(presetId, _state.value.bands.map { it.centerFreqMilliHz })

    private suspend fun probeBands(): List<EqualizerBand> = withContext(Dispatchers.Default) {
        runCatching {
            val probe = Equalizer(0, 0)
            try {
                readBands(probe)
            } finally {
                runCatching { probe.enabled = false }
                runCatching { probe.release() }
            }
        }.getOrDefault(emptyList())
    }

    private fun readBands(eq: Equalizer): List<EqualizerBand> {
        val range = eq.bandLevelRange
        val count = eq.numberOfBands.toInt()
        return List(count) { i ->
            EqualizerBand(
                index = i,
                centerFreqMilliHz = eq.getCenterFreq(i.toShort()),
                minLevelDb = range[0] / 100f,
                maxLevelDb = range[1] / 100f,
            )
        }
    }

    private fun applyToEffects() {
        val s = _state.value
        val eq = equalizer
        if (eq != null) {
            runCatching {
                eq.enabled = s.enabled
                s.bands.forEach { band ->
                    val db = s.bandLevelsDb.getOrElse(band.index) { 0f }
                        .coerceIn(band.minLevelDb, band.maxLevelDb)
                    eq.setBandLevel(band.index.toShort(), (db * 100f).roundToInt().toShort())
                }
            }
        }
        runCatching {
            bassBoost?.let { effect ->
                effect.enabled = s.bassBoostStrength > 0
                if (effect.strengthSupported) {
                    effect.setStrength(s.bassBoostStrength.toShort())
                }
            }
        }
        runCatching {
            virtualizer?.let { effect ->
                effect.enabled = s.virtualizerStrength > 0
                if (effect.strengthSupported) {
                    effect.setStrength(s.virtualizerStrength.toShort())
                }
            }
        }
    }

    private fun detachEffects(closeBroadcast: Boolean) {
        if (closeBroadcast) {
            attachedSessionId?.let { broadcastSession(it, open = false) }
        }
        runCatching { equalizer?.enabled = false }
        runCatching { bassBoost?.enabled = false }
        runCatching { virtualizer?.enabled = false }
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun broadcastSession(sessionId: Int, open: Boolean) {
        val action = if (open) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        } else {
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        }
        runCatching {
            context.sendBroadcast(
                Intent(action)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
            )
        }
    }

    private inline fun MutableStateFlow<EqualizerUiState>.updateIf(
        transform: (EqualizerUiState) -> EqualizerUiState,
    ) {
        while (true) {
            val current = value
            val next = transform(current)
            if (compareAndSet(current, next)) return
        }
    }

    private companion object {
        /** Effects run at normal priority; the system equalizer wins ties. */
        const val EFFECT_PRIORITY = 0
        /** The platform strength scale for BassBoost and Virtualizer. */
        const val MAX_STRENGTH = 1000
    }
}
