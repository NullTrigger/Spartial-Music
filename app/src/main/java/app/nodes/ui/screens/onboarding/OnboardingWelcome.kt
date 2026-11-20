/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.theme.SpartialElevation
import app.nodes.ui.theme.SpartialSans
import kotlin.math.abs
import kotlin.math.floor

/**
 * Screen 1: the welcome page. Rotating card carousel, the promise, and the two
 * ways in - the build's own sign-in, or a guest button that is deliberately
 * exactly as big and as visible as sign-in.
 */
@Composable
fun OnboardingWelcomeScreen(
    onContinue: () -> Unit,
    onOpenWebViewLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val privacyLink = stringResource(R.string.onb_privacy_url)
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onGoogleSignInResult(result.resultCode, result.data)
    }

    // Only advance on the transition that means "this sign-in just worked".
    // Arriving here already signed in (back from screen 2) must not bounce the
    // user forward again.
    var wasSignedIn by remember { mutableStateOf(state.signedIn) }
    LaunchedEffect(state.signedIn) {
        if (state.signedIn && !wasSignedIn) onContinue()
        wasSignedIn = state.signedIn
    }

    val cards = remember {
        listOf(
            WelcomeCard(
                drawableName = "onb_card_quality",
                titleRes = R.string.onb_card_quality,
                descriptionRes = R.string.onb_card_quality_desc,
                fallbackBrush = Brush.verticalGradient(
                    listOf(OnboardingColors.CardBlueStart, OnboardingColors.CardBlueEnd),
                ),
                fallbackTitleColor = Color.White,
                phase = 0f,
            ),
            WelcomeCard(
                drawableName = "onb_card_sessions",
                titleRes = R.string.onb_card_sessions,
                descriptionRes = R.string.onb_card_sessions_desc,
                fallbackBrush = Brush.verticalGradient(
                    listOf(OnboardingColors.CardPaper, OnboardingColors.CardPaper),
                ),
                fallbackTitleColor = OnboardingColors.CardInk,
                phase = 2f / 3f,
            ),
            WelcomeCard(
                drawableName = "onb_card_brand",
                titleRes = R.string.app_name,
                descriptionRes = R.string.onb_card_brand_desc,
                fallbackBrush = Brush.verticalGradient(
                    listOf(OnboardingColors.CardGreenStart, OnboardingColors.CardGreenEnd),
                ),
                fallbackTitleColor = OnboardingColors.CardInk,
                phase = 1f / 3f,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background),
    ) {
        OnboardingGlowBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 58.dp, bottom = 108.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingMark(
                width = 48.dp,
                color = OnboardingColors.Text,
                contentDescription = stringResource(R.string.onb_logo_description),
            )

            Spacer(Modifier.height(24.dp))

            WelcomeShowcase(cards = cards)

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onb_welcome_above),
                fontFamily = SpartialSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = OnboardingColors.TextSoft,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = SpartialSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                color = OnboardingColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (state.playBuild) R.string.onb_welcome_subtitle_play else R.string.onb_welcome_subtitle,
                ),
                fontFamily = SpartialSans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = OnboardingColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            state.notice?.let { notice ->
                Spacer(Modifier.height(16.dp))
                MessageBanner(
                    message = stringResource(
                        when (notice) {
                            OnboardingNotice.SIGN_IN_CANCELLED -> R.string.onb_sign_in_cancelled
                            OnboardingNotice.SIGN_IN_FAILED -> R.string.onb_sign_in_failed
                        },
                    ),
                    onDismiss = viewModel::dismissNotice,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(28.dp))

            val signInAvailable = state.signInMode != OnboardingSignInMode.NONE
            if (signInAvailable) {
                OnboardingButton(
                    label = stringResource(R.string.onb_sign_in_google),
                    onClick = {
                        val intent = viewModel.googleSignInIntent()
                        if (intent != null) {
                            // If no activity can take the Google intent, fall
                            // back to the in-app login the other flavour uses.
                            runCatching { signInLauncher.launch(intent) }
                                .onFailure { onOpenWebViewLogin() }
                        } else {
                            onOpenWebViewLogin()
                        }
                    },
                    filled = true,
                )
                Spacer(Modifier.height(12.dp))
                OnboardingButton(
                    label = stringResource(R.string.onb_continue_guest),
                    onClick = onContinue,
                    filled = false,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        if (state.playBuild) {
                            R.string.onb_sign_in_note_play
                        } else {
                            R.string.onb_sign_in_note
                        },
                    ),
                    fontFamily = SpartialSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = OnboardingColors.TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // A build with no sign-in at all: the guest button is the
                // primary, and there is nothing to explain about accounts.
                OnboardingButton(
                    label = stringResource(R.string.onb_continue_guest),
                    onClick = onContinue,
                    filled = true,
                )
            }
        }

        WelcomeFooter(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
            onOpenPrivacy = { openCommunity(context, privacyLink) },
        )
    }
}

@Composable
private fun WelcomeFooter(modifier: Modifier = Modifier, onOpenPrivacy: () -> Unit) {
    val privacyAction = stringResource(R.string.onb_privacy_action)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onb_footer_disclaimer),
            fontFamily = SpartialSans,
            fontSize = 11.sp,
            color = OnboardingColors.Footer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = privacyAction, onClick = onOpenPrivacy)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.onb_footer_credits),
            fontFamily = SpartialSans,
            fontSize = 11.sp,
            color = OnboardingColors.Footer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** The 56 dp fully rounded onboarding button, filled (primary) or glass. */
@Composable
internal fun OnboardingButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val foreground = if (filled) OnboardingColors.Background else OnboardingColors.Text
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(
                        if (enabled) OnboardingColors.Text else OnboardingColors.Text.copy(alpha = 0.3f),
                    )
                } else {
                    Modifier.onbGlass()
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = SpartialSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            color = foreground,
        )
    }
}

// ---------------------------------------------------------------------
// The rotating card carousel.
// ---------------------------------------------------------------------

/** One loop: 2.8 s in the centre, then two 0.5 s moves and two side holds. */
internal const val CAROUSEL_LOOP_MS = 9000

private const val CARD_WIDTH_DP = 180f
private const val CARD_HEIGHT_DP = 250f
private const val SIDE_OFFSET_DP = 118f
private const val SIDE_DROP_DP = 10f
private const val SIDE_SCALE = 0.88f
private const val SIDE_TILT_DEGREES = 40f

/** Where a card sits at one point of the loop. */
internal data class CardPose(
    val translationXDp: Float,
    val translationYDp: Float,
    val scale: Float,
    val rotationY: Float,
)

private val PoseCentre = CardPose(0f, 0f, 1f, 0f)
private val PoseLeft = CardPose(-SIDE_OFFSET_DP, SIDE_DROP_DP, SIDE_SCALE, SIDE_TILT_DEGREES)
private val PoseRight = CardPose(SIDE_OFFSET_DP, SIDE_DROP_DP, SIDE_SCALE, -SIDE_TILT_DEGREES)

private fun lerp(a: CardPose, b: CardPose, t: Float): CardPose = CardPose(
    translationXDp = a.translationXDp + (b.translationXDp - a.translationXDp) * t,
    translationYDp = a.translationYDp + (b.translationYDp - a.translationYDp) * t,
    scale = a.scale + (b.scale - a.scale) * t,
    rotationY = a.rotationY + (b.rotationY - a.rotationY) * t,
)

/** The move curve of the design sheet: cubic-bezier(.65, 0, .35, 1). */
internal fun easeInOutCard(fraction: Float): Float =
    cubicBezierPoint(fraction.coerceIn(0f, 1f), 0.65f, 0f, 0.35f, 1f)

private fun cubicBezierPoint(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var t = x
    // Three Newton steps on the x curve is plenty at this precision.
    repeat(4) {
        val dx = bezierAxis(t, x1, x2) - x
        val slope = bezierAxisDerivative(t, x1, x2)
        if (slope < 1e-5f && slope > -1e-5f) return@repeat
        t = (t - dx / slope).coerceIn(0f, 1f)
    }
    return bezierAxis(t, y1, y2)
}

private fun bezierAxis(t: Float, a: Float, b: Float): Float {
    val u = 1f - t
    return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
}

private fun bezierAxisDerivative(t: Float, a: Float, b: Float): Float {
    val u = 1f - t
    return 3f * u * u * a + 6f * u * t * (b - a) + 3f * t * t * (1f - b)
}

/**
 * The loop as a pure function of progress, so the reduced-motion layout and the
 * animation share one schedule: centre until 0.28, glide to the left, hold,
 * glide across (behind the centre card) to the right, hold, glide back in.
 */
internal fun cardPose(progress: Float): CardPose {
    val p = progress - floor(progress)
    return when {
        p < 0.28f -> PoseCentre
        p < 0.33f -> lerp(PoseCentre, PoseLeft, easeInOutCard((p - 0.28f) / 0.05f))
        p < 0.61f -> PoseLeft
        p < 0.66f -> lerp(PoseLeft, PoseRight, easeInOutCard((p - 0.61f) / 0.05f))
        p < 0.94f -> PoseRight
        else -> lerp(PoseRight, PoseCentre, easeInOutCard((p - 0.94f) / 0.06f))
    }
}

private data class WelcomeCard(
    val drawableName: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val fallbackBrush: Brush,
    val fallbackTitleColor: Color,
    /** Where this card starts in the loop: 0 centre, 1/3 left, 2/3 right. */
    val phase: Float,
)

/** The card nearest the middle of the stage, which is the one the dot follows. */
private fun centreCardIndex(cards: List<WelcomeCard>, loop: Float): Int {
    var best = 0
    var bestDistance = Float.MAX_VALUE
    cards.forEachIndexed { index, card ->
        val distance = abs(cardPose(loop + card.phase).translationXDp)
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/** The stage and its page dots, sharing one clock so they never disagree. */
@Composable
private fun WelcomeShowcase(cards: List<WelcomeCard>) {
    val motionDisabled = rememberAnimatorDisabled()
    val transition = rememberInfiniteTransition(label = "onboarding-carousel")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CAROUSEL_LOOP_MS, easing = LinearEasing),
        ),
        label = "onboarding-carousel-progress",
    )
    // Reduced motion freezes the schedule at its opening frame, which is
    // already the reference layout: the quality card centred, the other two
    // parked left and right.
    val loop = if (motionDisabled) 0f else animated

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            cards.forEach { card ->
                val pose = cardPose(loop + card.phase)
                val description = stringResource(card.descriptionRes)
                Box(
                    modifier = Modifier
                        .size(width = CARD_WIDTH_DP.dp, height = CARD_HEIGHT_DP.dp)
                        .graphicsLayer {
                            rotationY = pose.rotationY
                            cameraDistance = 12f * density
                            scaleX = pose.scale
                            scaleY = pose.scale
                            translationX = pose.translationXDp.dp.toPx()
                            translationY = pose.translationYDp.dp.toPx()
                        }
                        .zIndex(pose.scale)
                        .shadow(SpartialElevation.high, RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        .semantics { contentDescription = description },
                ) {
                    OnboardingCardArt(
                        drawableName = card.drawableName,
                        title = stringResource(card.titleRes),
                        fallbackBrush = card.fallbackBrush,
                        titleColor = card.fallbackTitleColor,
                        contentDescription = description,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        PageDots(count = cards.size, centre = centreCardIndex(cards, loop))
    }
}

@Composable
private fun PageDots(count: Int, centre: Int) {
    val selected = stringResource(R.string.onb_dot_selected)
    val other = stringResource(R.string.onb_dot_other)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(18.dp),
    ) {
        for (index in 0 until count) {
            val isCentre = index == centre
            Box(
                modifier = Modifier
                    .width(if (isCentre) 18.dp else 6.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCentre) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        },
                    )
                    .semantics {
                        contentDescription = if (isCentre) selected else other
                    },
            )
        }
    }
}
