/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.HomeSection
import app.nodes.core.model.Track
import app.nodes.ui.components.AlbumCard
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ContinuePlayingCard
import app.nodes.ui.components.EmptyPanel
import app.nodes.ui.components.GeneratedArt
import app.nodes.ui.components.HeroCarousel
import app.nodes.ui.components.HomeTopBar
import app.nodes.ui.components.RailEdgeGlow
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.sourceErrorMessage
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesWhite

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    onOpenCollection: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    avatarInitial: String = "N",
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val heroTracks = state.feed.hero.map { it.track }

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp),
        ) {
            item(key = "hero") {
                HeroCarousel(
                    slides = state.feed.hero,
                    onPlay = { track -> playerViewModel.play(track, heroTracks) },
                )
            }

            // The top bar floats over the hero artwork by design. Without a
            // hero there is nothing under it, so the first banner would be
            // drawn beneath the "Home" title; reserve that space instead.
            if (heroTracks.isEmpty()) {
                item(key = "top-space") {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(72.dp),
                    )
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    EmptyPanel(
                        title = stringResource(R.string.home_offline_banner),
                        description = sourceErrorMessage(error),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item(key = "continue") {
                ContinuePlayingSection(
                    tracks = state.feed.continuePlaying,
                    onTrackClick = { track ->
                        playerViewModel.play(track, state.feed.continuePlaying)
                    },
                    onOpenPlayer = onOpenPlayer,
                )
            }

            items(items = state.feed.sections, key = { it.id }) { section ->
                AlbumRail(
                    section = section,
                    onTrackClick = { track ->
                        val collection = track.collectionId
                        if (collection != null) {
                            onOpenCollection(collection)
                        } else {
                            playerViewModel.play(track, section.tracks)
                        }
                    },
                    onSeeAll = { onOpenSection(section) },
                )
            }

            if (state.loading) {
                item(key = "loading") { SkeletonRail() }
            }
        }

        HomeTopBar(
            onOpenSettings = onOpenSettings,
            avatarInitial = avatarInitial,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun ContinuePlayingSection(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    if (tracks.isEmpty()) return

    SectionHeader(
        title = stringResource(R.string.continue_playing),
        onClick = onOpenPlayer,
    )
    Box(Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = tracks, key = { it.id }) { track ->
                ContinuePlayingCard(track = track, onClick = { onTrackClick(track) })
            }
        }
        RailEdgeGlow(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun AlbumRail(
    section: HomeSection,
    onTrackClick: (Track) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (section.tracks.isEmpty()) return

    Spacer(Modifier.height(12.dp))
    SectionHeader(
        title = section.title,
        subtitle = section.subtitle,
        onClick = onSeeAll,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = section.tracks, key = { it.id }) { track ->
            AlbumCard(track = track, onClick = { onTrackClick(track) })
        }
    }
}

@Composable
private fun SkeletonRail() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { index ->
            GeneratedArt(
                title = "Nodes $index",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(20.dp)),
                showInitial = false,
            )
        }
    }
    Text(
        text = stringResource(R.string.label_loading),
        style = MaterialTheme.typography.labelSmall,
        color = NodesWhite.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
