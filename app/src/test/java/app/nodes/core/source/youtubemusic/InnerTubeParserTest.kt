/*
 * Copyright (C) 2026 The Spartial Music Authors.
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
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Neon Skyline"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Placeholder Trio","navigationEndpoint":{"browseEndpoint":{
                "browseId":"UC1","browseEndpointContextSupportedConfigs":{
                  "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
              {"text":" • "},
              {"text":"Neon Skyline","navigationEndpoint":{"browseEndpoint":{
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
          "title":{"runs":[{"text":"Late Night Mix","navigationEndpoint":{"browseEndpoint":{
            "browseId":"VLPL123","browseEndpointContextSupportedConfigs":{
              "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}}]},
          "subtitle":{"runs":[
            {"text":"Midnight Signals","navigationEndpoint":{"browseEndpoint":{
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
        assertEquals("Neon Skyline", track.title)
        assertEquals("Placeholder Trio", track.artist)
        assertEquals("Neon Skyline", track.album)
        assertEquals("MPRE1", track.albumId)
        assertEquals(160_000L, track.durationMs)
        assertEquals("https://img.example/400.jpg", track.artworkUri)
    }

    @Test
    fun `a row without a video id is not a song`() {
        val artistRow = """
            {"musicResponsiveListItemRenderer":{
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Midnight Signals"}]}}},
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
        assertEquals("Late Night Mix", card.title)
        assertEquals("Midnight Signals", card.artist)
    }

    /**
     * The album-page row shape: no thumbnail, no subtitle column, the
     * duration in a fixed column, and the artist only in the play label.
     */
    private val albumRow = """
        {"musicResponsiveListItemRenderer":{
          "overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{
            "accessibilityPlayData":{"accessibilityData":{"label":"Play Neon Skyline - Placeholder Trio"}}}}}},
          "playlistItemData":{"videoId":"abc123"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Neon Skyline"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"83M plays"}]}}}
          ],
          "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{
            "text":{"runs":[{"text":"2:40"}]}}}]
        }}
    """.trimIndent()

    private val albumHeader = """
        {"header":{"musicDetailHeaderRenderer":{
          "title":{"runs":[{"text":"Signal Bloom"}]},
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://img.example/album60.jpg","width":60,"height":60},
            {"url":"https://img.example/album600.jpg","width":600,"height":600}]}}}
        }}}
    """.trimIndent()

    private val viewModelHeader = """
        {"header":{"pageHeaderViewModel":{"header":{"pageHeaderContentViewModel":{
          "title":{"dynamicTextViewModel":{"text":{"content":"Night Drive"}}},
          "image":{"imageViewModel":{"image":{"sources":[
            {"url":"https://img.example/cover226.jpg","width":226,"height":226},
            {"url":"https://img.example/cover1200.jpg","width":1200,"height":1200}]}}}
        }}}}}
    """.trimIndent()

    @Test
    fun `album row takes its artist from the play label and its duration from the fixed column`() {
        val renderer = json(albumRow)["musicResponsiveListItemRenderer"]!!.jsonObject
        val track = InnerTubeParser.songRow(renderer)!!

        assertEquals("Placeholder Trio", track.artist)
        assertEquals(160_000L, track.durationMs)
        assertNull(track.artworkUri)
    }

    @Test
    fun `album rows borrow the page cover when they have none`() {
        val root = buildJsonObject {
            put("header", json(albumHeader)["header"]!!)
            put("contents", nodes(albumRow))
        }

        val page = InnerTubeParser.collectionPage(root, 10)

        assertEquals("https://img.example/album600.jpg", page.tracks.single().artworkUri)
    }

    @Test
    fun `collection page keeps the album header title and cover`() {
        val root = buildJsonObject {
            put("header", json(albumHeader)["header"]!!)
            put("contents", nodes(songRow))
        }

        val page = InnerTubeParser.collectionPage(root, 10)

        assertEquals("Signal Bloom", page.title)
        assertEquals("https://img.example/album600.jpg", page.artworkUri)
        assertEquals(1, page.tracks.size)
    }

    @Test
    fun `account parser reads the largest profile thumbnail`() {
        val response = json(
            """
            {"activeAccountHeaderRenderer":{
              "accountName":"Listener",
              "email":"listener@example.invalid",
              "avatar":{"thumbnails":[
                {"url":"https://img.example/avatar64.jpg","width":64,"height":64},
                {"url":"https://img.example/avatar256.jpg","width":256,"height":256}]}
            }}
            """.trimIndent(),
        )

        val account = InnerTubeParser.account(response)!!

        assertEquals("Listener", account.name)
        assertEquals("listener@example.invalid", account.email)
        assertEquals("https://img.example/avatar256.jpg", account.imageUrl)
    }

    @Test
    fun `collection page reads the newest header layout`() {
        val page = InnerTubeParser.collectionPage(json(viewModelHeader), 10)

        assertEquals("Night Drive", page.title)
        assertEquals("https://img.example/cover1200.jpg", page.artworkUri)
    }

    @Test
    fun `a page without a header still returns its rows`() {
        val root = buildJsonObject { put("contents", nodes(songRow)) }

        val page = InnerTubeParser.collectionPage(root, 10)

        assertNull(page.title)
        assertNull(page.artworkUri)
        assertEquals(1, page.tracks.size)
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

    /**
     * A genre search answers with editorial playlists inside the song list
     * renderer: the title in the first flex column, a browse endpoint instead
     * of a video, and "50 songs" in a later column.
     */
    private val searchPlaylistRow = """
        {"musicResponsiveListItemRenderer":{
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Disco",
               "navigationEndpoint":{"clickTrackingParams":"x","browseEndpoint":{
                 "browseId":"VLPLdisco","browseEndpointContextSupportedConfigs":{
                   "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"50 songs"}]}}}
          ],
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.example/disco.jpg","width":226,"height":226}]}}}
        }}
    """.trimIndent()

    @Test
    fun `a genre search row is read as a playlist, not dropped`() {
        val root = json("""{"contents":[$searchPlaylistRow]}""")
        val playlists = InnerTubeParser.searchPlaylists(root, 10)

        assertEquals(1, playlists.size)
        val playlist = playlists.single()
        assertEquals("VLPLdisco", playlist.id)
        assertEquals("Disco", playlist.title)
        assertEquals(50, playlist.trackCount)
        assertEquals("https://lh3.example/disco.jpg", playlist.artworkUri)
    }

    @Test
    fun `a genre search row without a playlist endpoint is ignored`() {
        val row = """
            {"musicResponsiveListItemRenderer":{
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Disco"}]}}}
              ]
            }}
        """.trimIndent()
        val root = json("""{"contents":[$row]}""")
        assertTrue(InnerTubeParser.searchPlaylists(root, 10).isEmpty())
    }
}
