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
import app.nodes.core.lyrics.LyricsProviders
import app.nodes.core.model.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    /**
     * YouTube Music through InnerTube is the only source the UI can reach. It is
     * a constant rather than a stored preference: the old "Sources" section is
     * gone, and a stale value from an earlier install must never leave Home,
     * Search or playback without a provider.
     */
    val primarySource: SourceId = SourceId.YOUTUBE_MUSIC,
    val dynamicColor: Boolean = false,
    val wifiOnlyStreaming: Boolean = false,
    val highQualityAudio: Boolean = true,
    /** Lyrics providers, in the order the user arranged. */
    val lyricsProviderOrder: List<String> = LyricsProviders.DEFAULT_ORDER,
    /** Providers the user switched off; kept separately so order survives. */
    val lyricsDisabledProviders: Set<String> = emptySet(),
    val lyricsPreferSynced: Boolean = true,
    val lyricsShowSource: Boolean = true,
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
        AppSettings(
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
            wifiOnlyStreaming = prefs[KEY_WIFI_ONLY] ?: false,
            highQualityAudio = prefs[KEY_HIGH_QUALITY] ?: true,
            lyricsProviderOrder = parseLyricsOrder(prefs[KEY_LYRICS_ORDER]),
            lyricsDisabledProviders = prefs[KEY_LYRICS_DISABLED] ?: emptySet(),
            lyricsPreferSynced = prefs[KEY_LYRICS_PREFER_SYNCED] ?: true,
            lyricsShowSource = prefs[KEY_LYRICS_SHOW_SOURCE] ?: true,
            youtubeRegion = prefs[KEY_YOUTUBE_REGION] ?: "US",
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            youtubeAccountEmail = prefs[KEY_YOUTUBE_EMAIL],
            youtubeSignedIn = prefs[KEY_YOUTUBE_SIGNED_IN] ?: false,
            quotaDate = prefs[KEY_QUOTA_DATE] ?: "",
            quotaUnitsUsed = prefs[KEY_QUOTA_USED] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun setWifiOnly(enabled: Boolean) = dataStore.edit { it[KEY_WIFI_ONLY] = enabled }

    suspend fun setHighQuality(enabled: Boolean) = dataStore.edit { it[KEY_HIGH_QUALITY] = enabled }

    suspend fun setLyricsProviderOrder(order: List<String>) =
        dataStore.edit { it[KEY_LYRICS_ORDER] = order.joinToString(",") }

    suspend fun setLyricsProviderEnabled(id: String, enabled: Boolean) = dataStore.edit { prefs ->
        val disabled = prefs[KEY_LYRICS_DISABLED] ?: emptySet()
        prefs[KEY_LYRICS_DISABLED] = if (enabled) disabled - id else disabled + id
    }

    suspend fun setLyricsPreferSynced(enabled: Boolean) =
        dataStore.edit { it[KEY_LYRICS_PREFER_SYNCED] = enabled }

    suspend fun setLyricsShowSource(enabled: Boolean) =
        dataStore.edit { it[KEY_LYRICS_SHOW_SOURCE] = enabled }

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
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_HIGH_QUALITY = booleanPreferencesKey("high_quality_audio")
        val KEY_YOUTUBE_REGION = stringPreferencesKey("youtube_region")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_YOUTUBE_EMAIL = stringPreferencesKey("youtube_account_email")
        val KEY_YOUTUBE_SIGNED_IN = booleanPreferencesKey("youtube_signed_in")
        val KEY_QUOTA_DATE = stringPreferencesKey("quota_date")
        val KEY_QUOTA_USED = intPreferencesKey("quota_units_used")
        val KEY_LYRICS_ORDER = stringPreferencesKey("lyrics_provider_order")
        val KEY_LYRICS_DISABLED = stringSetPreferencesKey("lyrics_disabled_providers")
        val KEY_LYRICS_PREFER_SYNCED = booleanPreferencesKey("lyrics_prefer_synced")
        val KEY_LYRICS_SHOW_SOURCE = booleanPreferencesKey("lyrics_show_source")
    }

    /**
     * Order as stored, with providers added since it was written appended at the
     * bottom: an old string cannot mention a provider that did not exist yet, and
     * dropping it silently would make the new provider unreachable.
     */
    private fun parseLyricsOrder(stored: String?): List<String> {
        if (stored.isNullOrBlank()) return LyricsProviders.DEFAULT_ORDER
        val saved = stored.split(',').map { it.trim() }.filter { it in LyricsProviders.KNOWN }
        val merged = saved + LyricsProviders.KNOWN.filterNot { it in saved }
        return merged.ifEmpty { LyricsProviders.DEFAULT_ORDER }
    }
}
