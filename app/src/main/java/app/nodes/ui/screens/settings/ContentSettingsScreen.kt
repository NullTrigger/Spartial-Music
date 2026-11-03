/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.components.MessageBanner

/**
 * Where music comes from: the source in use, the trending region the official
 * YouTube client needs, and the YouTube Music account behind the shelves.
 */
@Composable
fun ContentSettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val account by viewModel.musicAccount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val syncing by viewModel.likesSyncRunning.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.settings_group_content),
        onBack = onBack,
    ) {
        val banner = message
        if (banner != null) {
            item(key = "message") {
                MessageBanner(
                    message = banner,
                    onDismiss = viewModel::dismissMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        item(key = "source") {
            SettingsGroup(stringResource(R.string.settings_source_title)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_source_innertube),
                    subtitle = stringResource(R.string.settings_source_hint),
                    value = stringResource(R.string.settings_source_unofficial),
                )
                SettingsDivider()
                SettingsNote(stringResource(R.string.settings_ytmusic_warning))
            }
        }

        item(key = "region") {
            SettingsGroup(stringResource(R.string.settings_youtube_region)) {
                YoutubeRegions.forEachIndexed { index, region ->
                    if (index > 0) SettingsDivider()
                    SettingsCheckRow(
                        label = stringResource(region.second),
                        selected = region.first == settings.youtubeRegion,
                        onClick = { viewModel.setYoutubeRegion(region.first) },
                    )
                }
            }
        }

        item(key = "sync") {
            SettingsGroup(stringResource(R.string.settings_sync_group)) {
                SettingsActionRow(
                    title = stringResource(
                        if (syncing) R.string.settings_sync_running else R.string.settings_sync_title,
                    ),
                    subtitle = stringResource(R.string.settings_sync_subtitle),
                    onClick = viewModel::syncLikesToYouTube,
                )
                SettingsDivider()
                SettingsNote(stringResource(R.string.settings_sync_note))
            }
        }

        item(key = "account") {
            SettingsGroup(stringResource(R.string.settings_ytmusic_account)) {
                val signedIn = account
                if (signedIn == null) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_ytmusic_signin),
                        subtitle = stringResource(R.string.settings_ytmusic_account_hint),
                        onClick = onOpenLogin,
                    )
                } else {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_ytmusic_signout),
                        subtitle = stringResource(
                            R.string.settings_ytmusic_signed_in_as,
                            signedIn.email ?: signedIn.name ?: "",
                        ),
                        onClick = viewModel::signOutMusic,
                    )
                }
            }
        }
    }
}
