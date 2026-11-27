/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodes.BuildConfig
import app.nodes.core.data.SettingsRepository
import app.nodes.core.source.youtube.SignInState
import app.nodes.core.source.youtube.YouTubeSignIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which of the app's two existing sign-in entry points this build offers. */
enum class OnboardingSignInMode {
    /** Play Services Google Sign-In (`YouTubeSignIn`), available in the gms build. */
    GOOGLE,

    /** The in-app WebView login behind [app.nodes.ui.navigation.Routes.LOGIN]. */
    WEBVIEW,

    /** No sign-in at all: the guest button becomes the filled primary. */
    NONE,
}

/** A quiet, dismissible line under the welcome buttons. */
enum class OnboardingNotice { SIGN_IN_CANCELLED, SIGN_IN_FAILED }

data class OnboardingUiState(
    val ready: Boolean = false,
    val signInMode: OnboardingSignInMode = OnboardingSignInMode.WEBVIEW,
    /** The Play build must not name YouTube Music in its copy. */
    val playBuild: Boolean = false,
    val signedIn: Boolean = false,
    val notice: OnboardingNotice? = null,
    val avatarId: Int = OnboardingRules.DEFAULT_AVATAR,
    val username: String = OnboardingRules.DEFAULT_USERNAME,
    val usernameError: Boolean = false,
    /** Set once the stored profile has been read; stops it overwriting edits. */
    val seeded: Boolean = false,
)

/**
 * Backs the three onboarding screens and the Settings "Profile" editor.
 *
 * Everything it touches is local except the sign-in hand-off, which reuses the
 * sign-in entry points the app already had: Google Sign-In where the flavour
 * configures it, otherwise the existing WebView login.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val signIn: YouTubeSignIn,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnboardingUiState(
            signInMode = if (signIn.signInIntent() != null) {
                OnboardingSignInMode.GOOGLE
            } else {
                OnboardingSignInMode.WEBVIEW
            },
            playBuild = BuildConfig.FLAVOR == PLAY_BUILD,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { stored ->
                val current = _state.value
                _state.value = current.copy(
                    ready = true,
                    signedIn = stored.youtubeSignedIn || signIn.currentEmail() != null,
                    avatarId = if (current.seeded) {
                        current.avatarId
                    } else {
                        OnboardingRules.clampAvatar(stored.profileAvatarId)
                    },
                    username = if (current.seeded) current.username else stored.profileDisplayName,
                    seeded = true,
                )
            }
        }
    }

    fun onAvatarSelected(id: Int) {
        _state.value = _state.value.copy(avatarId = OnboardingRules.clampAvatar(id))
    }

    fun onPreviousAvatar() {
        _state.value = _state.value.copy(
            avatarId = OnboardingRules.previousAvatar(_state.value.avatarId),
        )
    }

    fun onNextAvatar() {
        _state.value = _state.value.copy(
            avatarId = OnboardingRules.nextAvatar(_state.value.avatarId),
        )
    }

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, usernameError = false)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    /** Non-null only when this build signs in through Play services. */
    fun googleSignInIntent(): Intent? =
        if (_state.value.signInMode == OnboardingSignInMode.GOOGLE) signIn.signInIntent() else null

    /** Result of the Play services sign-in activity. */
    fun onGoogleSignInResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            _state.value = _state.value.copy(notice = OnboardingNotice.SIGN_IN_CANCELLED)
            return
        }
        viewModelScope.launch {
            when (signIn.completeSignIn(data)) {
                SignInState.SignedIn -> {
                    settings.setYoutubeAccount(signIn.currentEmail(), true)
                    _state.value = _state.value.copy(signedIn = true, notice = null)
                }

                is SignInState.Error -> {
                    signIn.signOut()
                    _state.value = _state.value.copy(notice = OnboardingNotice.SIGN_IN_FAILED)
                }

                SignInState.SignedOut -> Unit
            }
        }
    }

    /**
     * Validates and stores the avatar and name. Returns the name the profile
     * ended up with, or null when the username is invalid and the screen must
     * stay put.
     */
    fun commitProfile(): String? {
        val resolved = OnboardingRules.resolvedUsername(_state.value.username)
            ?: run {
                _state.value = _state.value.copy(usernameError = true)
                return null
            }
        val avatar = OnboardingRules.clampAvatar(_state.value.avatarId)
        _state.value = _state.value.copy(username = resolved, usernameError = false)
        viewModelScope.launch {
            settings.setProfileAvatarId(avatar)
            settings.setProfileDisplayName(resolved)
        }
        return resolved
    }

    /** Marks first-run complete; the flow calls this only on the last screen. */
    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    private companion object {
        /** The flavour Google Sign-In ships in; see `app/build.gradle.kts`. */
        const val PLAY_BUILD = "gms"
    }
}
