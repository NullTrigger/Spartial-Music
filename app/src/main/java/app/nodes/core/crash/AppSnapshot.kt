/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.crash

/**
 * What the app was doing when it died, updated cheaply as the app runs.
 *
 * A crash handler runs on the dying thread with no access to Hilt, no looper and
 * no time: it cannot ask the player or the nav controller anything. So the few
 * facts worth having are copied into two volatile strings as they change, and
 * the handler just reads them.
 *
 * Values are plain text on purpose - track ids, a route name, counters. No
 * tokens, cookies or account identifiers are ever written here.
 */
object AppSnapshot {

    /** Current navigation route, e.g. `player` or `playlist/abc`. */
    @Volatile
    var route: String? = null

    /** One line about playback: track id, state, queue size, position. */
    @Volatile
    var player: String? = null
}
