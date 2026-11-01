/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** The Google account a YouTube Music cookie belongs to, for the settings row. */
data class InnerTubeAccount(
    val name: String,
    val email: String? = null,
)

/**
 * Holds the YouTube Music web session.
 *
 * Signing in means capturing the cookies the YouTube Music site sets after a
 * Google account login, exactly as the reference player does: there is no OAuth
 * token involved, the site's own cookie *is* the session. Because that cookie
 * can read and write a user's music library, it never touches plain
 * preferences, logs or the network cache: it lives in
 * [EncryptedSharedPreferences], whose key is held by the Android Keystore.
 *
 * Signing out deletes the stored cookie and the WebView cookies, so the next
 * request is anonymous again.
 */
@Singleton
class InnerTubeAuth @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val json: Json,
) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrNull()

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<InnerTubeAccount?> = _account.asStateFlow()

    @Volatile
    private var visitorDataCache: String? = prefs?.getString(KEY_VISITOR_DATA, null)

    /**
     * A cookie captured from the login WebView but not yet validated. It is used
     * for the one `account/account_menu` call that proves it is a session, so a
     * cookie that turns out to be anonymous is never written to storage.
     */
    @Volatile
    private var pendingCookie: String? = null

    @Volatile
    private var pendingDataSyncId: String? = null

    val isSignedIn: Boolean get() = !cookie().isNullOrBlank()

    fun cookie(): String? = (pendingCookie ?: prefs?.getString(KEY_COOKIE, null))
        ?.takeIf { it.isNotBlank() }

    fun dataSyncId(): String? = (pendingDataSyncId ?: prefs?.getString(KEY_DATA_SYNC_ID, null))
        ?.takeIf { it.isNotBlank() }

    fun visitorData(): String? = visitorDataCache

    /**
     * `Authorization: SAPISIDHASH <timestamp>_<sha1(timestamp + " " + SAPISID + " " + origin)>`.
     *
     * This is how the YouTube web app proves a cookie belongs to it; the origin
     * must be the one the request is sent to. Null when signed out or when the
     * cookie carries no SAPISID.
     */
    fun authorization(origin: String = ORIGIN): String? {
        val sapisid = sapisid() ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val digest = sha1("$timestamp $sapisid $origin") ?: return null
        return "SAPISIDHASH ${timestamp}_$digest"
    }

    private fun sapisid(): String? {
        val raw = cookie() ?: return null
        val map = raw.split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) {
                null
            } else {
                part.substring(0, index).trim() to part.substring(index + 1).trim()
            }
        }.toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"] ?: map["__Secure-1PAPISID"]
    }

    /**
     * A visitor id YouTube expects on every request. It is anonymous (it names a
     * device, not a person), so it is fetched once and kept. Failing to get one
     * only costs some personalisation, never a request.
     */
    suspend fun ensureVisitorData(): String? {
        visitorDataCache?.let { return it }
        val fetched = runCatching {
            val request = Request.Builder()
                .url("$ORIGIN/sw.js_data")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                InnerTubeParser.visitorData(response.body?.string().orEmpty())
            }
        }.getOrNull()
        if (fetched != null) {
            visitorDataCache = fetched
            prefs?.edit()?.putString(KEY_VISITOR_DATA, fetched)?.apply()
        }
        return fetched
    }

    /** Uses [cookie] for the next requests without persisting it. */
    fun beginPendingSession(cookie: String, visitorData: String?, dataSyncId: String?) {
        pendingCookie = cookie
        if (!visitorData.isNullOrBlank()) visitorDataCache = visitorData
        if (!dataSyncId.isNullOrBlank()) pendingDataSyncId = dataSyncId
    }

    /** Drops an unvalidated session without touching the stored one. */
    fun discardPendingSession() {
        pendingCookie = null
        pendingDataSyncId = null
    }

    /** Remembers a completed WebView login. Call only after the session validated. */
    fun saveSession(
        cookie: String,
        visitorData: String?,
        dataSyncId: String?,
        account: InnerTubeAccount,
    ) {
        pendingCookie = null
        pendingDataSyncId = null
        prefs?.edit()
            ?.putString(KEY_COOKIE, cookie)
            ?.putString(KEY_VISITOR_DATA, visitorData ?: visitorDataCache)
            ?.putString(KEY_DATA_SYNC_ID, dataSyncId)
            ?.putString(KEY_ACCOUNT_NAME, account.name)
            ?.putString(KEY_ACCOUNT_EMAIL, account.email)
            ?.apply()
        visitorDataCache = visitorData ?: visitorDataCache
        _account.value = account
    }

    fun signOut() {
        pendingCookie = null
        pendingDataSyncId = null
        prefs?.edit()
            ?.remove(KEY_COOKIE)
            ?.remove(KEY_DATA_SYNC_ID)
            ?.remove(KEY_ACCOUNT_NAME)
            ?.remove(KEY_ACCOUNT_EMAIL)
            ?.apply()
        _account.value = null
    }

    private fun readAccount(): InnerTubeAccount? {
        val name = prefs?.getString(KEY_ACCOUNT_NAME, null) ?: return null
        if (prefs.getString(KEY_COOKIE, null).isNullOrBlank()) return null
        return InnerTubeAccount(name = name, email = prefs.getString(KEY_ACCOUNT_EMAIL, null))
    }

    private fun sha1(value: String): String? = runCatching {
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()

    companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

        private const val PREFS_NAME = "innertube_session"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_VISITOR_DATA = "visitor_data"
        private const val KEY_DATA_SYNC_ID = "data_sync_id"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
    }
}
