/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val TRENDING = "trending"
    const val SUGGESTIONS = "suggestions"
    const val SESSIONS = "sessions"
    const val LIBRARY = "library"
    const val SONGS = "songs"
    const val STATS = "stats"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val SETTINGS_PLAYBACK = "settings/playback"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_CONTENT = "settings/content"
    const val SETTINGS_LYRICS = "settings/lyrics"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_ABOUT = "settings/about"
    const val DIAGNOSTICS = "diagnostics"
    const val LOGIN = "login"

    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST_PATTERN = "playlist/{$PLAYLIST_ARG}"

    /** Routes.collection kind values, matching app.nodes.ui.screens.collection.CollectionKind. */
    const val COLLECTION_ARTIST = "artist"
    const val COLLECTION_ALBUM = "album"
    const val COLLECTION_ARG_KIND = "kind"
    const val COLLECTION_ARG_NAME = "name"
    const val COLLECTION_PATTERN =
        "collection/{$COLLECTION_ARG_KIND}/{$COLLECTION_ARG_NAME}"

    /**
     * [name] is URI-encoded so artist and album names containing a slash
     * ("AC/DC") stay one path segment.
     */
    fun collection(kind: String, name: String): String =
        "collection/$kind/${Uri.encode(name)}"

    fun playlist(id: String): String = "playlist/$id"
}
