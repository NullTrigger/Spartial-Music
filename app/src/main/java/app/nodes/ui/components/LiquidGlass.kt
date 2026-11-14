/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid Glass: the shared surface treatment for the floating bar, mini
 * player, player controls and header icon buttons.
 *
 * Approach, chosen deliberately: **Haze (already a dependency) draws the
 * backdrop blur into the surface node; RenderEffects chained after it add
 * what Haze cannot.** One [HazeState] per screen captures the scrolling
 * content once and every glass surface reads it - the "capture once, share
 * across surfaces" budget the design sets. The blur radius is never
 * animated; press states animate scale, highlight and (API 33+) the lens.
 *
 * Tiers:
 *  - **API 33+**: blur + saturation + touch response, and while pressed an
 *    AGSL shader refracts the backdrop in the outer band (magnifies ~1.1x,
 *    chromatic R/B split at the rim).
 *  - **API 31–32**: blur + saturation + rim + touch response, no refraction.
 *  - **Below 31**: ~75% dark tint with the same rim and touch animations.
 */
object LiquidGlassDefaults {

    /** Light blur: content stays visible behind the surface. */
    val blurRadius = 12.dp

    /** Backdrop saturation boost, chained as a colour filter on 31+. */
    const val saturation = 1.45f

    /** Specular rim: white 55% at the top-left, 10% at the bottom-right. */
    val rim: Brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.55f),
            Color.White.copy(alpha = 0.10f),
        ),
    )

    /** White tint ~8% over a dark tint ~28%. */
    val tints = listOf(
        HazeTint(Color.Black.copy(alpha = 0.28f)),
        HazeTint(Color.White.copy(alpha = 0.08f)),
    )

    /** Below-31 fallback: ~75% opaque dark tint. */
    val fallbackTint = HazeTint(Color.Black.copy(alpha = 0.75f))

    /** Soft shadow: y 6dp, blur 20dp, black ~25%. */
    const val shadowElevation = 20
    const val shadowAlpha = 0.25f
}

/**
 * Press state for one glass surface: finger down and where, so the highlight
 * and the API 33+ lens can follow it. [dragX] is used only by the nav bubble
 * while it is dragged.
 */
class LiquidTouchState {
    var pressed by mutableStateOf(false)
        internal set
    var position by mutableStateOf(Offset.Zero)
        internal set
    var dragX by mutableStateOf(0f)
        internal set

    /** 0..1 as the press establishes; drives the 33+ lens. */
    val pressProgress = Animatable(0f)

    val isPressed: Boolean get() = pressed
}

@Composable
fun rememberLiquidTouch(): LiquidTouchState = remember { LiquidTouchState() }

/**
 * The Liquid Glass modifier.
 *
 * @param hazeState the screen-wide [HazeState]; the content behind carries
 *   `Modifier.hazeSource(state)` once. Null renders the below-31 fallback.
 * @param touchState press state; wire it with [Modifier.liquidGlassTouch] and
 *   [Modifier.liquidGlassPress], or leave null for a static surface.
 * @param refraction the API 33+ lens while pressed - the nav bubble only.
 */
fun Modifier.liquidGlass(
    hazeState: HazeState?,
    shape: Shape,
    touchState: LiquidTouchState? = null,
    refraction: Boolean = false,
): Modifier {
    val api31 = Build.VERSION.SDK_INT >= 31
    val api33 = Build.VERSION.SDK_INT >= 33
    return this
        .shadow(
            elevation = LiquidGlassDefaults.shadowElevation.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = LiquidGlassDefaults.shadowAlpha),
            spotColor = Color.Black.copy(alpha = LiquidGlassDefaults.shadowAlpha),
        )
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color(0xFF07080A),
                        tints = LiquidGlassDefaults.tints,
                        blurRadius = LiquidGlassDefaults.blurRadius,
                        fallbackTint = LiquidGlassDefaults.fallbackTint,
                    ),
                )
            } else {
                Modifier.background(Color.Black.copy(alpha = 0.75f))
            },
        )
        .then(
            if (api31) {
                // Saturation over Haze's blurred backdrop. The sheen and rim
                // drawn later are near-neutral, so the vibrancy lands on the
                // backdrop, which is the point.
                Modifier.graphicsLayer {
                    val matrix = ColorMatrix().apply {
                        setToSaturation(LiquidGlassDefaults.saturation)
                    }
                    val filter = ColorMatrixColorFilter(matrix)
                    renderEffect = android.graphics.RenderEffect
                        .createColorFilterEffect(filter)
                        .asComposeRenderEffect()
                }
            } else {
                Modifier
            },
        )
        .background(Color.White.copy(alpha = 0.06f))
        .rim(shape, api33)
        .then(
            if (refraction && api33 && touchState != null) {
                Modifier.refractionLens(touchState)
            } else {
                Modifier
            },
        )
}

/** Specular rim: soft inner glow, faint chromatic fringe on 33+, 1.5dp border. */
private fun Modifier.rim(shape: Shape, api33: Boolean): Modifier = this
    .drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.16f),
                0.12f to Color.Transparent,
            ),
        )
        if (api33) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(
                        Color.Red.copy(alpha = 0.16f),
                        Color.Transparent,
                        Color.Blue.copy(alpha = 0.16f),
                    ),
                ),
            )
        }
    }
    .border(width = 1.5.dp, brush = LiquidGlassDefaults.rim, shape = shape)

/**
 * API 33+: the lens. The shader sees everything drawn into this node - Haze's
 * blurred backdrop included - so it magnifies and bends the backdrop around
 * the finger with a chromatic pull at the outer band.
 */
private fun Modifier.refractionLens(touch: LiquidTouchState): Modifier {
    if (Build.VERSION.SDK_INT < 33) return this
    // Created once per composition-free call; the shader object is mutated in
    // the graphicsLayer block, which re-reads it whenever state changes.
    return graphicsLayer {
        if (touch.pressed && touch.pressProgress.value > 0.01f) {
            val shader = RuntimeShader(REFACTION_SHADER_SRC.trimIndent())
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uCenter", touch.position.x, touch.position.y)
            shader.setFloatUniform(
                "uRadius",
                maxOf(size.width, size.height) * 0.62f,
            )
            shader.setFloatUniform("uProgress", touch.pressProgress.value)
            renderEffect = android.graphics.RenderEffect
                .createRuntimeShaderEffect(shader, "uContent")
                .asComposeRenderEffect()
        } else {
            renderEffect = null
        }
    }
}

/** Tracks the finger on the surface while pressed, for highlight and lens. */
fun Modifier.liquidGlassTouch(touch: LiquidTouchState): Modifier =
    pointerInput(touch) {
        awaitEachGesture {
            val down = awaitFirstDown()
            touch.position = down.position
            touch.pressed = true
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                touch.position = change.position
                if (!change.pressed) break
            }
            touch.pressed = false
        }
    }

/**
 * Touch response: scale to ~1.06 on a ~0.7 damping spring, highlight following
 * the finger, light haptic on press; release springs back with overshoot and a
 * softer impact haptic. Drives [LiquidTouchState.pressProgress] for the lens.
 */
@Composable
fun Modifier.liquidGlassPress(
    touch: LiquidTouchState?,
    haptics: HapticFeedback,
    scaleOnPress: Float = 1.06f,
): Modifier {
    if (touch == null) return this
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (touch.pressed) scaleOnPress else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "liquidPress",
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !touch.pressed) {
            touch.pressed = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else if (!isPressed && touch.pressed) {
            touch.pressed = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(touch.pressed) {
        if (touch.pressed) {
            touch.pressProgress.snapTo(0f)
            touch.pressProgress.animateTo(1f, tween(120))
        } else {
            touch.pressProgress.snapTo(0f)
        }
    }
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (touch.pressed) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = touch.position,
                        radius = size.minDimension * 0.8f,
                    ),
                )
            }
        }
        // The interaction source needs an interaction origin: a quiet clickable
        // (indication = null) that reports press/release without visual change.
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = {},
        )
}

private const val REFACTION_SHADER_SRC = """
    uniform shader uContent;
    uniform float2 uSize;
    uniform float2 uCenter;
    uniform float uRadius;
    uniform float uProgress;

    half4 main(float2 p) {
        float2 toC = p - uCenter;
        float r = length(toC);
        float k = 1.0 - 0.1 * uProgress * max(0.0, 1.0 - r / uRadius);
        float2 sp = uCenter + toC * k;
        half4 c = uContent.eval(sp);

        float2 dEdge = min(p, uSize - p);
        float edge = min(dEdge.x, dEdge.y);
        float rim = smoothstep(14.0, 0.0, edge);
        if (rim > 0.001) {
            half4 cr = uContent.eval(sp + float2(1.5 * rim, 0.0));
            half4 cb = uContent.eval(sp - float2(1.5 * rim, 0.0));
            c = half4(mix(c.r, cr.r, rim * 0.6), c.g, mix(c.b, cb.b, rim * 0.6), c.a);
        }
        return c;
    }
"""
