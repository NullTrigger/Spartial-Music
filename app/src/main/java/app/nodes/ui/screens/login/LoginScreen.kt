/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.login

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.SettingsRepository
import app.nodes.core.source.youtubemusic.InnerTubeAuth
import app.nodes.core.source.youtubemusic.InnerTubeClient
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.PageHeader
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: InnerTubeAuth,
    private val innerTube: InnerTubeClient,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /**
     * Validates the cookie the WebView ended up with by asking YouTube for the
     * account menu. Only a cookie that answers with an account is stored; a
     * cookie that only carries anonymous visitor data is discarded, so the app
     * never claims to be signed in when it is not.
     */
    fun complete(cookie: String, visitorData: String?, dataSyncId: String?) {
        if (_state.value.loading || _state.value.signedIn) return
        if (!cookie.contains("SAPISID")) {
            _state.value = LoginUiState(error = "Not signed in yet. Finish the Google login.")
            return
        }

        _state.value = LoginUiState(loading = true)
        viewModelScope.launch {
            auth.beginPendingSession(cookie, visitorData, dataSyncId)
            val account = innerTube.account()
            if (account == null) {
                auth.discardPendingSession()
                _state.value = LoginUiState(
                    error = "Google signed in, but YouTube Music did not accept the session. Try again.",
                )
            } else {
                auth.saveSession(cookie, visitorData, dataSyncId, account)
                settings.setYoutubeAccount(account.email, true)
                _state.value = LoginUiState(signedIn = true)
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}

/**
 * Google sign-in for YouTube Music, through the site itself.
 *
 * The session the music site uses is a cookie, not an OAuth token, so the only
 * honest way to obtain it is to let the user sign in on Google's own page in a
 * WebView and then hand the resulting cookie to [InnerTubeAuth], which stores
 * it encrypted. Nothing here reads the account password, and the cookie is
 * never logged.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MusicLoginScreen(
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var visitorData by remember { mutableStateOf<String?>(null) }
    var dataSyncId by remember { mutableStateOf<String?>(null) }
    var completedAttempt by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun submit() {
        val cookie = CookieManager.getInstance()
            .getCookie(InnerTubeAuth.ORIGIN)
            .orEmpty()
        viewModel.complete(cookie, visitorData, dataSyncId)
    }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onBack()
    }

    BackHandler {
        val current = webView
        if (current?.canGoBack() == true) current.goBack() else onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.login_title),
            back = onBack,
            actions = {
                TextButton(onClick = ::submit) {
                    Text(
                        text = stringResource(R.string.login_done),
                        color = SpartialAccent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )

        Text(
            text = stringResource(R.string.login_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = SpartialGrey,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        // The page below, its progress bar and any error sit in one weighted
        // box: the WebView keeps a constant height for the whole session, so
        // signing in never triggers a Chromium relayout on top of the page's
        // own work. On a low-end device that relayout was the stutter.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // The bridge must only ever be fed by the pages this login
                    // actually uses; any other page loaded in the WebView could
                    // otherwise push visitor/session ids into the app.
                    var pageHost: String? = null
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        val web = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(web, true)
                        }
                        addJavascriptInterface(
                            SessionBridge(
                                hostAllowed = {
                                    val host = pageHost.orEmpty()
                                    host.endsWith("music.youtube.com") ||
                                        host.endsWith("accounts.google.com")
                                },
                            ) { visitor, syncId ->
                                if (!visitor.isNullOrBlank()) visitorData = visitor
                                if (!syncId.isNullOrBlank()) dataSyncId = syncId
                            },
                            "Android",
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                pageHost = url?.let { Uri.parse(it).host }
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                // The music site keeps the visitor id and the account
                                // data-sync id in window.yt.config_; read them once
                                // the page that defines them has loaded.
                                view.evaluateJavascript(
                                    "(function(){try{var c=(window.yt&&window.yt.config_)||{};" +
                                        "Android.onSession(c.VISITOR_DATA||''," +
                                        "(c.DATASYNC_ID||'').split('||')[0]);}catch(e){}})()",
                                    null,
                                )

                                val host = url?.let { Uri.parse(it).host }.orEmpty()
                                val cookie = CookieManager.getInstance()
                                    .getCookie(InnerTubeAuth.ORIGIN)
                                    .orEmpty()
                                if (!completedAttempt &&
                                    host.endsWith("music.youtube.com") &&
                                    cookie.contains("SAPISID")
                                ) {
                                    completedAttempt = true
                                    submit()
                                }
                            }
                        }
                        loadUrl(InnerTubeAuth.LOGIN_URL)
                    }
                },
                // Nothing to push into the view: all state is read inside the
                // factory, so unrelated recompositions leave the page alone.
                update = {},
                onRelease = { view -> view.destroy() },
            )

            // Overlaid instead of laid out above: showing progress must not
            // resize the WebView under the page being signed into.
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = SpartialAccent,
                )
            }

            state.error?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                ) {
                    MessageBanner(message = error, onDismiss = viewModel::dismissError)
                }
            }
        }
    }
}

/**
 * Receives the values the page reads from `window.yt.config_`.
 *
 * [hostAllowed] gates every call: the bridge is a global JavaScript object, so
 * without it any page the WebView ever loads could push values into the app.
 */
private class SessionBridge(
    private val hostAllowed: () -> Boolean,
    private val callback: (visitorData: String?, dataSyncId: String?) -> Unit,
) {
    @JavascriptInterface
    fun onSession(visitorData: String?, dataSyncId: String?) {
        if (!hostAllowed()) return
        callback.invoke(visitorData, dataSyncId)
    }
}
