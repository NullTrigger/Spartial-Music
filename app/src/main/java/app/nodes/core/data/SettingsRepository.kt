/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.nodes.core.model.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val primarySource: SourceId = SourceId.LOCAL,
    val enabledSources: Set<SourceId> = setOf(SourceId.LOCAL, SourceId.JAMENDO, SourceId.YOUTUBE),
    val dynamicColor: Boolean = false,
    val wifiOnlyStreaming: Boolean = false,
    val highQualityAudio: Boolean = true,
    val youtubeRegion: String = "US",
    val onboardingCompleted: Boolean = false,
    val youtubeAccountEmail: String? = null,
    val youtubeSignedIn: Boolean = false,
    /** Local YouTube quota bookkeeping, see YOUTUBE.md. */
    val quotaDate: String = "",
    val quotaUnitsUsed: Int = 0,
)

/**
 * All user state that is not library data. Deliberately local-only: nothing is
 * uploaded anywhere, and OAuth tokens are never persisted (see PRIVACY.md).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val enabled = prefs[KEY_ENABLED_SOURCES]?.mapNotNull { key ->
            SourceId.entries.firstOrNull { it.key == key }
        }?.toSet() ?: setOf(SourceId.LOCAL, SourceId.JAMENDO, SourceId.YOUTUBE)

        AppSettings(
            primarySource = SourceId.fromKey(prefs[KEY_PRIMARY_SOURCE] ?: SourceId.LOCAL.key),
            enabledSources = enabled,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            wifiOnlyStreaming = prefs[KEY_WIFI_ONLY] ?: false,
            highQualityAudio = prefs[KEY_HIGH_QUALITY] ?: true,
            youtubeRegion = prefs[KEY_YOUTUBE_REGION] ?: "US",
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            youtubeAccountEmail = prefs[KEY_YOUTUBE_EMAIL],
            youtubeSignedIn = prefs[KEY_YOUTUBE_SIGNED_IN] ?: false,
            quotaDate = prefs[KEY_QUOTA_DATE] ?: "",
            quotaUnitsUsed = prefs[KEY_QUOTA_USED] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setPrimarySource(sourceId: SourceId) = dataStore.edit { prefs ->
        prefs[KEY_PRIMARY_SOURCE] = sourceId.key
    }

    suspend fun setSourceEnabled(sourceId: SourceId, enabled: Boolean) = dataStore.edit { prefs ->
        val current = prefs[KEY_ENABLED_SOURCES]?.toMutableSet()
            ?: setOf(SourceId.LOCAL.key, SourceId.JAMENDO.key, SourceId.YOUTUBE.key).toMutableSet()
        if (enabled) current.add(sourceId.key) else current.remove(sourceId.key)
        prefs[KEY_ENABLED_SOURCES] = current
    }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setWifiOnly(enabled: Boolean) = dataStore.edit { it[KEY_WIFI_ONLY] = enabled }

    suspend fun setHighQuality(enabled: Boolean) = dataStore.edit { it[KEY_HIGH_QUALITY] = enabled }

    suspend fun setYoutubeRegion(region: String) = dataStore.edit { it[KEY_YOUTUBE_REGION] = region }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        dataStore.edit { it[KEY_ONBOARDING] = completed }

    suspend fun setYoutubeAccount(email: String?, signedIn: Boolean) = dataStore.edit { prefs ->
        if (email == null) prefs.remove(KEY_YOUTUBE_EMAIL) else prefs[KEY_YOUTUBE_EMAIL] = email
        prefs[KEY_YOUTUBE_SIGNED_IN] = signedIn
    }

    suspend fun setQuota(date: String, used: Int) = dataStore.edit { prefs ->
        prefs[KEY_QUOTA_DATE] = date
        prefs[KEY_QUOTA_USED] = used
    }

    private companion object {
        val KEY_PRIMARY_SOURCE = stringPreferencesKey("primary_source")
        val KEY_ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_HIGH_QUALITY = booleanPreferencesKey("high_quality_audio")
        val KEY_YOUTUBE_REGION = stringPreferencesKey("youtube_region")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_YOUTUBE_EMAIL = stringPreferencesKey("youtube_account_email")
        val KEY_YOUTUBE_SIGNED_IN = booleanPreferencesKey("youtube_signed_in")
        val KEY_QUOTA_DATE = stringPreferencesKey("quota_date")
        val KEY_QUOTA_USED = intPreferencesKey("quota_units_used")
    }
}
