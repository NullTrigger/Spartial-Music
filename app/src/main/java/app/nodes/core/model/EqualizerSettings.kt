/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ln

/**
 * Equalizer state as stored on disk. One JSON blob in preferences, decoded
 * with [decodeEqualizerSettings]; unknown or corrupt payloads fall back to
 * defaults instead of taking the settings screen down.
 *
 * [bandLevelsDb] is in device band order and lives in decibels; it is empty
 * while the device's bands are still unknown, in which case the preset curve
 * fills them in once they are (see [EqualizerPresets]).
 */
@Serializable
data class EqualizerSettings(
    val enabled: Boolean = true,
    val presetId: String = EqualizerPresets.FLAT,
    val bandLevelsDb: List<Float> = emptyList(),
    /** 0..1000, the platform's own scale. 0 means the effect is off. */
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
)

private val EqualizerJson = Json { ignoreUnknownKeys = true }

fun EqualizerSettings.encode(): String =
    EqualizerJson.encodeToString(EqualizerSettings.serializer(), this)

fun decodeEqualizerSettings(raw: String?): EqualizerSettings =
    if (raw.isNullOrBlank()) {
        EqualizerSettings()
    } else {
        runCatching {
            EqualizerJson.decodeFromString(EqualizerSettings.serializer(), raw)
        }.getOrDefault(EqualizerSettings())
    }

/**
 * The preset curves behind the equalizer screen. Each preset is a set of
 * (frequency Hz, level dB) control points; [levelsFor] samples the curve at
 * whatever bands the device actually has, so the same preset works on a
 * 3-band phone and a 10-band tablet.
 *
 * [CUSTOM] has no curve of its own: it is what the preset becomes once the
 * user drags a band, and the dragged levels live in [EqualizerSettings].
 */
object EqualizerPresets {
    const val FLAT = "flat"
    const val BASS_BOOST = "bass_boost"
    const val VOCAL = "vocal"
    const val TREBLE = "treble"
    const val ROCK = "rock"
    const val CLASSICAL = "classical"
    const val CUSTOM = "custom"

    /** Offered as chips, in order. [CUSTOM] is never a chip. */
    val PRESET_IDS = listOf(FLAT, BASS_BOOST, VOCAL, TREBLE, ROCK, CLASSICAL)

    private val CURVES: Map<String, List<Pair<Double, Double>>> = mapOf(
        FLAT to listOf(1000.0 to 0.0),
        BASS_BOOST to listOf(
            60.0 to 6.0, 150.0 to 4.0, 400.0 to 0.5,
            1000.0 to 0.0, 3000.0 to -0.5, 10000.0 to -1.0,
        ),
        VOCAL to listOf(
            60.0 to -2.5, 150.0 to -1.0, 400.0 to 1.5,
            1000.0 to 3.5, 3000.0 to 2.5, 10000.0 to 0.5,
        ),
        TREBLE to listOf(
            60.0 to -1.0, 150.0 to -0.5, 400.0 to 0.0,
            1000.0 to 1.0, 3000.0 to 3.0, 10000.0 to 5.5,
        ),
        ROCK to listOf(
            60.0 to 4.5, 150.0 to 2.5, 400.0 to -1.0,
            1000.0 to -1.5, 3000.0 to 1.5, 10000.0 to 3.5,
        ),
        CLASSICAL to listOf(
            60.0 to 3.0, 150.0 to 1.5, 400.0 to 0.0,
            1000.0 to 0.5, 3000.0 to 1.5, 10000.0 to 2.5,
        ),
    )

    /**
     * One dB level per band, sampled from the preset's curve in log-frequency.
     * Bands outside the curve take the end values. [CUSTOM] falls back to the
     * flat curve here; its real levels are the ones the user stored.
     */
    fun levelsFor(presetId: String, bandFrequenciesMilliHz: List<Int>): List<Float> {
        val points = CURVES[presetId] ?: CURVES.getValue(FLAT)
        return bandFrequenciesMilliHz.map { milliHz -> interpolate(points, milliHz / 1000.0) }
    }

    private fun interpolate(points: List<Pair<Double, Double>>, freqHz: Double): Float {
        val first = points.first()
        val last = points.last()
        if (points.size == 1 || freqHz <= first.first) return first.second.toFloat()
        if (freqHz >= last.first) return last.second.toFloat()
        val hi = points.indexOfFirst { (x, _) -> x >= freqHz }
        val lo = hi - 1
        val (x0, y0) = points[lo]
        val (x1, y1) = points[hi]
        val t = (ln(freqHz) - ln(x0)) / (ln(x1) - ln(x0))
        return (y0 + (y1 - y0) * t).toFloat()
    }
}
