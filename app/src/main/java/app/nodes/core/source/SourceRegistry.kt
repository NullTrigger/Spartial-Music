/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source

import app.nodes.core.model.SourceId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the provider the UI should use. Nothing outside this class maps a
 * [SourceId] to an implementation, which keeps provider-specific behaviour
 * (quotas, embedded playback, permissions) contained.
 *
 * The other providers stay registered so their code and data remain reachable
 * through [byId] (playback of an already stored track, for example), but the
 * primary source is fixed: the UI has no source picker any more, so a missing or
 * stale preference can never leave Home or Search without a provider.
 */
@Singleton
class SourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards MusicSource>,
) {
    private val byId: Map<SourceId, MusicSource> = sources.associateBy { it.id }

    fun all(): List<MusicSource> = SourceId.entries.mapNotNull { byId[it] }

    fun byId(id: SourceId): MusicSource? = byId[id]

    fun primary(): MusicSource = byId.getValue(SourceId.YOUTUBE_MUSIC)

    suspend fun forTrack(sourceId: SourceId): MusicSource? = byId[sourceId]
}
