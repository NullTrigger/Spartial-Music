/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.nodes.R
import app.nodes.core.model.Track
import app.nodes.core.util.thumbnailCandidates
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The home banner: a full-bleed carousel of song posters that owns the top of
 * the screen. The artwork fills the pager edge to edge, dissolves into the
 * background black over its lower third, and carries the "now playing" block,
 * the Play / Next pair and the page dots as overlays.
 *
 * Behaviour, per the design:
 *  - auto-advances every 5 s with a 500 ms ease, looping from the last banner
 *    back to the first;
 *  - pauses while the user touches the pager (drag or hold) and while the app
 *    is backgrounded, and never auto-advances at all when the system's
 *    reduced-motion setting is on;
 *  - decodes artwork at display size, keeps one page alive on either side as a
 *    preload, and shows a dark placeholder while a page loads.
 */
@Composable
fun HomeBannerCarousel(
    banners: List<Track>,
    loading: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    if (banners.isEmpty()) {
        // Loading state: a dark block the size of the banner, no skeleton rows.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(bannerHeight())
                .background(NodesBlack),
        ) {
            if (loading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(NodesWhite.copy(alpha = 0.03f)),
                )
            }
            header()
        }
        return
    }

    val count = banners.size
    val pagerState = key(count) {
        rememberPagerState(
            initialPage = if (count > 1) LOOP_START(count) else 0,
            pageCount = { if (count > 1) Int.MAX_VALUE else count },
        )
    }
    val scope = rememberCoroutineScope()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val context = LocalContext.current
    // System reduced motion: the animator scale the user (or a battery saver)
    // sets to 0. Auto-advance is motion the user cannot see arrive, so it is
    // disabled outright rather than snapping silently between posters.
    val reducedMotion = rememberReducedMotion(context)

    LaunchedEffect(pagerState, count, lifecycleState, dragging, reducedMotion) {
        if (count <= 1 || lifecycleState != Lifecycle.State.RESUMED || dragging || reducedMotion) {
            return@LaunchedEffect
        }
        delay(AUTO_ADVANCE_MS)
        while (true) {
            try {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                break
            }
            delay(AUTO_ADVANCE_MS)
        }
    }

    val height = bannerHeight()
    val active = pagerState.currentPage.mod(count)

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            BannerArtwork(
                track = banners[page.mod(count)],
                targetPx = with(LocalDensity.current) {
                    (LocalConfiguration.current.screenWidthDp.dp.toPx()).toInt()
                },
            )
        }

        // Bottom blend: the poster fades fully into the background black over
        // its lower third, so there is no seam against Continue Playing.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * 0.38f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to NodesBlack.copy(alpha = 0.55f),
                        0.80f to NodesBlack.copy(alpha = 0.94f),
                        1f to NodesBlack,
                    ),
                ),
        )

        // Top scrim: keeps the header and the source badge legible over bright
        // artwork without dimming the poster's centre.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to NodesBlack.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            header()
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.now_playing_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = NodesWhite.copy(alpha = 0.60f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = banners[active].title,
                    style = NodesType.sheetTitle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    color = NodesWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = banners[active].artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NodesWhite.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(NodesWhite.copy(alpha = 0.20f))
                            .clickable(role = Role.Button) {
                                onPlay(banners[active], banners)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.action_play),
                            style = NodesType.pill.copy(fontSize = 15.sp),
                            color = NodesWhite,
                        )
                    }
                    // Next advances the carousel, not the song: the same slide
                    // the auto-advance uses, and it restarts the timer by
                    // virtue of being the only writer while this effect runs.
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    pagerState.animateScrollToPage(
                                        page = pagerState.currentPage + 1,
                                        animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
                                    )
                                }
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.action_next),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NodesWhite,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = NodesWhite,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Page dots: the active one elongates into a pill.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    repeat(count) { index ->
                        val isActive = index == active
                        Box(
                            Modifier
                                .then(
                                    if (isActive) {
                                        Modifier
                                            .width(16.dp)
                                            .height(6.dp)
                                    } else {
                                        Modifier
                                            .size(6.dp)
                                    },
                                )
                                .clip(CircleShape)
                                .background(NodesWhite.copy(alpha = if (isActive) 1f else 0.30f)),
                        )
                    }
                }
            }
        }
    }
}

/** One full-bleed poster, decoded at display size with a dark placeholder. */
@Composable
private fun BannerArtwork(track: Track, targetPx: Int) {
    val context = LocalContext.current
    val candidates = remember(track.artworkUri, targetPx) {
        thumbnailCandidates(track.artworkUri, targetPx)
    }
    val primary = candidates.firstOrNull()
    if (primary == null) {
        GeneratedArt(title = track.title, modifier = Modifier.fillMaxSize(), showInitial = false)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(primary)
            .size(targetPx)
            .crossfade(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = { Box(Modifier.fillMaxSize().background(NodesBlack)) },
        error = {
            val fallback = candidates.getOrNull(1)
            if (fallback == null) {
                GeneratedArt(title = track.title, modifier = Modifier.fillMaxSize(), showInitial = false)
            } else {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(fallback).size(targetPx).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize().background(NodesBlack)) },
                    error = {
                        GeneratedArt(
                            title = track.title,
                            modifier = Modifier.fillMaxSize(),
                            showInitial = false,
                        )
                    },
                )
            }
        },
    )
}

@Composable
private fun rememberReducedMotion(context: android.content.Context): Boolean {
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
private fun bannerHeight(): androidx.compose.ui.unit.Dp =
    (LocalConfiguration.current.screenHeightDp * 0.60f).dp.coerceIn(420.dp, 560.dp)

private fun LOOP_START(count: Int): Int = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % count

private const val AUTO_ADVANCE_MS = 5_000L
private const val SLIDE_MS = 500
