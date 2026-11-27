/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Holds the screen awake while [enabled] and restores the previous behaviour
 * when the composable leaves. The flag lives on the View, so it cannot leak past
 * the screen that set it.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    if (!enabled) return
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
