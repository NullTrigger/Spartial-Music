/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.util

/**
 * Thumbnail sizes come from the provider, not from the screen that will show
 * them: YouTube Music hands out `=w60-h60-l90-rj` covers for every row, and the
 * same URL then gets stretched to a full-width hero. That is the blur.
 *
 * [upgradeThumbnail] rewrites the size the provider asked for into the size the
 * screen needs. It is pure string work - no network, no Android types - so it is
 * unit tested directly.
 */

/** Never ask for less than this, or a 44dp row on a dense screen goes soft. */
private const val MIN_PX = 128

/** Covers stop being useful above this; larger requests only cost bandwidth. */
private const val MAX_PX = 2048

/**
 * Hosts whose image URLs carry a `=w…-h…` / `=s…` size suffix. YouTube Music
 * artwork always does; plain websites do not, so other hosts are left alone.
 */
private val SIZED_HOST_SUFFIXES = listOf("googleusercontent.com", "ggpht.com")

/** One suffix token: `w544`, `h544`, `s120`, `l90`, `rj`, `p`, `no`, `nu`. */
private val SUFFIX_TOKEN = Regex("^[a-z]{1,3}\\d*$", RegexOption.IGNORE_CASE)

private val VIDEO_THUMB_NAME = Regex("^(maxres|sd|hq|mq|default)[a-z0-9]*\\.(jpg|jpeg|webp|png)$", RegexOption.IGNORE_CASE)

/**
 * YouTube's own thumbnail ladder, smallest first. Every step exists for every
 * video except `maxresdefault`, which is why it is last and always has a
 * fallback behind it.
 *
 * Picking the smallest step that still covers the box is the difference between
 * a list that fills in at once and one that crawls: a 1280x720 `maxresdefault`
 * is roughly ten times the bytes of `mqdefault`, and a 56dp row cannot show
 * that difference.
 */
private val VIDEO_THUMB_LADDER = listOf(
    "default" to 120,
    "mqdefault" to 320,
    "hqdefault" to 480,
    "sddefault" to 640,
    "maxresdefault" to 1280,
)

/**
 * The URL to load for a box that needs [targetPx] pixels on its longest edge.
 *
 * Unrecognised URLs are returned unchanged, so this is safe to call everywhere.
 */
fun upgradeThumbnail(url: String?, targetPx: Int): String? {
    if (url.isNullOrBlank()) return null
    val host = hostOf(url) ?: return url
    return when {
        host.endsWith("ytimg.com") -> youtubeVideoThumbnail(url, targetPx.coerceIn(1, MAX_PX))
        SIZED_HOST_SUFFIXES.any { host.endsWith(it) } ->
            resizeSizedUrl(url, targetPx.coerceIn(MIN_PX, MAX_PX))
        else -> url
    }
}

/**
 * Candidate URLs in the order they should be tried.
 *
 * YouTube thumbnails are picked from [VIDEO_THUMB_LADDER]: the smallest frame
 * that covers [targetPx], so a list row asks for a few kilobytes instead of a
 * full 1280x720 JPEG. `hqdefault.jpg` is the frame that always exists, so it
 * follows every candidate that was rewritten. Callers render the second
 * candidate when the first one fails.
 */
fun thumbnailCandidates(url: String?, targetPx: Int): List<String> {
    if (url.isNullOrBlank()) return emptyList()
    val upgraded = upgradeThumbnail(url, targetPx) ?: return emptyList()
    val host = hostOf(url)
    if (host != null && host.endsWith("ytimg.com")) {
        if (upgraded == url) return listOf(url)
        // hqdefault is the frame that always exists: maxres and sd can be
        // missing, and the original provider URL may be too small to cover
        // the box, so it is the fallback for every upgraded candidate.
        val fallback = url.substringBeforeLast('/') + "/hqdefault.jpg"
        return listOf(upgraded, fallback).distinct()
    }
    return listOf(upgraded)
}

/**
 * `i.ytimg.com/vi/<id>/hqdefault.jpg` → the ladder step that covers [px].
 *
 * The filename is replaced rather than the size resynthesised, because YouTube
 * serves distinct files per step and has no on-demand resizer for these.
 */
private fun youtubeVideoThumbnail(url: String, px: Int): String {
    val slash = url.lastIndexOf('/')
    if (slash < 0) return url
    val file = url.substring(slash + 1).substringBefore('?')
    if (!VIDEO_THUMB_NAME.matches(file)) return url
    val step = VIDEO_THUMB_LADDER.firstOrNull { it.second >= px }?.first
        ?: VIDEO_THUMB_LADDER.last().first
    if (file.startsWith(step, ignoreCase = true)) return url
    return url.substring(0, slash + 1) + step + ".jpg"
}

/** `…=w60-h60-l90-rj` → `…=w544-h544-l90-rj`, keeping non-size flags. */
private fun resizeSizedUrl(url: String, px: Int): String {
    val query = url.substringAfter('?', "")
    val base = url.substringBefore('?')
    val equals = base.lastIndexOf('=')
    if (equals <= 0) return url

    val head = base.substring(0, equals)
    val tokens = base.substring(equals + 1).split('-')
    if (tokens.isEmpty() || tokens.any { it.isEmpty() || !SUFFIX_TOKEN.matches(it) }) return url

    val isSize = { token: String ->
        token.startsWith("w", true) || token.startsWith("h", true) || token.startsWith("s", true)
    }
    if (tokens.none(isSize)) return url

    val kept = tokens.filterNot(isSize)
    // `=s120` is a square shorthand; `=w60-h60-…` takes explicit width and height.
    val size = if (tokens.any { it.startsWith("s", true) } && kept.none { it.startsWith("l", true) }) {
        "s$px"
    } else {
        "w$px-h$px"
    }
    val suffix = (listOf(size) + kept).joinToString("-")
    return head + "=" + suffix + if (query.isEmpty()) "" else "?$query"
}

/** Host of an http(s) URL, without a URL parser and without throwing. */
private fun hostOf(url: String): String? {
    val scheme = url.indexOf("://")
    if (scheme < 0) return null
    val authority = url.substring(scheme + 3).substringBefore('/').substringBefore('?')
    val host = authority.substringAfter('@').substringBefore(':').lowercase()
    return host.ifEmpty { null }
}
