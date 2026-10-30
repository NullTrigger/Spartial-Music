/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nodes.core.model.HeroSlide
import app.nodes.core.model.Track
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite

/**
 * Full-bleed hero carousel. The artwork fades into the page through a vertical
 * scrim so the type and the glass Play pill sit on true black, matching the
 * reference composition.
 */
@Composable
fun HeroCarousel(
    slides: List<HeroSlide>,
    onPlay: (Track) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onPageChanged: (Int) -> Unit = {},
) {
    if (slides.isEmpty()) return

    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.56f).dp

    val pagerState = rememberPagerState(pageCount = { slides.size })

    // Keep the page stable if the feed shrinks while the user is browsing.
    val safePage = pagerState.currentPage.coerceIn(0, slides.lastIndex)
    val current = slides[safePage]

    Box(modifier = modifier.fillMaxWidth().height(heroHeight)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> slides[index].track.id },
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                Artwork(
                    artworkUri = slides[page].track.artworkUri,
                    title = slides[page].track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                )
                Box(Modifier.fillMaxSize().centreVignette())
                Box(Modifier.fillMaxSize().bottomFade())
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = current.kicker,
                style = MaterialTheme.typography.labelSmall,
                color = NodesWhite.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = current.track.title,
                style = MaterialTheme.typography.displayLarge,
                color = NodesWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = current.track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = NodesGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.13f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                        .clickable { onPlay(current.track) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.titleMedium,
                        color = NodesWhite,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onNext)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
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

            Spacer(Modifier.height(16.dp))

            PagerDots(
                count = slides.size,
                selected = safePage,
                onSelect = { index ->
                    onPageChanged(index)
                },
            )
        }
    }
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
