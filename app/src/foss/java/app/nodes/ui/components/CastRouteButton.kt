/*
 * Copyright (C) 2026 The Spartial Music Authors.
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nodes.R

/**
 * The Cast slot in the Now Playing secondary controls.
 *
 * Casting a session requires the Play services Cast framework, which the FOSS
 * flavour does not bundle by design. This placeholder draws the same cast
 * glyph the gms flavour uses, in the player's accent colour, so the row keeps
 * its shape; tapping it explains that there is nothing to cast to.
 */
@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
    onUnavailableTap: () -> Unit = {},
    onChooserFailure: () -> Unit = {},
    tint: Color = Color.Unspecified,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = { onUnavailableTap() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Cast,
            contentDescription = stringResource(R.string.player_cast),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
