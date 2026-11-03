/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteButton
import app.nodes.R
import app.nodes.ui.theme.NodesGrey

/**
 * The Cast button for the gms build: a [MediaRouteButton] wired up by media3
 * so tapping it shows the Output Switcher, which transfers the session between
 * this device and Cast receivers. When the official embedded YouTube player is
 * on screen there is no castable session content, so the button renders as a
 * disabled ghost and the tap explains why instead.
 *
 * Part of [androidx.media3.cast], see developer.android.com/media/media3/cast.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
    onUnavailableTap: () -> Unit = {},
) {
    if (unavailable) {
        Box(
            modifier = modifier
                .size(40.dp)
                .clickable(onClick = onUnavailableTap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = stringResource(R.string.player_cast),
                tint = NodesGrey,
                modifier = Modifier.size(24.dp),
            )
        }
        return
    }

    AndroidView(
        factory = { context ->
            MediaRouteButton(context).also { button ->
                runCatching {
                    MediaRouteButtonFactory.setUpMediaRouteButton(context, button)
                }.onFailure {
                    // No Cast context (Play services unavailable on this
                    // device): leave the button dead rather than crashing.
                    button.isEnabled = false
                }
            }
        },
        modifier = modifier.size(40.dp),
    )
}
