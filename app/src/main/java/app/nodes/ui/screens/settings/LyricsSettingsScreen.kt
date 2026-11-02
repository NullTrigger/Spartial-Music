/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.lyrics.LyricsProviders
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesWhite

/**
 * Lyrics settings.
 *
 * The repository has always read a provider order, a disabled set and two
 * display switches; none of it was reachable. This screen edits exactly those
 * preferences, and local files stay pinned first because a file the user placed
 * next to a track is the most authoritative source there is.
 */
@Composable
fun LyricsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val movable = settings.lyricsProviderOrder.filter { it != LyricsProviders.LOCAL }
    val known = movable.filter { id -> LyricsSourceRows.any { it.first == id } }

    SettingsScaffold(
        title = stringResource(R.string.settings_lyrics_title),
        onBack = onBack,
    ) {
        item(key = "sources") {
            SettingsGroup(stringResource(R.string.settings_lyrics_sources)) {
                SettingsInfoRow(
                    title = stringResource(R.string.lyrics_provider_local),
                    subtitle = stringResource(R.string.settings_lyrics_local_hint),
                    value = stringResource(R.string.settings_lyrics_local_value),
                )
                known.forEachIndexed { index, id ->
                    SettingsDivider()
                    LyricsProviderRow(
                        name = providerLabel(id),
                        enabled = id !in settings.lyricsDisabledProviders,
                        canMoveUp = index > 0,
                        canMoveDown = index < known.lastIndex,
                        onToggle = { enabled -> viewModel.setLyricsProviderEnabled(id, enabled) },
                        onMoveUp = { viewModel.moveLyricsProvider(id, -1) },
                        onMoveDown = { viewModel.moveLyricsProvider(id, 1) },
                    )
                }
            }
        }

        item(key = "display") {
            SettingsGroup(stringResource(R.string.settings_lyrics_display)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_lyrics_auto_scroll),
                    subtitle = stringResource(R.string.settings_lyrics_auto_scroll_hint),
                    icon = Icons.Rounded.Sync,
                    checked = settings.lyricsAutoScroll,
                    onCheckedChange = viewModel::setLyricsAutoScroll,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_lyrics_prefer_synced),
                    subtitle = stringResource(R.string.settings_lyrics_prefer_synced_hint),
                    checked = settings.lyricsPreferSynced,
                    onCheckedChange = viewModel::setLyricsPreferSynced,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_lyrics_show_source),
                    subtitle = stringResource(R.string.settings_lyrics_show_source_hint),
                    checked = settings.lyricsShowSource,
                    onCheckedChange = viewModel::setLyricsShowSource,
                )
            }
        }

        item(key = "note") {
            SettingsGroup(stringResource(R.string.settings_lyrics_about)) {
                SettingsNote(stringResource(R.string.settings_lyrics_about_note))
            }
        }
    }
}

@Composable
private fun LyricsProviderRow(
    name: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) NodesWhite else NodesGrey,
            )
            Text(
                text = stringResource(
                    if (enabled) R.string.settings_lyrics_source_on else R.string.settings_lyrics_source_off,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NodesGreyDim,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.settings_lyrics_move_up),
                tint = if (canMoveUp) NodesGrey else NodesGreyDim.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.settings_lyrics_move_down),
                tint = if (canMoveDown) NodesGrey else NodesGreyDim.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
        SettingsSwitch(
            checked = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
