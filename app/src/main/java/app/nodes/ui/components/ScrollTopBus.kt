/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tapping the already-active tab asks its screen to scroll back to the top.
 *
 * The screens own their list state, so the bar cannot reach into them; this
 * tiny bus is the contract instead. A screen that wants the behaviour hoists
 * nothing - it collects [events] for its own route and scrolls its list.
 */
object ScrollTopBus {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun request(route: String) {
        _events.tryEmit(route)
    }
}
