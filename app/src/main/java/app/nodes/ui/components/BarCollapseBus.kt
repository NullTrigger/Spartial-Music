/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collapsed/expanded state for the floating bar, shared between the scrolling
 * screens (which observe their own lists) and the bar in NodesApp.
 */
object BarCollapseBus {

    private val _collapsed = MutableStateFlow(false)
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    fun set(value: Boolean) {
        _collapsed.value = value
    }
}
