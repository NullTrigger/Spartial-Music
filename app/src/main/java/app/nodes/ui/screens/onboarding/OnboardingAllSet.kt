/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.theme.SpartialSans

/**
 * Screen 3: the confirmation. Two optional community links, and the only way
 * into the app. The links are plain `ACTION_VIEW` intents to the browser: no
 * WebView, no redirect, nothing that could report whether the user tapped.
 */
@Composable
fun OnboardingAllSetScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val telegramUrl = stringResource(R.string.community_telegram_url)
    val githubUrl = stringResource(R.string.community_github_url)
    val savedLabel = stringResource(R.string.onb_allset_badge)

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
                .padding(top = 96.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                OnboardingAvatar(
                    id = state.avatarId,
                    diameter = 118.dp,
                    contentDescription = stringResource(R.string.onb_allset_avatar),
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(OnboardingColors.Check)
                        .border(3.dp, OnboardingColors.Background, CircleShape)
                        .semantics { contentDescription = savedLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = OnboardingColors.Background,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.onb_allset_title),
                fontFamily = SpartialSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = OnboardingColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.onb_allset_hello, state.username),
                fontFamily = SpartialSans,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = OnboardingColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(26.dp))

            Text(
                text = stringResource(R.string.onb_allset_support),
                fontFamily = SpartialSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color(0xFFE3E6EA),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(22.dp))

            OnboardingSupportCard(
                icon = Icons.Rounded.Send,
                iconBackground = OnboardingColors.TelegramBlue,
                iconTint = Color.White,
                iconBorder = null,
                title = stringResource(R.string.onb_community_title),
                description = stringResource(R.string.onb_community_desc),
                action = stringResource(R.string.onb_community_action),
                onClick = { openCommunity(context, telegramUrl) },
            )

            Spacer(Modifier.height(16.dp))

            OnboardingSupportCard(
                icon = Icons.Rounded.Star,
                iconBackground = OnboardingColors.GithubInk,
                iconTint = OnboardingColors.StarGold,
                iconBorder = Color.White.copy(alpha = 0.2f),
                title = stringResource(R.string.onb_github_title),
                description = stringResource(R.string.onb_github_desc),
                action = stringResource(R.string.onb_github_action),
                onClick = { openCommunity(context, githubUrl) },
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.onb_allset_note),
                fontFamily = SpartialSans,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = OnboardingColors.TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OnboardingButton(
            label = stringResource(R.string.onb_allset_button),
            onClick = {
                viewModel.commitProfile()
                viewModel.completeOnboarding(onDone)
            },
            filled = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 44.dp),
        )
    }
}

@Composable
private fun OnboardingSupportCard(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    iconBorder: Color?,
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), cardShape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBackground)
                .then(
                    if (iconBorder != null) {
                        Modifier.border(1.dp, iconBorder, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 10.dp),
        ) {
            Text(
                text = title,
                fontFamily = SpartialSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = OnboardingColors.Text,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                fontFamily = SpartialSans,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = OnboardingColors.TextMuted,
            )
        }

        Box(
            modifier = Modifier
                .height(40.dp)
                .width(84.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
                .semantics { contentDescription = action },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = action,
                fontFamily = SpartialSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = OnboardingColors.Text,
            )
        }
    }
}
