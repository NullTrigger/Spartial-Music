/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.lyrics.providers

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import java.util.Locale
import kotlin.math.max

/**
 * TTML (Apple Music's rich lyric format) to enhanced LRC.
 *
 * Derived from the TTML parser BetterLyrics ships (`betterlyrics/TTMLParser.kt`
 * in the Glossy tree); the field and namespace handling is kept behaviourally
 * identical so the same payloads parse, but the output is this app's inline
 * enhanced-LRC dialect rather than Glossy's separate word-data lines, because
 * [app.nodes.core.lyrics.LyricsParser] reads that one.
 *
 * Handles the quirks that actually occur in the wild: timing attributes
 * unprefixed or as `ttp:*`/`ttm:*`, `<p>` elements whose `begin` only lives on
 * their first `<span>`, background (`ttm:role:x-bg`) and duet (`ttm:agent`)
 * voices, and clock values in both `HH:MM:SS.mmm` and `MM:SS.mmm`.
 */
object Ttml {

    private const val TTML_PARAMETER_NS = "http://www.w3.org/ns/ttml#parameter"
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

    /** One parsed lyric line, words already split where the source had them. */
    data class Line(
        val text: String,
        val startSeconds: Double,
        val words: List<Word> = emptyList(),
    )

    data class Word(val text: String, val startSeconds: Double)

    /** `true` when the payload carries per-word timing worth preserving. */
    fun isWordSynced(lines: List<Line>): Boolean = lines.any { it.words.size > 1 }

    fun parse(ttml: String): List<Line> {
        if (ttml.isBlank()) return emptyList()
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            // Network payloads must never resolve entities or fetch DTDs.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val root = factory.newDocumentBuilder().parse(ttml.byteInputStream()).documentElement
            parseContainer(root, 0.0)
        } catch (error: Exception) {
            emptyList()
        }
    }

    private fun parseContainer(element: Element, offset: Double): List<Line> {
        val out = mutableListOf<Line>()
        val begin = timingAttr(element, "begin")
        val localOffset = if (begin.isNotEmpty()) parseTime(begin) else offset
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                when (name) {
                    "p" -> parseP(child, out, localOffset)
                    "div" -> out += parseContainer(child, localOffset)
                }
            }
            child = child.nextSibling
        }
        return out
    }

    private fun parseP(p: Element, out: MutableList<Line>, offset: Double) {
        val beginAttr = timingAttr(p, "begin")
        val direct = if (beginAttr.isNotEmpty()) parseTime(beginAttr) else Double.NaN
        val fromSpan = if (beginAttr.isNotEmpty() && !direct.isNaN()) null else firstSpanBegin(p)?.let(::parseTime)
        val startSeconds = sequenceOf(direct, fromSpan)
            .filterNotNull()
            .filter { !it.isNaN() && it.isFinite() }
            .firstOrNull() ?: return
        val agent = getAttr(p, "agent").takeIf { it.isNotEmpty() }
        val isBackground = p.getAttribute("ttm:role") == "x-bg"

        val words = mutableListOf<Word>()
        val text = collectContent(p, startSeconds + offset, words, agent, isBackground)
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            out += Line(text = clean, startSeconds = startSeconds + offset, words = words.toList())
        }
    }

    /**
     * Walks spans and text in document order. A `<span begin>` is treated as
     * one timed unit (Apple wraps single words this way); bare text nodes are
     * timed at the running cursor, which is the best the source offers.
     */
    private fun collectContent(
        element: Element,
        lineStartSeconds: Double,
        words: MutableList<Word>,
        agent: String?,
        isBackground: Boolean,
    ): String {
        val sb = StringBuilder()
        var cursor = lineStartSeconds
        var child = element.firstChild
        while (child != null) {
            when {
                child is Element &&
                    (child.localName ?: child.nodeName.substringAfterLast(':')) == "span" -> {
                    val spanBegin = timingAttr(child, "begin").takeIf { it.isNotEmpty() }
                    val spanStart = spanBegin?.let(::parseTime)?.takeUnless { it.isNaN() } ?: cursor
                    val ownAgent = getAttr(child, "agent").takeIf { it.isNotEmpty() } ?: agent
                    val spanIsBackground = isBackground || child.getAttribute("ttm:role") == "x-bg"

                    val spanText = child.textContent
                    val trimmed = spanText.trim()
                    if (trimmed.isNotEmpty()) {
                        // One word per timed span is the shape Apple exports;
                        // a span holding a phrase still records one anchor, so
                        // the highlight moves in one step across it.
                        words += Word(text = trimmed, startSeconds = spanStart)
                    }
                    sb.append(spanText)
                    val spanEnd = timingAttr(child, "end").takeIf { it.isNotEmpty() }
                        ?.let(::parseTime)
                        ?.takeUnless { it.isNaN() || !it.isFinite() }
                    cursor = max(cursor, spanEnd ?: spanStart)
                }

                child is Element -> sb.append(child.textContent)

                child.nodeType == Node.TEXT_NODE -> {
                    val raw = child.nodeValue ?: ""
                    sb.append(raw)
                    val trimmed = raw.trim()
                    if (trimmed.isNotEmpty()) {
                        words += Word(text = trimmed, startSeconds = cursor)
                    }
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun firstSpanBegin(p: Element): String? {
        var child = p.firstChild
        var best: String? = null
        var bestSeconds = Double.POSITIVE_INFINITY
        while (child != null) {
            if (child is Element &&
                (child.localName ?: child.nodeName.substringAfterLast(':')) == "span"
            ) {
                val b = timingAttr(child, "begin")
                if (b.isNotEmpty()) {
                    val s = parseTime(b)
                    if (s < bestSeconds) {
                        bestSeconds = s
                        best = b
                    }
                }
            }
            child = child.nextSibling
        }
        return best
    }

    private fun getAttr(el: Element, localName: String): String {
        val ttm = el.getAttribute("ttm:$localName")
        if (ttm.isNotEmpty()) return ttm
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS(TTML_METADATA_NS, localName)
    }

    private fun timingAttr(el: Element, localName: String): String {
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        val param = el.getAttributeNS(TTML_PARAMETER_NS, localName)
        if (param.isNotEmpty()) return param
        return ""
    }

    /** Both `MM:SS.mmm` and `HH:MM:SS.mmm`, and fractions with , or . */
    internal fun parseTime(raw: String): Double {
        val clean = raw.trim().removeSuffix("s").replace(',', '.')
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                1 -> parts[0].toDouble()
                2 -> parts[0].toInt() * 60 + parts[1].toDouble()
                else -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toDouble()
            }
        } catch (error: NumberFormatException) {
            Double.NaN
        }
    }

    /**
     * Enhanced LRC in this app's dialect: `[mm:ss.xx]<m:ss.xx>word`, which is
     * what [app.nodes.core.lyrics.LyricsParser.parseWords] reads - its word tag
     * shape is `<mm:ss.xx>`, so a bare `<12.34>` would render as literal text.
     * Lines without word timing stay plain.
     */
    fun toEnhancedLrc(lines: List<Line>): String {
        val sb = StringBuilder(lines.size * 96)
        lines.sortedBy { it.startSeconds }.forEach { line ->
            sb.append(formatStamp(line.startSeconds))
            if (isWordSynced(lines) && line.words.size > 1) {
                line.words.forEach { word ->
                    sb.append(formatWordTag(word.startSeconds))
                    sb.append(word.text).append(' ')
                }
            } else {
                sb.append(line.text)
            }
            sb.append('\n')
        }
        return sb.toString().trim()
    }

    private fun formatStamp(seconds: Double): String {
        val total = (seconds * 100).toInt().coerceAtLeast(0)
        val minutes = total / 6000
        val rest = total % 6000
        return "[%02d:%02d.%02d]".format(Locale.US, minutes, rest / 100, rest % 100)
    }

    /** `<m:ss.xx>`, the tag shape [app.nodes.core.lyrics.LyricsParser] reads. */
    private fun formatWordTag(seconds: Double): String {
        val total = (seconds * 100).toInt().coerceAtLeast(0)
        return "<%d:%05.2f>".format(Locale.US, total / 6000, total % 6000 / 100.0)
    }
}
