/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import app.nodes.core.model.SourceId
import app.nodes.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for YouTube's private InnerTube JSON API (the `youtubei` endpoints).
 *
 * This is the **unofficial** interface used by YouTube's own clients, not a
 * published API. It is not covered by the YouTube API Services Terms, it can
 * change or start requiring attestation tokens without notice, and Google may
 * rate-limit or block traffic that uses it. It exists in Nodes because the
 * maintainer chose to add it on top of the documented Data API path; see
 * YOUTUBE.md and THIRD_PARTY_LICENSES.md.
 *
 * Deliberate limits, so this stays a player and not a ripper:
 *  - search and playlist metadata only; no recommendations engine, no tracking;
 *  - the audio URL handed to Media3 is the one YouTube returns for this request,
 *    with nothing rewritten, deciphered or stripped (the `ANDROID_VR` client
 *    returns plain URLs, so no signature solving is implemented here);
 *  - nothing is written to disk except the artwork Coil caches as usual.
 */
@Singleton
class InnerTubeClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    /** Videos matching [query], newest first as YouTube ranks them. */
    suspend fun search(query: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", webContext())
            put("query", query)
        }
        val root = post("search", payload)
        rendererTracks(root).take(limit.coerceIn(1, 50))
    }

    /** Contents of a YouTube playlist (any playlist id, no `VL` prefix needed). */
    suspend fun playlist(playlistId: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", webContext())
            put("browseId", if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
        }
        val root = post("browse", payload)
        rendererTracks(root).take(limit.coerceIn(1, 100))
    }

    /**
     * Resolves a directly playable audio URL for [videoId], or null when YouTube
     * refuses to serve it (age gate, region block, live stream, private video).
     *
     * The URL is short-lived and bound to the requesting client, so it must be
     * used immediately and never cached or stored.
     */
    suspend fun audioUrl(videoId: String, preferHighQuality: Boolean): String? =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("context", vrContext())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val root = post("player", payload)
            val status = root["playabilityStatus"]?.obj()?.get("status")?.string()
            if (status != null && status != "OK") return@withContext null

            val data = root["streamingData"]?.obj() ?: return@withContext null
            val adaptive = (data["adaptiveFormats"] as? JsonArray)?.toList().orEmpty()
            val progressive = (data["formats"] as? JsonArray)?.toList().orEmpty()

            val audio = (adaptive + progressive)
                .mapNotNull { it as? JsonObject }
                .filter { format ->
                    val mime = format["mimeType"]?.string().orEmpty()
                    mime.startsWith("audio/")
                }
                .mapNotNull { format -> format["url"]?.string()?.let { format to it } }

            val picked = if (preferHighQuality) {
                audio.maxByOrNull { (format, _) -> format["bitrate"]?.int() ?: 0 }
            } else {
                audio.minByOrNull { (format, _) -> format["bitrate"]?.int() ?: 0 }
            }
            picked?.second
        }

    // ---------------------------------------------------------------------

    private fun webContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", WEB_CLIENT)
            put("clientVersion", WEB_CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
        }
    }

    /**
     * The VR client is used for playback only because it answers with plain
     * `url` fields instead of ciphered ones, which keeps signature deciphering
     * out of this project entirely.
     */
    private fun vrContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", VR_CLIENT)
            put("clientVersion", VR_CLIENT_VERSION)
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("osName", "Android")
            put("osVersion", "12")
            put("androidSdkVersion", 32)
            put("hl", "en")
            put("gl", "US")
            put("userAgent", VR_USER_AGENT)
        }
    }

    private fun post(path: String, payload: JsonObject): JsonObject {
        val request = Request.Builder()
            .url("$BASE_URL/$path?key=$PUBLIC_CLIENT_KEY&prettyPrint=false")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", VR_USER_AGENT)
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("InnerTube $path returned HTTP ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            return json.parseToJsonElement(text).jsonObject
        }
    }

    /** Walks the response for video-shaped nodes, whatever page wrapped them. */
    private fun rendererTracks(root: JsonElement): List<Track> {
        val out = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    for (key in VIDEO_KEYS) {
                        (node[key] as? JsonObject)?.toTrack()?.let { track ->
                            if (seen.add(track.id)) out += track
                        }
                    }
                    node.values.forEach(::walk)
                }

                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)
        return out
    }

    private fun JsonObject.toTrack(): Track? {
        val videoId = this["videoId"]?.string() ?: return null
        val title = this["title"]?.text() ?: return null
        val artist = this["ownerText"]?.text()
            ?: this["longBylineText"]?.text()
            ?: this["shortBylineText"]?.text()
            ?: "YouTube Music"
        val artwork = (this["thumbnail"]?.obj()?.get("thumbnails") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("url")?.string() }
            ?.lastOrNull()
        return Track(
            id = "ytmusic-$videoId",
            title = title,
            artist = artist,
            album = null,
            artworkUri = artwork,
            mediaUri = null,
            durationMs = parseClock(this["lengthText"]?.text()),
            sourceId = SourceId.YOUTUBE_MUSIC,
            videoId = videoId,
            externalUrl = "https://music.youtube.com/watch?v=$videoId",
        )
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    /** Reads `{"simpleText": "x"}` or `{"runs": [{"text": "x"}, …]}`. */
    private fun JsonElement?.text(): String? {
        val obj = this as? JsonObject ?: return (this as? JsonPrimitive)?.contentOrNull
        (obj["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        val runs = obj["runs"] as? JsonArray ?: return null
        return runs
            .mapNotNull { (it as? JsonObject)?.get("text")?.string() }
            .joinToString("")
            .ifBlank { null }
    }

    /** "3:42" / "1:02:03" -> milliseconds. */
    private fun parseClock(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        var total = 0L
        for (part in value.trim().split(":")) {
            val number = part.toLongOrNull() ?: return 0L
            total = total * 60 + number
        }
        return total * 1_000L
    }

    private companion object {
        const val BASE_URL = "https://www.youtube.com/youtubei/v1"
        const val WEB_CLIENT = "WEB"
        const val WEB_CLIENT_VERSION = "2.20240726.00.00"
        const val VR_CLIENT = "ANDROID_VR"
        const val VR_CLIENT_VERSION = "1.62.27"
        const val VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12; GB) gzip"

        /** The public key YouTube's own web client ships with; not a secret. */
        const val PUBLIC_CLIENT_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Node names that carry a playable video in search, browse and playlists. */
        val VIDEO_KEYS = listOf(
            "videoRenderer",
            "compactVideoRenderer",
            "playlistVideoRenderer",
            "gridVideoRenderer",
        )
    }
}
