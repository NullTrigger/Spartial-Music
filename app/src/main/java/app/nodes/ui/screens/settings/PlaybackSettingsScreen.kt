/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import kotlinx.coroutines.delay

/** Speed steps offered in the picker. */
private val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SleepOptions = listOf(0, 15, 30, 45, 60)

/**
 * Playback settings.
 *
 * Speed and skip-silence are applied by [app.nodes.playback.PlaybackService]
 * because a MediaController cannot set them, so they survive the session and
 * apply to the next track too. The sleep timer is an absolute deadline owned by
 * the playback controller, which is why it keeps counting while the app is in
 * the background. "Stop when cleared from recents" is enforced by the service
 * as well, since only it receives `onTaskRemoved`.
 */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val sleepEndsAt by viewModel.sleepTimerEndsAt.collectAsStateWithLifecycle()

    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSleepPicker by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(sleepEndsAt) {
        while (sleepEndsAt > 0L) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val speedText = stringResource(R.string.settings_speed_value, speedLabel(settings.playbackSpeed))
    val sleepText = if (sleepEndsAt <= 0L) {
        stringResource(R.string.settings_sleep_timer_off)
    } else {
        val minutes = ((sleepEndsAt - now).coerceAtLeast(0L) / 60_000L).toInt() + 1
        stringResource(R.string.settings_sleep_timer_remaining, minutes)
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_group_playback),
        onBack = onBack,
    ) {
        item(key = "audio") {
            SettingsGroup(stringResource(R.string.settings_audio)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_audio_quality),
                    subtitle = stringResource(R.string.settings_audio_quality_hint),
                    icon = Icons.Rounded.Headphones,
                    checked = settings.highQualityAudio,
                    onCheckedChange = viewModel::setHighQuality,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_hint),
                    checked = settings.wifiOnlyStreaming,
                    onCheckedChange = viewModel::setWifiOnly,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_skip_silence),
                    subtitle = stringResource(R.string.settings_skip_silence_hint),
                    icon = Icons.Rounded.SpaceBar,
                    checked = settings.skipSilence,
                    onCheckedChange = viewModel::setSkipSilence,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_playback_speed),
                    subtitle = stringResource(R.string.settings_playback_speed_hint),
                    value = speedText,
                    icon = Icons.Rounded.Speed,
                    onClick = { showSpeedPicker = true },
                )
            }
        }

        item(key = "timer") {
            SettingsGroup(stringResource(R.string.settings_sleep_timer)) {
                SettingsValueRow(
                    title = stringResource(R.string.settings_sleep_timer),
                    subtitle = stringResource(R.string.settings_sleep_timer_hint),
                    value = sleepText,
                    icon = Icons.Rounded.Timer,
                    onClick = { showSleepPicker = true },
                )
            }
        }

        item(key = "queue") {
            SettingsGroup(stringResource(R.string.settings_group_queue)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_queue_prevent_duplicates),
                    subtitle = stringResource(R.string.settings_queue_prevent_duplicates_hint),
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    checked = settings.queuePreventDuplicates,
                    onCheckedChange = viewModel::setQueuePreventDuplicates,
                )
            }
        }

        item(key = "background") {
            SettingsGroup(stringResource(R.string.settings_group_background)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_stop_music_on_task_clear),
                    subtitle = stringResource(R.string.settings_stop_music_on_task_clear_hint),
                    icon = Icons.Rounded.ClearAll,
                    checked = settings.stopMusicOnTaskClear,
                    onCheckedChange = viewModel::setStopMusicOnTaskClear,
                )
            }
        }

        item(key = "screen") {
            SettingsGroup(stringResource(R.string.settings_group_screen)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    subtitle = stringResource(R.string.settings_keep_screen_on_hint),
                    icon = Icons.Rounded.ScreenLockPortrait,
                    checked = settings.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }
        }
    }

    if (showSpeedPicker) {
        AlertDialog(
            onDismissRequest = { showSpeedPicker = false },
            title = { Text(stringResource(R.string.settings_playback_speed)) },
            text = {
                Column(Modifier.padding(top = 4.dp)) {
                    SpeedOptions.forEach { option ->
                        SettingsCheckRow(
                            label = stringResource(R.string.settings_speed_value, speedLabel(option)),
                            selected = option == settings.playbackSpeed,
                            onClick = {
                                viewModel.setPlaybackSpeed(option)
                                showSpeedPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedPicker = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }

    if (showSleepPicker) {
        AlertDialog(
            onDismissRequest = { showSleepPicker = false },
            title = { Text(stringResource(R.string.settings_sleep_timer)) },
            text = {
                Column(Modifier.padding(top = 4.dp)) {
                    SleepOptions.forEach { minutes ->
                        val label = if (minutes == 0) {
                            stringResource(R.string.settings_sleep_timer_off)
                        } else {
                            stringResource(R.string.settings_sleep_timer_minutes, minutes)
                        }
                        SettingsCheckRow(
                            label = label,
                            selected = minutes == 0 && sleepEndsAt <= 0L,
                            onClick = {
                                viewModel.setSleepTimer(minutes)
                                showSleepPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepPicker = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }
}

/** Compact speed label: 1.0 rather than 1.0f, 0.75 stays 0.75. */
private fun speedLabel(speed: Float): String = when (speed) {
    0.75f -> "0.75"
    1f -> "1.0"
    1.25f -> "1.25"
    1.5f -> "1.5"
    2f -> "2.0"
    else -> speed.toString()
}
