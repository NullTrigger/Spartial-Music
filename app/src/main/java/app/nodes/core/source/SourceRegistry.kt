/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source

import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.SourceId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the provider the UI should use. Nothing outside this class maps a
 * [SourceId] to an implementation, which keeps provider-specific behaviour
 * (quotas, embedded playback, permissions) contained.
 */
@Singleton
class SourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards MusicSource>,
    private val settings: SettingsRepository,
) {
    private val byId: Map<SourceId, MusicSource> = sources.associateBy { it.id }

    fun all(): List<MusicSource> = SourceId.entries.mapNotNull { byId[it] }

    fun byId(id: SourceId): MusicSource? = byId[id]

    suspend fun primary(): MusicSource {
        val preferred = settings.settings.first().primarySource
        return byId[preferred] ?: byId.getValue(SourceId.LOCAL)
    }

    suspend fun forTrack(sourceId: SourceId): MusicSource? = byId[sourceId]
}
