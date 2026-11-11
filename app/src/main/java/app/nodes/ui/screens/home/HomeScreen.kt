/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.HomeSection
import app.nodes.core.model.Track
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.ui.components.ArtworkCard
import app.nodes.ui.components.ContinuePlayingCard
import app.nodes.ui.components.HomeHero
import app.nodes.ui.components.HomeTopBar
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.EmptyState
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite

/**
 * Home is a single dark stage: a full-bleed hero, the listener's own recent
 * music, then the provider's shelves. The header floats over the hero artwork;
 * when there is no hero the list reserves the same strip so the wordmark never
 * lands on a section title.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val heroTracks = state.feed.hero.map { it.track }
    val loading = state.loading
    val hasContent = heroTracks.isNotEmpty() ||
        state.feed.continuePlaying.isNotEmpty() ||
        state.recents.isNotEmpty() ||
        state.feed.sections.any { it.tracks.isNotEmpty() }

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NodesSpacing.dockSpace),
        ) {
            if (heroTracks.isNotEmpty()) {
                item(key = "hero") {
                    HomeHero(
                        slides = state.feed.hero,
                        onPlay = { track -> playerViewModel.play(track, heroTracks) },
                    )
                }
            } else {
                item(key = "top-space") {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(72.dp),
                    )
                }
                item(key = "greeting") {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = NodesType.heroTitle,
                        color = NodesWhite,
                        modifier = Modifier.padding(
                            start = NodesSpacing.gutter,
                            end = NodesSpacing.gutter,
                            top = 6.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorState(
                        error = error,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            if (loading && !hasContent) {
                item(key = "loading") {
                    LoadingRows()
                }
            }

            if (state.recents.isNotEmpty()) {
                item(key = "recents-header") {
                    SectionHeader(
                        title = stringResource(R.string.home_recents),
                        onClick = onOpenPlayer,
                    )
                }
                item(key = "recents") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = state.recents, key = { "recent-${it.id}" }) { track ->
                            ArtworkCard(
                                track = track,
                                onClick = { playerViewModel.play(track, state.recents) },
                                width = 136.dp,
                                showPlay = true,
                            )
                        }
                    }
                }
            }

            if (state.recents.isEmpty() && state.feed.continuePlaying.isNotEmpty()) {
                item(key = "continue-header") {
                    SectionHeader(
                        title = stringResource(R.string.continue_playing),
                        onClick = onOpenPlayer,
                    )
                }
                item(key = "continue") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.feed.continuePlaying,
                            key = { "continue-${it.id}" },
                        ) { track ->
                            ContinuePlayingCard(
                                track = track,
                                onClick = {
                                    playerViewModel.play(track, state.feed.continuePlaying)
                                },
                            )
                        }
                    }
                }
            }

            items(items = state.feed.sections, key = { it.id }) { section ->
                if (section.tracks.isNotEmpty()) {
                    HomeSectionRail(
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
            }

            if (!loading && !hasContent && state.error == null) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.home_title),
                        description = stringResource(R.string.home_empty),
                    )
                }
            }

            item(key = "bottom-space") {
                Spacer(Modifier.height(8.dp))
            }
        }

        HomeTopBar(
            onOpenSettings = onOpenSettings,
            onOpenStats = onOpenStats,
            avatarInitial = profileInitial(account),
            signedIn = account != null,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun HomeSectionRail(
    section: HomeSection,
    onTrackClick: (Track) -> Unit,
    onSeeAll: () -> Unit,
) {
    SectionHeader(
        title = section.title,
        subtitle = section.subtitle,
        onClick = onSeeAll,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = section.tracks, key = { "${section.id}-${it.id}" }) { track ->
            ArtworkCard(
                track = track,
                subtitle = track.artist,
                onClick = { onTrackClick(track) },
                width = 150.dp,
                showPlay = true,
            )
        }
    }
}

/**
 * The letter on the avatar: the account's display name first, then its email,
 * and "N" when nobody is signed in. Non-letter characters are skipped so an
 * emoji or a leading space does not end up in the circle.
 */
private fun profileInitial(account: InnerTubeAccount?): String {
    val source = account?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: account?.email?.trim()?.takeIf { it.isNotEmpty() }
        ?: return "N"
    return source.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "N"
}
