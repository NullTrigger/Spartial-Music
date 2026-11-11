/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nodes.ui.components.PageHeader
import app.nodes.ui.theme.GlassBorder
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesGreyDim
import app.nodes.ui.theme.NodesSurface
import app.nodes.ui.theme.NodesAccent
import app.nodes.ui.theme.NodesAccentDeep
import app.nodes.ui.theme.NodesWhite

/**
 * One settings screen: the standard Nodes header, then a lazy list of groups.
 *
 * Every settings screen is built from [SettingsGroup] cards so a new topic is a
 * new group rather than a longer scroll, and the back arrow never has to be
 * re-implemented.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        PageHeader(title = title, subtitle = subtitle, back = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
            content = content,
        )
    }
}

/** A titled card. Rows inside it are separated by [SettingsDivider]. */
@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = NodesGreyDim,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(NodesSurface)
                .border(1.dp, GlassBorder, shape),
            content = content,
        )
    }
}

/** Inset hairline between two rows of the same card. */
@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.05f)),
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector, tint: Color = NodesGrey) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = NodesWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon)
        }
        RowText(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 12.dp else 0.dp),
        )
        SettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun SettingsValueRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon)
        }
        RowText(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 12.dp else 0.dp),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = NodesGreyDim,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }
}

/** Same visual weight as a value row, but with nothing to open. */
@Composable
fun SettingsInfoRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon)
        }
        RowText(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 12.dp else 0.dp),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** A tappable row that runs an action instead of opening a screen. */
@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIcon(icon)
        }
        RowText(
            title = title,
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 12.dp else 0.dp),
        )
    }
}

/** A short explanatory paragraph, inset inside a card. */
@Composable
fun SettingsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = NodesGrey,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** The signed-in / anonymous card at the top of the root settings screen. */
@Composable
fun SettingsAccountCard(
    initial: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        NodesAccent.copy(alpha = 0.16f),
                        NodesSurface.copy(alpha = 0.9f),
                    ),
                ),
            )
            .border(1.dp, NodesAccent.copy(alpha = 0.22f), shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(NodesAccent, NodesAccentDeep),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = NodesBlack,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 13.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = NodesWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NodesGrey,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = NodesGreyDim,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}

/** The one switch style every settings screen uses. */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = settingsSwitchColors(),
        modifier = modifier,
    )
}

/** A radio-style row: the selected region carries a check mark. */
@Composable
fun SettingsCheckRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = NodesWhite,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = NodesAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = NodesBlack,
    checkedTrackColor = NodesAccent,
    uncheckedThumbColor = NodesGrey,
    uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
)

/** Opens a link, ignoring the case where no browser is installed. */
internal fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        if (it !is ActivityNotFoundException) throw it
    }
}
