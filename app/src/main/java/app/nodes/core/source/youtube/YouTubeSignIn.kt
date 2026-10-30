/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtube

import android.accounts.Account
import android.content.Context
import android.content.Intent
import app.nodes.BuildConfig
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInState {
    data object SignedOut : SignInState
    data object SignedIn : SignInState
    data class Error(val message: String) : SignInState
}

/**
 * Optional Google Sign-In, requesting only `youtube.readonly`.
 *
 * The OAuth access token is held in memory for the session and never written to
 * disk, logged, or sent anywhere except Google's own API. Signing in only adds
 * the user's own playlists to the Library rail; the app works fully signed out.
 */
@Singleton
class YouTubeSignIn @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<SignInState>(SignInState.SignedOut)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accountEmail: String? = null

    val isConfigured: Boolean get() = BuildConfig.YOUTUBE_OAUTH_CLIENT_ID.isNotBlank()

    fun currentToken(): String? = accessToken

    fun currentEmail(): String? = accountEmail

    fun rememberTokenForTests(token: String?) {
        accessToken = token
    }

    private fun options(): GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(YOUTUBE_READONLY))
        .requestServerAuthCode(BuildConfig.YOUTUBE_OAUTH_CLIENT_ID)
        .build()

    fun signInIntent(): Intent? {
        if (!isConfigured) return null
        return GoogleSignIn.getClient(context, options()).signInIntent
    }

    /**
     * Completes sign-in and exchanges the account for a short-lived access
     * token. Runs on IO because the Play Services token call is blocking.
     */
    suspend fun completeSignIn(data: Intent?): SignInState = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount = Tasks.await(task)
            val googleAccount: Account? = account.account
            if (googleAccount == null) {
                return@withContext SignInState.Error("Google did not return an account.").also {
                    _state.value = it
                }
            }
            val token = GoogleAuthUtil.getToken(context, googleAccount, "oauth2:$YOUTUBE_READONLY")
            accessToken = token
            accountEmail = account.email
            _state.value = SignInState.SignedIn
            SignInState.SignedIn
        } catch (e: ApiException) {
            val error = SignInState.Error("Sign-in failed (code ${e.statusCode}).")
            _state.value = error
            error
        } catch (e: Exception) {
            val error = SignInState.Error(e.message ?: "Sign-in failed.")
            _state.value = error
            error
        }
    }

    fun signOut() {
        accessToken = null
        accountEmail = null
        _state.value = SignInState.SignedOut
        runCatching { GoogleSignIn.getClient(context, options()).signOut() }
    }

    companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
