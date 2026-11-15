/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The colours the player takes from the current artwork.
 *
 * @param dominant the art's main colour: it tints the blurred background.
 * @param vibrant the art's most colourful hue: it tints the seek bar and the
 *   control icons.
 * @param deep the dominant colour, darkened: the bottom of the gradient.
 * @param accent the vibrant colour, lifted until it stays readable on the
 *   dark glass and on the tinted background.
 */
data class ArtworkPalette(
    val dominant: Color,
    val vibrant: Color,
    val deep: Color,
    val accent: Color,
)

/**
 * How long the artwork and the palette take to crossfade, in ms.
 */
const val EDGE_FADE_MS = 500

/**
 * Extracts a palette from album art, in the spirit of the Palette API but
 * without the dependency: the artwork is decoded once at a small size, the
 * pixels are grouped into hue buckets, and the busiest bucket becomes the
 * dominant colour while the most colourful populated bucket becomes the
 * vibrant one.
 *
 * The work happens on the IO dispatcher and the result is cached per track and
 * artwork, so revisiting a song is instant and a song whose cover changed is
 * sampled again.
 */
object ArtworkPaletteSampler {

    private val cache = ConcurrentHashMap<String, ArtworkPalette>()

    private fun key(trackId: String, artworkUri: String?): String = "$trackId|$artworkUri"

    suspend fun sample(
        context: android.content.Context,
        trackId: String,
        artworkUri: String?,
    ): ArtworkPalette {
        val cacheKey = key(trackId, artworkUri)
        cache[cacheKey]?.let { return it }
        val extracted = artworkUri?.takeIf { it.isNotBlank() }?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .size(SAMPLE_PX)
                        .allowHardware(false)
                        .build()
                    val drawable = context.imageLoader.execute(request).drawable
                        ?: return@withContext null
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                        ?: drawable.toBitmap(width = SAMPLE_PX, height = SAMPLE_PX)
                    extract(bitmap)
                }.getOrNull()
            }
        }
        val palette = extracted ?: fallback(trackId)
        cache[cacheKey] = palette
        return palette
    }

    /**
     * The palette used when there is no artwork, or the extraction failed: a
     * seeded colour from the curated accents, so the screen still has a hue.
     */
    fun fallback(trackId: String): ArtworkPalette {
        val seed = seedColor(trackId)
        val dominant = seed.copy(alpha = 1f).darkened(0.62f)
        val vibrant = seed.copy(alpha = 1f).readableAccent()
        return ArtworkPalette(
            dominant = dominant,
            vibrant = vibrant,
            deep = dominant.darkened(0.45f),
            accent = vibrant.readableAccent(),
        )
    }

    internal fun extract(bitmap: Bitmap): ArtworkPalette? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val sample = if (bitmap.width > SAMPLE_EDGE || bitmap.height > SAMPLE_EDGE) {
            Bitmap.createScaledBitmap(bitmap, SAMPLE_EDGE, SAMPLE_EDGE, true)
        } else {
            bitmap
        }

        // One bucket per 10 degrees of hue.
        val counts = IntArray(HUE_BUCKETS)
        val sums = FloatArray(HUE_BUCKETS)
        val huesX = FloatArray(HUE_BUCKETS)
        val huesY = FloatArray(HUE_BUCKETS)
        val hsv = FloatArray(3)
        var y = 0
        while (y < sample.height) {
            var x = 0
            while (x < sample.width) {
                val pixel = sample.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha >= 40) {
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    // Skip the extremes: pure black says nothing about the
                    // cover's colour, and a white pixel only counts as white
                    // when it has no colour left. A fully saturated colour has
                    // a value of 1 and must not be mistaken for white.
                    val isWhite = value > 0.985f && saturation < 0.15f
                    val isBlack = value < 0.06f
                    if (!isWhite && !isBlack && saturation >= 0.08f) {
                        val bucket = ((hsv[0] / (360f / HUE_BUCKETS)).toInt())
                            .coerceIn(0, HUE_BUCKETS - 1)
                        counts[bucket] += 1
                        sums[bucket] += saturation
                        val radians = Math.toRadians(hsv[0].toDouble())
                        huesX[bucket] += cos(radians).toFloat()
                        huesY[bucket] += sin(radians).toFloat()
                    }
                }
                x += STEP
            }
            y += STEP
        }
        if (sample !== bitmap) sample.recycle()

        val population = counts.max()
        if (population <= 0) return null

        fun meanHue(bucket: Int): Float {
            val x = huesX[bucket]
            val y = huesY[bucket]
            if (x == 0f && y == 0f) return 0f
            val degrees = Math.toDegrees(atan2(y, x).toDouble()).toFloat()
            return (degrees + 360f) % 360f
        }

        fun meanSaturation(bucket: Int): Float = sums[bucket] / counts[bucket].coerceAtLeast(1)

        // The dominant colour: the most populated hue.
        val dominantBucket = (0 until HUE_BUCKETS).maxByOrNull { counts[it] } ?: return null
        val dominantSaturation = meanSaturation(dominantBucket).coerceIn(0.18f, 0.62f)
        val dominant = Color.hsv(
            hue = meanHue(dominantBucket),
            saturation = dominantSaturation,
            value = 0.46f,
        )

        // The vibrant colour: the most colourful bucket with enough pixels to
        // be a real part of the cover, not a stray highlight.
        val vibrantBucket = (0 until HUE_BUCKETS)
            .filter { counts[it] >= max(3, population / 40) }
            .filter { meanSaturation(it) >= 0.28f }
            .maxByOrNull { counts[it] * meanSaturation(it) }
        val vibrant = if (vibrantBucket != null) {
            Color.hsv(
                hue = meanHue(vibrantBucket),
                saturation = meanSaturation(vibrantBucket).coerceIn(0.45f, 0.92f),
                value = 0.92f,
            )
        } else {
            dominant
        }

        return ArtworkPalette(
            dominant = dominant,
            vibrant = vibrant,
            deep = dominant.darkened(0.52f),
            accent = vibrant.readableAccent(),
        )
    }

    private const val SAMPLE_PX = 128
    private const val SAMPLE_EDGE = 64
    private const val STEP = 2
    private const val HUE_BUCKETS = 36
}

/**
 * The palette of the current track, crossfading to a new one whenever the
 * artwork changes.
 *
 * The previous palette stays on screen until the new one has been extracted,
 * so the background never flashes a neutral colour between two songs.
 */
@Composable
fun rememberArtworkPalette(
    trackId: String?,
    artworkUri: String?,
): ArtworkPalette {
    val context = LocalContext.current
    val fallback = remember(trackId) {
        ArtworkPaletteSampler.fallback(trackId.orEmpty())
    }
    var target by remember(trackId, artworkUri) { mutableStateOf(fallback) }

    LaunchedEffect(trackId, artworkUri) {
        val id = trackId ?: return@LaunchedEffect
        target = ArtworkPaletteSampler.sample(context, id, artworkUri)
    }

    val spec = tween<Color>(EDGE_FADE_MS)
    val dominant by animateColorAsState(target.dominant, spec, label = "paletteDominant")
    val vibrant by animateColorAsState(target.vibrant, spec, label = "paletteVibrant")
    val deep by animateColorAsState(target.deep, spec, label = "paletteDeep")
    val accent by animateColorAsState(target.accent, spec, label = "paletteAccent")
    return ArtworkPalette(dominant = dominant, vibrant = vibrant, deep = deep, accent = accent)
}

/** Darkens a colour by scaling its value. */
fun Color.darkened(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Lifts a colour until it stays readable on the dark glass and on the tinted
 * background: icons drawn in it must not disappear into the backdrop.
 *
 * A saturated blue can never be bright in the luminance sense - pure blue's
 * luminance is a twentieth of white's - so the colour is first pushed to a
 * light, slightly desaturated version of its hue, and only then blended
 * towards white while it is still too dark to see.
 */
fun Color.readableAccent(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    // A tint, not a neon, and bright enough to read on the dark glass.
    hsv[1] = hsv[1].coerceAtMost(0.70f)
    hsv[2] = hsv[2].coerceAtLeast(0.92f)
    var result = Color(android.graphics.Color.HSVToColor(hsv))
    var guard = 0
    while (relativeLuminanceOf(result) < MIN_ACCENT_LUMINANCE && guard < 8) {
        result = Color(
            red = result.red + (1f - result.red) * 0.30f,
            green = result.green + (1f - result.green) * 0.30f,
            blue = result.blue + (1f - result.blue) * 0.30f,
            alpha = result.alpha,
        )
        guard++
    }
    return result
}

private const val MIN_ACCENT_LUMINANCE = 0.28f

/** The relative luminance of a colour, per WCAG. */
private fun relativeLuminanceOf(colour: Color): Float {
    fun channel(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.03928f) v / 12.92f else Math.pow(
            ((v + 0.055f) / 1.055f).toDouble(),
            2.4,
        ).toFloat()
    }
    return (0.2126f * channel(colour.red)) +
        (0.7152f * channel(colour.green)) +
        (0.0722f * channel(colour.blue))
}
