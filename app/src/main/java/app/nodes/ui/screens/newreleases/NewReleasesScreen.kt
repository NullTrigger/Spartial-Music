/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.newreleases

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.Playlist
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PageHeader
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialSpacing
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

/**
 * What the service itself lists as new: albums and EPs, two to a row, each one
 * opening like any other album. Deliberately a grid of releases rather than a
 * second song feed - Trending and Suggestions already cover "what to play now".
 */
@Composable
fun NewReleasesScreen(
    onBack: () -> Unit,
    onOpenRelease: (String) -> Unit,
    viewModel: NewReleasesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = SpartialSpacing.dockSpace),
        ) {
            item(key = "header") {
                PageHeader(
                    title = stringResource(R.string.new_releases_title),
                    subtitle = stringResource(R.string.new_releases_subtitle),
                    back = onBack,
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorState(
                        error = error,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            if (state.loading && state.releases.isEmpty()) {
                item(key = "loading") { LoadingRows() }
            }

            items(
                items = state.releases.chunked(2),
                key = { row -> "release-row-${row.first().id}" },
            ) { row ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = SpartialSpacing.gutter,
                                end = SpartialSpacing.gutter,
                                top = 8.dp,
                                bottom = 12.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        row.forEach { release ->
                            ReleaseCard(
                                release = release,
                                onClick = { onOpenRelease(release.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Artwork(
            artworkUri = release.artworkUri,
            title = release.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(18.dp),
            targetPx = 512,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = release.title,
            style = SpartialType.rowTitle,
            color = SpartialWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (release.trackCount > 0) {
                stringResource(R.string.collection_track_count, release.trackCount)
            } else {
                // A card without a count names whose release it is instead of
                // claiming "0 tracks".
                release.ownerName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.new_releases_album)
            },
            style = SpartialType.rowMeta,
            color = SpartialGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
