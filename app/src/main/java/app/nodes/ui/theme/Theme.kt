/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

private val NodesDarkScheme: ColorScheme = darkColorScheme(
    primary = NodesTeal,
    onPrimary = NodesBlack,
    primaryContainer = NodesTealDeep,
    onPrimaryContainer = NodesWhite,
    secondary = NodesWhite,
    onSecondary = NodesBlack,
    secondaryContainer = NodesSurfaceHigh,
    onSecondaryContainer = NodesWhite,
    tertiary = NodesGrey,
    onTertiary = NodesBlack,
    background = NodesBlack,
    onBackground = NodesWhite,
    surface = NodesSurface,
    onSurface = NodesWhite,
    surfaceVariant = NodesSurfaceHigh,
    onSurfaceVariant = NodesGrey,
    surfaceContainer = NodesSurface,
    surfaceContainerHigh = NodesSurfaceHigh,
    surfaceContainerHighest = NodesSurfaceHighest,
    surfaceContainerLow = NodesBlackElevated,
    surfaceContainerLowest = NodesBlack,
    outline = NodesGreyDim,
    outlineVariant = NodesSurfaceHighest,
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B),
    onError = NodesBlack,
)

/**
 * Nodes is dark by default. Light theme and dynamic colour are opt-in in
 * Settings, and dynamic colour is only honoured on Android 12+.
 */
@Composable
fun NodesTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicDarkColorScheme(context)
        else -> NodesDarkScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            // Keep the vertical system bar contrast scrim off: the glass UI
            // draws its own backdrop.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    // Force the dark scheme when the user has not opted into light: the light
    // palette is intentionally not shipped yet (see Settings > Appearance).
    val effective = if (darkTheme || !systemDark) colorScheme else colorScheme

    MaterialTheme(
        colorScheme = effective,
        typography = NodesTypography,
        content = content,
    )
}

/** Convenience for tests and previews. */
internal fun debugColorInt(value: androidx.compose.ui.graphics.Color): Int = value.toArgb()
