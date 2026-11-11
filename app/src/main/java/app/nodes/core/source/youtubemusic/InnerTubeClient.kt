/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import app.nodes.core.model.HomeSection
import app.nodes.core.model.Playlist
import app.nodes.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What YouTube answered when asked for a stream.
 *
 * [Refused] carries a short, per-client account of why nothing was served, so the
 * player can tell the user something more useful than "it failed" and a bug
 * report carries the diagnosis without a log.
 */
sealed interface InnerTubeStream {
    data class Playable(val url: String, val client: String) : InnerTubeStream

    data class Refused(val detail: String) : InnerTubeStream
}

/**
 * One of YouTube's own app identities, as used on the private InnerTube API.
 *
 * YouTube decides what audio it will hand out partly by which app is asking, and
 * the answers change over time: the identity that serves a track this month can
 * return "unplayable" next month. Working players carry several and try them in
 * order, so these profiles are a list rather than a constant. The values are the
 * ones Metrolist and Glossy ship (see THIRD_PARTY_LICENSES.md).
 *
 * [clientId] is not a secret: it is the numeric id YouTube's own web client sends
 * in `X-YouTube-Client-Name`, and the API rejects requests whose header does not
 * match the `clientName` in the body.
 */
internal data class InnerTubeClientProfile(
    val name: String,
    val version: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val includeUserAgentInContext: Boolean = false,
    val embedded: Boolean = false,
    val loginSupported: Boolean = false,
)

/** Everything a home request produced, before the source shapes it into a feed. */
internal data class InnerTubeHome(val sections: List<HomeSection>)

/**
 * Client for YouTube Music's private InnerTube JSON API.
 *
 * This is the **unofficial** interface used by YouTube's own clients, not a
 * published API. It is not covered by the YouTube API Services Terms, it can
 * change or start requiring attestation tokens without notice, and Google may
 * rate-limit or block traffic that uses it. It exists in Nodes because the
 * maintainer chose to add it on top of the documented Data API path; see
 * YOUTUBE.md and THIRD_PARTY_LICENSES.md.
 *
 * The wire format follows the reference player (Glossy/Metrolist):
 *  - every call is a POST to `music.youtube.com/youtubei/v1/<method>`;
 *  - the body carries `context.client` with the matching `clientName` and a
 *    current `clientVersion`, plus `hl` and `gl`;
 *  - the matching `X-YouTube-Client-Name`/`-Version` headers are sent, with
 *    `X-Origin`/`Referer` for the music site and the visitor id when known;
 *  - Home is `browse` with `browseId: FEmusic_home`, search is `search` with
 *    YouTube Music's "songs" filter, playback is `player`.
 *
 * Deliberate limits, so this stays a player and not a ripper:
 *  - nothing is downloaded or re-encoded; the URL handed to Media3 is the one
 *    YouTube returned, and the reachability probe reads a single byte;
 *  - no PoToken minting and no attestation bypass: playback only uses client
 *    identities that answer with a plain `url` field, and a track that only a
 *    token-gated client would serve is reported as unavailable.
 */
@Singleton
class InnerTubeClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: InnerTubeAuth,
) {

    /**
     * Recently minted stream URLs, keyed by `"videoId:quality"`. Only Playable
     * answers are stored; a Refused answer is re-tried on the next play.
     */
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, CachedStream>()

    private fun streamCacheKey(videoId: String, preferHighQuality: Boolean): String =
        "$videoId:${if (preferHighQuality) "hi" else "lo"}"


    // ------------------------------------------------------------------
    // Browsing
    // ------------------------------------------------------------------

    /**
     * The user's home. Signed out this is YouTube Music's anonymous home
     * (playlist and album shelves); signed in it also carries the personalised
     * shelves, because the request is made with the session cookie.
     */
    internal suspend fun home(): InnerTubeHome = withContext(Dispatchers.IO) {
        val root = withRetry {
            post("browse", browseBody("FEmusic_home"), WEB_REMIX, authenticated = true)
        }
        InnerTubeHome(InnerTubeParser.homeSections(root))
    }

    /** Explore carries real song shelves ("Trending") even signed out. */
    internal suspend fun explore(): InnerTubeHome = withContext(Dispatchers.IO) {
        val root = withRetry {
            post("browse", browseBody("FEmusic_explore"), WEB_REMIX, authenticated = true)
        }
        InnerTubeHome(InnerTubeParser.homeSections(root))
    }

    /** Songs matching [query], ranked as YouTube Music ranks them. */
    suspend fun search(query: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", contextFor(WEB_REMIX))
            put("query", query)
            put("params", MUSIC_SONGS_FILTER)
        }
        val root = withRetry { post("search", payload, WEB_REMIX, authenticated = false) }
        InnerTubeParser.songRows(root, limit.coerceIn(1, 50))
    }

    /** What YouTube Music suggests while a query is being typed. */
    suspend fun suggestions(input: String, limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            if (input.isBlank()) return@withContext emptyList()
            val payload = buildJsonObject {
                put("context", contextFor(WEB_REMIX))
                put("input", input)
            }
            runCatching {
                val root = post("music/get_search_suggestions", payload, WEB_REMIX, authenticated = false)
                InnerTubeParser.suggestions(root)
            }.getOrDefault(emptyList()).take(limit)
        }

    /** Tracks of an album, playlist or the liked-songs list ("LM"). */
    suspend fun collection(collectionId: String, limit: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("context", contextFor(WEB_REMIX))
                put("browseId", browseIdFor(collectionId))
            }
            val root = withRetry { post("browse", payload, WEB_REMIX, authenticated = true) }
            InnerTubeParser.collectionTracks(root, limit.coerceIn(1, 200))
        }

    /**
     * The user's own albums and playlists. Empty when signed out (the endpoint
     * then answers with a sign-in prompt instead of a library).
     */
    suspend fun libraryPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn) return@withContext emptyList()
        val root = withRetry {
            post("browse", browseBody("FEmusic_library_landing"), WEB_REMIX, authenticated = true)
        }
        InnerTubeParser.libraryPlaylists(root)
    }

    /** Confirms a captured cookie is a real session, and names its account. */
    suspend fun account(): InnerTubeAccount? = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn) return@withContext null
        val payload = buildJsonObject { put("context", contextFor(WEB_REMIX)) }
        runCatching {
            InnerTubeParser.account(post("account/account_menu", payload, WEB_REMIX, authenticated = true))
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    /**
     * Resolves a directly playable audio URL for [videoId].
     *
     * The clients in [PLAYBACK_CLIENTS] are asked in turn, best first; the first
     * answer that is both `OK` and actually fetchable wins. Each URL is bound to
     * the client that minted it and expires after a few hours, so a minted URL
     * is reused only inside [STREAM_CACHE_TTL_MS] - comfortably shorter than
     * googlevideo's own lifetime. The cache is what makes replays, skipping
     * back, and a prefetched next track start instantly instead of paying for
     * the whole client round-trip again; a stale entry is dropped by
     * [invalidateStream] when the CDN answers 403.
     */
    suspend fun audioUrl(videoId: String, preferHighQuality: Boolean): InnerTubeStream =
        withContext(Dispatchers.IO) {
            val key = streamCacheKey(videoId, preferHighQuality)
            streamCache[key]?.takeIf { it.isFresh() }?.let { cached ->
                return@withContext InnerTubeStream.Playable(cached.url, cached.client)
            }

            val notes = mutableListOf<String>()

            for (profile in PLAYBACK_CLIENTS) {
                val pick = try {
                    requestStream(profile, videoId, preferHighQuality)
                } catch (error: IOException) {
                    notes += "${profile.name}: network error"
                    continue
                }

                if (pick.status != STATUS_OK) {
                    val reason = pick.reason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    notes += "${profile.name}: ${pick.status.humanStatus()}$reason"
                    continue
                }

                val url = pick.url
                if (url == null) {
                    notes += if (pick.ciphered) {
                        "${profile.name}: signed stream, needs a decipher"
                    } else {
                        "${profile.name}: no audio stream"
                    }
                    continue
                }

                if (!isReachable(url, profile.userAgent)) {
                    notes += "${profile.name}: stream rejected by the CDN"
                    continue
                }

                streamCache[key] = CachedStream(url, profile.name, System.currentTimeMillis())
                return@withContext InnerTubeStream.Playable(url, profile.name)
            }

            InnerTubeStream.Refused(notes.joinToString("; "))
        }

    /** Drops the cached URL(s) for [videoId] so the next play re-mints one. */
    fun invalidateStream(videoId: String) {
        streamCache.keys.removeAll { it.startsWith("$videoId:") }
    }

    private class CachedStream(val url: String, val client: String, val mintedAt: Long) {
        fun isFresh(): Boolean =
            System.currentTimeMillis() - mintedAt < STREAM_CACHE_TTL_MS
    }

    private suspend fun requestStream(
        profile: InnerTubeClientProfile,
        videoId: String,
        preferHighQuality: Boolean,
    ): InnerTubeParser.StreamPick {
        val payload = buildJsonObject {
            put("context", contextFor(profile))
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val root = withRetry { post("player", payload, profile, authenticated = true) }
        return InnerTubeParser.stream(root, preferHighQuality)
    }

    /**
     * Whether the CDN will actually serve [url] to [userAgent].
     *
     * The probe is a ranged GET for a single byte, because googlevideo answers
     * HEAD with 403 and would reject every candidate if it were used instead.
     * Media3 would otherwise take the URL and fail later, mid-playback, with the
     * player already showing the track as playing.
     */
    private fun isReachable(url: String, userAgent: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        client.newCall(request).execute().use { response -> response.isSuccessful }
    }.getOrDefault(false)

    // ------------------------------------------------------------------
    // Account writes
    // ------------------------------------------------------------------

    /**
     * Pushes "liked" for [videoId] onto the signed-in account.
     *
     * This is the one write Nodes makes to a YouTube account, under the
     * maintainer decision recorded in YOUTUBE.md. The session cookie already
     * carries that power the moment it is captured; the endpoint is YouTube
     * Music's own `like/like`. Liking a video that is already liked is accepted
     * and counts as success, so a sync can simply replay every local like.
     *
     * @return true when YouTube accepted the call, and false when signed out,
     *         throttled or rejected - the caller shows what that means.
     */
    suspend fun likeVideo(videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn) return@withContext false
        val payload = buildJsonObject {
            put("context", contextFor(WEB_REMIX))
            putJsonObject("target") { put("videoId", videoId) }
            putJsonArray("actions") { add(buildJsonObject { put("action", "LIKE") }) }
        }
        runCatching { postEmptyOk("like/like", payload, WEB_REMIX, authenticated = true) }
            .getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // Wire format
    // ------------------------------------------------------------------

    private fun browseBody(browseId: String): JsonObject = buildJsonObject {
        put("context", contextFor(WEB_REMIX))
        put("browseId", browseId)
    }

    private fun contextFor(profile: InnerTubeClientProfile): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", profile.name)
            put("clientVersion", profile.version)
            if (profile.includeUserAgentInContext) put("userAgent", profile.userAgent)
            profile.osName?.let { put("osName", it) }
            profile.osVersion?.let { put("osVersion", it) }
            profile.deviceMake?.let { put("deviceMake", it) }
            profile.deviceModel?.let { put("deviceModel", it) }
            profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
            put("hl", "en")
            put("gl", "US")
            auth.visitorData()?.let { put("visitorData", it) }
        }
        if (profile.embedded) {
            putJsonObject("thirdParty") { put("embedUrl", EMBED_URL) }
        }
        // The account this request acts as; YouTube ignores it when anonymous.
        if (profile.loginSupported && auth.isSignedIn) {
            auth.dataSyncId()?.let { syncId -> putJsonObject("user") { put("onBehalfOfUser", syncId) } }
        }
    }

    private suspend fun post(
        path: String,
        payload: JsonObject,
        profile: InnerTubeClientProfile,
        authenticated: Boolean,
    ): JsonObject {
        val request = buildRequest(path, payload, profile, authenticated)
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("InnerTube $path returned HTTP ${response.code}")
            }
            return json.parseToJsonElement(text).jsonObjectOrNull()
                ?: throw IOException("InnerTube $path returned an unreadable body")
        }
    }

    /**
     * Like [post] for the write endpoints that answer with an empty body
     * (`like/like` does). Success is an HTTP 2xx and nothing more.
     */
    private suspend fun postEmptyOk(
        path: String,
        payload: JsonObject,
        profile: InnerTubeClientProfile,
        authenticated: Boolean,
    ): Boolean {
        val request = buildRequest(path, payload, profile, authenticated)
        return client.newCall(request).execute().use { response -> response.isSuccessful }
    }

    /**
     * One InnerTube request with every header the endpoint requires: client
     * identity headers matching the body's context, origin headers for the
     * music site, the visitor id, and session cookie plus SAPISIDHASH
     * authorization for authenticated, logged-in-capable profiles.
     */
    private suspend fun buildRequest(
        path: String,
        payload: JsonObject,
        profile: InnerTubeClientProfile,
        authenticated: Boolean,
    ): Request {
        val visitor = auth.ensureVisitorData()
        val url = "$BASE_URL/$path?prettyPrint=false"

        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", profile.clientId)
            .header("X-YouTube-Client-Version", profile.version)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("User-Agent", profile.userAgent)

        visitor?.let { builder.header("X-Goog-Visitor-Id", it) }

        if (authenticated && profile.loginSupported) {
            auth.cookie()?.let { cookie ->
                builder.header("Cookie", cookie)
                auth.authorization()?.let { builder.header("Authorization", it) }
                builder.header("X-Goog-AuthUser", "0")
            }
        }

        return builder.build()
    }

    /**
     * Transient socket errors are retried; a 403/400 is not, because repeating
     * the same rejected request only delays the error the user needs to see.
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var last: IOException? = null
        for (attempt in 0 until RETRIES) {
            try {
                return block()
            } catch (error: IOException) {
                last = error
                if (attempt < RETRIES - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw last ?: IOException("InnerTube request failed")
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    /** "UNPLAYABLE" -> "unplayable", so the failure line reads like a sentence. */
    private fun String.humanStatus(): String = lowercase().replace('_', ' ')

    private fun browseIdFor(id: String): String = when {
        // Albums (MPRE…), channel pages (UC…) and already-prefixed playlists are
        // browse ids in their own right; only bare playlist ids need the VL.
        id.startsWith("VL") || id.startsWith("MPRE") || id.startsWith("FEmusic") -> id
        id.startsWith("UC") -> id
        else -> "VL$id"
    }

    companion object {
        /** Where every request goes. The music host is the one the reference uses. */
        const val ORIGIN = "https://music.youtube.com"
        private const val BASE_URL = "$ORIGIN/youtubei/v1"
        private const val STATUS_OK = "OK"
        private const val EMBED_URL = "https://www.reddit.com/"
        private const val RETRIES = 3
        private const val RETRY_DELAY_MS = 500L

        /**
         * How long a minted googlevideo URL may be reused. YouTube's own expiry
         * is roughly six hours; half an hour keeps a wide safety margin so a
         * cached URL still plays even if the clock or the CDN drifts.
         */
        private const val STREAM_CACHE_TTL_MS = 30L * 60_000L

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * YouTube Music's "songs" search filter (the `params` a music search
         * carries). It restricts the answer to songs, which is why an ordinary
         * video - a gameplay clip, for instance - no longer appears.
         */
        const val MUSIC_SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

        /**
         * Browse and search client: the music web client, which is the only one
         * that speaks the music shelves. It needs no token for metadata.
         */
        internal val WEB_REMIX = InnerTubeClientProfile(
            name = "WEB_REMIX",
            version = "1.20260114.03.00",
            clientId = "67",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) " +
                "Gecko/20100101 Firefox/140.0",
            loginSupported = true,
        )

        /**
         * Playback clients, tried in this order.
         *
         * These are the identities that answer with plain `url` fields instead
         * of a ciphered one; WEB_REMIX is deliberately absent because it only
         * serves audio together with a PoToken, which this project does not
         * mint. A track that no client here will serve is reported as
         * unavailable rather than worked around.
         */
        internal val PLAYBACK_CLIENTS = listOf(
            InnerTubeClientProfile(
                name = "VISIONOS",
                version = "0.1",
                clientId = "101",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
                osName = "visionOS",
                osVersion = "1.3.21O771",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
            ),
            InnerTubeClientProfile(
                name = "ANDROID_VR",
                version = "1.65.10",
                clientId = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                    "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = 32,
                includeUserAgentInContext = true,
            ),
            InnerTubeClientProfile(
                name = "ANDROID_VR",
                version = "1.43.32",
                clientId = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
                    "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; " +
                    "Cronet/107.0.5284.2)",
                osName = "Android",
                osVersion = "12",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = 32,
                includeUserAgentInContext = true,
            ),
            InnerTubeClientProfile(
                name = "IOS",
                version = "21.03.1",
                clientId = "5",
                userAgent = "com.google.ios.youtube/21.03.1 " +
                    "(iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
                osVersion = "18.2.22C152",
            ),
            InnerTubeClientProfile(
                name = "IOS",
                version = "21.03.3",
                clientId = "5",
                userAgent = "com.google.ios.youtube/21.03.3 " +
                    "(iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
                osName = "iPadOS",
                osVersion = "17.7.10.21H450",
                deviceMake = "Apple",
                deviceModel = "iPad7,6",
            ),
            InnerTubeClientProfile(
                name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                version = "2.0",
                clientId = "85",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
                embedded = true,
            ),
        )
    }
}
