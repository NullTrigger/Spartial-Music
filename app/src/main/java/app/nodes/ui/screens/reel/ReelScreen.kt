/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.reel

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ThumbUpAlt
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.ui.components.AddToPlaylistBus
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialGreyDim
import app.nodes.ui.theme.SpartialWhite
import kotlinx.coroutines.delay

/**
 * Spartial Reel: an endless vertical feed whose pages ARE the playback queue.
 *
 * The layout follows the reference: a full-bleed cover with the design's
 * scrim, the title row and text tabs at the top, one action column down the
 * right edge, the track block and "Up next" line at the bottom left, and a
 * 3dp progress hairline above the floating tab bar - which stays visible,
 * with Reel highlighted, while the mini player does not.
 */
@Composable
fun ReelScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ReelViewModel = hiltViewModel(),
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var coverMenuOpen by remember { mutableStateOf(false) }

    val queue = playerState.queue
    val currentIndex = playerState.queueIndex
    val current = playerState.current
    val next = queue.getOrNull(currentIndex + 1)
    val embedded = playerState.embeddedVideoId != null

    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    // The pager mirrors the queue. Nothing playing yet: seed it; opening the
    // screen is also one of the two moments pending likes are retried.
    LaunchedEffect(Unit) { viewModel.onScreenShown() }
    val pageCountState = rememberUpdatedState(queue.size.coerceAtLeast(1))
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
        pageCount = { pageCountState.value },
    )

    // External changes (autoplay advancing, a play from elsewhere) move the
    // pager; a swipe moves playback (below). One step at a time, never while
    // the user is mid-drag, so the two cannot fight.
    LaunchedEffect(currentIndex, queue.size) {
        if (!pagerState.isScrollInProgress &&
            kotlin.math.abs(pagerState.currentPage - currentIndex) <= 1 &&
            pagerState.currentPage != currentIndex
        ) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        viewModel.onPageSettled(pagerState.settledPage)
    }

    // The quiet sync warning expires on its own.
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(5_000)
            viewModel.clearNotice()
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && current != null) viewModel.setCoverFile(current, uri)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        if (embedded) {
            // The official YouTube player cannot live inside a pager: it is
            // a view the WebView owns. Say so instead of faking a feed.
            ReelEmbeddedState(onOpenSettings = onOpenSettings)
        } else if (state.tab == ReelTab.FOLLOWING && state.followingEmpty) {
            ReelFollowingEmpty(onOpenSearch = onOpenSearch)
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // One page kept either side: the next cover is prefetched,
                // nothing further is decoded ahead.
                beyondViewportPageCount = 1,
            ) { page ->
                val track = queue.getOrNull(page)
                if (track == null) {
                    // Swiped faster than the fetch: a quiet shimmer, never an
                    // end screen.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(SpartialBlack),
                        contentAlignment = Alignment.Center,
                    ) {
                        FindingMoreShimmer(reduceMotion = reduceMotion)
                    }
                } else {
                    val isCurrentPage = page == pagerState.currentPage
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(track.id) {
                                detectTapGestures(
                                    onTap = {
                                        if (isCurrentPage) viewModel.togglePlayPause()
                                    },
                                    onLongPress = {
                                        if (isCurrentPage) coverMenuOpen = true
                                    },
                                )
                            },
                    ) {
                        ReelCover(
                            track = track,
                            cover = if (isCurrentPage) state.cover else null,
                            allowAnimated = state.allowAnimated,
                            pageVisible = isCurrentPage,
                            onCoverFailure = { viewModel.onCoverFailure(track) },
                        )
                    }
                }
            }
        }

        if (!embedded && !(state.tab == ReelTab.FOLLOWING && state.followingEmpty)) {
            ReelOverlay(
                current = current,
                next = next,
                isPlaying = playerState.isPlaying,
                isLiked = current != null && current.id in likedIds,
                isSaved = state.savedInPlaylist,
                progress = playerState.progress,
                tab = state.tab,
                reduceMotion = reduceMotion,
                onSearch = onOpenSearch,
                onTab = viewModel::selectTab,
                onTogglePlay = viewModel::togglePlayPause,
                onLike = {
                    if (current != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleLike(current)
                    }
                },
                onPlaylist = { current?.let { AddToPlaylistBus.request(it) } },
                onShare = {
                    current?.let { track ->
                        val text = buildShareText(track)
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                null,
                            ),
                        )
                    }
                },
                onSave = {
                    if (current != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleSaved(current)
                    }
                },
            )
        }

        // Cover menu: the user's own loops only, files only.
        DropdownMenu(
            expanded = coverMenuOpen,
            onDismissRequest = { coverMenuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reel_set_cover)) },
                onClick = {
                    coverMenuOpen = false
                    coverPicker.launch(arrayOf("video/mp4", "video/webm"))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reel_remove_cover)) },
                onClick = {
                    coverMenuOpen = false
                    current?.let(viewModel::removeCover)
                },
            )
        }

        state.notice?.let {
            MessageBanner(
                message = when (it) {
                    ReelViewModel.NOTICE_LIKE_UNSYNCED ->
                        stringResource(R.string.reel_like_unsynced)
                    else -> it
                },
                onDismiss = viewModel::clearNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 190.dp),
            )
        }
    }
}

/** "Title - Artist" plus the track's own page link when it has one. */
private fun buildShareText(track: Track): String {
    val base = "${track.title} - ${track.artist}".trim().trimEnd('-', ' ')
    return when {
        track.sourceId == app.nodes.core.model.SourceId.YOUTUBE_MUSIC &&
            track.videoId != null ->
            "$base https://music.youtube.com/watch?v=${track.videoId}"

        track.externalUrl != null -> "$base ${track.externalUrl}"
        else -> base // Local files share text only, never the file itself.
    }
}

/** Everything drawn over the pager: header, tabs, actions, bottom block. */
@Composable
private fun ReelOverlay(
    current: Track?,
    next: Track?,
    isPlaying: Boolean,
    isLiked: Boolean,
    isSaved: Boolean,
    progress: Float,
    tab: ReelTab,
    reduceMotion: Boolean,
    onSearch: () -> Unit,
    onTab: (ReelTab) -> Unit,
    onTogglePlay: () -> Unit,
    onLike: () -> Unit,
    onPlaylist: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Header: name + search.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reel_title),
                style = reelStyle(20.sp, FontWeight.ExtraBold, SpartialWhite),
            )
            Spacer(Modifier.weight(1f))
            ReelGlassCircle(
                size = 48.dp,
                onClick = onSearch,
                contentDescription = stringResource(R.string.reel_search),
            ) {                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = SpartialWhite,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Text tabs under the header.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 64.dp, start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ReelTabLabel(
                text = stringResource(R.string.reel_tab_for_you),
                selected = tab == ReelTab.FOR_YOU,
                onClick = { onTab(ReelTab.FOR_YOU) },
            )
            ReelTabLabel(
                text = stringResource(R.string.reel_tab_following),
                selected = tab == ReelTab.FOLLOWING,
                onClick = { onTab(ReelTab.FOLLOWING) },
            )
        }

        // Action column, right edge, starting about a third down.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 290.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReelActionButton(
                icon = if (isLiked) Icons.Rounded.ThumbUpAlt else Icons.Rounded.ThumbUpOffAlt,
                label = stringResource(R.string.reel_action_like),
                tint = if (isLiked) LikePink else SpartialWhite,
                onClick = onLike,
            )
            ReelActionButton(
                icon = Icons.Rounded.PlaylistAdd,
                label = stringResource(R.string.reel_action_playlist),
                tint = SpartialWhite,
                onClick = onPlaylist,
            )
            ReelActionButton(
                icon = Icons.AutoMirrored.Rounded.Send,
                label = stringResource(R.string.reel_action_share),
                tint = SpartialWhite,
                onClick = onShare,
            )
            ReelActionButton(
                icon = if (isSaved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                label = stringResource(R.string.reel_action_save),
                tint = if (isSaved) SpartialWhite else SpartialWhite.copy(alpha = 0.9f),
                onClick = onSave,
            )
        }

        // Bottom-left block: equalizer, title, artist, up next.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 88.dp, bottom = 172.dp),
        ) {
            if (current != null) {
                EqualizerBars(playing = isPlaying, reduceMotion = reduceMotion)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = current.title,
                    style = reelStyle(24.sp, FontWeight.ExtraBold, SpartialWhite),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = current.artist,
                    style = reelStyle(
                        15.sp,
                        FontWeight.SemiBold,
                        SpartialWhite.copy(alpha = 0.88f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandLess,
                        contentDescription = null,
                        tint = SpartialWhite,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.reel_up_next),
                        style = reelStyle(12.sp, FontWeight.Bold, SpartialWhite),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = next.title,
                        style = reelStyle(13.sp, FontWeight.Normal, SpartialWhite.copy(alpha = 0.85f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }

        // The 3dp progress hairline, just above the floating tab bar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(3.dp)
                .background(SpartialWhite.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(SpartialWhite, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private val LikePink = Color(0xFFFF6B81)

/** 48dp glass circle: black 45% fill, white 28% border. */
@Composable
private fun ReelGlassCircle(
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    contentDescription: String,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Color.Black.copy(alpha = 0.45f),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ReelActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    CircleShape,
                )
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = reelStyle(11.sp, FontWeight.Bold, SpartialWhite),
        )
    }
}

@Composable
private fun ReelTabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = reelStyle(
            15.sp,
            if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            if (selected) SpartialWhite else SpartialGreyDim,
        ),
        modifier = Modifier.clickable(role = Role.Tab, onClick = onClick),
    )
}

/** Four bars breathing while the track plays; flat when paused or reduced. */
@Composable
private fun EqualizerBars(playing: Boolean, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = if (playing && !reduceMotion) {
        List(4) { index ->
            transition.animateFloat(
                initialValue = 0.3f + index * 0.1f,
                targetValue = 1f - index * 0.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420 + index * 130, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
        }
    } else {
        null
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(18.dp),
    ) {
        for (index in 0 until 4) {
            val fraction = heights?.get(index)?.value ?: 0.45f
            Box(
                Modifier
                    .width(4.dp)
                    .height((6 + 12 * fraction).dp)
                    .background(SpartialWhite.copy(alpha = 0.92f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun FindingMoreShimmer(reduceMotion: Boolean) {
    val pulse = if (reduceMotion) {
        0.7f
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "shimmerAlpha",
        ).value
    }
    Text(
        text = stringResource(R.string.reel_finding_more),
        fontSize = 14.sp,
        color = SpartialGrey.copy(alpha = pulse),
    )
}

@Composable
private fun ReelEmbeddedState(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpartialBlack)
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.reel_embedded_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SpartialWhite,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.reel_embedded_body),
            fontSize = 14.sp,
            color = SpartialGrey,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    RoundedCornerShape(24.dp),
                )
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.reel_open_settings),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SpartialWhite,
            )
        }
    }
}

@Composable
private fun ReelFollowingEmpty(onOpenSearch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpartialBlack)
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.reel_following_empty),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SpartialWhite,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    RoundedCornerShape(24.dp),
                )
                .clickable(role = Role.Button, onClick = onOpenSearch)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.reel_find_artists),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SpartialWhite,
            )
        }
    }
}

/** The design's label shadow: a soft black blur under white text. */
private fun reelStyle(
    size: TextUnit,
    weight: FontWeight,
    color: Color,
): TextStyle = TextStyle(
    fontSize = size,
    fontWeight = weight,
    color = color,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 1f),
        blurRadius = 8f,
    ),
)
