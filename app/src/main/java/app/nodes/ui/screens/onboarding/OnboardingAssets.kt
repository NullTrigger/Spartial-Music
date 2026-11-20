/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.ui.theme.ArtAccents
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialSans
import java.util.concurrent.ConcurrentHashMap

/**
 * The onboarding palette. It follows the app's dark glass language, but where
 * the design sheet and the shared tokens disagree the sheet's values win, so
 * they are named once here instead of scattered across the three screens.
 */
object OnboardingColors {
    val Background = SpartialBlack // #07080A
    val Text = Color(0xFFF1F2F4)
    val TextSoft = Color(0xFFA9AFB6)
    val TextMuted = Color(0xFFB4BAC1)
    val TextFaint = Color(0xFF9AA0A6)
    val Footer = Color(0xFF8A9097)
    val GlassFill = Color(0x14FFFFFF) // white 8%
    val GlassBorder = Color(0x47FFFFFF) // white 28%
    val GlowBlue = Color(0xFF1E6FB8)
    val GlowGreen = Color(0xFF6E9A6B)
    val Check = Color(0xFF4CC38A)
    val TelegramBlue = Color(0xFF2AABEE)
    val GithubInk = Color(0xFF1D2026)
    val StarGold = Color(0xFFF5C451)
    val CardBlueStart = Color(0xFF0B66B3)
    val CardBlueEnd = Color(0xFF5BA8DA)
    val CardPaper = Color(0xFFF4F5F7)
    val CardGreenStart = Color(0xFFF4F7F1)
    val CardGreenEnd = Color(0xFF8FB08C)
    val CardInk = Color(0xFF1A1D21)

    /** The glass surface used by every onboarding control. */
    val GlassFillStrong = Color(0x1FFFFFFF)
}

/** The tint behind a drawn avatar placeholder, stable for one id. */
internal fun onboardingAvatarAccent(id: Int): Color =
    ArtAccents[(id - OnboardingRules.MIN_AVATAR).mod(ArtAccents.size)]

/**
 * Runtime image lookup.
 *
 * The card and avatar bitmaps are contributed separately from the code, so
 * nothing here may fail when one is absent: a name is resolved to a resource
 * id (0 means missing) and every caller draws a fallback for that case.
 */
private val drawableIdCache = ConcurrentHashMap<String, Int>()

fun drawableIdFor(context: Context, name: String): Int = drawableIdCache.getOrPut(name) {
    context.resources.getIdentifier(name, "drawable", context.packageName)
}

@Composable
fun rememberDrawableId(name: String): Int {
    val context = LocalContext.current
    return remember(name) { drawableIdFor(context, name) }
}

/**
 * True when the system's animation scale is off, which is what "reduce motion"
 * means for these screens. The carousel then draws one static row instead.
 */
@Composable
fun rememberAnimatorDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/** Two soft colour glows behind the content. Decorative: never focusable. */
@Composable
fun OnboardingGlowBackdrop(modifier: Modifier = Modifier) {
    val width = LocalConfiguration.current.screenWidthDp.dp
    val height = LocalConfiguration.current.screenHeightDp.dp
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.TopStart)
                .offset(x = width * 0.04f, y = height * 0.05f)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to OnboardingColors.GlowBlue.copy(alpha = 0.25f),
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.TopStart)
                .offset(x = width - 260.dp, y = height * 0.48f)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to OnboardingColors.GlowGreen.copy(alpha = 0.25f),
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** The logo mark: three petals around a centre, drawn so no build can lack it. */
@Composable
fun OnboardingMark(
    width: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val height = width * (30f / 48f)
    val description = contentDescription.orEmpty()
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .then(
                if (description.isEmpty()) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = description }
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val petalWidth = size.height * 0.38f
            val petalHeight = size.height * 0.44f
            val petalRise = size.height * 0.22f
            repeat(3) { index ->
                rotate(degrees = index * 120f, pivot = Offset(cx, cy)) {
                    drawOval(
                        color = color,
                        size = Size(petalWidth, petalHeight),
                        topLeft = Offset(cx - petalWidth / 2f, cy - petalRise - petalHeight / 2f),
                    )
                }
            }
        }
    }
}

/**
 * The app icon mark at the top of the wizard: `ic_app_mark` (the same glyph the
 * splash screen uses) when the build carries it, otherwise the drawn
 * [OnboardingMark], so the header is never empty.
 */
@Composable
fun OnboardingAppIcon(
    width: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resId = rememberDrawableId("ic_app_mark")
    if (resId == 0) {
        OnboardingMark(
            width = width,
            color = OnboardingColors.Text,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        return
    }
    val description = contentDescription.orEmpty()
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(width = width, height = width * (224f / 244f))
            .then(
                if (description.isEmpty()) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = description }
                },
            ),
    )
}

/**
 * One avatar: `avatar_<id>` when the build carries it, otherwise a drawn
 * placeholder (a tinted disc with a head and shoulders).
 */
@Composable
fun OnboardingAvatar(
    id: Int,
    diameter: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resId = rememberDrawableId("avatar_$id")
    val accent = onboardingAvatarAccent(id)
    val description = contentDescription.orEmpty()
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.5f))
            .then(
                if (description.isEmpty()) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = description }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val side = this.size.width
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.35f)),
                        endY = this.size.height,
                    ),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.30f),
                    radius = side * 0.33f,
                    center = Offset(side / 2f, this.size.height * 0.88f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.78f),
                    radius = side * 0.17f,
                    center = Offset(side / 2f, this.size.height * 0.40f),
                )
            }
        }
    }
}

/**
 * Card art for the welcome carousel: the contributed bitmap when it exists,
 * otherwise the drawn card from the design sheet with its title.
 */
@Composable
fun OnboardingCardArt(
    drawableName: String,
    title: String,
    fallbackBrush: Brush,
    titleColor: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val resId = rememberDrawableId(drawableName)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp)),
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(fallbackBrush),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = title,
                    fontFamily = SpartialSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = titleColor,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
    }
}

/**
 * The onboarding glass surface: white film, hairline border, fully rounded or
 * whatever shape the control asks for. No [Modifier.blur], so it also renders
 * on API 26.
 */
internal fun Modifier.onbGlass(
    shape: Shape = CircleShape,
    fill: Color = OnboardingColors.GlassFill,
    borderColor: Color = OnboardingColors.GlassBorder,
    borderWidth: Dp = 1.dp,
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(borderWidth, borderColor, shape)
