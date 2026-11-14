/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * YouTube Data API v3, official endpoints only:
 *   - search.list          (100 units)  -> search
 *   - videos.list chart    (1 unit)     -> Trending / Home hero
 *   - playlists.list       (1 unit)     -> curated rails, user's playlists
 *   - playlistItems.list   (1 unit)     -> playlist contents
 *
 * Docs: https://developers.google.com/youtube/v3/docs
 */
interface YouTubeApi {

    @GET("youtube/v3/search")
    suspend fun search(
        @Query("part") part: String,
        @Query("type") type: String,
        @Query("videoCategoryId") videoCategoryId: String,
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") key: String,
    ): SearchListResponse

    @GET("youtube/v3/videos")
    suspend fun mostPopular(
        @Query("part") part: String,
        @Query("chart") chart: String,
        @Query("videoCategoryId") videoCategoryId: String,
        @Query("regionCode") regionCode: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") key: String,
    ): VideoListResponse

    @GET("youtube/v3/playlists")
    suspend fun popularPlaylists(
        @Query("part") part: String,
        @Query("chart") chart: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") key: String,
    ): PlaylistListResponse

    /** Authorized call: the signed-in user's own playlists. */
    @GET("youtube/v3/playlists")
    suspend fun myPlaylists(
        @Query("part") part: String,
        @Query("mine") mine: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") key: String,
        @Header("Authorization") authorization: String,
    ): PlaylistListResponse

    /** Authorized call: videos the signed-in user rated "like". */
    @GET("youtube/v3/videos")
    suspend fun likedVideos(
        @Query("part") part: String,
        @Query("myRating") myRating: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") key: String,
        @Header("Authorization") authorization: String,
    ): VideoListResponse

    @GET("youtube/v3/playlistItems")
    suspend fun playlistItems(
        @Query("part") part: String,
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int,
        @Query("key") key: String,
    ): PlaylistItemListResponse
}

@Serializable
data class SearchListResponse(
    val items: List<SearchItem> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)

@Serializable
data class SearchItem(
    val id: VideoIdRef? = null,
    val snippet: Snippet? = null,
)

@Serializable
data class VideoIdRef(
    val kind: String? = null,
    val videoId: String? = null,
)

@Serializable
data class Snippet(
    val title: String? = null,
    val channelTitle: String? = null,
    val publishedAt: String? = null,
    val thumbnails: Thumbnails? = null,
)

@Serializable
data class Thumbnails(
    val default: Thumbnail? = null,
    val medium: Thumbnail? = null,
    val high: Thumbnail? = null,
)

@Serializable
data class Thumbnail(
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class VideoListResponse(
    val items: List<VideoItem> = emptyList(),
)

@Serializable
data class VideoItem(
    val id: String? = null,
    val snippet: Snippet? = null,
    val contentDetails: VideoContentDetails? = null,
)

@Serializable
data class VideoContentDetails(
    val duration: String? = null,
)

@Serializable
data class PlaylistListResponse(
    val items: List<PlaylistItem> = emptyList(),
)

@Serializable
data class PlaylistItem(
    val id: String? = null,
    val snippet: Snippet? = null,
    val contentDetails: PlaylistContentDetails? = null,
)

@Serializable
data class PlaylistContentDetails(
    val itemCount: Int = 0,
)

@Serializable
data class PlaylistItemListResponse(
    val items: List<PlaylistEntry> = emptyList(),
)

@Serializable
data class PlaylistEntry(
    val snippet: Snippet? = null,
    val contentDetails: PlaylistEntryDetails? = null,
)

@Serializable
data class PlaylistEntryDetails(
    val videoId: String? = null,
    val videoPublishedAt: String? = null,
)
