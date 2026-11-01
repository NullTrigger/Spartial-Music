/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.BuildConfig
import app.nodes.R
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.PageHeader
import app.nodes.ui.screens.crash.CrashActivity
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val musicAccount by viewModel.musicAccount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.settings_title),
            back = onBack,
        )

        message?.let { text ->
            MessageBanner(message = text, onDismiss = viewModel::dismissMessage)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "appearance") {
                SettingsSection(stringResource(R.string.settings_appearance))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dark_only),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            item(key = "audio") {
                SettingsSection(stringResource(R.string.settings_audio))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = null,
                    checked = settings.wifiOnlyStreaming,
                    onCheckedChange = viewModel::setWifiOnly,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_audio_quality),
                    subtitle = null,
                    checked = settings.highQualityAudio,
                    onCheckedChange = viewModel::setHighQuality,
                )
            }

            item(key = "ytmusic-account") {
                SettingsSection(stringResource(R.string.settings_ytmusic_account))
                Text(
                    text = stringResource(R.string.settings_ytmusic_account_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                val account = musicAccount
                if (account != null) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_ytmusic_signout),
                        subtitle = stringResource(
                            R.string.settings_ytmusic_signed_in_as,
                            account.email ?: account.name,
                        ),
                        onClick = viewModel::signOutMusic,
                    )
                } else {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_ytmusic_signin),
                        subtitle = null,
                        onClick = onOpenLogin,
                    )
                }
            }

            item(key = "storage") {
                SettingsSection(stringResource(R.string.settings_storage))
                SettingsActionRow(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = null,
                    onClick = viewModel::clearCache,
                )
            }

            item(key = "support") {
                SettingsSection(stringResource(R.string.settings_support))
                SettingsActionRow(
                    title = stringResource(R.string.settings_crash_reports),
                    subtitle = stringResource(R.string.settings_crash_reports_subtitle),
                    onClick = {
                        context.startActivity(Intent(context, CrashActivity::class.java))
                    },
                )
            }

            item(key = "about") {
                SettingsSection(stringResource(R.string.settings_about))
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.settings_source_of_truth),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_terms),
                    onClick = { openUrl(context, "https://www.youtube.com/t/terms") },
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_google_privacy),
                    onClick = { openUrl(context, "https://policies.google.com/privacy") },
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_licenses),
                    onClick = { openUrl(context, "https://github.com/nodes-app/nodes/blob/main/NOTICE.md") },
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_privacy),
                    onClick = { openUrl(context, "https://github.com/nodes-app/nodes/blob/main/PRIVACY.md") },
                )
                Text(
                    text = stringResource(R.string.disclaimer_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGrey.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGrey.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = NodesGrey,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NodesWhite,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NodesBlack,
                checkedTrackColor = NodesTeal,
                uncheckedThumbColor = NodesGrey,
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
            ),
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NodesWhite,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                )
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = NodesWhite.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "↗",
            style = MaterialTheme.typography.bodyMedium,
            color = NodesGrey,
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        if (it !is ActivityNotFoundException) throw it
    }
}
