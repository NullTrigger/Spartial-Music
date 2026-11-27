/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nodes.R
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialWhite

/**
 * Home header: wordmark on the left, settings and a small profile avatar on the
 * right. It floats over the hero artwork, with a subtle shadowed rim so it
 * stays legible on both bright and dark art.
 */
@Composable
fun HomeTopBar(
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    avatarInitial: String = "N",
    signedIn: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = SpartialWhite,
            modifier = Modifier.weight(1f),
        )
        CircleIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.cd_open_settings),
            onClick = onOpenSettings,
            tint = SpartialWhite,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SpartialWhite)
                .clickable(onClick = onOpenStats),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarInitial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpartialBlack,
            )
        }
    }
}

/** Circular back control used by every detail screen. */
@Composable
fun CircularBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.cd_back),
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .glass(CircleShape, strong = true, scrim = 0.18f)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = SpartialWhite,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Back on the left, caller-supplied actions on the right, over artwork. */
@Composable
fun DetailTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularBackButton(onBack = onBack)
        Spacer(Modifier.weight(1f))
        actions?.invoke()
    }
}

/**
 * Header for secondary screens (settings, stats, sessions). `back` is null on
 * tab roots. Kept as the single utility-screen header so those screens inherit
 * the new typography and circular navigation without a second component.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    back: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                CircularBackButton(onBack = back)
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = SpartialWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions?.invoke()
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SpartialGrey,
                modifier = Modifier.padding(
                    start = if (back != null) 52.dp else 8.dp,
                    top = 2.dp,
                ),
            )
        }
    }
}
