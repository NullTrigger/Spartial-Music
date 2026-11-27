/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collapsed/expanded state for the floating bar, shared between the scrolling
 * screens (which observe their own lists) and the bar in SpartialApp.
 */
object BarCollapseBus {

    private val _collapsed = MutableStateFlow(false)
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    fun set(value: Boolean) {
        _collapsed.value = value
    }
}

/** Index weight for [BarCollapseOnScroll]; no single row is this tall. */
private const val ITEM_STRIDE = 100_000f

/** Movement below this is jitter, not a scroll. */
private const val COLLAPSE_SLOP = 24f

/**
 * The bar's scroll contract: collapse while the list travels down, expand
 * again on the way back up.
 *
 * The position is index-weighted because
 * [LazyListState.firstVisibleItemScrollOffset] alone resets to 0 at every item
 * boundary: read on its own, stepping over one row looks like a hard scroll
 * upwards and reopens the bar in the middle of a downward flick.
 */
@Composable
fun BarCollapseOnScroll(state: LazyListState) {
    LaunchedEffect(state) {
        var previous = 0f
        var seeded = false
        snapshotFlow {
            state.firstVisibleItemIndex * ITEM_STRIDE + state.firstVisibleItemScrollOffset
        }.collect { position ->
            // The first value is where the list already stands, not a scroll:
            // reading it as movement would collapse the bar on entry to any
            // screen that restores its position.
            if (!seeded) {
                seeded = true
                previous = position
                return@collect
            }
            val delta = position - previous
            previous = position
            if (delta > COLLAPSE_SLOP) {
                BarCollapseBus.set(true)
            } else if (delta < -COLLAPSE_SLOP) {
                BarCollapseBus.set(false)
            }
        }
    }
}
