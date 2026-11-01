/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.source.youtubemusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are trimmed copies of real YouTube Music answers, kept small enough
 * to read. They pin the fields the app depends on - video id, title, artist,
 * album, clock, artwork, shelf titles - so a change in YouTube's shape shows up
 * here instead of on a device.
 */
class InnerTubeParserTest {

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    private fun nodes(vararg raw: String): JsonArray =
        JsonArray(raw.map { Json.parseToJsonElement(it) })

    private val songRow = """
        {"musicResponsiveListItemRenderer":{
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://img.example/60.jpg","width":60},
            {"url":"https://img.example/400.jpg","width":400}]}}},
          "playlistItemData":{"videoId":"abc123"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"One Love"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Shubh","navigationEndpoint":{"browseEndpoint":{
                "browseId":"UC1","browseEndpointContextSupportedConfigs":{
                  "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
              {"text":" • "},
              {"text":"One Love","navigationEndpoint":{"browseEndpoint":{
                "browseId":"MPRE1","browseEndpointContextSupportedConfigs":{
                  "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}},
              {"text":" • "},
              {"text":"2:40"}]}}}
          ]
        }}
    """.trimIndent()

    private val playlistCard = """
        {"musicTwoRowItemRenderer":{
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://img.example/playlist.jpg","width":226}]}}},
          "title":{"runs":[{"text":"Punjabi Party","navigationEndpoint":{"browseEndpoint":{
            "browseId":"VLPL123","browseEndpointContextSupportedConfigs":{
              "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}}]},
          "subtitle":{"runs":[
            {"text":"Karan Aujla","navigationEndpoint":{"browseEndpoint":{
              "browseId":"UC2","browseEndpointContextSupportedConfigs":{
                "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
            {"text":" • "},{"text":"Playlist"}]}
        }}
    """.trimIndent()

    @Test
    fun `song row keeps title artist album duration and artwork`() {
        val renderer = json(songRow)["musicResponsiveListItemRenderer"]!!.jsonObject
        val track = InnerTubeParser.songRow(renderer)!!

        assertEquals("abc123", track.videoId)
        assertEquals("One Love", track.title)
        assertEquals("Shubh", track.artist)
        assertEquals("One Love", track.album)
        assertEquals(160_000L, track.durationMs)
        assertEquals("https://img.example/400.jpg", track.artworkUri)
    }

    @Test
    fun `a row without a video id is not a song`() {
        val artistRow = """
            {"musicResponsiveListItemRenderer":{
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Anirudh"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}}
              ]
            }}
        """.trimIndent()
        val renderer = json(artistRow)["musicResponsiveListItemRenderer"]!!.jsonObject
        assertNull(InnerTubeParser.songRow(renderer))
    }

    @Test
    fun `playlist card becomes a collection instead of a track`() {
        val renderer = json(playlistCard)["musicTwoRowItemRenderer"]!!.jsonObject
        val card = InnerTubeParser.twoRowCard(renderer)!!

        assertNull(card.videoId)
        assertEquals("VLPL123", card.collectionId)
        assertEquals("Punjabi Party", card.title)
        assertEquals("Karan Aujla", card.artist)
    }

    @Test
    fun `home keeps shelves with items and drops empty ones`() {
        val home = sectionList(
            JsonArray(
                listOf(
                    carousel("Trending", nodes(songRow, playlistCard)),
                    carousel("Empty", JsonArray(emptyList())),
                ),
            ),
        )

        val sections = InnerTubeParser.homeSections(home)
        assertEquals(1, sections.size)
        assertEquals("Trending", sections.first().title)
        assertEquals(2, sections.first().tracks.size)
    }

    @Test
    fun `search reads every music row in order`() {
        val search = buildJsonObject {
            put("contents", nodes(songRow, songRow))
        }
        val tracks = InnerTubeParser.songRows(search, 10)

        // The same video id twice is one song; a continuation would add more.
        assertEquals(1, tracks.size)
        assertEquals("ytmusic-abc123", tracks.first().id)
    }

    @Test
    fun `high quality prefers aac then bitrate`() {
        val player = """
            {"playabilityStatus":{"status":"OK"},
             "streamingData":{"adaptiveFormats":[
               {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":130677,
                "audioQuality":"AUDIO_QUALITY_MEDIUM","url":"https://cdn.example/140"},
               {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":136544,
                "audioQuality":"AUDIO_QUALITY_MEDIUM","url":"https://cdn.example/251"},
               {"itag":139,"mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":50152,
                "audioQuality":"AUDIO_QUALITY_LOW","url":"https://cdn.example/139"}
             ]}}
        """.trimIndent()

        val pick = InnerTubeParser.stream(json(player), preferHighQuality = true)
        assertEquals("https://cdn.example/140", pick.url)
        assertEquals(130_677, pick.bitrate)
        assertTrue(pick.mimeType!!.startsWith("audio/mp4"))
    }

    @Test
    fun `a signed stream is reported as ciphered rather than used`() {
        val player = """
            {"playabilityStatus":{"status":"OK"},
             "streamingData":{"adaptiveFormats":[
               {"itag":140,"mimeType":"audio/mp4","bitrate":130677,
                "signatureCipher":"s=OBSCURED&sp=sig&url=https%3A%2F%2Fcdn.example%2F140"}
             ]}}
        """.trimIndent()

        val pick = InnerTubeParser.stream(json(player), preferHighQuality = true)
        assertNull(pick.url)
        assertTrue(pick.ciphered)
    }

    @Test
    fun `an unplayable answer keeps its reason`() {
        val player = """
            {"playabilityStatus":{"status":"UNPLAYABLE","reason":"Video unavailable"}}
        """.trimIndent()

        val pick = InnerTubeParser.stream(json(player), preferHighQuality = true)
        assertEquals("UNPLAYABLE", pick.status)
        assertEquals("Video unavailable", pick.reason)
        assertNull(pick.url)
    }

    @Test
    fun `visitor data is read from the sw js data guard`() {
        val body = ")]}'\n[null,[null,\"x\"],[\"CgtTESTVISITOR\",\"other\"]]"
        assertEquals("CgtTESTVISITOR", InnerTubeParser.visitorData(body))
    }

    @Test
    fun `clock parsing handles hours`() {
        assertEquals(160_000L, InnerTubeParser.parseClock("2:40"))
        assertEquals(3_723_000L, InnerTubeParser.parseClock("1:02:03"))
        assertEquals(0L, InnerTubeParser.parseClock("n/a"))
    }

    // ------------------------------------------------------------------

    private fun carousel(title: String, contents: JsonArray): JsonObject = buildJsonObject {
        put(
            "musicCarouselShelfRenderer",
            buildJsonObject {
                put(
                    "header",
                    buildJsonObject {
                        put(
                            "musicCarouselShelfBasicHeaderRenderer",
                            buildJsonObject {
                                put(
                                    "title",
                                    buildJsonObject {
                                        put(
                                            "runs",
                                            JsonArray(listOf(buildJsonObject { put("text", title) })),
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
                put("contents", contents)
            },
        )
    }

    private fun sectionList(contents: JsonArray): JsonObject = buildJsonObject {
        put(
            "contents",
            buildJsonObject {
                put(
                    "singleColumnBrowseResultsRenderer",
                    buildJsonObject {
                        put(
                            "tabs",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put(
                                            "tabRenderer",
                                            buildJsonObject {
                                                put(
                                                    "content",
                                                    buildJsonObject {
                                                        put(
                                                            "sectionListRenderer",
                                                            buildJsonObject {
                                                                put("contents", contents)
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                ),
                            ),
                        )
                    },
                )
            },
        )
    }
}
