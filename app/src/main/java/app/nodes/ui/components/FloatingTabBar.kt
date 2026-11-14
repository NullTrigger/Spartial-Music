/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.ui.theme.SpartialWhite
import app.nodes.ui.theme.navAccent
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

/**
 * One destination of the floating bar: a Phosphor fill-weight drawable (MIT).
 */
data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
)

/* ---------------------------------------------------------------- geometry */

/** The pill's inner padding; the four items share what is left. */
private val PillInnerPadding = 4.dp

/** The nav row's height, pill and Search circle alike. */
private val PillHeight = 64.dp

/** The bubble at rest is the item width by this height. */
private val BubbleRestHeight = 56.dp

/** Pressed, the bubble grows to 76dp - 6dp proud of the pill top and bottom. */
private val BubblePressedHeight = 76.dp

/** Pressed, the bubble is this much wider than its item. */
private const val BubblePressedWidthScale = 1.2f

/** The bubble never crosses the pill's padding, in either direction. */
private const val BubbleMaxStretch = 0.12f

/** Per-tab magnification while the bubble is pressed. */
private const val TabPressScale = 0.1f

/** The mini player, above the nav row when expanded. */
private val MiniPlayerHeight = 60.dp

/** The mini player, in the collapsed row. */
private val MiniPlayerCollapsedHeight = 56.dp

/** Gap between the mini player and the nav row. */
private val MiniPlayerGap = 8.dp

/** Gap between the collapsed row's elements, and its clearance from the edge. */
private val CollapsedGap = 12.dp

/** The nav row sits this far above the system navigation inset. */
private val BarBottomPadding = 12.dp

/** Same horizontal inset for both states, so the circles line up. */
private val BarHorizontalPadding = 16.dp

/**
 * The floating bar, expanded and collapsed.
 *
 * **Placement.** One [Column]: the mini player capsule first, then the nav row
 * last, so the nav row is always the bottom-most element and the mini player's
 * bottom edge sits 8dp above it. Both states bottom out on the same baseline,
 * `safeAreaBottom + 12dp`.
 *
 * **Expanded**: the glass pill (Home, Trending, Sessions, Library) with the
 * Search circle beside it. **Collapsed**: one row - the current tab's circle,
 * the mini player, Search - or, with nothing loaded, two equal circles centred
 * as a group.
 *
 * **The bubble** rides in the pill as its own unclipped layer. It is the single
 * source of truth for the selection: its centre decides which tab glows, how
 * much, and where it snaps on release. It grows on the raw pointer-down with no
 * touch slop, is clamped hard inside the pill's padding, and navigates only on
 * release.
 */
@Composable
fun FloatingTabBar(
    currentRoute: String?,
    items: List<BottomNavItem>,
    onSelect: (String) -> Unit,
    hazeState: HazeState? = null,
    collapsed: Boolean = false,
    miniPlayer: (@Composable () -> Unit)? = null,
    onExpand: () -> Unit = {},
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

    if (!collapsed) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = BarHorizontalPadding)
                .padding(bottom = BarBottomPadding),
        ) {
            // The mini player first, the nav row last - never the other way
            // round, or the capsule ends up below the bar it belongs above.
            if (miniPlayer != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerHeight),
                ) {
                    miniPlayer()
                }
                Spacer(Modifier.height(MiniPlayerGap))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PillHeight),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
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
                    size = PillHeight,
                )
            }
        }
    } else {
        CollapsedRow(
            items = items,
            currentRoute = currentRoute,
            onSelect = onSelect,
            onExpand = onExpand,
            hazeState = hazeState,
            searchItem = searchItem,
            miniPlayer = miniPlayer,
        )
    }
}

/**
 * The collapsed bar: one [Row] for both cases.
 *
 * With a song loaded the mini player takes the middle at weight 1f and the
 * circles sit at the edges. With nothing loaded the capsule is gone and the two
 * circles centre as a group. The gap between them is the only thing that moves
 * (8dp to 12dp), and it springs, so starting or stopping a song slides the
 * circles apart or together instead of snapping them.
 */
@Composable
private fun CollapsedRow(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onExpand: () -> Unit,
    hazeState: HazeState?,
    searchItem: BottomNavItem,
    miniPlayer: (@Composable () -> Unit)?,
) {
    val hasSong = miniPlayer != null
    val settle = spring<Dp>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val gap by animateDpAsState(
        targetValue = if (hasSong) MiniPlayerGap else CollapsedGap,
        animationSpec = settle,
        label = "collapsedGap",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BarHorizontalPadding)
            .padding(bottom = BarBottomPadding),
        // Centred as a group when there is nothing between the circles.
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val current = items.firstOrNull { it.route == currentRoute } ?: items.first()
        Box(
            modifier = Modifier
                .size(BubbleRestHeight)
                .liquidGlass(hazeState, CircleShape)
                .clickable(role = Role.Button, onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(current.icon),
                contentDescription = stringResource(R.string.nav_expand),
                tint = navAccent,
                modifier = Modifier.size(26.dp),
            )
        }
        if (hasSong) {
            Box(
                Modifier
                    .weight(1f)
                    .height(MiniPlayerCollapsedHeight),
            ) {
                miniPlayer?.invoke()
            }
        }
        SearchCircle(
            item = searchItem,
            selected = currentRoute == searchItem.route,
            onSelect = onSelect,
            hazeState = hazeState,
            size = BubbleRestHeight,
        )
    }
}

@Composable
private fun SearchCircle(
    item: BottomNavItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
    hazeState: HazeState?,
    size: Dp,
) {
    val touch = rememberLiquidTouch()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .liquidGlassTouch(touch)
            .size(size)
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
 * The pill: one glass layer, one bubble layer, one icon layer.
 *
 * The three are siblings, in that order, and none of them is an ancestor of the
 * next - which is what lets the bubble protrude past the pill without being
 * clipped, and what keeps exactly one icon per tab on screen. The bubble is the
 * only thing that moves.
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
    val bubbleTouch = rememberLiquidTouch()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pillWidthPx = with(density) { maxWidth.toPx() }
        val innerPaddingPx = with(density) { PillInnerPadding.toPx() }
        // Every item is (pillWidth - 2 x 4dp) / 4 wide.
        val itemWidthPx = (pillWidthPx - 2f * innerPaddingPx) / items.size
        val itemWidth = with(density) { itemWidthPx.toDp() }

        // Item centres, in the pill's own coordinate space.
        fun centreOf(index: Int): Float = innerPaddingPx + itemWidthPx * (index + 0.5f)

        // The bubble's centre is the single source of truth. Everything else -
        // which tab glows, by how much, and where the bubble lands on release -
        // is derived from this one value, so the highlight can never disagree
        // with the bubble.
        val bubbleCentre = remember(items.size, pillWidthPx) {
            Animatable(centreOf(selectedIndex))
        }
        var hoverIndex by remember(items.size, pillWidthPx) {
            mutableIntStateOf(selectedIndex)
        }
        var pressed by remember { mutableStateOf(false) }
        var stretch by remember { mutableFloatStateOf(0f) }

        // Hard clamp: the bubble's bounds stay inside [4dp, pillWidth - 4dp].
        // No overshoot and no rubber band - a drag past the end simply stops.
        val minCentre = innerPaddingPx + itemWidthPx * BubblePressedWidthScale / 2f
        val maxCentre = pillWidthPx - innerPaddingPx - itemWidthPx * BubblePressedWidthScale / 2f
        // Chained rather than coerceIn: a degenerate measurement must clamp
        // to one end instead of throwing on min > max.
        fun clampCentre(x: Float): Float =
            x.coerceAtLeast(minCentre).coerceAtMost(maxCentre)

        fun hoverOf(x: Float): Int =
            (0 until items.size).minByOrNull { abs(x - centreOf(it)) } ?: 0

        // Rest is the item width; pressed it is 1.2x wider and 76dp tall, so it
        // stands 6dp proud of the 64dp pill. A spring settles both.
        val geometry = spring<Float>(dampingRatio = 0.75f, stiffness = 400f)
        val grow = if (pressed) 1f else 0f
        val growAnimated = animateFloatAsState(
            targetValue = grow,
            animationSpec = geometry,
            label = "bubbleGrow",
        )
        val bubbleWidthDp = itemWidth * (1f + (BubblePressedWidthScale - 1f) * growAnimated.value)
        val bubbleHeightDp = BubbleRestHeight +
            (BubblePressedHeight - BubbleRestHeight) * growAnimated.value

        // Layer 1: the pill's own glass. A sibling of the bubble, never its
        // parent, or the clip that rounds the pill would also cut the bubble's
        // protrusion off.
        Box(
            Modifier
                .fillMaxSize()
                .liquidGlass(hazeState, RoundedCornerShape(32.dp)),
        )

        // Layer 2: the bubble. No clip on this box or anything above it.
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (bubbleCentre.value - bubbleWidthDp.toPx() / 2f).toDp() },
                        y = (PillHeight - bubbleHeightDp) / 2,
                    )
                    .size(bubbleWidthDp, bubbleHeightDp)
                    .graphicsLayer {
                        // The velocity stretch only applies while it still fits.
                        scaleX = 1f + stretch
                    }
                    .liquidGlass(
                        hazeState = hazeState,
                        shape = RoundedCornerShape(28.dp),
                        touchState = bubbleTouch,
                        refraction = pressed && !reduceMotion,
                    )
                    .testTag("bubble"),
            )
        }

        // Layer 3: exactly one icon and one label per tab.
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, item ->
                Tab(
                    item = item,
                    selected = index == selectedIndex,
                    proximity = proximityOf(bubbleCentre.value, centreOf(index), itemWidthPx),
                    pressed = pressed,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val dragModifier = Modifier.pointerInput(items, pillWidthPx, itemWidthPx) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press) continue
                    val down = event.changes.firstOrNull() ?: continue
                    down.consume()
                    val pointerId = down.id

                    pressed = true
                    stretch = 0f
                    bubbleTouch.pressed = true
                    bubbleTouch.position = down.position
                    hoverIndex = hoverOf(clampCentre(down.position.x))
                    bubbleCentre.snapTo(clampCentre(down.position.x))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    var lastX = down.position.x
                    var speed = 0f
                    while (true) {
                        val move = awaitPointerEvent(PointerEventPass.Initial)
                        val change = move.changes.firstOrNull { it.id == pointerId } ?: break
                        val x = change.position.x
                        speed = speed * 0.6f + abs(x - lastX) * 0.4f
                        lastX = x

                        val clamped = clampCentre(x)
                        bubbleCentre.snapTo(clamped)
                        bubbleTouch.position = change.position

                        val hovered = hoverOf(clamped)
                        if (hovered != hoverIndex) {
                            hoverIndex = hovered
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }

                        // Velocity stretch, up to 12%, and only while the
                        // stretched bubble still fits inside the clamp.
                        val candidate = (speed / (itemWidthPx * 12f)).coerceIn(0f, BubbleMaxStretch)
                        val stretchedWidth = itemWidthPx * BubblePressedWidthScale * (1f + candidate)
                        stretch = if (
                            clamped - stretchedWidth / 2f >= innerPaddingPx &&
                            clamped + stretchedWidth / 2f <= pillWidthPx - innerPaddingPx
                        ) {
                            candidate
                        } else {
                            0f
                        }

                        change.consume()
                        if (!change.pressed) break
                    }

                    // Release: snap to the nearest item, then navigate. The
                    // bubble never navigates mid-drag.
                    stretch = 0f
                    bubbleTouch.pressed = false
                    val target = hoverOf(bubbleCentre.value)
                    hoverIndex = target
                    pressed = false
                    bubbleCentre.animateTo(
                        targetValue = centreOf(target),
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (items[target].route != currentRoute) {
                        onSelect(items[target].route)
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().then(dragModifier))
    }
}

/** 1 under the bubble's centre, falling to 0 one item width away. */
private fun proximityOf(centre: Float, itemCentre: Float, itemWidth: Float): Float =
    (1f - abs(centre - itemCentre) / itemWidth).coerceIn(0f, 1f)

@Composable
private fun Tab(
    item: BottomNavItem,
    selected: Boolean,
    proximity: Float,
    pressed: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The accent follows the bubble's proximity rather than a hard selected
    // flag, so the glow tracks the drag instead of jumping between tabs.
    val inactive = SpartialWhite.copy(alpha = 0.60f)
    val tint = lerp(inactive, navAccent, proximity)
    // Magnification only while the bubble is held: at rest every icon is the
    // same size.
    val scale = 1f + TabPressScale * proximity * (if (pressed) 1f else 0f)
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("tab-${item.route}")
            // No clickable: the pill owns the gesture and the selection, so a
            // second detector here would fire alongside it. Activation is
            // still exposed to accessibility through the semantics action.
            .semantics {
                role = Role.Tab
                this.selected = selected
                onClick {
                    onSelect(item.route)
                    true
                }
            },
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
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
            ),
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
