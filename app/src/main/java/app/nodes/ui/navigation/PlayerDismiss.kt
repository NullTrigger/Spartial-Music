/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.navigation

/**
 * How far the full player has already been dragged down, as a fraction of the
 * screen height.
 *
 * Closing the player by swiping is two producers of one motion: the gesture
 * puts the sheet under the finger, and then the navigation pop transition has
 * to carry it the rest of the way. Without a shared number the transition
 * started again from the top, so the sheet snapped back up before sliding a
 * token quarter-screen and fading - which is exactly what made swiping down
 * feel unlike dragging the sheet.
 *
 * Plain volatile state rather than snapshot state: it is read once when the
 * exit transition is built, and it must never invalidate a composition.
 */
object PlayerDismiss {

    /** 0 = fully on screen, 1 = fully past the bottom edge. */
    @Volatile
    var fraction: Float = 0f

    /**
     * Set when the swipe gesture closes the sheet, cleared by the app bar.
     *
     * The bar underneath is normally replaced on the same frame the route
     * changes, which is before the sheet has moved out of the way - so the
     * capsule used to pop in over the player. A swipe close means the sheet is
     * already travelling, so the bar can wait and rise into the space the sheet
     * leaves behind. Every other close wants the bar immediately.
     */
    @Volatile
    var handover: Boolean = false
}
