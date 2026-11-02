/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nodes.BuildConfig
import app.nodes.R

/**
 * Version, licence and the two policies that come with using YouTube as a
 * source. Everything here is a link out; nothing is uploaded.
 */
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(R.string.settings_about_title),
        onBack = onBack,
    ) {
        item(key = "version") {
            SettingsGroup(stringResource(R.string.settings_version_label)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    subtitle = stringResource(R.string.settings_source_of_truth),
                    icon = Icons.Rounded.Info,
                )
                SettingsDivider()
                SettingsNote(stringResource(R.string.disclaimer_short))
                SettingsDivider()
                SettingsNote(stringResource(R.string.app_tagline))
            }
        }

        item(key = "legal") {
            SettingsGroup(stringResource(R.string.settings_legal)) {
                SettingsValueRow(
                    title = stringResource(R.string.settings_terms),
                    icon = Icons.Rounded.Description,
                    onClick = { openUrl(context, "https://www.youtube.com/t/terms") },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_google_privacy),
                    icon = Icons.Rounded.PrivacyTip,
                    onClick = { openUrl(context, "https://policies.google.com/privacy") },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_licenses),
                    onClick = { openUrl(context, "https://github.com/nodes-app/nodes/blob/main/NOTICE.md") },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_privacy),
                    onClick = { openUrl(context, "https://github.com/nodes-app/nodes/blob/main/PRIVACY.md") },
                )
            }
        }
    }
}
