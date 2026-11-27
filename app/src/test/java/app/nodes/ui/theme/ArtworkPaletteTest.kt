/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The palette the player takes from the artwork.
 *
 * The extraction is deterministic, so a cover of one colour must give back
 * that colour's hue: the background, the seek bar and the control icons all
 * come from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkPaletteTest {

    /** A flat bitmap of one colour. */
    private fun solidBitmap(rgb: Int, size: Int = 64): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                bitmap.setPixel(x, y, AndroidColor.rgb(
                    (rgb shr 16) and 0xFF,
                    (rgb shr 8) and 0xFF,
                    rgb and 0xFF,
                ))
            }
        }
        return bitmap
    }

    @Test
    fun extract_blueArtwork_givesABluePalette() {
        val palette = ArtworkPaletteSampler.extract(solidBitmap(0x3DA9FF))
        assertNotNull(palette)
        requireNotNull(palette)

        // The hue has to survive: a blue cover must not turn the player green.
        val hue = hueOf(palette.dominant)
        assertTrue(
            "expected a blue hue, got $hue for ${palette.dominant}",
            hue > 190f && hue < 250f,
        )
        // The deep stop is darker than the dominant one.
        assertTrue(
            "the deep colour must be darker than the dominant one",
            palette.deep.luminanceValue() < palette.dominant.luminanceValue(),
        )
    }

    @Test
    fun extract_redArtwork_givesARedPalette() {
        val palette = ArtworkPaletteSampler.extract(solidBitmap(0xE04040))
        requireNotNull(palette)
        val hue = hueOf(palette.dominant)
        assertTrue(
            "expected a red hue, got $hue for ${palette.dominant}",
            hue < 30f || hue > 340f,
        )
    }

    @Test
    fun extract_emptyBitmap_returnsNull() {
        assertEquals(null, ArtworkPaletteSampler.extract(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun fallback_isDeterministicAndReadable() {
        val first = ArtworkPaletteSampler.fallback("track:1")
        val second = ArtworkPaletteSampler.fallback("track:1")
        assertEquals(first.dominant, second.dominant)
        assertEquals(first.accent, second.accent)

        // The accent has to stay legible on the dark glass.
        assertTrue(
            "the accent must be readable, was ${first.accent}",
            first.accent.luminanceValue() >= 0.28f,
        )
    }

    @Test
    fun readableAccent_liftsADarkColour() {
        val lifted = Color(AndroidColor.rgb(10, 10, 30)).readableAccent()
        assertTrue(
            "a dark accent must be lifted, was $lifted",
            lifted.luminanceValue() >= 0.28f,
        )
        // The hue survives the lift, so the icon still belongs to the cover.
        val hue = hueOf(lifted)
        assertTrue("expected a blue hue, got $hue", hue > 190f && hue < 250f)
    }

    /** The hue of a colour, in degrees. */
    private fun hueOf(colour: Color): Float {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(colour.toArgb(), hsv)
        return hsv[0]
    }

    /** The relative luminance of a colour, per WCAG. */
    private fun Color.luminanceValue(): Float {
        fun channel(value: Float): Float {
            val v = value.coerceIn(0f, 1f)
            return if (v <= 0.03928f) v / 12.92f else Math.pow(
                ((v + 0.055f) / 1.055f).toDouble(),
                2.4,
            ).toFloat()
        }
        return (0.2126f * channel(red)) +
            (0.7152f * channel(green)) +
            (0.0722f * channel(blue))
    }
}
