/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import android.content.Context
import app.nodes.core.model.LyricLine
import app.nodes.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synced lyrics from local `.lrc` sidecar files.
 *
 * Nodes does not fetch lyrics from third parties (no licence, and no tracking).
 * If you drop `files/lyrics/<trackId>.lrc` into the app's private storage - for
 * example your own transcription of a Creative Commons track - the player shows
 * it time-synced.
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()

    suspend fun lyricsFor(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        cache.getOrPut(track.id) { readSidecar(track.id) }
    }

    private fun readSidecar(trackId: String): List<LyricLine> {
        val file = File(File(context.filesDir, "lyrics"), "$trackId.lrc")
        if (!file.isFile) return emptyList()
        return file.readLines()
            .mapNotNull(::parseLine)
            .sortedBy { it.timeMs }
    }

    companion object {
        private val TIMESTAMP = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]\s*(.*)$""")

        /** Parses one `[mm:ss.xx] text` line. */
        fun parseLine(line: String): LyricLine? {
            val match = TIMESTAMP.find(line.trim()) ?: return null
            val minutes = match.groupValues[1].toLongOrNull() ?: return null
            val seconds = match.groupValues[2].toLongOrNull() ?: return null
            val fractionRaw = match.groupValues[3]
            val fraction = when (fractionRaw.length) {
                0 -> 0L
                1 -> fractionRaw.toLongOrNull()?.times(100) ?: 0L
                2 -> fractionRaw.toLongOrNull()?.times(10) ?: 0L
                else -> fractionRaw.take(3).toLongOrNull() ?: 0L
            }
            val text = match.groupValues[4].trim()
            return LyricLine(timeMs = minutes * 60_000 + seconds * 1_000 + fraction, text = text)
        }
    }
}
