/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.ui.theme.SpartialDanger
import app.nodes.ui.theme.SpartialSans
import app.nodes.ui.theme.SpartialSpacing

/**
 * Screen 2: choose an avatar and a username. Everything typed here stays on
 * the device; the avatar itself is never sent anywhere.
 */
@Composable
fun OnboardingProfileScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProfileForm(
        state = state,
        showGuestLine = !state.signedIn,
        primaryLabel = stringResource(R.string.onb_continue),
        onBack = onBack,
        onSelectAvatar = viewModel::onAvatarSelected,
        onPreviousAvatar = viewModel::onPreviousAvatar,
        onNextAvatar = viewModel::onNextAvatar,
        onUsernameChange = viewModel::onUsernameChange,
        onSubmit = { if (viewModel.commitProfile() != null) onContinue() },
        modifier = modifier,
    )
}

/**
 * The same editor reopened from Settings. The rules are identical; only the
 * framing (no guest line, "Save" instead of "Continue") differs.
 */
@Composable
fun ProfileEditScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProfileForm(
        state = state,
        showGuestLine = false,
        primaryLabel = stringResource(R.string.onb_profile_save),
        // The floating bar is docked on every screen after the wizard, and
        // this page's Save button used to finish underneath it.
        dockBar = true,
        onBack = onDone,
        onSelectAvatar = viewModel::onAvatarSelected,
        onPreviousAvatar = viewModel::onPreviousAvatar,
        onNextAvatar = viewModel::onNextAvatar,
        onUsernameChange = viewModel::onUsernameChange,
        onSubmit = { if (viewModel.commitProfile() != null) onDone() },
        modifier = modifier,
    )
}

@Composable
private fun ProfileForm(
    state: OnboardingUiState,
    showGuestLine: Boolean,
    primaryLabel: String,
    dockBar: Boolean = false,
    onBack: () -> Unit,
    onSelectAvatar: (Int) -> Unit,
    onPreviousAvatar: () -> Unit,
    onNextAvatar: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingColors.Background),
    ) {
        OnboardingGlowBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OnboardingChevronButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.onb_back),
                    onClick = onBack,
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.onb_avatar_title),
                fontFamily = SpartialSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = OnboardingColors.Text,
                textAlign = TextAlign.Center,
            )
            if (showGuestLine) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onb_guest_line),
                    fontFamily = SpartialSans,
                    fontSize = 14.sp,
                    color = OnboardingColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingChevronButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.onb_previous_avatar),
                    onClick = onPreviousAvatar,
                )
                Spacer(Modifier.width(18.dp))
                OnboardingAvatar(
                    id = state.avatarId,
                    diameter = 126.dp,
                    contentDescription = stringResource(R.string.onb_avatar_preview),
                )
                Spacer(Modifier.width(18.dp))
                OnboardingChevronButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.onb_next_avatar),
                    onClick = onNextAvatar,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.onb_avatar_caption),
                fontFamily = SpartialSans,
                fontSize = 14.sp,
                color = OnboardingColors.TextMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            for (rowIndex in 0 until 3) {
                AvatarGridRow(
                    rowIndex = rowIndex,
                    selectedId = state.avatarId,
                    onSelect = onSelectAvatar,
                )
                if (rowIndex < 2) Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(26.dp))

            OnboardingUsernameField(
                value = state.username,
                helper = stringResource(R.string.onb_username_helper),
                placeholder = stringResource(R.string.onb_username_placeholder),
                errorMessage = if (state.usernameError) {
                    stringResource(R.string.onb_username_error)
                } else {
                    null
                },
                onValueChange = onUsernameChange,
                onSubmit = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            )

            Spacer(Modifier.height(16.dp))

            OnboardingButton(
                label = primaryLabel,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                filled = true,
            )

            // Room for the floating bar where it is docked, so the button above
            // is reachable instead of ending up behind the bar and the
            // navigation bar.
            Spacer(
                Modifier.height(
                    36.dp + if (dockBar) SpartialSpacing.dockSpace else 0.dp,
                ),
            )
        }
    }
}

/** One row of three avatars. The selected one gets the double ring. */
@Composable
private fun AvatarGridRow(
    rowIndex: Int,
    selectedId: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (column in 0 until 3) {
            val id = rowIndex * 3 + column + 1
            val isSelected = id == selectedId
            val label = avatarLabel(id, isSelected)
            val cell: Dp = 84.dp + if (isSelected) 12.dp else 6.dp
            Box(
                modifier = Modifier
                    .size(cell)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = { onSelect(id) })
                    .semantics { this.contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    // Background-coloured ring, then the light ring outside it.
                    Box(
                        modifier = Modifier
                            .size(84.dp + 12.dp)
                            .clip(CircleShape)
                            .background(OnboardingColors.Text)
                            .padding(3.dp)
                            .background(OnboardingColors.Background, CircleShape),
                    )
                }
                OnboardingAvatar(id = id, diameter = 84.dp, contentDescription = null)
            }
        }
    }
}

@Composable
private fun OnboardingUsernameField(
    value: String,
    helper: String,
    placeholder: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onbGlass(),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = SpartialSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = OnboardingColors.Text,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(OnboardingColors.Text),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontFamily = SpartialSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = OnboardingColors.TextFaint,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = errorMessage ?: helper,
            fontFamily = SpartialSans,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = if (errorMessage != null) SpartialDanger else OnboardingColors.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A 44 dp glass circle that still offers a 48 dp touch target. */
@Composable
private fun OnboardingChevronButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = contentDescription
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .onbGlass(fill = OnboardingColors.GlassFillStrong),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnboardingColors.Text,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun avatarLabel(id: Int, selected: Boolean): String = stringResource(
    if (selected) R.string.onb_avatar_selected else R.string.onb_avatar_option,
    id,
)
