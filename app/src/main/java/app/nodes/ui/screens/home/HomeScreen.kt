/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.home

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.HomeSection
import app.nodes.core.model.Track
import app.nodes.core.source.youtubemusic.InnerTubeAccount
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ArtworkCard
import app.nodes.ui.components.HomeBannerCarousel
import app.nodes.ui.screens.onboarding.avatarDrawableFor
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.BarCollapseBus
import app.nodes.ui.components.BarCollapseOnScroll
import app.nodes.ui.components.NewSongsPager
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.ScrollTopBus
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.EmptyState
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialSpacing
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

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
    onOpenTrending: () -> Unit = {},
    onOpenNewReleases: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val profileAvatarId by viewModel.profileAvatarId.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val banners = state.banners
    val loading = state.loading
    // The queue's suggestions lead the hero. They are the app's own picks for
    // what plays next, so the carousel and the session show the same music
    // instead of two unrelated lists. Nothing is fetched for this: it is the
    // live queue, unchanged by a Home refresh.
    val upNext = playerState.queue
        .drop(playerState.queueIndex + 1)
        .filter { it.videoId != null || it.mediaUri != null }
        .distinctBy { it.id }
        .take(HERO_UP_NEXT_COUNT)
    val upNextIds = upNext.mapTo(HashSet()) { it.id }
    val heroBanners = remember(banners, upNext) {
        heroWithUpNext(banners = banners, upNext = upNext)
    }

    // Coming back to the app re-asks the provider for its rails, so the
    // suggestions are this session's and not the ones fetched before the app
    // was left. See HomeViewModel.refreshOnOpen.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refreshOnOpen() }

    Box(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
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
        BarCollapseOnScroll(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = SpartialSpacing.dockSpace),
        ) {
            item(key = "banner") {
                HomeBannerCarousel(
                    banners = heroBanners,
                    upNextIds = upNextIds,
                    loading = loading,
                    onPlay = { track, queue ->
                        // A carousel slide can only ever be a playable song
                        // now, but a collection card that slipped through must
                        // open its page instead of failing to play.
                        val collection = track.collectionId
                        if (collection != null) {
                            onOpenCollection(collection)
                        } else {
                            playerViewModel.play(track, queue)
                            onOpenPlayer()
                        }
                    },
                    header = {
                        HomeHeader(
                            account = account,
                            avatarId = profileAvatarId,
                            onOpenSettings = onOpenSettings,
                            onOpenStats = onOpenStats,
                        )
                    },
                )
            }

            // Way in to the rest of the app's discovery pages. They sit above
            // the rails rather than in the tab bar, which is already full.
            item(key = "browse") {
                LazyRow(
                    contentPadding = PaddingValues(
                        start = SpartialSpacing.gutter,
                        end = SpartialSpacing.gutter,
                        top = 20.dp,
                        bottom = 4.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "browse-new") {
                        PillButton(
                            text = stringResource(R.string.home_browse_new_releases),
                            onClick = onOpenNewReleases,
                            icon = Icons.Rounded.NewReleases,
                            style = PillStyle.Glass,
                        )
                    }
                    item(key = "browse-trending") {
                        PillButton(
                            text = stringResource(R.string.home_browse_trending),
                            onClick = onOpenTrending,
                            icon = Icons.Rounded.TrendingUp,
                            style = PillStyle.Glass,
                        )
                    }
                    item(key = "browse-for-you") {
                        PillButton(
                            text = stringResource(R.string.home_browse_suggestions),
                            onClick = onOpenSuggestions,
                            icon = Icons.Rounded.AutoAwesome,
                            style = PillStyle.Glass,
                        )
                    }
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

            // Continue Playing: the history, most recent first, the song that is
            // on right now left out of the row it would only duplicate.
            val history = state.feed.continuePlaying
                .filter { it.id != playerState.current?.id }
            if (history.isNotEmpty()) {
                item(key = "continue-header") {
                    Text(
                        text = stringResource(R.string.continue_playing),
                        style = SpartialType.sectionTitle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = SpartialWhite,
                        modifier = Modifier.padding(
                            start = SpartialSpacing.gutter,
                            end = SpartialSpacing.gutter,
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

            if (state.searchSuggestions.isNotEmpty()) {
                item(key = "search-suggestions") {
                    HomeSectionRail(
                        section = HomeSection(
                            id = "search-suggestions",
                            title = stringResource(R.string.home_search_suggestions_title),
                            subtitle = stringResource(R.string.home_search_suggestions_subtitle),
                            tracks = state.searchSuggestions,
                        ),
                        onTrackClick = { track ->
                            val collection = track.collectionId
                            if (collection != null) {
                                onOpenCollection(collection)
                            } else {
                                playerViewModel.play(track, state.searchSuggestions)
                            }
                        },
                        onSeeAll = onOpenSuggestions,
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
                                // Album and playlist cards in the same shelf are
                                // not playable; they must not enter the queue.
                                playerViewModel.play(
                                    track,
                                    section.tracks.filter {
                                        it.videoId != null || it.mediaUri != null
                                    },
                                )
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
    avatarId: Int,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SpartialSpacing.gutter, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = SpartialType.heroTitle.copy(fontSize = 28.sp),
            color = SpartialWhite,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.cd_open_settings),
            tint = SpartialWhite,
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
                .background(SpartialWhite)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenStats),
            contentAlignment = Alignment.Center,
        ) {
            // The avatar picked in onboarding when this build carries its
            // bitmap; the account initial only shows for the drawn fallback.
            val avatarRes = avatarDrawableFor(avatarId)
            if (avatarRes != 0) {
                Image(
                    painter = painterResource(avatarRes),
                    contentDescription = stringResource(R.string.cd_open_history),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = profileInitial(account),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpartialBlack,
                )
            }
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
        contentPadding = PaddingValues(horizontal = SpartialSpacing.gutter),
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
                                    color = SpartialWhite,
                                ),
                            ) { append(track.title) }
                            track.artist.takeIf { it.isNotBlank() }?.let { artist ->
                                withStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = SpartialWhite.copy(alpha = 0.60f),
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
                            style = SpartialType.rowMeta.copy(
                                fontSize = 12.sp,
                                color = SpartialWhite.copy(alpha = 0.50f),
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
        contentPadding = PaddingValues(horizontal = SpartialSpacing.gutter),
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

/** Up-next suggestions that lead the hero before the provider's picks. */
internal const val HERO_UP_NEXT_COUNT = 3

/** How many posters the hero carousel holds, queue suggestions included. */
internal const val HERO_BANNER_LIMIT = 8

/**
 * The hero carousel's order: the queue's next suggestions first, then the
 * provider's banners, deduped and capped. A track both sides offer drops out
 * of the hero entirely - it is about to play anyway, and the slot is better
 * spent on content nothing else covers. The queue already carries the app's
 * own suggestion output, so leading with it makes the hero agree with what is
 * actually about to play instead of offering a second, unrelated list.
 */
internal fun heroWithUpNext(
    banners: List<Track>,
    upNext: List<Track>,
    limit: Int = HERO_BANNER_LIMIT,
): List<Track> {
    val bannerIds = banners.mapTo(HashSet()) { it.id }
    val upNextIds = upNext.mapTo(HashSet()) { it.id }
    val lead = upNext
        .filter { (it.videoId != null || it.mediaUri != null) && it.id !in bannerIds }
        .distinctBy { it.id }
        .take(HERO_UP_NEXT_COUNT)
    return (lead + banners.filterNot { it.id in upNextIds }).take(limit)
}
