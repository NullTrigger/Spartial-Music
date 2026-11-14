/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics

import java.util.Locale

/**
 * Providers sometimes answer "found" with a note that the track has no lyrics
 * or is instrumental. Those are misses, not lyrics, and must not be shown as a
 * one-line song. The check is deliberately conservative: it only rejects known
 * placeholder phrases, never a real line that happens to mention music.
 */
internal fun cleanLyricsText(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text == "..." || text == "…") return null
    val folded = text.lowercase(Locale.ROOT)
    if (PLACEHOLDER_MARKERS.any { folded.contains(it) }) return null
    return text
}

private val PLACEHOLDER_MARKERS = listOf(
    "纯音乐",
    "暂无歌词",
    "没有填词",
    "no lyrics found",
    "lyrics not available",
    "instrumental",
)
