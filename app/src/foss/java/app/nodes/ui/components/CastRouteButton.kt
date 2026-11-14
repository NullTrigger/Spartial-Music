/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Cast slot in the Now Playing secondary controls.
 *
 * Casting a session requires the Play services Cast framework, which the FOSS
 * flavour does not bundle by design. This placeholder deliberately composes
 * nothing: the caller keeps its row layout, and the gms flavour has its own
 * CastRouteButton (same path in `src/gms/java`) rendering a real
 * MediaRouteButton.
 */
@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier,
    unavailable: Boolean = false,
    onUnavailableTap: () -> Unit = {},
    onChooserFailure: () -> Unit = {},
) {
    // Nothing to cast without the Cast framework.
}
