/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.ui.theme.NodesWhite
import dev.chrisbanes.haze.HazeState

/**
 * One destination of the floating bar.
 *
 * The glyph is a Phosphor fill-weight drawable (MIT), always filled: active is
 * white, inactive white at 55%.
 */
data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
)

/**
 * The floating bar: a glass pill with the four primary destinations and a
 * separate glass circle for Search, docked above the system gesture area.
 *
 * The active tab's wash slides between tabs on its spring; the glyphs are
 * always filled, white when active and 55% white otherwise.
 */
@Composable
fun FloatingTabBar(
    currentRoute: String?,
    items: List<BottomNavItem>,
    onSelect: (String) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    // Order comes from the caller; the last item is the circular search.
    val pillItems = items.dropLast(1)
    val searchItem = items.lastOrNull() ?: return
    if (pillItems.isEmpty()) return
    val glass = glassNavBackdrop(Modifier, hazeState, RoundedCornerShape(32.dp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .then(glass),
        ) {
            TabPill(
                items = pillItems,
                currentRoute = currentRoute,
                onSelect = onSelect,
            )
        }

        val searchSelected = currentRoute == searchItem.route
        Box(
            modifier = Modifier
                .size(64.dp)
                .then(glass)
                .clickable(role = Role.Tab, onClick = { onSelect(searchItem.route) })
                .testTag("tab-${searchItem.route}")
                .semantics { selected = searchSelected },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(searchItem.icon),
                contentDescription = stringResource(searchItem.labelRes),
                tint = NodesWhite,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** The pill's four slots, with the highlight riding between them on its spring. */
@Composable
private fun TabPill(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val slotWidth = maxWidth / items.size
        val highlightOffset by animateDpAsState(
            targetValue = slotWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "tabHighlight",
        )
        Box(
            Modifier
                .offset(x = highlightOffset)
                .width(slotWidth)
                .fillMaxHeight()
                .padding(5.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                Tab(
                    item = item,
                    selected = item.route == currentRoute,
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Tab(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag("tab-${item.route}")
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = stringResource(item.labelRes),
            tint = if (selected) NodesWhite else NodesWhite.copy(alpha = 0.55f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
            color = if (selected) NodesWhite else NodesWhite.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }
}

/**
 * The bar's destinations, in display order: the four primary tabs in the pill,
 * Search last (it renders as the separate circle). Glyphs are Phosphor
 * fill-weight drawables.
 */
val NodesNavItems = listOf(
    BottomNavItem("home", R.string.nav_home, R.drawable.ic_nav_home),
    BottomNavItem("trending", R.string.nav_trending, R.drawable.ic_nav_trending),
    BottomNavItem("sessions", R.string.nav_sessions, R.drawable.ic_nav_sessions),
    BottomNavItem("library", R.string.nav_library, R.drawable.ic_nav_library),
    BottomNavItem("search", R.string.nav_search, R.drawable.ic_nav_search),
)
