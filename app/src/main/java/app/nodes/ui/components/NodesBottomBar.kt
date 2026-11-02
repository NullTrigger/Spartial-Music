/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesWhite

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

/**
 * Floating glass navigation bar: one pill, four tabs, no separate button.
 *
 * The selected tab is marked by a lighter pill that *slides* between positions
 * (one animated offset rather than a per-item fade), while the icon scales up a
 * little and its label turns white. Unselected tabs stay grey.
 *
 * The surface carries a 90% black scrim under the glass film. Without it the
 * page behind - artwork, list text - read straight through the bar and clashed
 * with the labels. A real backdrop blur is not possible here: `Modifier.blur`
 * blurs the element's own content, not what is behind it, and Compose has no
 * backdrop-filter for content drawn earlier in the tree. A near-opaque scrim is
 * the honest equivalent, and it is what API 26 can afford anyway.
 */
@Composable
fun NodesBottomBar(
    currentRoute: String?,
    items: List<BottomNavItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        val itemWidth = maxWidth / items.size
        val highlightOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            label = "navHighlight",
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glass(shape = CircleShape, strong = true, scrim = 0.9f),
        ) {
            Box(
                Modifier
                    .offset(x = highlightOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 7.dp, vertical = 7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.13f)),
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    NavTab(
                        item = item,
                        selected = item.route == currentRoute,
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) NodesWhite else NodesGrey
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        label = "navIconScale",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelRes),
            tint = tint,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.sp,
            ),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Trending is now "Explore": same screen and route, renamed to say what it is
 * for. Search lives inside the bar like the other tabs.
 */
val NodesNavItems = listOf(
    BottomNavItem("home", R.string.nav_home, Icons.Rounded.Home),
    BottomNavItem("trending", R.string.nav_explore, Icons.Rounded.Explore),
    BottomNavItem("library", R.string.nav_library, Icons.Rounded.LibraryMusic),
    BottomNavItem("search", R.string.nav_search, Icons.Rounded.Search),
)
