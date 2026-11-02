/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.navigation

object Routes {
    const val HOME = "home"
    const val TRENDING = "trending"
    const val SESSIONS = "sessions"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_CONTENT = "settings/content"
    const val SETTINGS_LYRICS = "settings/lyrics"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_ABOUT = "settings/about"
    const val DIAGNOSTICS = "diagnostics"
    const val LOGIN = "login"
    const val LIKED = "liked"
    const val DOWNLOADS = "downloads"

    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST_PATTERN = "playlist/{$PLAYLIST_ARG}"

    fun playlist(id: String): String = "playlist/$id"
}
