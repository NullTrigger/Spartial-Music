/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import app.nodes.core.data.db.RemoteCacheDao
import app.nodes.core.data.db.RemoteCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small TTL cache for provider responses. Deliberately dumb: it stores the raw
 * JSON body keyed by a caller-provided string and leaves decoding to the
 * provider, which keeps quota-spending requests out of the network as much as
 * possible (YouTube gives us 10,000 units a day).
 */
@Singleton
class RemoteCache @Inject constructor(
    private val dao: RemoteCacheDao,
) {
    suspend fun get(key: String, maxAgeMs: Long = DEFAULT_TTL_MS): String? {
        val entry = dao.get(key) ?: return null
        val age = System.currentTimeMillis() - entry.fetchedAt
        return if (age in 0..maxAgeMs) entry.body else null
    }

    suspend fun put(key: String, body: String) {
        dao.put(RemoteCacheEntity(key = key, body = body, fetchedAt = System.currentTimeMillis()))
    }

    suspend fun clear() = dao.clear()

    suspend fun prune() = dao.clearOlderThan(System.currentTimeMillis() - MAX_AGE_MS)

    companion object {
        /** Browsing content is cheap to refresh but expensive in quota. */
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
