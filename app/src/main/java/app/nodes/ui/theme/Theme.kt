/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import android.os.Build
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

private val SpartialDarkScheme: ColorScheme = darkColorScheme(
    primary = SpartialAccent,
    onPrimary = SpartialBlack,
    primaryContainer = SpartialSurfaceHigh,
    onPrimaryContainer = SpartialWhite,
    secondary = SpartialWhite,
    onSecondary = SpartialBlack,
    secondaryContainer = SpartialSurfaceHigh,
    onSecondaryContainer = SpartialWhite,
    tertiary = SpartialGrey,
    onTertiary = SpartialBlack,
    background = SpartialBlack,
    onBackground = SpartialWhite,
    surface = SpartialSurface,
    onSurface = SpartialWhite,
    surfaceVariant = SpartialSurfaceHigh,
    onSurfaceVariant = SpartialGrey,
    surfaceContainer = SpartialSurface,
    surfaceContainerHigh = SpartialSurfaceHigh,
    surfaceContainerHighest = SpartialSurfaceHighest,
    surfaceContainerLow = SpartialBlackElevated,
    surfaceContainerLowest = SpartialBlack,
    outline = SpartialGreyDim,
    outlineVariant = SpartialSurfaceHighest,
    error = SpartialDanger,
    onError = SpartialBlack,
)

/**
 * Spartial Music is dark by default. Light theme is intentionally not shipped: the new
 * surfaces are artwork-tinted gradients designed against the near-black base.
 * Dynamic colour can still be opted into in Settings on Android 12+.
 */
@Composable
fun SpartialTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (useDynamic) dynamicDarkColorScheme(context) else SpartialDarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpartialTypography,
        content = content,
    )
}

/** Convenience for tests and previews. */
internal fun debugColorInt(value: androidx.compose.ui.graphics.Color): Int = value.toArgb()
