/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.nodes.core.lyrics.LyricsProviders
import app.nodes.core.model.EqualizerSettings
import app.nodes.core.model.SourceId
import app.nodes.core.model.decodeEqualizerSettings
import app.nodes.core.model.encode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    val wifiOnlyStreaming: Boolean = false,
    val highQualityAudio: Boolean = true,
    /** Lyrics providers, in the order the user arranged. */
    val lyricsProviderOrder: List<String> = LyricsProviders.DEFAULT_ORDER,
    /**
     * Providers the user switched off; kept separately so order survives.
     * The third-party group ([LyricsProviders.ADDITIONAL_SOURCES]) is off
     * unless the user explicitly switched it on.
     */
    val lyricsDisabledProviders: Set<String> = LyricsProviders.ADDITIONAL_SOURCES,
    val lyricsPreferSynced: Boolean = true,
    val lyricsShowSource: Boolean = true,
    /** Follow the current line while a track plays. Glossy's default. */
    val lyricsAutoScroll: Boolean = true,
    /**
     * Keep one copy of a track in the queue. Adding something already queued
     * moves it instead of listing it twice, which is what made the old queue
     * look like it was repeating the same collection.
     */
    val queuePreventDuplicates: Boolean = true,
    val youtubeRegion: String = "US",
    val onboardingCompleted: Boolean = false,
    /** Onboarding profile: one of the nine local avatars. Never sent anywhere. */
    val profileAvatarId: Int = DEFAULT_AVATAR_ID,
    /** The name shown in Listen Together; blank in storage means [DEFAULT_DISPLAY_NAME]. */
    val profileDisplayName: String = DEFAULT_DISPLAY_NAME,
    val youtubeAccountEmail: String? = null,
    val youtubeSignedIn: Boolean = false,
    /** Local YouTube quota bookkeeping, see YOUTUBE.md. */
    val quotaDate: String = "",
    val quotaUnitsUsed: Int = 0,
    /** Write a crash report to disk when the app dies. On by default. */
    val crashReportsEnabled: Boolean = true,
    /** 0.5x to 2.0x. Applied by the player service as well as the UI. */
    val playbackSpeed: Float = 1f,
    /** Persist the play queue across restarts and restore it paused. */
    val rememberQueue: Boolean = true,
    /** Persist the shuffle state with the queue. */
    val rememberShuffle: Boolean = true,
    /** Keep the queue going with related tracks when it runs low. */
    val autoplaySimilar: Boolean = false,
    val skipSilence: Boolean = false,
    /** Animated Reel covers: user loops and provider videos. On by default. */
    val animatedCovers: Boolean = true,
    /** Whether an animated cover may stream over mobile data. Off by default. */
    val animatedCoversOnMobile: Boolean = false,
    /** The built-in "Saved" playlist's id, created on first use. */
    val savedPlaylistId: String? = null,
    /** Blend into the next track instead of cutting between songs. */
    val automix: Boolean = false,
    /** Fade length in seconds used by Automix. */
    val crossfadeSeconds: Int = 5,
    val keepScreenOn: Boolean = false,
    /**
     * Stop playback and take the media service down when the app is swiped out
     * of recents. Off by default: background playback is the expected behaviour
     * for a music player, and this is an opt-in.
     */
    val stopMusicOnTaskClear: Boolean = false,
    /** Epoch millis at which the sleep timer pauses playback; 0 means off. */
    val sleepTimerEndsAt: Long = 0L,
)

private const val DEFAULT_AVATAR_ID = 1
private const val MIN_AVATAR_ID = 1
private const val MAX_AVATAR_ID = 9
private const val DEFAULT_DISPLAY_NAME = "Listener"

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
            wifiOnlyStreaming = prefs[KEY_WIFI_ONLY] ?: false,
            highQualityAudio = prefs[KEY_HIGH_QUALITY] ?: true,
            lyricsProviderOrder = parseLyricsOrder(prefs[KEY_LYRICS_ORDER]),
            lyricsDisabledProviders = effectiveDisabled(prefs),
            lyricsPreferSynced = prefs[KEY_LYRICS_PREFER_SYNCED] ?: true,
            lyricsShowSource = prefs[KEY_LYRICS_SHOW_SOURCE] ?: true,
            lyricsAutoScroll = prefs[KEY_LYRICS_AUTO_SCROLL] ?: true,
            queuePreventDuplicates = prefs[KEY_QUEUE_PREVENT_DUPLICATES] ?: true,
            youtubeRegion = prefs[KEY_YOUTUBE_REGION] ?: "US",
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            profileAvatarId = prefs[KEY_PROFILE_AVATAR] ?: DEFAULT_AVATAR_ID,
            profileDisplayName = prefs[KEY_PROFILE_NAME].orBlankToNull() ?: DEFAULT_DISPLAY_NAME,
            youtubeAccountEmail = prefs[KEY_YOUTUBE_EMAIL],
            youtubeSignedIn = prefs[KEY_YOUTUBE_SIGNED_IN] ?: false,
            quotaDate = prefs[KEY_QUOTA_DATE] ?: "",
            quotaUnitsUsed = prefs[KEY_QUOTA_USED] ?: 0,
            crashReportsEnabled = prefs[KEY_CRASH_REPORTS] ?: true,
            playbackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1f,
            rememberQueue = prefs[KEY_REMEMBER_QUEUE] ?: true,
            rememberShuffle = prefs[KEY_REMEMBER_SHUFFLE] ?: true,
            autoplaySimilar = prefs[KEY_AUTOPLAY_SIMILAR] ?: false,
            skipSilence = prefs[KEY_SKIP_SILENCE] ?: false,
            animatedCovers = prefs[KEY_ANIMATED_COVERS] ?: true,
            animatedCoversOnMobile = prefs[KEY_ANIMATED_COVERS_MOBILE] ?: false,
            savedPlaylistId = prefs[KEY_SAVED_PLAYLIST],
            automix = prefs[KEY_AUTOMIX] ?: false,
            crossfadeSeconds = prefs[KEY_CROSSFADE_SECONDS] ?: 5,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
            stopMusicOnTaskClear = prefs[KEY_STOP_MUSIC_ON_TASK_CLEAR] ?: false,
            sleepTimerEndsAt = prefs[KEY_SLEEP_TIMER_ENDS_AT] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // One-time settings migration: installs that predate the "Additional
        // lyric sources" disclosure had every provider on. Turn the third-party
        // group off once. Idempotent, and reads fall back to the same default
        // before it lands.
        migrationScope.launch {
            runCatching { migrateLyricsSourcesOnce() }
        }
    }

    /**
     * Writes the third-party lyric sources into the disabled set exactly once,
     * so an updated install matches a fresh one (the group off by default).
     */
    suspend fun migrateLyricsSourcesOnce() = dataStore.edit { prefs ->
        if (prefs[KEY_LYRICS_EXTRA_MIGRATED] != true) {
            prefs[KEY_LYRICS_DISABLED] = effectiveDisabled(prefs)
            prefs[KEY_LYRICS_EXTRA_MIGRATED] = true
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) = dataStore.edit { it[KEY_WIFI_ONLY] = enabled }

    suspend fun setHighQuality(enabled: Boolean) = dataStore.edit { it[KEY_HIGH_QUALITY] = enabled }

    suspend fun setLyricsProviderOrder(order: List<String>) =
        dataStore.edit { it[KEY_LYRICS_ORDER] = order.joinToString(",") }

    suspend fun setLyricsProviderEnabled(id: String, enabled: Boolean) = dataStore.edit { prefs ->
        // Materialise the one-time default first, so switching one source on
        // cannot silently switch the whole third-party group on with it.
        val disabled = effectiveDisabled(prefs)
        prefs[KEY_LYRICS_DISABLED] = if (enabled) disabled - id else disabled + id
        prefs[KEY_LYRICS_EXTRA_MIGRATED] = true
    }

    suspend fun setLyricsPreferSynced(enabled: Boolean) =
        dataStore.edit { it[KEY_LYRICS_PREFER_SYNCED] = enabled }

    suspend fun setLyricsShowSource(enabled: Boolean) =
        dataStore.edit { it[KEY_LYRICS_SHOW_SOURCE] = enabled }

    suspend fun setLyricsAutoScroll(enabled: Boolean) =
        dataStore.edit { it[KEY_LYRICS_AUTO_SCROLL] = enabled }

    suspend fun setQueuePreventDuplicates(enabled: Boolean) =
        dataStore.edit { it[KEY_QUEUE_PREVENT_DUPLICATES] = enabled }

    suspend fun setYoutubeRegion(region: String) = dataStore.edit { it[KEY_YOUTUBE_REGION] = region }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        dataStore.edit { it[KEY_ONBOARDING] = completed }

    /** The onboarding avatar, clamped to the nine the picker offers. */
    val profileAvatarId: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_PROFILE_AVATAR] ?: DEFAULT_AVATAR_ID).coerceIn(MIN_AVATAR_ID, MAX_AVATAR_ID)
    }

    suspend fun setProfileAvatarId(id: Int) = dataStore.edit { prefs ->
        prefs[KEY_PROFILE_AVATAR] = id.coerceIn(MIN_AVATAR_ID, MAX_AVATAR_ID)
    }

    /** The local display name. Empty or whitespace-only falls back to "Listener". */
    val profileDisplayName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PROFILE_NAME].orBlankToNull() ?: DEFAULT_DISPLAY_NAME
    }

    suspend fun setProfileDisplayName(name: String) = dataStore.edit { prefs ->
        prefs[KEY_PROFILE_NAME] = name.trim().ifEmpty { DEFAULT_DISPLAY_NAME }
    }

    suspend fun setYoutubeAccount(email: String?, signedIn: Boolean) = dataStore.edit { prefs ->
        if (email == null) prefs.remove(KEY_YOUTUBE_EMAIL) else prefs[KEY_YOUTUBE_EMAIL] = email
        prefs[KEY_YOUTUBE_SIGNED_IN] = signedIn
    }

    suspend fun setCrashReportsEnabled(enabled: Boolean) =
        dataStore.edit { it[KEY_CRASH_REPORTS] = enabled }

    suspend fun setPlaybackSpeed(speed: Float) =
        dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed.coerceIn(MIN_SPEED, MAX_SPEED) }

    suspend fun setRememberQueue(enabled: Boolean) =
        dataStore.edit { it[KEY_REMEMBER_QUEUE] = enabled }

    suspend fun setRememberShuffle(enabled: Boolean) =
        dataStore.edit { it[KEY_REMEMBER_SHUFFLE] = enabled }

    suspend fun setAutoplaySimilar(enabled: Boolean) =
        dataStore.edit { it[KEY_AUTOPLAY_SIMILAR] = enabled }

    /** Whether the Listen Together privacy notice has been accepted once. */
    val sessionsConsentShown: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SESSIONS_CONSENT] ?: false
    }

    suspend fun setSessionsConsentShown() =
        dataStore.edit { it[KEY_SESSIONS_CONSENT] = true }

    /** Whether the post-sign-in "also follow your local artists?" prompt ran. */
    val followsImportPromptShown: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FOLLOWS_IMPORT_PROMPT] ?: false
    }

    suspend fun setFollowsImportPromptShown() =
        dataStore.edit { it[KEY_FOLLOWS_IMPORT_PROMPT] = true }

    /**
     * Applies the non-secret settings from a library backup. Null fields are
     * left exactly as they are; nothing here touches credentials because the
     * backup model has no field that could hold one.
     */
    suspend fun applyBackupSettings(backup: app.nodes.core.data.BackupSettings) =
        dataStore.edit { prefs ->
            backup.highQualityAudio?.let { prefs[KEY_HIGH_QUALITY] = it }
            backup.wifiOnlyStreaming?.let { prefs[KEY_WIFI_ONLY] = it }
            backup.playbackSpeed?.let { prefs[KEY_PLAYBACK_SPEED] = it.coerceIn(MIN_SPEED, MAX_SPEED) }
            backup.skipSilence?.let { prefs[KEY_SKIP_SILENCE] = it }
            backup.queuePreventDuplicates?.let { prefs[KEY_QUEUE_PREVENT_DUPLICATES] = it }
            backup.rememberQueue?.let { prefs[KEY_REMEMBER_QUEUE] = it }
            backup.rememberShuffle?.let { prefs[KEY_REMEMBER_SHUFFLE] = it }
            backup.autoplaySimilar?.let { prefs[KEY_AUTOPLAY_SIMILAR] = it }
            backup.profileAvatarId?.let { prefs[KEY_PROFILE_AVATAR] = it.coerceIn(MIN_AVATAR_ID, MAX_AVATAR_ID) }
            backup.profileDisplayName?.let { prefs[KEY_PROFILE_NAME] = it.trim().ifEmpty { DEFAULT_DISPLAY_NAME } }
        }

    suspend fun setSkipSilence(enabled: Boolean) =
        dataStore.edit { it[KEY_SKIP_SILENCE] = enabled }

    suspend fun setAutomix(enabled: Boolean) =
        dataStore.edit { it[KEY_AUTOMIX] = enabled }

    suspend fun setAnimatedCovers(enabled: Boolean) =
        dataStore.edit { it[KEY_ANIMATED_COVERS] = enabled }

    suspend fun setAnimatedCoversOnMobile(enabled: Boolean) =
        dataStore.edit { it[KEY_ANIMATED_COVERS_MOBILE] = enabled }

    suspend fun setSavedPlaylistId(id: String) =
        dataStore.edit { it[KEY_SAVED_PLAYLIST] = id }

    suspend fun setCrossfadeSeconds(seconds: Int) =
        dataStore.edit { it[KEY_CROSSFADE_SECONDS] = seconds.coerceIn(1, 12) }

    suspend fun setKeepScreenOn(enabled: Boolean) =
        dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }

    suspend fun setStopMusicOnTaskClear(enabled: Boolean) =
        dataStore.edit { it[KEY_STOP_MUSIC_ON_TASK_CLEAR] = enabled }

    suspend fun setSleepTimerEndsAt(endsAt: Long) =
        dataStore.edit { it[KEY_SLEEP_TIMER_ENDS_AT] = endsAt }

    suspend fun setQuota(date: String, used: Int) = dataStore.edit { prefs ->
        prefs[KEY_QUOTA_DATE] = date
        prefs[KEY_QUOTA_USED] = used
    }

    /** Equalizer state as one JSON blob; see [EqualizerSettings]. */
    val equalizerSettings: Flow<EqualizerSettings> = dataStore.data.map { prefs ->
        decodeEqualizerSettings(prefs[KEY_EQUALIZER])
    }

    suspend fun setEqualizerSettings(equalizer: EqualizerSettings) =
        dataStore.edit { it[KEY_EQUALIZER] = equalizer.encode() }

    private companion object {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_HIGH_QUALITY = booleanPreferencesKey("high_quality_audio")
        val KEY_YOUTUBE_REGION = stringPreferencesKey("youtube_region")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_PROFILE_AVATAR = intPreferencesKey("profile_avatar_id")
        val KEY_PROFILE_NAME = stringPreferencesKey("profile_display_name")
        val KEY_YOUTUBE_EMAIL = stringPreferencesKey("youtube_account_email")
        val KEY_YOUTUBE_SIGNED_IN = booleanPreferencesKey("youtube_signed_in")
        val KEY_QUOTA_DATE = stringPreferencesKey("quota_date")
        val KEY_QUOTA_USED = intPreferencesKey("quota_units_used")
        val KEY_LYRICS_ORDER = stringPreferencesKey("lyrics_provider_order")
        val KEY_LYRICS_DISABLED = stringSetPreferencesKey("lyrics_disabled_providers")
        val KEY_LYRICS_EXTRA_MIGRATED = booleanPreferencesKey("lyrics_extra_sources_migrated")
        val KEY_LYRICS_PREFER_SYNCED = booleanPreferencesKey("lyrics_prefer_synced")
        val KEY_LYRICS_SHOW_SOURCE = booleanPreferencesKey("lyrics_show_source")
        val KEY_LYRICS_AUTO_SCROLL = booleanPreferencesKey("lyrics_auto_scroll")
        val KEY_QUEUE_PREVENT_DUPLICATES = booleanPreferencesKey("queue_prevent_duplicates")
        val KEY_CRASH_REPORTS = booleanPreferencesKey("crash_reports_enabled")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val KEY_AUTOMIX = booleanPreferencesKey("automix")
        val KEY_ANIMATED_COVERS = booleanPreferencesKey("animated_covers")
        val KEY_ANIMATED_COVERS_MOBILE = booleanPreferencesKey("animated_covers_mobile")
        val KEY_SAVED_PLAYLIST = stringPreferencesKey("saved_playlist_id")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_STOP_MUSIC_ON_TASK_CLEAR = booleanPreferencesKey("stop_music_on_task_clear")
        val KEY_SLEEP_TIMER_ENDS_AT = longPreferencesKey("sleep_timer_ends_at")
        val KEY_EQUALIZER = stringPreferencesKey("equalizer_settings")
        val KEY_REMEMBER_QUEUE = booleanPreferencesKey("remember_queue")
        val KEY_REMEMBER_SHUFFLE = booleanPreferencesKey("remember_shuffle")
        val KEY_AUTOPLAY_SIMILAR = booleanPreferencesKey("autoplay_similar")
        val KEY_SESSIONS_CONSENT = booleanPreferencesKey("sessions_consent_shown")
        val KEY_FOLLOWS_IMPORT_PROMPT = booleanPreferencesKey("follows_import_prompt_shown")

        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
    }

    /**
     * The disabled set as the app applies it: the stored choices plus the
     * third-party sources until the one-time migration (or the user's first
     * toggle) has recorded that the default was applied.
     */
    private fun effectiveDisabled(prefs: Preferences): Set<String> {
        val stored = prefs[KEY_LYRICS_DISABLED] ?: emptySet()
        return if (prefs[KEY_LYRICS_EXTRA_MIGRATED] == true) {
            stored
        } else {
            stored + LyricsProviders.ADDITIONAL_SOURCES
        }
    }

    /**
     * Order as stored, with providers added since it was written appended at the
     * bottom: an old string cannot mention a provider that did not exist yet, and
     * dropping it silently would make the new provider unreachable.
     */
    private fun String?.orBlankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseLyricsOrder(stored: String?): List<String> {
        if (stored.isNullOrBlank()) return LyricsProviders.DEFAULT_ORDER
        val saved = stored.split(',').map { it.trim() }.filter { it in LyricsProviders.KNOWN }
        val merged = saved + LyricsProviders.KNOWN.filterNot { it in saved }
        return merged.ifEmpty { LyricsProviders.DEFAULT_ORDER }
    }
}
