/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Pure logic for the stats windows: the epoch-based `since` boundaries and the
 * headline month label. No Android runtime required.
 */
class StatsPeriodTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `this month starts at the first day of the month`() {
        val now = utcMillis(2026, 5, 20, 15, 30)
        val expected = utcMillis(2026, 5, 1, 0, 0)
        assertEquals(expected, StatsPeriod.THIS_MONTH.since(now))
    }

    @Test
    fun `this month boundary survives month and year edges`() {
        val now = utcMillis(2026, 1, 10, 8, 0)
        val expected = utcMillis(2026, 1, 1, 0, 0)
        assertEquals(expected, StatsPeriod.THIS_MONTH.since(now))
    }

    @Test
    fun `last 30 days window is now minus exactly thirty days`() {
        val now = 1_700_000_000_000L
        assertEquals(
            now - 30L * 24 * 60 * 60 * 1000,
            StatsPeriod.LAST_30_DAYS.since(now),
        )
    }

    @Test
    fun `all time has no lower bound`() {
        assertEquals(0L, StatsPeriod.ALL_TIME.since(1_700_000_000_000L))
    }

    @Test
    fun `this month exposes the month name in the requested locale`() {
        val now = utcMillis(2026, 5, 20, 15, 30)
        assertEquals("May", StatsPeriod.THIS_MONTH.monthLabel(now, Locale.ENGLISH))
    }

    @Test
    fun `only this month has a headline month`() {
        val now = utcMillis(2026, 5, 20, 15, 30)
        assertNull(StatsPeriod.LAST_30_DAYS.monthLabel(now, Locale.ENGLISH))
        assertNull(StatsPeriod.ALL_TIME.monthLabel(now, Locale.ENGLISH))
    }

    @Test
    fun `all periods have a visible label`() {
        StatsPeriod.entries.forEach { period ->
            assertEquals(true, period.labelRes != 0)
        }
    }
}
