/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtube

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInState {
    data object SignedOut : SignInState
    data object SignedIn : SignInState
    data class Error(val message: String) : SignInState
}

/**
 * FOSS stand-in for the Play services Google Sign-In.
 *
 * The FOSS flavour bundles no Google binary by design (the same rule that
 * keeps Cast out of it), so Google Sign-In cannot exist here. This stub keeps
 * the identical public API and reports every sign-in attempt as unavailable,
 * which the sign-in button already surfaces as a friendly error. The official
 * YouTube source simply stays signed out in this build.
 */
@Singleton
class YouTubeSignIn @Inject constructor(
    @Suppress("UNUSED_PARAMETER") @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
) {
    private val _state = MutableStateFlow<SignInState>(SignInState.SignedOut)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    val isConfigured: Boolean get() = false

    fun currentToken(): String? = null

    fun currentEmail(): String? = null

    fun rememberTokenForTests(token: String?) = Unit

    fun signInIntent(): Intent? = null

    suspend fun completeSignIn(data: Intent?): SignInState {
        val error = SignInState.Error("Google sign-in is not available in this build.")
        _state.value = error
        return error
    }

    fun signOut() {
        _state.value = SignInState.SignedOut
    }

    companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
