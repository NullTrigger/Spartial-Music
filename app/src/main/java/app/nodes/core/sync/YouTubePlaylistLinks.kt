/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One local playlist copied to the account, and what that copy already holds.
 *
 * [matches] is `local track id -> YouTube video id`. Keeping the video id means
 * a sync never has to search YouTube again for a track it has already matched,
 * which is what makes refreshing a 200-song playlist cheap instead of 200
 * searches every half hour.
 */
@Serializable
data class YouTubePlaylistLink(
    val localPlaylistId: String,
    val remotePlaylistId: String,
    val matches: Map<String, String> = emptyMap(),
)

/**
 * The small sidecar that remembers which local playlist became which YouTube
 * playlist. It lives in the app's private files directory next to the library
 * database: no credential, no account data, just two ids and a track mapping.
 *
 * A link outlives an app restart on purpose - that is the whole point of the
 * auto-sync - and is dropped only when the local playlist is deleted.
 */
@Singleton
class YouTubePlaylistLinks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    private val mutex = Mutex()

    suspend fun load(): List<YouTubePlaylistLink> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
    }

    suspend fun find(localPlaylistId: String): YouTubePlaylistLink? =
        load().firstOrNull { it.localPlaylistId == localPlaylistId }

    /** Adds or replaces the link for one local playlist. */
    suspend fun save(link: YouTubePlaylistLink) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readUnlocked().filterNot { it.localPlaylistId == link.localPlaylistId } + link
            writeUnlocked(all)
        }
    }

    suspend fun forget(localPlaylistId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = readUnlocked()
            val kept = all.filterNot { it.localPlaylistId == localPlaylistId }
            if (kept.size != all.size) writeUnlocked(kept)
        }
    }

    private fun readUnlocked(): List<YouTubePlaylistLink> = runCatching {
        if (!file.exists()) {
            emptyList()
        } else {
            json.decodeFromString<List<YouTubePlaylistLink>>(file.readText())
        }
    }.getOrDefault(emptyList())

    private fun writeUnlocked(links: List<YouTubePlaylistLink>) {
        runCatching {
            if (links.isEmpty()) {
                file.delete()
            } else {
                file.writeText(json.encodeToString(links))
            }
        }
    }

    private companion object {
        const val FILE_NAME = "youtube_playlists.json"
    }
}
