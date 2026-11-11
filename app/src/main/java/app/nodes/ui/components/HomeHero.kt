/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.nodes.R
import app.nodes.core.model.HeroSlide
import app.nodes.core.model.Track
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-bleed hero carousel with the now-playing poster, label, title, artist
 * and the Play / Next pair. The pager advances on its own while the screen is
 * resumed and pauses while a finger is down.
 */
@Composable
fun HomeHero(
    slides: List<HeroSlide>,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) return
    val count = slides.size
    val pagerState = key(count) {
        rememberPagerState(
            initialPage = if (count > 1) loopStart(count) else 0,
            pageCount = { if (count > 1) Int.MAX_VALUE else count },
        )
    }
    val scope = rememberCoroutineScope()
    val index = pagerState.currentPage.mod(count)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, count, lifecycleState, dragging) {
        if (count <= 1 || lifecycleState != Lifecycle.State.RESUMED || dragging) return@LaunchedEffect
        delay(if (dragging) 3_000L else 5_000L)
        while (true) {
            try {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(700, easing = FastOutSlowInEasing),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                break
            }
            delay(5_000L)
        }
    }

    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.62f)
        .dp
        .coerceIn(420.dp, 560.dp)

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            beyondViewportPageCount = 1,
            key = { page -> "hero-$page-${slides[page.mod(count)].track.id}" },
        ) { page ->
            val slide = slides[page.mod(count)]
            val track = slide.track
            Box(Modifier.fillMaxSize()) {
                Artwork(
                    artworkUri = track.artworkUri,
                    title = track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    contentScale = ContentScale.Crop,
                    targetPx = 1280,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.12f),
                                0.42f to Color.Transparent,
                                0.68f to NodesBlack.copy(alpha = 0.58f),
                                1f to NodesBlack,
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, bottom = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = slide.kicker.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NodesGrey,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(
                            1.8f,
                            androidx.compose.ui.unit.TextUnitType.Sp,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = track.title,
                        style = NodesType.heroTitle,
                        color = NodesWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NodesGrey,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroButton(
                            label = stringResource(R.string.action_play),
                            primary = true,
                            onClick = { onPlay(track) },
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = NodesBlack,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        HeroButton(
                            label = stringResource(R.string.action_next),
                            primary = false,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = NodesWhite,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
        }

        PagerDots(
            count = count,
            selected = index,
            onSelect = { target ->
                val delta = (target - index + count) % count
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + delta) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun HeroButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(
                if (primary) NodesWhite else Color.White.copy(alpha = 0.13f),
            )
            .then(if (primary) Modifier else Modifier.glass(CircleShape, strong = true))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = NodesType.pill,
            color = if (primary) NodesBlack else NodesWhite,
        )
        if (trailing != null) {
            Spacer(Modifier.width(3.dp))
            trailing()
        }
    }
}

/** Page dots: the active dot stretches into a short white pill. */
@Composable
fun PagerDots(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 8,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count.coerceAtMost(maxVisible)) { index ->
            val active = index == selected
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 6.dp,
                label = "dotWidth",
            )
            Box(
                Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (active) NodesWhite else Color.White.copy(alpha = 0.3f))
                    .clickable { onSelect(index) },
            )
        }
    }
}

private fun loopStart(count: Int): Int {
    val base = Int.MAX_VALUE / 2
    return base - base % count
}
