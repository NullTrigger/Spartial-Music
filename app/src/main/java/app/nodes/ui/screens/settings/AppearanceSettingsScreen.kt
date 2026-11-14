/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
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
    val dynamicHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        stringResource(R.string.settings_dynamic_color_hint)
    } else {
        stringResource(R.string.settings_dynamic_color_unsupported)
    }

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

        item(key = "colour") {
            SettingsGroup(stringResource(R.string.settings_colour)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = dynamicHint,
                    icon = Icons.Rounded.Palette,
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsDivider()
                SettingsNote(stringResource(R.string.settings_dynamic_color_note))
            }
        }
    }
}
