/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import android.content.Context
import app.nodes.core.lyrics.LyricsPayload
import app.nodes.core.lyrics.LyricsProvider
import app.nodes.core.lyrics.LyricsQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The lyrics the user put on the device themselves:
 * `files/lyrics/<trackId>.lrc`. This keeps working offline and is tried before
 * any network provider, so a hand-made transcription always wins.
 */
class LocalLyricsProvider(
    @ApplicationContext private val context: Context,
) : LyricsProvider {

    override val id: String = "local"
    override val displayName: String = "Local file"

    override suspend fun fetch(query: LyricsQuery): LyricsPayload = withContext(Dispatchers.IO) {
        val file = File(File(context.filesDir, "lyrics"), "${query.trackId}.lrc")
        if (!file.isFile) return@withContext LyricsPayload.NotFound
        val raw = runCatching { file.readText() }.getOrNull()
        if (raw.isNullOrBlank()) {
            LyricsPayload.NotFound
        } else {
            LyricsPayload.Found(raw = raw, syncedHint = true)
        }
    }

    /** Kept for the id the app used before there was a provider layer. */
    fun fileFor(trackId: String): File = File(File(context.filesDir, "lyrics"), "$trackId.lrc")
}
