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
 * The URL to load for a box that needs [targetPx] pixels on its longest edge.
 *
 * Unrecognised URLs are returned unchanged, so this is safe to call everywhere.
 */
fun upgradeThumbnail(url: String?, targetPx: Int): String? {
    if (url.isNullOrBlank()) return null
    val px = targetPx.coerceIn(MIN_PX, MAX_PX)
    val host = hostOf(url) ?: return url
    return when {
        host.endsWith("ytimg.com") -> youtubeVideoThumbnail(url)
        SIZED_HOST_SUFFIXES.any { host.endsWith(it) } -> resizeSizedUrl(url, px)
        else -> url
    }
}

/**
 * Candidate URLs in the order they should be tried.
 *
 * YouTube's `maxresdefault.jpg` is the sharpest frame but does not exist for
 * every video, so the always-present `hqdefault.jpg` follows it. Callers render
 * the second candidate when the first one fails.
 */
fun thumbnailCandidates(url: String?, targetPx: Int): List<String> {
    if (url.isNullOrBlank()) return emptyList()
    val upgraded = upgradeThumbnail(url, targetPx) ?: return emptyList()
    val host = hostOf(url)
    if (host != null && host.endsWith("ytimg.com") && upgraded != url) {
        val hq = url.substringBeforeLast('/') + "/hqdefault.jpg"
        return listOf(upgraded, hq).distinct()
    }
    return listOf(upgraded)
}

/** `i.ytimg.com/vi/<id>/hqdefault.jpg` → the same path with `maxresdefault.jpg`. */
private fun youtubeVideoThumbnail(url: String): String {
    val slash = url.lastIndexOf('/')
    if (slash < 0) return url
    val file = url.substring(slash + 1).substringBefore('?')
    if (!VIDEO_THUMB_NAME.matches(file)) return url
    if (file.startsWith("maxres", ignoreCase = true)) return url
    return url.substring(0, slash + 1) + "maxresdefault.jpg"
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
