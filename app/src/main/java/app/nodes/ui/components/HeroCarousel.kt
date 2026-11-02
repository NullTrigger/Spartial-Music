/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.nodes.R
import app.nodes.core.crash.AppLogger
import app.nodes.core.model.HeroSlide
import app.nodes.core.model.Track
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-bleed hero carousel that advances on its own, right to left.
 *
 * The pager is given a practically infinite page count and starts in the middle
 * of it, so "next" is always `currentPage + 1`: the poster always travels in the
 * same direction and the last slide is followed by the first without a jump or a
 * backwards animation back to page 0. The visible slide is `page % size`.
 *
 * The timer stops when the screen is not resumed (another route on top of Home,
 * or the app in the background) and is cancelled with the composition, so it
 * cannot keep scrolling a carousel nobody is looking at. It also pauses while a
 * finger is dragging, and resumes three seconds after the finger lifts instead
 * of four.
 *
 * The slide itself is smoothed twice: the pages either side of the visible one
 * are composed ahead of time so the next poster is already decoded when the
 * auto-advance starts, and the advance uses a slow eased tween rather than the
 * default spring. The title, artist and play pill live inside their page and
 * travel with the artwork instead of snapping when the page settles; only the
 * dots stay pinned.
 */
@Composable
fun HeroCarousel(
    slides: List<HeroSlide>,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onPageChanged: (Int) -> Unit = {},
) {
    if (slides.isEmpty()) return

    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.56f).dp
    val count = slides.size

    // Keyed on the size: if the feed shrinks from five slides to one, a pager
    // still sitting on page 1,073,741,823 of a now one-page list has no valid
    // position. Rebuilding it for a new size is cheaper than guarding every
    // index inside the pager.
    val pagerState = key(count) {
        rememberPagerState(
            initialPage = if (count > 1) loopStart(count) else 0,
            pageCount = { if (count > 1) Int.MAX_VALUE else count },
        )
    }
    val scope = rememberCoroutineScope()

    val index = pagerState.currentPage.mod(count)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    var hasDragged by remember { mutableStateOf(false) }

    LaunchedEffect(dragging) { if (dragging) hasDragged = true }
    LaunchedEffect(index) { onPageChanged(index) }

    LaunchedEffect(pagerState, count, lifecycleState, dragging) {
        if (count <= 1) return@LaunchedEffect
        if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        // Coming back from a drag resumes sooner than the normal cadence.
        delay(if (hasDragged) 3_000L else 4_000L)
        while (true) {
            try {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = HeroSlideAnimation,
                )
            } catch (cancelled: CancellationException) {
                // A dot tap interrupts the slide without cancelling this
                // effect, so keep the loop alive; only a real cancellation
                // (screen left, feed resized) should end it.
                if (!currentCoroutineContext().isActive) throw cancelled
            } catch (error: Exception) {
                // A pager that cannot scroll must not take the process down.
                AppLogger.w("Hero", "Auto-advance failed", error)
                break
            }
            delay(4_000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Keep the pages either side of the visible one composed, so their
            // artwork is decoded well before the next auto-advance starts.
            beyondViewportPageCount = 1,
            key = { page -> "hero-$page-${slides[page.mod(count)].track.id}" },
        ) { page ->
            val slide = slides[page.mod(count)]
            Box(Modifier.fillMaxSize()) {
                Artwork(
                    artworkUri = slide.track.artworkUri,
                    title = slide.track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    targetPx = 1280,
                )
                Box(Modifier.fillMaxSize().centreVignette())
                Box(Modifier.fillMaxSize().bottomFade())

                // The copy belongs to its poster, so it slides in and out with
                // the artwork instead of snapping once the pager settles. The
                // 27dp leaves the pinned dots their own strip at the bottom.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 27.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = slide.track.title,
                        style = MaterialTheme.typography.displayLarge,
                        color = NodesWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = slide.track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NodesGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    Spacer(Modifier.height(18.dp))

                    // Compact pill: wrap-content, 42dp tall, centred under the artist.
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color.White.copy(alpha = 0.13f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(21.dp))
                            .clickable { onPlay(slide.track) }
                            .padding(horizontal = 26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = NodesWhite,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.action_play),
                            style = MaterialTheme.typography.labelLarge,
                            color = NodesWhite,
                        )
                    }
                }
            }
        }

        PagerDots(
            count = count,
            selected = index,
            onSelect = { target ->
                scope.launch {
                    // Shortest way round, always still travelling forwards.
                    val delta = (target - index + count) % count
                    try {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + delta,
                            animationSpec = HeroSlideAnimation,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        AppLogger.w("Hero", "Selecting a page failed", error)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        )
    }
}

/**
 * A slow eased slide. The default spring starts and stops too abruptly for a
 * full-bleed poster; this travels for 700ms and settles gently.
 */
private val HeroSlideAnimation = tween<Float>(
    durationMillis = 700,
    easing = FastOutSlowInEasing,
)

/** A page in the middle of the range that is a whole multiple of [count]. */
private fun loopStart(count: Int): Int {
    val base = Int.MAX_VALUE / 2
    return base - base % count
}

/**
 * Pager dots: the active dot stretches into a wide white pill, as in the
 * reference screenshot.
 */
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
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count.coerceAtMost(maxVisible)) { index ->
            val active = index == selected
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                label = "dotWidth",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(if (active) NodesWhite else Color.White.copy(alpha = 0.28f))
                    .clickable { onSelect(index) },
            )
        }
    }
}
