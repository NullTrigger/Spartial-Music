/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R

/**
 * The route slot in the Now Playing secondary controls, FOSS flavour.
 *
 * Casting needs the Play services Cast framework, which this flavour does not
 * bundle by design. Instead of a dead Cast glyph, this button opens an audio
 * output panel: which output the phone is using (speaker, wired or Bluetooth)
 * and a bar that controls this phone's media volume. Nothing leaves the
 * device; the panel only reads the local [AudioManager].
 *
 * Signature matches the gms `CastRouteButton` exactly; the Cast-specific
 * callbacks are unused here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
    onUnavailableTap: () -> Unit = {},
    onChooserFailure: () -> Unit = {},
    tint: Color = Color.Unspecified,
) {
    var showPanel by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(52.dp)
            .clickable(onClick = { showPanel = true }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Airplay,
            contentDescription = stringResource(R.string.player_output_route),
            tint = tint,
            modifier = Modifier.size(34.dp),
        )
    }
    if (showPanel) {
        AudioOutputPanel(onDismiss = { showPanel = false })
    }
}

/**
 * A bottom sheet with the connected output and a media-volume bar, in the
 * reference player's style: device row on top, one bar underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioOutputPanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember {
        (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)
    }
    var volume by remember {
        mutableFloatStateOf(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat(),
        )
    }
    val output = remember { connectedOutput(context) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.player_output_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(
                    imageVector = output.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = output.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.player_output_volume),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Slider(
                    value = volume,
                    onValueChange = { new ->
                        volume = new
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            new.toInt(),
                            0,
                        )
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/** One output row: icon and user-facing label. */
private data class OutputDevice(val icon: ImageVector, val label: String)

/**
 * The output the phone is using: Bluetooth when a Bluetooth audio device is
 * connected, then wired headphones, otherwise the phone speaker. Only reads
 * which devices are connected - no device names leave the panel.
 */
private fun connectedOutput(context: Context): OutputDevice {
    val audioManager = context.getSystemService(AudioManager::class.java)
    val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
    val bluetooth = outputs.any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
    val wired = outputs.any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }
    return when {
        bluetooth -> OutputDevice(
            icon = Icons.Rounded.Bluetooth,
            label = context.getString(R.string.player_output_device_bluetooth),
        )
        wired -> OutputDevice(
            icon = Icons.Rounded.Headphones,
            label = context.getString(R.string.player_output_device_wired),
        )
        else -> OutputDevice(
            icon = Icons.Rounded.Speaker,
            label = context.getString(R.string.player_output_device_speaker),
        )
    }
}
