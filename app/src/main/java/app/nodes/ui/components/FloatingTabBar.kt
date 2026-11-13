/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.ui.theme.NodesType
import app.nodes.ui.theme.NodesWhite

/**
 * One destination of the floating bar.
 *
 * [activeIcon] is the filled variant shown while the tab is selected, matching
 * the SF-style "filled state" the design calls for; [icon] is the line-weight
 * outline shown otherwise.
 */
data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val activeIcon: ImageVector = icon,
)

/**
 * The floating bar: a glass pill with the four primary destinations and a
 * separate glass circle for Search, docked above the system gesture area.
 *
 * The active tab sits inside a lighter rounded wash that slides between tabs
 * on a short spring rather than cutting. The glass look is the shared film in
 * [glass] - a translucent white fill with a hairline border and a faint top
 * highlight - which is the deliberate fallback the design allows where real
 * backdrop blur would cost frames; a real blur behind a bar that sits over a
 * scrolling feed would redraw on every scroll tick.
 */
@Composable
fun FloatingTabBar(
    currentRoute: String?,
    items: List<BottomNavItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Order comes from the caller; the last item is the circular search.
    val pillItems = items.dropLast(1)
    val searchItem = items.lastOrNull() ?: return
    if (pillItems.isEmpty()) return

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
                .glass(shape = RoundedCornerShape(32.dp), strong = true, scrim = 0.30f),
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
                .glass(shape = CircleShape, strong = true, scrim = 0.30f)
                .clickable(role = Role.Tab, onClick = { onSelect(searchItem.route) })
                .testTag("tab-${searchItem.route}")
                .semantics { selected = searchSelected },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (searchSelected) searchItem.activeIcon else searchItem.icon,
                contentDescription = stringResource(searchItem.labelRes),
                tint = NodesWhite,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

/** The pill's four slots, with the highlight riding between them. */
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
            imageVector = if (selected) item.activeIcon else item.icon,
            contentDescription = stringResource(item.labelRes),
            tint = NodesWhite,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(item.labelRes),
            style = NodesType.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
            color = if (selected) NodesWhite else NodesWhite.copy(alpha = 0.60f),
            maxLines = 1,
        )
    }
}

/**
 * The bar's destinations, in display order: the four primary tabs in the pill,
 * Search last (it renders as the separate circle).
 */
val NodesNavItems = listOf(
    BottomNavItem("home", R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    BottomNavItem("trending", R.string.nav_trending, Icons.Outlined.TrendingUp, Icons.Rounded.TrendingUp),
    BottomNavItem("sessions", R.string.nav_sessions, Icons.Outlined.Group, Icons.Rounded.Group),
    BottomNavItem("library", R.string.nav_library, Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    BottomNavItem("search", R.string.nav_search, Icons.Outlined.Search, Icons.Rounded.Search),
)
