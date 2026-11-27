/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local bookkeeping for the YouTube Data API's daily quota.
 *
 * Quota resets at midnight Pacific Time, so the local counter keys on the
 * Pacific date rather than the device date. This never talks to Google: it only
 * stops Spartial Music from burning units it knows are gone, and lets the UI explain
 * what happened instead of showing a raw 403.
 */
@Singleton
class QuotaTracker @Inject constructor(
    private val settings: SettingsRepository,
) {
    private fun pacificDate(): String = LocalDate.now(PACIFIC).toString()

    suspend fun usedToday(): Int {
        val current = settings.current()
        return if (current.quotaDate == pacificDate()) current.quotaUnitsUsed else 0
    }

    suspend fun remaining(): Int = (DAILY_QUOTA_UNITS - usedToday()).coerceAtLeast(0)

    suspend fun canAfford(units: Int): Boolean = remaining() >= units

    /**
     * Records a spend. Only call this when a request is actually issued - a
     * cache hit costs nothing.
     */
    suspend fun spend(units: Int) {
        val today = pacificDate()
        val current = settings.current()
        val used = if (current.quotaDate == today) current.quotaUnitsUsed else 0
        settings.setQuota(today, used + units)
    }

    companion object {
        val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

        /** Default Google Cloud project allowance. */
        const val DAILY_QUOTA_UNITS = 10_000

        const val COST_SEARCH = 100
        const val COST_LIST = 1
    }
}
