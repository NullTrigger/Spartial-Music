/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.session

/**
 * The room servers this build knows about.
 *
 * Shared listening needs a relay: the app has no other way to learn that a
 * friend started the same song. That is a deliberate exception to the rest of
 * the app, which keeps every byte on the device - see `PRIVACY.md`, which
 * spells out exactly what crosses the socket (a room code, a display name and
 * the track the host is playing) and what never does (account cookies,
 * listening history, the audio itself).
 *
 * The default is empty on purpose: shared listening is the one feature that
 * talks to a third-party server, so the app must never connect to one until
 * the user deliberately picks it. It is a free-plan worker, and the room state
 * is held in SQLite-backed durable objects, so nothing is stored beyond the live room.
 */
object ListenTogetherServers {

    /** The Spartial/Glossy Cloudflare deployment. `GET` on it lists live rooms. */
    const val CLOUDFLARE = "wss://glossy-listen-together.izybro110.workers.dev/ws"

    /**
     * The Metrolist community server, kept as a fallback: it speaks the same
     * protocol, so a room created here can be joined there and vice versa.
     */
    const val METROLIST = "wss://metroserverx.meowery.eu/ws"

    /**
     * What the app connects to when the user has not chosen a server: nothing.
     * Connecting is a privacy decision the user makes, never a default.
     */
    const val DEFAULT = ""

    /** Human labels for the server picker. Both are run by third parties. */
    val KNOWN = listOf(
        CLOUDFLARE to "Cloudflare (third-party server)",
        METROLIST to "Metrolist community (third-party server)",
    )

    /**
     * Accepts a bare host typed into the picker: `example.com` becomes
     * `wss://example.com/ws`, and an `https://` URL becomes `wss://`, so a
     * copied address still works.
     */
    fun normalise(input: String): String {
        val trimmed = input.trim()
        // A blank field stays "no server chosen"; it must never fall back to
        // someone else's deployment.
        if (trimmed.isBlank()) return DEFAULT
        if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) return trimmed
        val host = trimmed.removePrefix("https://").removePrefix("http://").removeSuffix("/")
        val scheme = if (host.startsWith("localhost:") || host.startsWith("127.0.0.1")) "ws" else "wss"
        return "$scheme://$host/ws"
    }
}
