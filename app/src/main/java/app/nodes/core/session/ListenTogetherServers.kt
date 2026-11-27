/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.session

/**
 * The room server this build connects to.
 *
 * Shared listening needs a relay: the app has no other way to learn that a
 * friend started the same song. That is a deliberate exception to the rest of
 * the app, which keeps every byte on the device - see `PRIVACY.md`, which
 * spells out exactly what crosses the socket (a room code, a display name and
 * the track the host is playing) and what never does (account cookies,
 * listening history, the audio itself).
 *
 * **Maintainer decision:** the server picker is gone and everyone joins the
 * same room protocol server, [SERVER_1]. A room only works if both ends are on
 * the same relay, and a picker whose only entry was that relay just made
 * joining a friend's room fail. Connecting is still a deliberate action: the
 * one-time consent dialog is shown before the first connection, and nothing
 * connects while it is declined.
 *
 * Do not add a second relay here without a maintainer decision (see
 * `AGENTS.md`). The service is a third-party free-plan worker with
 * SQLite-backed durable objects, so room state lives only as long as the room.
 */
object ListenTogetherServers {

    /** The Cloudflare deployment. `GET` on it lists live rooms. */
    const val SERVER_1 = "wss://glossy-listen-together.izybro110.workers.dev/ws"

    /** The relay every session uses. */
    const val DEFAULT = SERVER_1
}
