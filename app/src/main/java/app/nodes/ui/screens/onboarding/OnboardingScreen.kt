/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nodes.R
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesGrey
import app.nodes.ui.theme.NodesSerif
import app.nodes.ui.theme.NodesWhite

/**
 * First-run screen, matching the reference lockup: mark + serif wordmark, the
 * "This is an Indie Project" line, three white pills, and the quiet footnote.
 */
@Composable
fun OnboardingScreen(
    onAllSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val githubUrl = stringResource(R.string.community_github_url)
    val telegramUrl = stringResource(R.string.community_telegram_url)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NodesBlack)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_spartial_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = NodesSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = NodesWhite,
            )
        }

        Spacer(Modifier.height(72.dp))

        Text(
            text = stringResource(R.string.onboarding_heading),
            style = MaterialTheme.typography.headlineLarge,
            color = NodesWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))

        OnboardingPill(
            label = stringResource(R.string.onboarding_github),
            icon = Icons.Rounded.Code,
            onClick = { openCommunity(context, githubUrl) },
        )
        Spacer(Modifier.height(14.dp))
        OnboardingPill(
            label = stringResource(R.string.onboarding_telegram),
            icon = Icons.Rounded.Send,
            onClick = { openCommunity(context, telegramUrl) },
        )
        Spacer(Modifier.height(14.dp))
        OnboardingPill(
            label = stringResource(R.string.onboarding_all_set),
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            onClick = onAllSet,
        )

        Spacer(Modifier.height(26.dp))

        Text(
            text = stringResource(R.string.onboarding_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = NodesGrey,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OnboardingPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(CircleShape)
            .background(NodesWhite)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NodesBlack,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = NodesBlack,
        )
    }
}

private fun openCommunity(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        if (it !is ActivityNotFoundException) throw it
    }
}
