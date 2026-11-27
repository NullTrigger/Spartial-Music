/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R

/**
 * Appearance. Spartial Music ships dark only: the glass surfaces and the artwork
 * backdrop are designed against a near-black base, so a light palette is a
 * separate piece of work rather than a switch.
 */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()

    SettingsScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
    ) {
        item(key = "theme") {
            SettingsGroup(stringResource(R.string.settings_theme)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_theme_dark),
                    subtitle = stringResource(R.string.settings_dark_only),
                    icon = Icons.Rounded.DarkMode,
                )
            }
        }

        item(key = "covers") {
            SettingsGroup(stringResource(R.string.settings_animated_covers_group)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_animated_covers),
                    subtitle = stringResource(R.string.settings_animated_covers_hint),
                    icon = Icons.Rounded.MovieFilter,
                    checked = settings.animatedCovers,
                    onCheckedChange = viewModel::setAnimatedCovers,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_animated_covers_mobile),
                    subtitle = stringResource(R.string.settings_animated_covers_mobile_hint),
                    icon = Icons.Rounded.Wifi,
                    checked = settings.animatedCoversOnMobile,
                    onCheckedChange = viewModel::setAnimatedCoversOnMobile,
                )
                SettingsDivider()
                SettingsNote(stringResource(R.string.settings_animated_covers_note))
            }
        }
    }
}
