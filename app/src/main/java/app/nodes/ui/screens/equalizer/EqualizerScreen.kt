/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.playback.EqualizerBand
import app.nodes.core.model.EqualizerPresets
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.screens.settings.SettingsDivider
import app.nodes.ui.screens.settings.SettingsGroup
import app.nodes.ui.screens.settings.SettingsScaffold
import app.nodes.ui.screens.settings.SettingsSwitchRow
import app.nodes.ui.screens.settings.SettingsValueRow
import app.nodes.ui.theme.GlassBorder
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialSurface
import app.nodes.ui.theme.SpartialWhite
import kotlin.math.roundToInt

/**
 * The equalizer screen: preset curves, per-band sliders derived from the
 * device's own equalizer, bass boost and virtualizer where the platform
 * supports them, and a way out to the system equalizer when that exists.
 *
 * Everything is disabled - with the reason spelled out - while a
 * YouTube-embedded track plays: the official player keeps its audio out of
 * the app's audio session, so no effect can reach it. That is a property of
 * the embedded player, not a bug; streamed and local tracks are affected
 * normally.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val embedded = playerState.embeddedVideoId != null
    val controlsEnabled = state.enabled && !embedded
    val context = LocalContext.current
    val systemIntent = viewModel.systemEqualizerIntent()

    SettingsScaffold(
        title = stringResource(R.string.eq_title),
        onBack = onBack,
    ) {
        item(key = "enable") {
            SettingsGroup(stringResource(R.string.eq_title)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.eq_enable),
                    subtitle = stringResource(R.string.eq_enable_summary),
                    checked = state.enabled,
                    onCheckedChange = { if (!embedded) viewModel.setEnabled(it) },
                )
                if (embedded) {
                    SettingsDivider()
                    Text(
                        text = stringResource(R.string.eq_embedded_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = SpartialGrey,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
        }

        item(key = "presets") {
            SettingsGroup(stringResource(R.string.eq_presets)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EqualizerPresets.PRESET_IDS.forEach { presetId ->
                        PresetChip(
                            label = presetLabel(presetId),
                            selected = state.presetId == presetId,
                            enabled = controlsEnabled,
                            onClick = { viewModel.setPreset(presetId) },
                        )
                    }
                }
            }
        }

        item(key = "bands") {
            SettingsGroup(stringResource(R.string.eq_bands)) {
                when {
                    state.effectsUnavailable -> ExplainerText(R.string.eq_unavailable)
                    state.bands.isEmpty() -> ExplainerText(R.string.eq_wait_session)
                    else -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.bands.forEach { band ->
                            BandSlider(
                                band = band,
                                levelDb = state.bandLevelsDb.getOrElse(band.index) { 0f },
                                enabled = controlsEnabled,
                                onDelta = { delta -> viewModel.adjustBandLevel(band.index, delta) },
                            )
                        }
                    }
                }
            }
        }

        if (state.bassBoostSupported || state.virtualizerSupported) {
            item(key = "effects") {
                SettingsGroup(stringResource(R.string.eq_effects)) {
                    if (state.bassBoostSupported) {
                        StrengthRow(
                            title = stringResource(R.string.eq_bass_boost),
                            strength = state.bassBoostStrength,
                            enabled = controlsEnabled,
                            onStrength = viewModel::setBassBoost,
                        )
                    }
                    if (state.bassBoostSupported && state.virtualizerSupported) {
                        SettingsDivider()
                    }
                    if (state.virtualizerSupported) {
                        StrengthRow(
                            title = stringResource(R.string.eq_virtualizer),
                            strength = state.virtualizerStrength,
                            enabled = controlsEnabled,
                            onStrength = viewModel::setVirtualizer,
                        )
                    }
                }
            }
        }

        item(key = "fallback") {
            SettingsGroup(stringResource(R.string.eq_advanced)) {
                SettingsValueRow(
                    title = stringResource(R.string.eq_reset),
                    onClick = { viewModel.reset() },
                )
                if (systemIntent != null) {
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.eq_open_system),
                        onClick = { runCatching { context.startActivity(systemIntent) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun presetLabel(presetId: String): String = stringResource(
    when (presetId) {
        EqualizerPresets.BASS_BOOST -> R.string.eq_preset_bass_boost
        EqualizerPresets.VOCAL -> R.string.eq_preset_vocal
        EqualizerPresets.TREBLE -> R.string.eq_preset_treble
        EqualizerPresets.ROCK -> R.string.eq_preset_rock
        EqualizerPresets.CLASSICAL -> R.string.eq_preset_classical
        EqualizerPresets.CUSTOM -> R.string.eq_preset_custom
        else -> R.string.eq_preset_flat
    },
)

@Composable
private fun ExplainerText(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.bodySmall,
        color = SpartialGrey,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) SpartialWhite else SpartialSurface)
            .border(1.dp, if (selected) SpartialWhite else GlassBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                selected -> SpartialBlack
                enabled -> SpartialWhite
                else -> SpartialWhite.copy(alpha = 0.5f)
            },
        )
    }
}

/**
 * One device band: a vertical slider between the platform's own dB limits.
 * Drags report deltas, so no gesture ever starts from a stale level, and the
 * whole 44dp column is the touch target for TalkBack and fingers alike.
 */
@Composable
private fun BandSlider(
    band: EqualizerBand,
    levelDb: Float,
    enabled: Boolean,
    onDelta: (Float) -> Unit,
) {
    val freqLabel = bandFrequencyLabel(band.centerFreqMilliHz)
    val talkback = stringResource(R.string.eq_band_talkback, freqLabel, levelDb)
    val range = band.maxLevelDb - band.minLevelDb
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(44.dp),
    ) {
        Text(
            text = stringResource(R.string.eq_band_value, levelDb),
            style = MaterialTheme.typography.labelSmall,
            color = SpartialGrey,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(150.dp)
                .semantics { contentDescription = talkback }
                .pointerInput(band.index, enabled) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (enabled && range > 0f) {
                            onDelta(-dragAmount / size.height * range)
                        }
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val trackWidth = 4.dp.toPx()
                val thumbRadius = 9.dp.toPx()
                val x = size.width / 2f
                val fraction = if (range > 0f) {
                    ((levelDb - band.minLevelDb) / range).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                val thumbY = thumbRadius + (size.height - 2 * thumbRadius) * (1f - fraction)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f),
                    topLeft = Offset(x - trackWidth / 2f, thumbRadius),
                    size = Size(trackWidth, size.height - 2 * thumbRadius),
                    cornerRadius = CornerRadius(trackWidth / 2f),
                )
                drawRoundRect(
                    color = SpartialWhite.copy(alpha = 0.55f),
                    topLeft = Offset(x - trackWidth / 2f, thumbY),
                    size = Size(trackWidth, size.height - thumbRadius - thumbY),
                    cornerRadius = CornerRadius(trackWidth / 2f),
                )
                drawCircle(
                    color = SpartialWhite,
                    radius = thumbRadius,
                    center = Offset(x, thumbY),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = freqLabel,
            style = MaterialTheme.typography.labelSmall,
            color = SpartialGrey,
        )
    }
}

@Composable
private fun StrengthRow(
    title: String,
    strength: Int,
    enabled: Boolean,
    onStrength: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = SpartialWhite,
            modifier = Modifier.width(96.dp),
        )
        Slider(
            value = strength / 1000f,
            onValueChange = { onStrength((it * 1000).roundToInt()) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.eq_strength_value, strength / 10),
            style = MaterialTheme.typography.labelSmall,
            color = SpartialGrey,
            modifier = Modifier.width(40.dp),
        )
    }
}

/** 60000 mHz → "60 Hz"; 14000000 mHz → "14.0 kHz". Formatted by the string
 *  resources themselves, so the locale stays observable to composition. */
@Composable
private fun bandFrequencyLabel(centerFreqMilliHz: Int): String {
    val hz = centerFreqMilliHz / 1000f
    return if (hz >= 1000f) {
        stringResource(R.string.eq_band_khz, hz / 1000f)
    } else {
        stringResource(R.string.eq_band_hz, hz)
    }
}
