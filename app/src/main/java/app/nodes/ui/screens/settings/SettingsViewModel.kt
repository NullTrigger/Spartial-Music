/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.core.data.AppSettings
import app.nodes.core.data.QuotaTracker
import app.nodes.core.data.RemoteCache
import app.nodes.core.data.SearchHistoryRepository
import app.nodes.core.data.SettingsRepository
import app.nodes.core.model.SourceAvailability
import app.nodes.core.model.SourceId
import app.nodes.core.source.SourceRegistry
import app.nodes.core.source.youtube.SignInState
import app.nodes.core.source.youtube.YouTubeSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val registry: SourceRegistry,
    private val quota: QuotaTracker,
    private val cache: RemoteCache,
    private val searchHistory: SearchHistoryRepository,
    private val signIn: YouTubeSignIn,
) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _availability = MutableStateFlow<List<SourceAvailability>>(emptyList())
    val availability: StateFlow<List<SourceAvailability>> = _availability.asStateFlow()

    private val _quotaRemaining = MutableStateFlow(QuotaTracker.DAILY_QUOTA_UNITS)
    val quotaRemaining: StateFlow<Int> = _quotaRemaining.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val isSignInConfigured: Boolean get() = signIn.isConfigured
    val signInState: StateFlow<SignInState> = signIn.state

    /** The token is memory-only, so after a restart a fresh sign-in is required. */
    val hasSession: Boolean get() = signIn.currentToken() != null

    init {
        viewModelScope.launch {
            settings.settings.collect {
                refreshAvailability()
            }
        }
    }

    private suspend fun refreshAvailability() {
        _availability.value = registry.all().map { it.availability() }
        _quotaRemaining.value = quota.remaining()
    }

    fun setPrimarySource(sourceId: SourceId) {
        viewModelScope.launch {
            settings.setPrimarySource(sourceId)
            _message.value = "Home and Search now use ${sourceId.key}."
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(enabled) }
    }

    fun setHighQuality(enabled: Boolean) {
        viewModelScope.launch { settings.setHighQuality(enabled) }
    }

    fun setInnerTubeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setInnerTubeEnabled(enabled)
            _message.value = if (enabled) {
                "YouTube Music (unofficial) is on. It can stop working at any time."
            } else {
                "YouTube Music (unofficial) is off."
            }
        }
    }

    fun signInIntent(): Intent? = signIn.signInIntent()

    fun completeSignIn(data: Intent?) {
        viewModelScope.launch {
            when (val result = signIn.completeSignIn(data)) {
                is SignInState.SignedIn -> {
                    settings.setYoutubeAccount(signIn.currentEmail(), true)
                    _message.value = "Signed in as ${signIn.currentEmail() ?: "your Google account"}."
                }

                is SignInState.Error -> _message.value = result.message
                SignInState.SignedOut -> Unit
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signIn.signOut()
            settings.setYoutubeAccount(null, false)
            _message.value = "Signed out. Your local library is untouched."
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cache.clear()
            searchHistory.clear()
            _message.value = "Cached responses cleared."
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
