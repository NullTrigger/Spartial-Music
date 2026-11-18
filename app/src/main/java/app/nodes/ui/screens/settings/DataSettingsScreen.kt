/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
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
 * What is stored on the device and how to get rid of it. Crash reports are
 * written locally and never uploaded, which is why the switch sits next to the
 * cache rather than under "About".
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val searches by viewModel.recentSearchCount.collectAsStateWithLifecycle()

    // Export writes one versioned JSON file; import merges it. Neither needs a
    // storage permission - the picker hands over a scoped URI.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportLibrary(uri, includeHistory = true)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importLibrary(uri)
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_group_data),
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

        item(key = "cache") {
            SettingsGroup(stringResource(R.string.settings_storage)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = stringResource(R.string.settings_storage_hint),
                    icon = Icons.Rounded.Storage,
                    onClick = viewModel::clearCache,
                )
            }
        }

        item(key = "backup") {
            SettingsGroup(stringResource(R.string.settings_backup_group)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_export_library),
                    subtitle = stringResource(R.string.settings_export_library_hint),
                    icon = Icons.Rounded.FileUpload,
                    onClick = { exportLauncher.launch("spartial-library.json") },
                )
                SettingsDivider()
                SettingsActionRow(
                    title = stringResource(R.string.settings_import_library),
                    subtitle = stringResource(R.string.settings_import_library_hint),
                    icon = Icons.Rounded.FileDownload,
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }
        }

        item(key = "history") {
            SettingsGroup(stringResource(R.string.settings_search_history)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_search_history_clear),
                    subtitle = if (searches > 0) {
                        stringResource(R.string.settings_search_history_count, searches)
                    } else {
                        stringResource(R.string.settings_search_history_empty)
                    },
                    icon = Icons.Rounded.History,
                    onClick = viewModel::clearSearchHistory,
                )
            }
        }

        item(key = "crash") {
            SettingsGroup(stringResource(R.string.settings_crash_reports)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_crash_reports_toggle),
                    subtitle = stringResource(R.string.settings_crash_reports_short),
                    icon = Icons.Rounded.Shield,
                    checked = settings.crashReportsEnabled,
                    onCheckedChange = viewModel::setCrashReportsEnabled,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_crash_reports_open),
                    subtitle = stringResource(R.string.settings_crash_reports_subtitle),
                    onClick = onOpenDiagnostics,
                )
            }
        }
    }
}
