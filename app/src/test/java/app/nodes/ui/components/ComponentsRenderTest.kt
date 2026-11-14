/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.nodes.core.model.Track
import app.nodes.ui.theme.SpartialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM render tests for the shared chrome. They run on Robolectric so the
 * floating bar, mini player and song row keep composing (and stay reachable by
 * accessibility services) without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentsRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val track = Track(
        id = "test:1",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        durationMs = 180_000L,
    )

    @Test
    fun floatingTabBar_showsFiveTabs_andReportsSelection() {
        var selected: String? = null
        compose.setContent {
            SpartialTheme {
                FloatingTabBar(
                    currentRoute = "home",
                    items = SpartialNavItems,
                    onSelect = { selected = it },
                    hazeState = null,
                )
            }
        }

        SpartialNavItems.forEach { item ->
            compose.onNodeWithTag("tab-${item.route}").assertExists()
        }
        compose.onNodeWithTag("tab-home").assertIsSelected()
        compose.onNodeWithTag("tab-library").assertIsNotSelected()

        compose.onNodeWithTag("tab-library").performClick()
        assertEquals("library", selected)
    }

    @Test
    fun miniPlayer_showsTrack_andTogglesPlayback() {
        var toggles = 0
        compose.setContent {
            SpartialTheme {
                MiniPlayer(
                    track = track,
                    isPlaying = true,
                    isBuffering = false,
                    onClick = {},
                    onToggle = { toggles++ },
                    onNext = {},
                )
            }
        }

        compose.onNodeWithText("Test Song").assertExists()
        compose.onNodeWithText("Test Artist").assertExists()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun songRow_showsMetadata_andHandlesRowAndLikeTaps() {
        var rowClicks = 0
        var likeClicks = 0
        compose.setContent {
            SpartialTheme {
                SongRow(
                    track = track,
                    onClick = { rowClicks++ },
                    modifier = Modifier.testTag("song-row"),
                    position = 1,
                    isLiked = false,
                    onToggleLike = { likeClicks++ },
                    showDownload = false,
                )
            }
        }

        compose.onNodeWithText("Test Song").assertExists()
        compose.onNodeWithText("Test Artist").assertExists()

        compose.onNodeWithTag("song-row").performClick()
        assertEquals(1, rowClicks)

        compose.onNodeWithContentDescription("Save to your library").performClick()
        assertTrue(likeClicks == 1)
    }
}
