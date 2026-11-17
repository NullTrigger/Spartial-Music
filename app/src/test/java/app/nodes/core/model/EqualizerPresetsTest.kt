/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preset curves must land correctly on any device band layout, whatever
 * the phone maker chose: 3, 5 or 10 bands, one band count on one handset and
 * another on the next.
 */
class EqualizerPresetsTest {

    private val bands3 = listOf(100_000, 1_000_000, 10_000_000)
    private val bands5 = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000)
    private val bands10 = listOf(
        31_000, 62_000, 125_000, 250_000, 500_000,
        1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_000_000,
    )
    private val allBandSets = listOf(bands3, bands5, bands10)

    @Test
    fun `flat is zero decibels on every band count`() {
        for (bands in allBandSets) {
            val levels = EqualizerPresets.levelsFor(EqualizerPresets.FLAT, bands)
            assertEquals(bands.size, levels.size)
            assertTrue(levels.all { it == 0f })
        }
    }

    @Test
    fun `output always matches the band count`() {
        for (preset in EqualizerPresets.PRESET_IDS) {
            for (bands in allBandSets) {
                assertEquals(bands.size, EqualizerPresets.levelsFor(preset, bands).size)
            }
        }
    }

    @Test
    fun `bass boost lifts the lows above the highs`() {
        for (bands in allBandSets) {
            val levels = EqualizerPresets.levelsFor(EqualizerPresets.BASS_BOOST, bands)
            assertTrue("lowest band must gain more than highest", levels.first() > levels.last())
        }
    }

    @Test
    fun `treble lifts the highs above the lows`() {
        for (bands in allBandSets) {
            val levels = EqualizerPresets.levelsFor(EqualizerPresets.TREBLE, bands)
            assertTrue("highest band must gain more than lowest", levels.last() > levels.first())
        }
    }

    @Test
    fun `vocal peaks in the middle`() {
        val levels = EqualizerPresets.levelsFor(EqualizerPresets.VOCAL, bands5)
        assertEquals(levels.max(), levels[2], 0.0001f)
    }

    @Test
    fun `custom falls back to the flat curve`() {
        val custom = EqualizerPresets.levelsFor(EqualizerPresets.CUSTOM, bands5)
        val flat = EqualizerPresets.levelsFor(EqualizerPresets.FLAT, bands5)
        assertEquals(flat, custom)
    }

    @Test
    fun `curves stay inside a sane decibel window`() {
        for (preset in EqualizerPresets.PRESET_IDS) {
            for (bands in allBandSets) {
                for (level in EqualizerPresets.levelsFor(preset, bands)) {
                    assertTrue("$preset level $level out of range", level in -15f..15f)
                }
            }
        }
    }
}
