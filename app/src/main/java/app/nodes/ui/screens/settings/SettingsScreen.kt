/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.BuildConfig
import app.nodes.R
import app.nodes.core.model.SourceAvailability
import app.nodes.core.model.SourceId
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.glass
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val quotaRemaining by viewModel.quotaRemaining.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val signInState by viewModel.signInState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.completeSignIn(result.data)
    }

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

            item(key = "sources") {
                SettingsSection(stringResource(R.string.settings_sources))
                Text(
                    text = stringResource(R.string.settings_sources_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                availability.forEach { source ->
                    SourceRow(
                        availability = source,
                        selected = settings.primarySource == source.sourceId,
                        onClick = { viewModel.setPrimarySource(source.sourceId) },
                    )
                }
                if (quotaRemaining < 200) {
                    Text(
                        text = "YouTube quota left today: $quotaRemaining units.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesTeal,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
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

            item(key = "youtube") {
                SettingsSection(stringResource(R.string.settings_youtube))
                Text(
                    text = stringResource(R.string.settings_powered_by_youtube),
                    style = MaterialTheme.typography.labelLarge,
                    color = NodesTeal,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                Text(
                    text = stringResource(R.string.settings_youtube_signin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = NodesGrey,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                if (!viewModel.isSignInConfigured) {
                    Text(
                        text = stringResource(R.string.settings_youtube_missing_client),
                        style = MaterialTheme.typography.bodySmall,
                        color = NodesGrey,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                } else if (settings.youtubeSignedIn) {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_youtube_signout),
                        subtitle = settings.youtubeAccountEmail,
                        onClick = viewModel::signOut,
                    )
                } else {
                    SettingsActionRow(
                        title = stringResource(R.string.settings_youtube_signin),
                        subtitle = signInState.let {
                            if (it is app.nodes.core.source.youtube.SignInState.Error) it.message else null
                        },
                        onClick = {
                            val intent = viewModel.signInIntent()
                            if (intent != null) signInLauncher.launch(intent)
                        },
                    )
                }
                SettingsLinkRow(
                    title = stringResource(R.string.settings_terms),
                    onClick = { openUrl(context, "https://www.youtube.com/t/terms") },
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_google_privacy),
                    onClick = { openUrl(context, "https://policies.google.com/privacy") },
                )
                Text(
                    text = stringResource(R.string.disclaimer_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesGrey.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            item(key = "storage") {
                SettingsSection(stringResource(R.string.settings_storage))
                SettingsActionRow(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = null,
                    onClick = viewModel::clearCache,
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
                    title = stringResource(R.string.settings_licenses),
                    onClick = { openUrl(context, "https://github.com/nodes-app/nodes/blob/main/NOTICE.md") },
                )
                SettingsLinkRow(
                    title = stringResource(R.string.settings_privacy),
                    onClick = { openUrl(context, "https://github.com/nodes-app/nodes/blob/main/PRIVACY.md") },
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

@Composable
private fun SourceRow(
    availability: SourceAvailability,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = availability.sourceId.localizedName(),
                style = MaterialTheme.typography.bodyLarge,
                color = NodesWhite,
            )
            val detail = availability.message ?: availability.status.name.lowercase()
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (availability.isReady) NodesTeal else NodesGrey,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(NodesTeal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = NodesBlack,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceId.localizedName(): String = when (this) {
    SourceId.LOCAL -> stringResource(R.string.source_local)
    SourceId.JAMENDO -> stringResource(R.string.source_jamendo)
    SourceId.YOUTUBE -> stringResource(R.string.source_youtube)
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        if (it !is ActivityNotFoundException) throw it
    }
}
