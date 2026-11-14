/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.HomeSection
import app.nodes.core.model.Track
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ArtworkCard
import app.nodes.ui.components.HomeBannerCarousel
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.BarCollapseBus
import app.nodes.ui.components.NewSongsPager
import app.nodes.ui.components.ScrollTopBus
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.EmptyState
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesSpacing
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite

/**
 * Home is one vertical scroll:
 *
 *  1. the banner carousel with the header and the now-playing block riding on
 *     it, dissolving into black at its foot;
 *  2. Continue Playing - the play history, most recent first;
 *  3. New Songs You May Like - the suggestions algorithm's output;
 *  4. the provider's own shelves (Quick picks and friends), unchanged.
 *
 * The old Recents rail is gone: Continue Playing carries the history now.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSection: (HomeSection) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSuggestions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val banners = state.banners
    val loading = state.loading

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // The bar's contracts: tap-to-top, and collapse when the content
        // scrolls down (past ~24dp), expand again on the way back up.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            ScrollTopBus.events.collect { target ->
                if (target == "home") {
                    BarCollapseBus.set(false)
                    runCatching { listState.scrollToItem(0) }
                }
            }
        }
        androidx.compose.runtime.LaunchedEffect(listState) {
            var lastOffset = 0
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemScrollOffset }
                .collect { offset ->
                    val delta = offset - lastOffset
                    lastOffset = offset
                    if (delta > 24) BarCollapseBus.set(true)
                    else if (delta < -24) BarCollapseBus.set(false)
                }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NodesSpacing.dockSpace),
        ) {
            item(key = "banner") {
                HomeBannerCarousel(
                    banners = banners,
                    loading = loading,
                    onPlay = { track, queue ->
                        playerViewModel.play(track, queue)
                        onOpenPlayer()
                    },
                    header = {
                        HomeHeader(
                            account = account,
                            onOpenSettings = onOpenSettings,
                            onOpenStats = onOpenStats,
                        )
                    },
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorState(
                        error = error,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            // Continue Playing: the history, most recent first, the song that is
            // on right now left out of the row it would only duplicate.
            val history = state.feed.continuePlaying
                .filter { it.id != playerState.current?.id }
            if (history.isNotEmpty()) {
                item(key = "continue-header") {
                    Text(
                        text = stringResource(R.string.continue_playing),
                        style = NodesType.sectionTitle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = NodesWhite,
                        modifier = Modifier.padding(
                            start = NodesSpacing.gutter,
                            end = NodesSpacing.gutter,
                            top = 26.dp,
                            bottom = 12.dp,
                        ),
                    )
                }
                item(key = "continue") {
                    ContinuePlayingRow(
                        tracks = history,
                        onPlay = { track ->
                            playerViewModel.play(track, history)
                        },
                    )
                }
            }

            if (state.newSongs.isNotEmpty() || loading) {
                item(key = "new-songs") {
                    NewSongsPager(
                        songs = state.newSongs,
                        loading = loading,
                        onPlay = { track, queue -> playerViewModel.play(track, queue) },
                        onSeeAll = onOpenSuggestions,
                        modifier = Modifier.padding(top = 26.dp),
                    )
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

            if (loading && banners.isEmpty() && state.newSongs.isEmpty() &&
                state.feed.sections.all { it.tracks.isEmpty() }
            ) {
                item(key = "loading") {
                    LoadingRows()
                }
            }

            if (!loading && banners.isEmpty() && state.newSongs.isEmpty() &&
                state.feed.sections.all { it.tracks.isEmpty() } && state.error == null
            ) {
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
    }
}

/**
 * The home header, riding on the banner: the word "Home" on the left, the
 * settings shortcut and the profile avatar on the right - the same two
 * controls HomeTopBar used to carry, with the wordmark retired.
 */
@Composable
private fun HomeHeader(
    account: InnerTubeAccount?,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NodesSpacing.gutter, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = NodesType.heroTitle.copy(fontSize = 28.sp),
            color = NodesWhite,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.cd_open_settings),
            tint = NodesWhite,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenSettings)
                .padding(6.dp)
                .size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NodesWhite)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenStats),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profileInitial(account),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NodesBlack,
            )
        }
    }
}

/**
 * One history entry, directly on the black background - no card, no fill.
 *
 * The title and artist share one line as a single annotated string: the
 * ellipsis cuts from the right, so the artist (at the end) truncates first and
 * the title second, which is exactly the priority the design asks for. The
 * album line appears whenever the song has one; the model has no producer
 * field, so `Track.album` is the metadata used here.
 */
@Composable
private fun ContinuePlayingRow(
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = NodesSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = tracks, key = { "continue-${it.id}" }) { track ->
            Row(
                modifier = Modifier
                    .fillParentMaxWidth(0.88f)
                    .height(76.dp)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onPlay(track) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    artworkUri = track.artworkUri,
                    title = track.title,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    targetPx = 256,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = NodesWhite,
                                ),
                            ) { append(track.title) }
                            track.artist.takeIf { it.isNotBlank() }?.let { artist ->
                                withStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = NodesWhite.copy(alpha = 0.60f),
                                    ),
                                ) { append(" ($artist)") }
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    track.album?.takeIf { it.isNotBlank() }?.let { album ->
                        Text(
                            text = album,
                            style = NodesType.rowMeta.copy(
                                fontSize = 12.sp,
                                color = NodesWhite.copy(alpha = 0.50f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
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
