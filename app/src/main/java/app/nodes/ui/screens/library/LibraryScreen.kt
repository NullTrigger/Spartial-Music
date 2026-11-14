/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nodes.R
import app.nodes.core.model.Playlist
import app.nodes.core.model.Track
import app.nodes.ui.components.Artwork
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.PageHeader
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.SearchField
import app.nodes.ui.components.SectionHeader
import app.nodes.ui.components.SegmentedFilter
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.glass
import app.nodes.ui.components.trackSupportingLine
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialSpacing
import app.nodes.ui.theme.SpartialType
import app.nodes.ui.theme.SpartialWhite

private enum class LibraryTab(val labelRes: Int) {
    PLAYLISTS(R.string.library_playlists_section),
    LIKED(R.string.library_liked),
    RECENT(R.string.library_recently_added),
    ARTISTS(R.string.library_artists),
}

/**
 * The listener's own music. Everything is local except the YouTube Music
 * playlists and likes that appear when that account is connected.
 *
 * Playlists start the screen; songs, recently played and artists are one tap
 * away in the segmented control. The local filter narrows whichever tab is
 * open without another database query.
 */
@Composable
fun LibraryScreen(
    onOpenPlaylist: (String) -> Unit,
    onOpenSongs: () -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    val tab = LibraryTab.entries[tabIndex]

    val playlists = state.playlists.filter { it.matchesQuery(query) }
    val liked = state.likedTracks.filter { it.matchesQuery(query) }
    val recent = state.recentlyPlayed.filter { it.matchesQuery(query) }
    val artists = state.artists.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        PageHeader(
            title = stringResource(R.string.library_title),
            subtitle = stringResource(
                R.string.library_summary,
                state.playlists.size,
                state.allTracks.size,
            ),
        )

        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = {},
            placeholder = stringResource(R.string.library_search),
            modifier = Modifier.padding(
                start = SpartialSpacing.gutter,
                end = SpartialSpacing.gutter,
                top = 12.dp,
            ),
        )

        SegmentedFilter(
            options = LibraryTab.entries.map { stringResource(it.labelRes) },
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it },
            modifier = Modifier.padding(horizontal = SpartialSpacing.gutter, vertical = 10.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = SpartialSpacing.dockSpace),
        ) {
            if (state.loading) {
                item(key = "loading") { LoadingRows() }
                return@LazyColumn
            }

            when (tab) {
                LibraryTab.PLAYLISTS -> {
                    item(key = "playlists-actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpartialSpacing.gutter, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PillButton(
                                text = stringResource(R.string.library_new_playlist),
                                onClick = { showCreate = true },
                                icon = Icons.Rounded.Add,
                            )
                        }
                    }

                    if (playlists.isEmpty()) {
                        item(key = "playlists-empty") {
                            EmptyState(
                                title = stringResource(R.string.library_playlists_section),
                                description = if (query.isBlank()) {
                                    stringResource(R.string.library_empty_playlists)
                                } else {
                                    stringResource(R.string.library_empty_search, query)
                                },
                            )
                        }
                    } else {
                        item(key = "pins-head") {
                            SectionHeader(title = stringResource(R.string.library_pins))
                        }
                        item(key = "pins") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SpartialSpacing.gutter),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = playlists.take(3),
                                    key = { "pin-${it.id}" },
                                ) { playlist ->
                                    PlaylistTile(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist.id) },
                                        modifier = Modifier.width(148.dp),
                                    )
                                }
                            }
                        }

                        item(key = "playlists-head") {
                            SectionHeader(title = stringResource(R.string.library_playlists_section))
                        }
                        items(
                            items = playlists.chunked(2),
                            key = { row -> "playlist-row-${row.first().id}" },
                        ) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = SpartialSpacing.gutter,
                                        vertical = 6.dp,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                row.forEach { playlist ->
                                    PlaylistTile(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                LibraryTab.LIKED -> {
                    item(key = "liked-head") {
                        SectionHeader(
                            title = stringResource(R.string.library_liked),
                            subtitle = stringResource(R.string.tracks_count, liked.size),
                            onClick = onOpenSongs,
                            actionLabel = stringResource(R.string.library_all_songs),
                        )
                    }
                    if (liked.isEmpty()) {
                        item(key = "liked-empty") {
                            EmptyState(
                                title = stringResource(R.string.library_liked),
                                description = if (query.isBlank()) {
                                    stringResource(R.string.library_empty_liked)
                                } else {
                                    stringResource(R.string.library_empty_search, query)
                                },
                            )
                        }
                    } else {
                        trackItems(
                            tracks = liked,
                            likedIds = state.likedIds,
                            activeId = playerState.current?.id,
                            onPlay = { track -> playerViewModel.play(track, liked) },
                            onToggleLike = viewModel::toggleLike,
                            onPlayNext = playerViewModel::playNext,
                            onAddToQueue = playerViewModel::addToQueue,
                            onOpenArtist = onOpenArtist,
                        )
                    }
                }

                LibraryTab.RECENT -> {
                    item(key = "recent-head") {
                        SectionHeader(
                            title = stringResource(R.string.library_recently_added),
                            subtitle = stringResource(R.string.tracks_count, recent.size),
                        )
                    }
                    if (recent.isEmpty()) {
                        item(key = "recent-empty") {
                            EmptyState(
                                title = stringResource(R.string.library_recently_added),
                                description = if (query.isBlank()) {
                                    stringResource(R.string.library_empty_recent)
                                } else {
                                    stringResource(R.string.library_empty_search, query)
                                },
                            )
                        }
                    } else {
                        trackItems(
                            tracks = recent,
                            likedIds = state.likedIds,
                            activeId = playerState.current?.id,
                            onPlay = { track -> playerViewModel.play(track, recent) },
                            onToggleLike = viewModel::toggleLike,
                            onPlayNext = playerViewModel::playNext,
                            onAddToQueue = playerViewModel::addToQueue,
                            onOpenArtist = onOpenArtist,
                        )
                    }
                }

                LibraryTab.ARTISTS -> {
                    item(key = "artists-head") {
                        SectionHeader(
                            title = stringResource(R.string.library_artists),
                            subtitle = stringResource(R.string.artist_songs_count, artists.size),
                        )
                    }
                    if (artists.isEmpty()) {
                        item(key = "artists-empty") {
                            EmptyState(
                                title = stringResource(R.string.library_artists),
                                description = if (query.isBlank()) {
                                    stringResource(R.string.library_empty_artists)
                                } else {
                                    stringResource(R.string.library_empty_search, query)
                                },
                            )
                        }
                    } else {
                        items(artists, key = { "artist-${it.name}" }) { artist ->
                            LibraryArtistRow(
                                artist = artist,
                                onClick = { onOpenArtist(artist.name) },
                            )
                        }
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = Color(0xFF15171B),
            title = {
                Text(stringResource(R.string.library_new_playlist), color = SpartialWhite)
            },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.library_playlist_name)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = SpartialWhite,
                        unfocusedTextColor = SpartialWhite,
                        cursorColor = SpartialWhite,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(name)
                        showCreate = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackItems(
    tracks: List<Track>,
    likedIds: Set<String>,
    activeId: String?,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    items(tracks, key = { "library-${it.id}" }) { track ->
        SongRow(
            track = track,
            onClick = { onPlay(track) },
            supportingText = trackSupportingLine(track),
            isLiked = track.id in likedIds,
            onToggleLike = { onToggleLike(track) },
            showDownload = false,
            active = track.id == activeId,
            menu = {
                TrackMenuButton(
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onViewArtist = if (track.artist.isNotBlank()) {
                        { onOpenArtist(track.artist) }
                    } else {
                        null
                    },
                )
            },
        )
    }
}

@Composable
private fun PlaylistTile(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Artwork(
            artworkUri = playlist.artworkUri,
            title = playlist.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(18.dp),
            targetPx = 512,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = SpartialType.rowTitle,
            color = SpartialWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.playlist_track_count, playlist.trackCount),
            style = SpartialType.rowMeta,
            color = SpartialGrey,
            maxLines = 1,
        )
    }
}

@Composable
private fun LibraryArtistRow(
    artist: LibraryArtist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SpartialSpacing.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = artist.artworkUri,
            title = artist.name,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            targetPx = 256,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = SpartialType.rowTitle,
                color = SpartialWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.artist_songs_count, artist.trackCount),
                style = SpartialType.rowMeta,
                color = SpartialGrey,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = SpartialGrey,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun Playlist.matchesQuery(term: String): Boolean {
    val needle = term.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        description?.contains(needle, ignoreCase = true) == true ||
        ownerName?.contains(needle, ignoreCase = true) == true
}

private fun Track.matchesQuery(term: String): Boolean {
    val needle = term.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        artist.contains(needle, ignoreCase = true) ||
        album?.contains(needle, ignoreCase = true) == true
}
