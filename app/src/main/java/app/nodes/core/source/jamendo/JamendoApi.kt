/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.jamendo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Jamendo v3.0 API. The catalogue is Creative Commons licensed, which is why it
 * is the demo provider: tracks can be streamed directly and credited properly.
 * Docs: https://developer.jamendo.com/v3.0
 */
interface JamendoApi {

    @GET("tracks/")
    suspend fun tracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
        @Query("order") order: String?,
        @Query("search") search: String?,
        @Query("audioformat") audioFormat: String,
    ): JamendoEnvelope<JamendoTrackDto>

    @GET("playlists/")
    suspend fun playlists(
        @Query("client_id") clientId: String,
        @Query("format") format: String,
        @Query("limit") limit: Int,
        @Query("order") order: String,
    ): JamendoEnvelope<JamendoPlaylistDto>

    @GET("playlists/tracks")
    suspend fun playlistTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String,
        @Query("id") playlistId: String,
        @Query("limit") limit: Int,
        @Query("audioformat") audioFormat: String,
    ): JamendoEnvelope<JamendoTrackDto>
}

@Serializable
data class JamendoEnvelope<T>(
    val headers: JamendoHeaders? = null,
    val results: List<T> = emptyList(),
)

@Serializable
data class JamendoHeaders(
    val status: String? = null,
    val code: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("results_count") val resultsCount: Int = 0,
)

@Serializable
data class JamendoTrackDto(
    val id: String = "",
    val name: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    val album_image: String? = null,
    val image: String? = null,
    val audio: String? = null,
    val audiodownload: String? = null,
    val duration: Int = 0,
    val shareurl: String? = null,
    @SerialName("license_ccurl") val licenseUrl: String? = null,
)

@Serializable
data class JamendoPlaylistDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val image: String? = null,
    val user_name: String? = null,
    @SerialName("tracks_count") val tracksCount: Int = 0,
    val shareurl: String? = null,
)
