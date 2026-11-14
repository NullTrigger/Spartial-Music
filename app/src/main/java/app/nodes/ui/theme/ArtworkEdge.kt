/*
 * Copyright (C) 2026 The Spartial Music Authors.
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Samples the artwork's edge colour: the colour the player fades into.
 *
 * The sample is taken from the part of the artwork that is actually visible and
 * actually touches the fade - not from the bottom of the file. The player lays
 * the cover out scaled to fill the width, centred, so a tall cover is cropped
 * equally top and bottom and its real bottom edge never reaches the screen.
 * The strip is therefore computed in image coordinates after that crop, then
 * averaged, so a single stray pixel cannot decide the whole gradient.
 *
 * Results are cached per track, so going back to a song is instant, and the
 * work happens off the main thread on a 64px copy.
 */
object ArtworkEdgeSampler {

    /** Per-track cache: revisiting a song must not resample it. */
    private val cache = ConcurrentHashMap<String, Color>()

    /** The fallback when there is no artwork, or sampling fails. */
    val fallback: Color = navAccent.copy(
        red = navy(navAccent.red),
        green = navy(navAccent.green),
        blue = navy(navAccent.blue),
    )

    suspend fun sample(
        context: android.content.Context,
        trackId: String,
        artworkUri: String?,
        stageWidthPx: Int,
        stageHeightPx: Int,
    ): Color {
        cache[trackId]?.let { return it }
        if (artworkUri.isNullOrBlank() || stageWidthPx <= 0 || stageHeightPx <= 0) return fallback
        val sampled = withContext(Dispatchers.IO) {
            runCatching { sampleBlocking(context, artworkUri, stageWidthPx, stageHeightPx) }.getOrNull()
        } ?: fallback
        // Only cache a real sample: a failed one would keep returning the
        // fallback for that track even after the artwork loads.
        if (sampled != fallback) cache[trackId] = sampled
        return sampled
    }

    private suspend fun sampleBlocking(
        context: android.content.Context,
        artworkUri: String,
        stageWidthPx: Int,
        stageHeightPx: Int,
    ): Color? {
        val drawable = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(SAMPLE_PX)
                .allowHardware(false)
                .build(),
        ).drawable ?: return null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
            ?: drawable.toBitmap(width = SAMPLE_PX, height = SAMPLE_PX)
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val strip = visibleBottomStrip(bitmap, stageWidthPx, stageHeightPx)
        val average = averageStrip(bitmap, strip) ?: return null
        return readable(average)
    }

    /**
     * The rows of the image that end up at the bottom of the fade.
     *
     * The cover is drawn scaled to the stage width and centred vertically, so
     * when it is taller than the stage it is cropped equally at the top and
     * the bottom: the visible bottom edge sits above the file's bottom edge.
     */
    private fun visibleBottomStrip(bitmap: Bitmap, stageWidthPx: Int, stageHeightPx: Int): IntRange {
        val imageWidth = bitmap.width.toDouble()
        val imageHeight = bitmap.height.toDouble()

        // The stage height expressed in image pixels.
        val stageHeightInImage = stageHeightPx * (imageWidth / stageWidthPx)
        val drawnHeight = imageHeight
        val cropped = max(0.0, (drawnHeight - stageHeightInImage) / 2.0)
        val visibleBottom = (imageHeight - cropped).coerceAtLeast(1.0)
        val visibleTop = cropped.coerceAtMost(visibleBottom - 1)
        val visibleHeight = visibleBottom - visibleTop
        val stripHeight = (visibleHeight * STRIP_FRACTION).coerceAtLeast(1.0)
        val from = (visibleBottom - stripHeight).toInt().coerceIn(0, bitmap.height - 1)
        val to = (visibleBottom - 1).toInt().coerceIn(from, bitmap.height - 1)
        return from..to
    }

    /** The mean colour of the strip, ignoring transparent pixels. */
    private fun averageStrip(bitmap: Bitmap, rows: IntRange): Color? {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (y in rows) {
            if (y < 0 || y >= bitmap.height) continue
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if ((pixel ushr 24) and 0xFF < 40) continue
                red += (pixel shr 16) and 0xFF
                green += (pixel shr 8) and 0xFF
                blue += pixel and 0xFF
                count++
            }
        }
        if (count == 0L) return null
        return Color(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt(),
        )
    }

    /**
     * Clamps the brightness so white text stays readable on top of it:
     * bright covers are darkened, near-black covers are lifted a little so the
     * screen does not go flat.
     */
    private fun readable(colour: Color): Color {
        var result = colour
        var guard = 0
        while (relativeLuminance(result) > 0.34f && guard < 12) {
            result = Color(
                (result.red * 0.88f),
                (result.green * 0.88f),
                (result.blue * 0.88f),
            )
            guard++
        }
        guard = 0
        while (relativeLuminance(result) < 0.045f && guard < 8) {
            result = Color(
                (result.red + (1f - result.red) * 0.06f),
                (result.green + (1f - result.green) * 0.06f),
                (result.blue + (1f - result.blue) * 0.06f),
            )
            guard++
        }
        return result
    }

    private fun relativeLuminance(colour: Color): Float {
        fun channel(value: Float): Float {
            val v = value.coerceIn(0f, 1f)
            return if (v <= 0.03928f) v / 12.92f else Math.pow((v + 0.055) / 1.055, 2.4).toFloat()
        }
        return (0.2126f * channel(colour.red)) +
            (0.7152f * channel(colour.green)) +
            (0.0722f * channel(colour.blue))
    }

    /** The fallback is a deep blue built from the accent, not the accent itself. */
    private fun navy(value: Float): Float = value * 0.34f

    private const val SAMPLE_PX = 64
    private const val STRIP_FRACTION = 0.10
}

/**
 * The player gradient's colour for the current track.
 *
 * The colour is recomputed when the track changes - not when playback is
 * paused or the position moves - and the previous colour stays on screen until
 * the new artwork has actually been sampled, so the art and the wash never
 * disagree. The change itself is a 500 ms crossfade.
 */
@Composable
fun rememberDynamicEdgeColour(
    trackId: String?,
    artworkUri: String?,
    stageWidthPx: Int,
    stageHeightPx: Int,
): Color {
    val context = LocalContext.current

    var target by remember { mutableStateOf(ArtworkEdgeSampler.fallback) }
    val displayed by animateColorAsState(
        targetValue = target,
        animationSpec = tween(EDGE_FADE_MS, easing = FastOutSlowInEasing),
        label = "edgeColour",
    )

    LaunchedEffect(trackId, stageWidthPx, stageHeightPx) {
        val id = trackId ?: return@LaunchedEffect
        // Deliberately not keyed on the artwork URI: the same track id means
        // the same song, and revisiting it must be instant thanks to the cache.
        val sampled = ArtworkEdgeSampler.sample(
            context = context,
            trackId = id,
            artworkUri = artworkUri,
            stageWidthPx = stageWidthPx,
            stageHeightPx = stageHeightPx,
        )
        // Only now does the colour change; until this point the old gradient is
        // still on screen, which is what keeps art and wash matched.
        target = sampled
    }
    return displayed
}

/** How long the artwork and the gradient take to crossfade, in ms. */
const val EDGE_FADE_MS = 500
