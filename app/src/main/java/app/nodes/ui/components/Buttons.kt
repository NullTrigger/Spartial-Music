/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialGreyDim
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

enum class PillStyle {
    /** White fill, black label: the primary action. */
    Solid,

    /** Translucent glass, white label. */
    Glass,

    /** A tinted wash, used to carry an artwork colour into a control. */
    Tint,
}

/**
 * The shared rounded action. 44dp tall by default so it is a legal touch
 * target; the label and icon follow the user's font scale.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: PillStyle = PillStyle.Solid,
    enabled: Boolean = true,
    tint: Color = SpartialWhite,
    contentDescription: String? = null,
) {
    val background = when (style) {
        PillStyle.Solid -> if (enabled) SpartialWhite else SpartialWhite.copy(alpha = 0.18f)
        PillStyle.Glass -> Color.White.copy(alpha = 0.09f)
        PillStyle.Tint -> tint.copy(alpha = 0.26f)
    }
    val foreground = when (style) {
        PillStyle.Solid -> if (enabled) SpartialBlack else SpartialGreyDim
        else -> if (enabled) SpartialWhite else SpartialGreyDim
    }
    val shape = CircleShape
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(background)
            .then(
                if (style == PillStyle.Glass) {
                    Modifier.glass(shape = shape, strong = true)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = SpartialType.pill,
            color = foreground,
            maxLines = 1,
        )
    }
}

/**
 * A circular icon control used across the player, top bars and row actions.
 * The default 44dp keeps it accessible while still reading as a quiet ghost
 * button.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = SpartialWhite,
    background: Color = Color.White.copy(alpha = 0.1f),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(background) else Modifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else SpartialGreyDim,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * A small count or "DOLBY" style badge. It uses the same glass language as the
 * player controls and never intercepts touch.
 */
@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = SpartialGrey,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 0.8.sp,
            ),
            color = tint,
            maxLines = 1,
        )
    }
}
