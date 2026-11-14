/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.ui.theme.SpartialWhite
import app.nodes.ui.theme.navAccent
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One destination of the floating bar: a Phosphor fill-weight drawable (MIT).
 */
data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
)

/**
 * The floating bar, expanded and collapsed.
 *
 * **Expanded**: the glass pill (Home, Trending, Sessions, Library) with the
 * Search circle beside it, and the mini player stacked below: nav row, 8dp
 * gap, mini player, all inset 16dp.
 *
 * **Collapsed** (content scrolled down): one 56dp row - the current tab's
 * icon in [navAccent] at the left, the mini player between, Search at the
 * right. The mini player is the same composable in both states, so it never
 * pops.
 *
 * **The bubble**: the active tab rests in a brighter glass capsule. Touching
 * it lifts it (x1.2 x x1.25), dragging slides it along the pill with a
 * rubber-band clamp and up to ~12% stretch; on release it snaps to the
 * nearest tab, shrinks back and navigates then - never during the drag.
 */
@Composable
fun FloatingTabBar(
    currentRoute: String?,
    items: List<BottomNavItem>,
    onSelect: (String) -> Unit,
    hazeState: HazeState? = null,
    collapsed: Boolean = false,
    miniPlayer: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Order comes from the caller; the last item is the circular search.
    val pillItems = items.dropLast(1)
    val searchItem = items.lastOrNull() ?: return
    if (pillItems.isEmpty()) return
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!collapsed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .liquidGlass(hazeState, RoundedCornerShape(32.dp)),
                ) {
                    TabPill(
                        items = pillItems,
                        currentRoute = currentRoute,
                        onSelect = onSelect,
                        hazeState = hazeState,
                        reduceMotion = reduceMotion,
                    )
                }
                SearchCircle(
                    item = searchItem,
                    selected = currentRoute == searchItem.route,
                    onSelect = onSelect,
                    hazeState = hazeState,
                )
            }
            miniPlayer?.invoke()
        } else {
            // Collapsed: one 56dp row, circles at the edges, mini between.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = items.firstOrNull { it.route == currentRoute } ?: items.first()
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .liquidGlass(hazeState, CircleShape)
                        .clickable(role = Role.Button, onClick = { onSelect(current.route) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(current.icon),
                        contentDescription = stringResource(current.labelRes),
                        tint = navAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                miniPlayer?.invoke()
                SearchCircle(
                    item = searchItem,
                    selected = currentRoute == searchItem.route,
                    onSelect = onSelect,
                    hazeState = hazeState,
                )
            }
        }
    }
}

@Composable
private fun SearchCircle(
    item: BottomNavItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
    hazeState: HazeState?,
) {
    val touch = rememberLiquidTouch()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .liquidGlassTouch(touch)
            .size(64.dp)
            .liquidGlass(hazeState, CircleShape, touch)
            .liquidGlassPress(touch, haptics)
            .clickable(role = Role.Tab, onClick = { onSelect(item.route) })
            .testTag("tab-${item.route}")
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = stringResource(item.labelRes),
            tint = SpartialWhite,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * The pill with the four tabs and the draggable active bubble.
 */
@Composable
private fun TabPill(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    hazeState: HazeState?,
    reduceMotion: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    var dragIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    val bubbleTouch = rememberLiquidTouch()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val slotWidth = maxWidth / items.size
        val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }
        val highlightOffset by animateFloatAsState(
            targetValue = slotWidthPx * dragIndex + dragX,
            animationSpec = if (dragging) {
                spring(stiffness = Spring.StiffnessHigh)
            } else {
                spring(stiffness = 1100f, dampingRatio = 0.9f)
            },
            label = "bubble",
        )
        // The bubble: brighter glass, lifted while touched, draggable.
        val lift by animateFloatAsState(
            targetValue = if (bubbleTouch.isPressed || dragging) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (reduceMotion) Spring.DampingRatioNoBouncy else 0.7f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "lift",
        )
        val stretch = if (dragging && !reduceMotion) {
            (abs(dragX) / (slotWidthPx * 4f)).coerceIn(0f, 0.12f)
        } else {
            0f
        }

        // The drag surface wraps the bubble and the tabs; it observes events
        // even when a child consumes them, so a drag starting on any tab works.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(items, currentRoute, slotWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startIndex = (startX / slotWidthPx).toInt().coerceIn(items.indices)
                        dragIndex = startIndex
                        dragging = true
                        bubbleTouch.pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val raw = change.position.x - startX
                            val min = -startIndex * slotWidthPx
                            val max = (items.lastIndex - startIndex) * slotWidthPx
                            dragX = when {
                                raw < min -> min + (raw - min) * 0.3f
                                raw > max -> max + (raw - max) * 0.3f
                                else -> raw
                            }
                            bubbleTouch.position = change.position
                            val over = ((startIndex * slotWidthPx + dragX) / slotWidthPx)
                                .roundToInt()
                                .coerceIn(items.indices)
                            if (over != dragIndex) {
                                dragIndex = over
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                            if (!change.pressed) break
                        }
                        dragging = false
                        bubbleTouch.pressed = false
                        val target = ((startIndex * slotWidthPx + dragX) / slotWidthPx)
                            .roundToInt()
                            .coerceIn(items.indices)
                        dragX = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (items[target].route != currentRoute) {
                            onSelect(items[target].route)
                        }
                    }
                },
        ) {
            // Glass capsule riding at the bubble's position, protruding above
            // and below the pill while lifted.
            Box(
                modifier = Modifier
                    .offset(x = with(LocalDensity.current) { highlightOffset.toDp() })
                    .padding(4.dp)
                    .width(slotWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .liquidGlass(
                        hazeState = hazeState,
                        shape = RoundedCornerShape(26.dp),
                        touchState = bubbleTouch,
                        refraction = (bubbleTouch.isPressed || dragging) && !reduceMotion,
                    )
                    .graphicsLayer {
                        scaleX = 1f + stretch
                        scaleY = 1f + (lift * 0.25f)
                    }
                    .testTag("bubble"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(items[dragIndex.coerceIn(items.indices)].icon),
                    contentDescription = null,
                    tint = navAccent,
                    modifier = Modifier.size(26.dp),
                )
            }

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, item ->
                    val selected = index == dragIndex.coerceIn(items.indices)
                    Tab(
                        item = item,
                        selected = selected,
                        onClick = {
                            dragIndex = index
                            onSelect(item.route)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
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
    // Icon and label under the bubble move to the accent in ~120ms; the rest
    // stay at white 60%.
    val tint by animateColorAsState(
        targetValue = if (selected) navAccent else SpartialWhite.copy(alpha = 0.60f),
        animationSpec = tween(120),
        label = "tabTint",
    )
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
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * The bar's destinations, in display order: the four primary tabs in the pill,
 * Search last (it renders as the separate circle). Glyphs are Phosphor
 * fill-weight drawables.
 */
val SpartialNavItems = listOf(
    BottomNavItem("home", R.string.nav_home, R.drawable.ic_nav_home),
    BottomNavItem("trending", R.string.nav_trending, R.drawable.ic_nav_trending),
    BottomNavItem("sessions", R.string.nav_sessions, R.drawable.ic_nav_sessions),
    BottomNavItem("library", R.string.nav_library, R.drawable.ic_nav_library),
    BottomNavItem("search", R.string.nav_search, R.drawable.ic_nav_search),
)
