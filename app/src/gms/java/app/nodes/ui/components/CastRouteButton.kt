/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
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
import app.nodes.core.crash.AppLogger
import app.nodes.ui.theme.SpartialGrey

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
    onChooserFailure: () -> Unit = {},
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
                tint = SpartialGrey,
                modifier = Modifier.size(24.dp),
            )
        }
        return
    }

    AndroidView(
        factory = { context ->
            // MediaRouteButton is an AppCompat view: it resolves its cast
            // colours from the AppCompat theme attributes, which the Compose
            // Material 3 theme never defines. The unresolved attributes make
            // mediarouter's contrast check throw "background can not be
            // translucent" right inside the constructor (crash report
            // 2026-11-03), so the button is always built inside the solid
            // Theme.Spartial.Cast context (gms/res/values/themes.xml).
            val castContext = ContextThemeWrapper(context, R.style.Theme_Nodes_Cast)
            runCatching {
                GuardedMediaRouteButton(castContext, onChooserFailure).also { button ->
                    runCatching {
                        MediaRouteButtonFactory.setUpMediaRouteButton(castContext, button)
                    }.onFailure {
                        // No Cast context (Play services unavailable on this
                        // device): leave the button dead rather than crashing.
                        button.isEnabled = false
                    }
                }
            }.getOrElse {
                // Last resort: an OEM theme overlay may still defeat colour
                // resolution. An invisible view keeps the Now Playing layout
                // intact instead of crashing the composition on draw.
                View(castContext).apply { visibility = View.GONE }
            }
        },
        modifier = modifier.size(40.dp),
    )
}

/**
 * A [MediaRouteButton] that cannot take the player down.
 *
 * `performClick()` opens the route chooser, and that path throws
 * IllegalStateException when the view cannot reach a FragmentManager - which
 * needs an activity that is a FragmentActivity (crash report 2026-11-12).
 * MainActivity is one now, so this is a guard rather than the fix: an OEM
 * theme overlay or a detached view could still take that path, and a tap on
 * Cast must never end a listening session.
 *
 * The super call is what fires the click listeners, so it is kept and only the
 * chooser failure is swallowed.
 */
private class GuardedMediaRouteButton(
    context: Context,
    private val onChooserFailure: () -> Unit,
) : MediaRouteButton(context) {

    override fun performClick(): Boolean = try {
        super.performClick()
    } catch (error: IllegalStateException) {
        AppLogger.e(TAG, "The cast route chooser could not open", error)
        onChooserFailure()
        false
    }

    private companion object {
        const val TAG = "Cast"
    }
}
