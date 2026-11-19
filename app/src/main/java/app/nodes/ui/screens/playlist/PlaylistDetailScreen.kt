/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.playlist

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nodes.R
import app.nodes.core.data.LibraryRepository
import app.nodes.core.model.Playlist
import app.nodes.core.model.SourceErrorType
import app.nodes.core.model.SourceId
import app.nodes.core.model.SourceResult
import app.nodes.core.model.Track
import app.nodes.core.source.SourceRegistry
import app.nodes.core.source.youtubemusic.YouTubeMusicSource
import app.nodes.ui.components.CircleIconButton
import app.nodes.ui.components.DetailTopBar
import app.nodes.ui.components.EmptyState
import app.nodes.ui.components.ErrorState
import app.nodes.ui.components.LoadingRows
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.MetaRow
import app.nodes.ui.components.PillButton
import app.nodes.ui.components.PillStyle
import app.nodes.ui.components.PlayerBackdrop
import app.nodes.ui.components.AddToPlaylistBus
import app.nodes.ui.components.SongRow
import app.nodes.ui.components.TrackMenuButton
import app.nodes.ui.components.runtimeLabel
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.theme.SpartialGrey
import app.nodes.ui.theme.SpartialSpacing
import app.nodes.ui.theme.SpartialWhite
import app.nodes.ui.theme.rememberArtworkTint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
    val error: SourceErrorType? = null,
    /** True for local playlists, the only editable ones. */
    val isLocal: Boolean = false,
    /** A YouTube Music copy of this playlist is being made right now. */
    val syncing: Boolean = false,
    /** Result of the last push, as a small state the screen renders. */
    val syncMessage: String? = null,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val registry: SourceRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    // Serializes drag-reorder writes so rapid moves apply in order.
    private val reorderMutex = Mutex()

    val likedIds: StateFlow<Set<String>> = library.likedTracks
        .map { tracks -> tracks.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun load(playlistId: String) {
        viewModelScope.launch {
            _state.value = PlaylistUiState(loading = true)
            val detail = library.playlistDetail(playlistId)
            if (detail != null) {
                _state.value = PlaylistUiState(
                    playlist = detail.playlist,
                    tracks = detail.tracks,
                    loading = false,
                    isLocal = true,
                )
                return@launch
            }

            // Not a local playlist, so it is a YouTube Music album, playlist or
            // the liked-songs list: load it through the provider.
            when (val result = registry.byId(SourceId.YOUTUBE_MUSIC)?.getPlaylist(playlistId)) {
                is SourceResult.Success -> {
                    // Record what was fetched: album and artist pages are a
                    // Room query over music the app has already seen.
                    runCatching { library.cacheTracks(result.data.tracks) }
                    _state.value = PlaylistUiState(
                        playlist = result.data.playlist,
                        tracks = result.data.tracks,
                        loading = false,
                    )
                }

                is SourceResult.Failure -> _state.value = PlaylistUiState(
                    loading = false,
                    error = result.type,
                )

                null -> _state.value = PlaylistUiState(
                    loading = false,
                    error = SourceErrorType.UNKNOWN,
                )
            }
        }
    }

    fun toggleLike(track: Track) {
        viewModelScope.launch { library.toggleLike(track) }
    }

    fun setAllLiked(liked: Boolean) {
        val tracks = _state.value.tracks
        viewModelScope.launch {
            tracks.forEach { library.setLiked(it, liked) }
        }
    }

    /** Pins or unpins the playlist; only local playlists are pinnable. */
    fun togglePin() {
        val playlist = _state.value.playlist ?: return
        if (!playlist.isLocal) return
        val target = !playlist.isPinned
        _state.value = _state.value.copy(playlist = playlist.copy(isPinned = target))
        viewModelScope.launch { library.setPinned(playlist.id, target) }
    }

    /**
     * Copies this local playlist to the signed-in YouTube Music account. The
     * option is offered for local playlists only, and the result is reported
     * as a banner instead of a silent write.
     */
    fun pushToYouTubeMusic() {
        val state = _state.value
        val playlist = state.playlist ?: return
        if (!playlist.isLocal || state.tracks.isEmpty() || state.syncing) return
        val source = registry.byId(SourceId.YOUTUBE_MUSIC) as? YouTubeMusicSource ?: return
        _state.value = state.copy(syncing = true, syncMessage = null)
        viewModelScope.launch {
            val result = runCatching { source.pushPlaylist(playlist.title, state.tracks) }
                .getOrElse { YouTubeMusicSource.PlaylistPush.Failed }
            _state.value = _state.value.copy(
                syncing = false,
                syncMessage = when (result) {
                    is YouTubeMusicSource.PlaylistPush.Done -> "playlist-sync:${result.added}:${result.missed}"
                    YouTubeMusicSource.PlaylistPush.SignedOut -> "playlist-sync:signed-out"
                    YouTubeMusicSource.PlaylistPush.Failed -> "playlist-sync:failed"
                },
            )
        }
    }

    fun dismissSyncMessage() {
        _state.value = _state.value.copy(syncMessage = null)
    }

    /** Removes one row; local playlists only. The cached track itself stays. */
    fun removeTrack(track: Track) {
        val playlistId = _state.value.playlist?.id ?: return
        _state.value = _state.value.copy(
            tracks = _state.value.tracks.filterNot { it.id == track.id },
        )
        viewModelScope.launch { library.removeFromPlaylist(playlistId, track.id) }
    }

    /**
     * Moves one row up or down by [delta] steps - drag reorder. The list
     * updates immediately and the store follows under a mutex, so rapid
     * drags apply in the order they happened.
     */
    fun moveTrack(trackId: String, delta: Int) {
        val playlistId = _state.value.playlist?.id ?: return
        val tracks = _state.value.tracks
        val from = tracks.indexOfFirst { it.id == trackId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, tracks.lastIndex)
        if (from == to) return
        _state.value = _state.value.copy(
            tracks = tracks.toMutableList().apply { add(to, removeAt(from)) },
        )
        viewModelScope.launch {
            reorderMutex.withLock { library.movePlaylistTrack(playlistId, from, to) }
        }
    }

    fun renamePlaylist(name: String) {
        val trimmed = name.trim()
        val playlistId = _state.value.playlist?.id ?: return
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(
            playlist = _state.value.playlist?.copy(title = trimmed),
        )
        viewModelScope.launch { library.renamePlaylist(playlistId, trimmed) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        val playlistId = _state.value.playlist?.id ?: return
        viewModelScope.launch {
            library.deletePlaylist(playlistId)
            onDeleted()
        }
    }
}

/**
 * Playlist page with the same full-bleed, cover-to-colour treatment as Now
 * Playing. The artwork fills the upper screen and dissolves into the tinted
 * wash; playlist identity and actions sit in that transition, with the songs
 * continuing on the clean colour field below.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String, String?) -> Unit = { _, _ -> },
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val tracks = state.tracks
    val playlist = state.playlist
    val title = playlist?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.playlist_kicker)
    val artworkUri = playlist?.artworkUri ?: tracks.firstOrNull()?.artworkUri
    val tint = rememberArtworkTint(artworkUri, title)
    val heroTitleOffset = LocalConfiguration.current.screenHeightDp.dp * 0.54f
    val allLiked = tracks.isNotEmpty() && tracks.all { it.id in likedIds }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) { viewModel.load(playlistId) }

    val playAll: () -> Unit = {
        tracks.firstOrNull()?.let { playerViewModel.play(it, tracks) }
    }
    val shuffle: () -> Unit = {
        val shuffled = tracks.shuffled()
        shuffled.firstOrNull()?.let { playerViewModel.play(it, shuffled) }
    }
    val addAllToQueue: () -> Unit = {
        tracks.forEach(playerViewModel::addToQueue)
    }

    PlayerBackdrop(
        artworkUri = artworkUri,
        title = title,
        tint = tint,
        showArtwork = true,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = SpartialSpacing.dockSpace),
        ) {
            item(key = "top") {
                DetailTopBar(
                    onBack = onBack,
                    actions = {
                        Box {
                            CircleIconButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.playlist_more),
                                onClick = { menuOpen = true },
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                if (state.isLocal) {
                                    PlaylistMenuItem(
                                        label = stringResource(R.string.playlist_sync_youtube),
                                        icon = Icons.Rounded.FileUpload,
                                        enabled = !state.syncing && tracks.isNotEmpty(),
                                    ) {
                                        menuOpen = false
                                        viewModel.pushToYouTubeMusic()
                                    }
                                }
                                PlaylistMenuItem(
                                    label = stringResource(R.string.playlist_play_all),
                                    icon = Icons.Rounded.PlayArrow,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    playAll()
                                }
                                PlaylistMenuItem(
                                    label = stringResource(R.string.playlist_shuffle),
                                    icon = Icons.Rounded.Shuffle,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    shuffle()
                                }
                                PlaylistMenuItem(
                                    label = stringResource(R.string.player_add_to_queue),
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    addAllToQueue()
                                }
                                PlaylistMenuItem(
                                    label = stringResource(
                                        if (allLiked) {
                                            R.string.action_unstar
                                        } else {
                                            R.string.action_star
                                        },
                                    ),
                                    icon = if (allLiked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    enabled = tracks.isNotEmpty(),
                                ) {
                                    menuOpen = false
                                    viewModel.setAllLiked(!allLiked)
                                }
                                if (state.isLocal) {
                                    HorizontalDivider()
                                    PlaylistMenuItem(
                                        label = stringResource(R.string.playlist_rename),
                                        icon = Icons.Rounded.Edit,
                                        enabled = true,
                                    ) {
                                        menuOpen = false
                                        renameOpen = true
                                    }
                                    PlaylistMenuItem(
                                        label = stringResource(R.string.playlist_delete),
                                        icon = Icons.Rounded.DeleteOutline,
                                        enabled = true,
                                    ) {
                                        menuOpen = false
                                        deleteOpen = true
                                    }
                                }
                            }
                        }
                    },
                )
            }

            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpartialSpacing.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Keep the identity over the lower part of the cover so
                    // the artwork, title and wash read as one full-bleed hero.
                    Spacer(Modifier.height(heroTitleOffset))
                    Text(
                        text = stringResource(R.string.playlist_kicker).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SpartialWhite.copy(alpha = 0.76f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = SpartialWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    MetaRow(
                        parts = listOf(
                            stringResource(R.string.playlist_track_count, tracks.size),
                            runtimeLabel(tracks.sumOf { it.durationMs }),
                            playlist?.ownerName?.takeIf { it.isNotBlank() }?.let {
                                stringResource(R.string.playlist_featuring, it)
                            }.orEmpty(),
                            playlist?.createdAt?.let { createdAt ->
                                val date = remember(createdAt) {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM)
                                        .format(Date(createdAt))
                                }
                                stringResource(R.string.playlist_created, date)
                            }.orEmpty(),
                        ),
                    )
                    val description = playlist?.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.playlist_description_empty)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpartialGrey,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (tracks.isNotEmpty()) {
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpartialSpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillButton(
                            text = stringResource(R.string.playlist_play_all),
                            onClick = playAll,
                            icon = Icons.Rounded.PlayArrow,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = stringResource(R.string.playlist_shuffle),
                            onClick = shuffle,
                            icon = Icons.Rounded.Shuffle,
                            style = PillStyle.Glass,
                        )
                        CircleIconButton(
                            icon = if (playlist?.isPinned == true) {
                                Icons.Rounded.PushPin
                            } else {
                                Icons.Outlined.PushPin
                            },
                            contentDescription = stringResource(
                                if (playlist?.isPinned == true) {
                                    R.string.action_unpin
                                } else {
                                    R.string.action_pin
                                },
                            ),
                            onClick = { viewModel.togglePin() },
                            tint = if (playlist?.isPinned == true) SpartialWhite else SpartialGrey,
                            filled = playlist?.isPinned == true,
                            enabled = playlist?.isLocal == true,
                        )
                        CircleIconButton(
                            icon = Icons.Rounded.Download,
                            contentDescription = stringResource(
                                R.string.action_download_unavailable,
                            ),
                            onClick = {},
                            enabled = false,
                            filled = true,
                        )
                    }
                }

                item(key = "tracks-head") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = SpartialSpacing.gutter,
                                end = SpartialSpacing.gutter,
                                top = 22.dp,
                                bottom = 6.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_tracks).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = SpartialGrey,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.playlist_track_count, tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = SpartialGrey,
                        )
                    }
                }

                itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
                    // Local playlists can be reordered: a handle drags the row
                    // one slot at a time, and the store follows underneath.
                    val rowHeightPx = with(LocalDensity.current) { 76.dp.toPx() }
                    var dragOffset by remember(track.id) { mutableFloatStateOf(0f) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isLocal) {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = stringResource(R.string.playlist_drag_handle),
                                tint = SpartialGrey,
                                modifier = Modifier
                                    .size(44.dp)
                                    .padding(horizontal = 8.dp)
                                    .pointerInput(track.id) {
                                        detectDragGestures(
                                            onDragEnd = { dragOffset = 0f },
                                            onDragCancel = { dragOffset = 0f },
                                        ) { _, dragAmount ->
                                            dragOffset += dragAmount.y
                                            if (dragOffset > rowHeightPx) {
                                                dragOffset -= rowHeightPx
                                                viewModel.moveTrack(track.id, 1)
                                            } else if (dragOffset < -rowHeightPx) {
                                                dragOffset += rowHeightPx
                                                viewModel.moveTrack(track.id, -1)
                                            }
                                        }
                                    },
                            )
                        }
                        SongRow(
                            track = track,
                            onClick = { playerViewModel.play(track, tracks) },
                            onLongClick = { AddToPlaylistBus.request(track) },
                            position = index + 1,
                            isLiked = track.id in likedIds,
                            onToggleLike = { viewModel.toggleLike(track) },
                            showDownload = false,
                            active = track.id == playerState.current?.id,
                            activePlaying = track.id == playerState.current?.id &&
                                playerState.isPlaying,
                            modifier = Modifier.weight(1f),
                            menu = {
                                TrackMenuButton(
                                    onPlayNext = { playerViewModel.playNext(track) },
                                    onAddToQueue = { playerViewModel.addToQueue(track) },
                                    onAddToPlaylist = { AddToPlaylistBus.request(track) },
                                    onRemoveFromPlaylist = if (state.isLocal) {
                                        { viewModel.removeTrack(track) }
                                    } else {
                                        null
                                    },
                                    onViewArtist = if (track.artist.isNotBlank()) {
                                        { onOpenArtist(track.artist) }
                                    } else {
                                        null
                                    },
                                    onViewAlbum = track.album?.takeIf { it.isNotBlank() }?.let { album ->
                                        { onOpenAlbum(album, track.albumId) }
                                    },
                                )
                            },
                        )
                    }
                }
            }

            when {
                state.loading && tracks.isEmpty() -> item(key = "loading") {
                    LoadingRows()
                }

                state.error != null && tracks.isEmpty() -> item(key = "error") {
                    ErrorState(
                        error = state.error,
                        onRetry = { viewModel.load(playlistId) },
                    )
                }

                !state.loading && tracks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = title,
                        description = stringResource(R.string.playlist_load_failed),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = { viewModel.load(playlistId) },
                    )
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
        }

        // Rename and delete only exist for local playlists.
        if (renameOpen) {
            var name by remember { mutableStateOf(title) }
            AlertDialog(
                onDismissRequest = { renameOpen = false },
                containerColor = Color(0xFF15171B),
                title = {
                    Text(stringResource(R.string.playlist_rename_title), color = SpartialWhite)
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
                            viewModel.renamePlaylist(name)
                            renameOpen = false
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameOpen = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp),
            )
        }
        if (deleteOpen) {
            AlertDialog(
                onDismissRequest = { deleteOpen = false },
                containerColor = Color(0xFF15171B),
                title = {
                    Text(stringResource(R.string.playlist_delete), color = SpartialWhite)
                },
                text = {
                    Text(
                        text = stringResource(R.string.playlist_delete_confirm, title),
                        color = SpartialWhite,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteOpen = false
                            viewModel.deletePlaylist(onBack)
                        },
                    ) {
                        Text(stringResource(R.string.action_remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteOpen = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp),
            )
        }

        state.syncMessage?.let { code ->
            MessageBanner(
                message = when {
                    code == "playlist-sync:signed-out" ->
                        stringResource(R.string.playlist_sync_signed_out)
                    code == "playlist-sync:failed" ->
                        stringResource(R.string.playlist_sync_failed)
                    else -> {
                        val parts = code.removePrefix("playlist-sync:").split(":")
                        stringResource(
                            R.string.playlist_sync_done,
                            parts.getOrNull(0)?.toIntOrNull() ?: 0,
                            parts.getOrNull(1)?.toIntOrNull() ?: 0,
                        )
                    }
                },
                onDismiss = viewModel::dismissSyncMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
        }
    }
}

@Composable
private fun PlaylistMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) SpartialGrey else SpartialGrey.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}
