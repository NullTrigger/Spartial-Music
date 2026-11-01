/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics

import app.nodes.core.model.LyricLine
import app.nodes.core.model.LyricWord

/** One parsed provider payload. `synced` is false for static, untimed text. */
data class ParsedLyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
)

/**
 * Turns provider text into [LyricLine]s.
 *
 * Three shapes arrive in practice and all of them are handled here:
 *  - plain LRC, `[mm:ss.xx] text`, including several stamps on one line;
 *  - enhanced (word-level) LRC, `[mm:ss.xx]v1: <mm:ss.xx>word <mm:ss.xx>word`,
 *    which is what Spotify-derived payloads and YouLyPlus/TTML conversions look
 *    like once flattened;
 *  - untimed text, one line per row, kept in order but never followed.
 *
 * Nothing here touches the network or Android, so it is all unit-testable.
 */
object LyricsParser {

    /** `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]`. */
    private val LINE_STAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** `<mm:ss.xx>` word tag used by enhanced LRC. */
    private val WORD_TAG = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")

    /** `[ar:…]`, `[offset:…]`, … — metadata, never rendered. */
    private val METADATA = Regex(
        """^\[(ar|ti|al|au|by|offset|re|ve|length|tool|encoding):.*]$""",
        RegexOption.IGNORE_CASE,
    )

    /** `v1:`, `v2:` — the duet voice a line belongs to. */
    private val AGENT_PREFIX = Regex("""^v\d+:\s*""")

    private val AGENT_INLINE = Regex("""\{agent:[^}]+}""")

    /** Lines providers append to credit themselves. */
    private val CREDIT = Regex(
        """^(synced by|lyrics by|music by|arranged by|translated by|submitted by)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    /** How long the final line stays highlighted when nothing follows it. */
    private const val TAIL_MS = 5_000L

    fun parse(raw: String?): ParsedLyrics {
        if (raw.isNullOrBlank()) return ParsedLyrics(emptyList(), false)
        val timed = parseTimed(raw)
        if (timed.isNotEmpty()) return ParsedLyrics(timed, true)
        return ParsedLyrics(parsePlain(raw), false)
    }

    private fun parseTimed(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        raw.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || METADATA.matches(trimmed)) return@forEach
            val stamps = LINE_STAMP.findAll(trimmed).toList()
            if (stamps.isEmpty()) return@forEach

            var content = trimmed.substring(stamps.last().range.last + 1)
            content = AGENT_PREFIX.replace(content, "")
            content = content.replace(AGENT_INLINE, "").replace("{bg}", "")
            if (CREDIT.matches(content.trim())) return@forEach

            val words = parseWords(content)
            val text = if (words.isEmpty()) content.trim() else words.joinToString(" ") { it.text }
            if (text.isBlank()) return@forEach

            // One physical line can carry several stamps (a repeated chorus).
            stamps.forEach { stamp ->
                out += LyricLine(timeMs = stampToMs(stamp), text = text, words = words)
            }
        }
        return fillEnds(out.sortedBy { it.timeMs })
    }

    private fun parseWords(content: String): List<LyricWord> {
        val tags = WORD_TAG.findAll(content).toList()
        if (tags.isEmpty()) return emptyList()
        val raw = tags.mapIndexed { index, tag ->
            val end = tags.getOrNull(index + 1)?.range?.first ?: content.length
            tag to content.substring(tag.range.last + 1, end).trim()
        }
        val kept = raw.filter { it.second.isNotEmpty() }
        return kept.mapIndexed { index, (tag, word) ->
            LyricWord(
                text = word,
                startMs = stampToMs(tag),
                endMs = kept.getOrNull(index + 1)?.let { stampToMs(it.first) } ?: 0L,
            )
        }
    }

    /** Gives every line an end, and every last word one, so highlighting can move. */
    private fun fillEnds(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { index, line ->
        val end = lines.getOrNull(index + 1)?.timeMs?.takeIf { it > line.timeMs }
            ?: (line.timeMs + TAIL_MS)
        val words = if (line.words.isEmpty()) {
            emptyList()
        } else {
            line.words.mapIndexed { wordIndex, word ->
                val wordEnd = when {
                    word.endMs > word.startMs -> word.endMs
                    wordIndex == line.words.lastIndex -> end
                    else -> word.startMs
                }
                word.copy(endMs = wordEnd)
            }
        }
        line.copy(endMs = end, words = words)
    }

    private fun parsePlain(raw: String): List<LyricLine> = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !METADATA.matches(it) && !CREDIT.matches(it) }
        .mapIndexed { index, text ->
            LyricLine(timeMs = index * 1_000L, text = text, endMs = (index + 1) * 1_000L)
        }

    private fun stampToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fractionRaw = match.groupValues[3]
        val fraction = when (fractionRaw.length) {
            0 -> 0L
            1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100
            2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10
            else -> fractionRaw.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1_000L + fraction
    }
}
