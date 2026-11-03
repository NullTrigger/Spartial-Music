/*
 * Copyright (C) 2026 The Nodes Authors.
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
import androidx.compose.foundation.layout.height
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
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesTeal
import app.nodes.ui.theme.NodesWhite

/**
 * Transparent home top bar: large title on the left, settings and the circular
 * avatar on the right. It floats over the hero artwork, as in the reference.
 *
 * Once an account is connected the settings gear is dropped: the avatar is the
 * account entry point, and a second gear next to it was just visual noise.
 */
@Composable
fun HomeTopBar(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    avatarInitial: String = "N",
    signedIn: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            color = NodesWhite,
            modifier = Modifier.weight(1f),
        )
        if (!signedIn) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    tint = NodesWhite,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NodesTeal)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarInitial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NodesBlack,
            )
        }
    }
}

/**
 * Header for secondary screens. `back` is null on tab roots.
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
            .padding(start = 8.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = back),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = NodesWhite,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = NodesWhite,
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
                color = NodesGrey,
                modifier = Modifier.padding(start = if (back != null) 52.dp else 12.dp, top = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
