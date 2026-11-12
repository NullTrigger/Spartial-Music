/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val tintCache = ConcurrentHashMap<String, Color>()

/**
 * A stable tint for content without artwork. The title seeds one of the
 * curated cinematic accents so a local playlist still has a personality
 * without shipping any third-party image.
 */
fun seedColor(seed: String): Color {
    if (seed.isBlank()) return ArtAccents.first()
    return ArtAccents[abs(seed.hashCode()) % ArtAccents.size]
}

/**
 * Pulls a dominant colour from the artwork and normalises it for a background
 * gradient. Returns the seeded fallback immediately while Coil decodes the
 * image, so the screen never flashes a neutral placeholder.
 */
@Composable
fun rememberArtworkTint(artworkUri: String?, seed: String): Color {
    val fallback = remember(seed) { seedColor(seed) }
    var tint by remember(artworkUri, seed) {
        mutableStateOf(
            artworkUri?.let(tintCache::get) ?: fallback,
        )
    }
    val context = LocalContext.current
    LaunchedEffect(artworkUri, seed) {
        val uri = artworkUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        tintCache[uri]?.let { cached ->
            tint = cached
            return@LaunchedEffect
        }
        val extracted = runCatching {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(96)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val drawable = result.drawable ?: return@withContext null
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: drawable.toBitmap(width = 64, height = 64)
                dominantColor(bitmap)
            }
        }.getOrNull()
        if (extracted != null) {
            tintCache[uri] = extracted
            tint = extracted
        }
    }
    return tint
}

/**
 * Scores pixels by saturation and mid-tone brightness, then returns a colour
 * that works as a gradient start: saturated enough to read, dark enough that
 * white text and controls stay legible.
 */
private fun dominantColor(bitmap: Bitmap): Color? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    val sample = if (bitmap.width > 64 || bitmap.height > 64) {
        Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    } else {
        bitmap
    }
    val hsv = FloatArray(3)
    val buckets = HashMap<Int, FloatArray>()
    val stepX = (sample.width / 32).coerceAtLeast(1)
    val stepY = (sample.height / 32).coerceAtLeast(1)
    var y = 0
    while (y < sample.height) {
        var x = 0
        while (x < sample.width) {
            val pixel = sample.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 40) {
                x += stepX
                continue
            }
            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val saturation = hsv[1]
            val value = hsv[2]
            if (saturation < 0.16f || value < 0.12f || value > 0.96f) {
                x += stepX
                continue
            }
            val hueBucket = ((hue / 30f).toInt() * 30f)
            val bucketIndex = hueBucket.toInt() / 30 +
                (saturation >= 0.5f).let { if (it) 12 else 0 } +
                (value >= 0.55f).let { if (it) 24 else 0 }
            val score = 0.35f + saturation * 0.9f + (1f - abs(value - 0.5f) * 1.1f)
            val entry = buckets.getOrPut(bucketIndex) { floatArrayOf(hueBucket, 0f, 0f, 0f) }
            entry[1] += score
            entry[2] += saturation * score
            entry[3] += value * score
            x += stepX
        }
        y += stepY
    }
    if (sample !== bitmap) sample.recycle()
    val best = buckets.values.maxByOrNull { it[1] } ?: return null
    val weight = best[1].coerceAtLeast(0.001f)
    val hue = best[0]
    val saturation = (best[2] / weight).coerceIn(0.35f, 0.82f)
    val value = (best[3] / weight).let { raw ->
        // Keep the gradient dark enough for white type; bright artwork gets a
        // little more lift than near-black artwork.
        (raw * 0.72f).coerceIn(0.28f, 0.54f)
    }
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
}

/** Converts a tint into the soft, desaturated wash used behind sheets. */
fun Color.toBackdrop(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * 0.78f).coerceIn(0.18f, 0.7f)
    hsv[2] = hsv[2].coerceIn(0.22f, 0.48f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The richer version of [toBackdrop] used by the full-screen player. It keeps
 * more of the artwork's chroma and lifts the value just enough that the
 * gradient reads as the album rather than as a black screen.
 */
fun Color.toPlayerWash(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.46f, 0.80f)
    hsv[2] = hsv[2].coerceIn(0.48f, 0.66f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The lower stop of the player gradient: a darker shade of the same hue rather
 * than a near-black drop, so the controls sit on colour the way the reference
 * screen does.
 */
fun Color.toPlayerDeep(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * 0.94f).coerceIn(0.38f, 0.76f)
    hsv[2] = 0.34f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** A pale, high-contrast version of the tint for sliders and small accents. */
fun Color.toPlayerAccent(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * 0.52f).coerceIn(0.18f, 0.46f)
    hsv[2] = 0.94f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
