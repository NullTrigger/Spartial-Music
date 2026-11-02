/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.BuildConfig
import app.nodes.R
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * Settings root.
 *
 * One card per topic, each row carrying its current value, and a sub-screen for
 * anything that needs more than a single switch. The account card answers
 * "what is signed in and what is serving my audio" before the user has to open
 * anything.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPlayback: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val musicAccount by viewModel.musicAccount.collectAsStateWithLifecycle()

    val anonymous = stringResource(R.string.settings_status_signed_out)
    val anonymousHint = stringResource(R.string.settings_status_signed_out_hint)
    val signedInHint = stringResource(R.string.settings_status_signed_in_hint)
    val dynamicHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        stringResource(R.string.settings_dynamic_color_hint)
    } else {
        stringResource(R.string.settings_dynamic_color_unsupported)
    }
    val lyricsSummary = stringResource(
        R.string.settings_lyrics_summary,
        providerLabel(settings.lyricsProviderOrder.firstOrNull { it !in settings.lyricsDisabledProviders }),
        settings.lyricsProviderOrder.count { it !in settings.lyricsDisabledProviders },
        settings.lyricsProviderOrder.size,
    )

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) {
        item(key = "account") {
            val account = musicAccount
            if (account == null) {
                SettingsAccountCard(
                    initial = "N",
                    title = anonymous,
                    subtitle = anonymousHint,
                    onClick = onOpenLogin,
                )
            } else {
                SettingsAccountCard(
                    initial = (account.name ?: account.email ?: "N").take(1).uppercase(),
                    title = account.email ?: account.name ?: anonymous,
                    subtitle = signedInHint,
                    onClick = onOpenContent,
                )
            }
        }

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

        item(key = "playback") {
            SettingsGroup(stringResource(R.string.settings_group_playback)) {
                SettingsValueRow(
                    title = stringResource(R.string.settings_group_playback),
                    subtitle = stringResource(R.string.settings_group_playback_hint),
                    value = stringResource(
                        if (settings.highQualityAudio) R.string.settings_quality_high else R.string.settings_quality_low,
                    ),
                    icon = Icons.Rounded.Headphones,
                    onClick = onOpenPlayback,
                )
            }
        }

        item(key = "appearance") {
            SettingsGroup(stringResource(R.string.settings_appearance)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = dynamicHint,
                    icon = Icons.Rounded.Palette,
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(R.string.settings_dark_only),
                    value = stringResource(R.string.settings_theme_dark),
                    icon = Icons.Rounded.DarkMode,
                    onClick = onOpenAppearance,
                )
            }
        }

        item(key = "content") {
            SettingsGroup(stringResource(R.string.settings_group_content)) {
                SettingsValueRow(
                    title = stringResource(R.string.settings_source_title),
                    subtitle = stringResource(R.string.settings_source_hint),
                    value = stringResource(R.string.settings_source_innertube),
                    icon = Icons.Rounded.Public,
                    onClick = onOpenContent,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_youtube_region),
                    value = regionLabel(settings.youtubeRegion),
                    onClick = onOpenContent,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_lyrics_title),
                    subtitle = lyricsSummary,
                    icon = Icons.Rounded.Subtitles,
                    onClick = onOpenLyrics,
                )
            }
        }

        item(key = "data") {
            SettingsGroup(stringResource(R.string.settings_group_data)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_crash_reports),
                    subtitle = stringResource(R.string.settings_crash_reports_short),
                    icon = Icons.Rounded.Shield,
                    checked = settings.crashReportsEnabled,
                    onCheckedChange = viewModel::setCrashReportsEnabled,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_storage),
                    subtitle = stringResource(R.string.settings_storage_hint),
                    icon = Icons.Rounded.Storage,
                    onClick = onOpenData,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = stringResource(R.string.settings_crash_reports_subtitle),
                    onClick = onOpenDiagnostics,
                )
            }
        }

        item(key = "about") {
            SettingsGroup(stringResource(R.string.settings_about)) {
                SettingsValueRow(
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_source_of_truth),
                    icon = Icons.Rounded.Info,
                    onClick = onOpenAbout,
                )
            }
        }

        item(key = "footer") {
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGreyDim,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp),
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGrey.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp),
            )
            Text(
                text = stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.labelSmall,
                color = NodesGreyDim,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
            )
        }
    }
}
