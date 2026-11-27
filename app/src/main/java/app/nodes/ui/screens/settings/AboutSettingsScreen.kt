/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialWhite
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nodes.BuildConfig
import app.nodes.R
import app.nodes.core.data.GitHubReleaseChecker
import app.nodes.core.data.ReleaseUpdate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * Version, manual GitHub release checks, licence and the policies that come
 * with using YouTube as a source. No usage data is uploaded.
 */
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.about_telegram_url)
    val scope = rememberCoroutineScope()
    val releaseChecker = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReleaseCheckerEntryPoint::class.java,
        ).releaseChecker()
    }
    var updateCheckMessage by remember { mutableStateOf<Pair<Int, String?>?>(null) }
    var updateResult by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var checkingForUpdate by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_about_title),
        onBack = onBack,
    ) {
        item(key = "team") {
            SettingsGroup(stringResource(R.string.about_team)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AboutMember(
                        photo = R.drawable.about_chikku,
                        name = stringResource(R.string.about_chikku_name),
                        role = stringResource(R.string.about_chikku_role),
                    )
                    AboutMember(
                        photo = R.drawable.about_m4trix,
                        name = stringResource(R.string.about_m4trix_name),
                        role = stringResource(R.string.about_m4trix_role),
                    )
                }
            }
        }

        item(key = "community") {
            SettingsGroup(stringResource(R.string.about_community)) {
                SettingsNote(stringResource(R.string.about_community_body))
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.about_telegram),
                    subtitle = stringResource(R.string.about_telegram_hint),
                    icon = Icons.Rounded.Send,
                    onClick = { openUrl(context, telegramUrl) },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.about_github),
                    subtitle = stringResource(R.string.about_github_hint),
                    onClick = { openUrl(context, GITHUB_REPO_URL) },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.about_star),
                    subtitle = stringResource(R.string.about_star_hint),
                    icon = Icons.Rounded.Star,
                    onClick = { openUrl(context, GITHUB_REPO_URL) },
                )
            }
        }

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
                SettingsDivider()
                SettingsActionRow(
                    title = stringResource(
                        if (checkingForUpdate) R.string.update_checking else R.string.update_check,
                    ),
                    subtitle = updateCheckMessage?.let { (resourceId, version) ->
                        if (version == null) stringResource(resourceId)
                        else stringResource(resourceId, version)
                    } ?: stringResource(R.string.update_check_hint),
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = {
                        if (!checkingForUpdate) {
                            checkingForUpdate = true
                            updateCheckMessage = null
                            updateResult = null
                            scope.launch {
                                runCatching { releaseChecker.check() }
                                    .onSuccess { result ->
                                        updateResult = result
                                        updateCheckMessage = when {
                                            !result.updateAvailable -> R.string.update_latest_version to
                                                result.latestVersion
                                            result.apkUrl == null -> R.string.update_release_asset_missing to
                                                result.latestVersion
                                            else -> R.string.update_available to result.latestVersion
                                        }
                                    }
                                    .onFailure {
                                        updateCheckMessage = R.string.update_check_failed to null
                                    }
                                checkingForUpdate = false
                            }
                        }
                    },
                )
                updateResult?.takeIf { it.updateAvailable }?.let { result ->
                    SettingsDivider()
                    SettingsValueRow(
                        title = stringResource(R.string.update_open_release),
                        subtitle = result.latestVersion,
                        icon = Icons.Rounded.SystemUpdate,
                        onClick = { openUrl(context, result.apkUrl ?: result.releaseUrl) },
                    )
                }
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
                    onClick = { openUrl(context, "https://github.com/NullTrigger/Spartial-Music/blob/main/NOTICE.md") },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_privacy),
                    onClick = { openUrl(context, "https://github.com/NullTrigger/Spartial-Music/blob/main/PRIVACY.md") },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_takedown),
                    subtitle = stringResource(R.string.settings_takedown_hint),
                    onClick = { openUrl(context, "https://docs.github.com/en/site-policy/content-removal-policies/dmca-takedown-policy") },
                )
            }
        }
    }
}

private const val GITHUB_REPO_URL = "https://github.com/NullTrigger/Spartial-Music"

/** One team member: a round photo over the name and role. */
@Composable
private fun AboutMember(photo: Int, name: String, role: String) {
    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(photo),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = SpartialWhite,
            maxLines = 1,
        )
        Text(
            text = role,
            style = MaterialTheme.typography.bodySmall,
            color = SpartialGrey,
            textAlign = TextAlign.Center,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReleaseCheckerEntryPoint {
    fun releaseChecker(): GitHubReleaseChecker
}
