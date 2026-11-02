/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R

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

    SettingsScaffold(
        title = stringResource(R.string.settings_group_content),
        onBack = onBack,
    ) {
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
