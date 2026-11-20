/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nodes.R
import app.nodes.core.crash.AppSnapshot
import app.nodes.ui.components.AddToPlaylistBus
import app.nodes.ui.components.BarCollapseBus
import app.nodes.ui.components.FloatingTabBar
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.MiniPlayer
import app.nodes.ui.components.SpartialNavItems
import app.nodes.ui.components.ScrollTopBus
import dev.chrisbanes.haze.hazeSource
import app.nodes.ui.screens.collection.CollectionScreen
import app.nodes.ui.screens.diagnostics.DiagnosticsScreen
import app.nodes.ui.screens.home.HomeScreen
import app.nodes.ui.screens.library.LibraryScreen
import app.nodes.ui.screens.login.MusicLoginScreen
import app.nodes.ui.screens.onboarding.OnboardingAllSetScreen
import app.nodes.ui.screens.onboarding.OnboardingProfileScreen
import app.nodes.ui.screens.onboarding.OnboardingWelcomeScreen
import app.nodes.ui.screens.onboarding.ProfileEditScreen
import app.nodes.ui.components.EmptyState
import app.nodes.ui.screens.equalizer.EqualizerScreen
import app.nodes.ui.screens.player.NowPlayingScreen
import app.nodes.ui.screens.playlist.PlaylistNotice
import app.nodes.ui.screens.playlist.PlaylistPickerDialog
import app.nodes.ui.screens.playlist.PlaylistPickerViewModel
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.screens.playlist.PlaylistDetailScreen
import app.nodes.ui.screens.reel.ReelScreen
import app.nodes.ui.screens.search.SearchScreen
import app.nodes.ui.screens.sessions.SessionsScreen
import app.nodes.ui.screens.settings.AboutSettingsScreen
import app.nodes.ui.screens.settings.AppearanceSettingsScreen
import app.nodes.ui.screens.settings.ContentSettingsScreen
import app.nodes.ui.screens.settings.DataSettingsScreen
import app.nodes.ui.screens.settings.LyricsSettingsScreen
import app.nodes.ui.screens.settings.PlaybackSettingsScreen
import app.nodes.ui.screens.settings.SettingsScreen
import app.nodes.ui.screens.songs.SongsScreen
import app.nodes.ui.screens.stats.StatsScreen
import app.nodes.ui.screens.suggestions.SuggestionsScreen
import app.nodes.ui.screens.trending.TrendingScreen
import app.nodes.ui.theme.SpartialBlack
import kotlinx.coroutines.delay

@Composable
fun SpartialApp(startOnboarding: Boolean = false) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    // Collapsed state: scrolling screens report through the bus.
    val collapsed by BarCollapseBus.collapsed.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val inOnboarding = route in Routes.ONBOARDING_ROUTES
    // The bar is persistent: every screen except the full-screen player, the
    // pre-auth login flow and the first-run wizard keeps it docked, nested
    // screens included.
    val showBar = route != null && route != Routes.PLAYER && route != Routes.LOGIN && !inOnboarding
    // The full player owns the screen; every other destination keeps playback
    // one tap away in the mini player. The Reel is the other exception: it is
    // the player, full-bleed, so a capsule over it would only cover the art.
    // The wizard has nothing playing yet, so it stays clear too.
    val showMiniPlayer =
        route != null && route != Routes.PLAYER && route != Routes.REEL && !inOnboarding
    val message = playerState.message

    LaunchedEffect(message) {
        if (message != null) {
            delay(5_000)
            playerViewModel.clearMessage()
        }
    }

    // Every "add to playlist" surface requests through the bus; the picker and
    // the notice it leaves behind live here so they outlive the requesting screen.
    val pickerViewModel: PlaylistPickerViewModel = hiltViewModel()
    val addRequest by AddToPlaylistBus.track.collectAsStateWithLifecycle()
    val playlistNotice by pickerViewModel.notice.collectAsStateWithLifecycle()
    val notice = playlistNotice
    if (notice != null) {
        LaunchedEffect(notice) {
            delay(5_000)
            pickerViewModel.clearNotice()
        }
    }

    // The crash handler cannot ask the nav controller anything, so the current
    // route is copied somewhere a dying process can still read it.
    LaunchedEffect(route) { AppSnapshot.route = route }

    // The glass bar and mini player blur whatever scrolls behind them; the
    // NavHost content is that backdrop.
    val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }

    Box(
        Modifier
            .fillMaxSize()
            .background(SpartialBlack),
    ) {
        NavHost(
            navController = navController,
            startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingWelcomeScreen(
                    onContinue = { navController.navigate(Routes.ONBOARDING_PROFILE) },
                    onOpenWebViewLogin = { navController.navigate(Routes.LOGIN) },
                )
            }

            composable(Routes.ONBOARDING_PROFILE) {
                OnboardingProfileScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(Routes.ONBOARDING_ALL_SET) },
                )
            }

            composable(Routes.ONBOARDING_ALL_SET) {
                OnboardingAllSetScreen(
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            // The wizard is finished: drop it (and its start
                            // destination) so back from Home leaves the app.
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.SETTINGS_PROFILE) {
                ProfileEditScreen(onDone = { navController.popBackStack() })
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    onOpenSuggestions = { navController.navigate(Routes.SUGGESTIONS) },
                    onOpenCollection = { collectionId ->
                        navController.navigate(Routes.playlist(collectionId))
                    },
                    onOpenSection = { section ->
                        section.tracks.firstOrNull()?.let { track ->
                            playerViewModel.play(track, section.tracks)
                            navController.navigate(Routes.PLAYER)
                        }
                    },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                )
            }

            composable(Routes.TRENDING) {
                TrendingScreen(
                    onOpenArtist = { name ->
                        navController.navigate(Routes.collection(Routes.COLLECTION_ARTIST, name))
                    },
                    onOpenAlbum = { name, albumId ->
                        navController.navigate(Routes.album(name, albumId))
                    },
                )
            }

            composable(Routes.SUGGESTIONS) {
                SuggestionsScreen(
                    onOpenArtist = { name ->
                        navController.navigate(Routes.collection(Routes.COLLECTION_ARTIST, name))
                    },
                )
            }

            composable(Routes.SESSIONS) { SessionsScreen() }

            composable(Routes.STATS) {
                StatsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenPlaylist = { playlistId ->
                        navController.navigate(Routes.playlist(playlistId))
                    },
                    onOpenSongs = { navController.navigate(Routes.SONGS) },
                    onOpenArtist = { name ->
                        navController.navigate(Routes.collection(Routes.COLLECTION_ARTIST, name))
                    },
                )
            }

            composable(Routes.SONGS) {
                SongsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name ->
                        navController.navigate(Routes.collection(Routes.COLLECTION_ARTIST, name))
                    },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenArtist = { name ->
                        navController.navigate(Routes.collection(Routes.COLLECTION_ARTIST, name))
                    },
                    onOpenAlbum = { name, albumId ->
                        navController.navigate(Routes.album(name, albumId))
                    },
                    onOpenTrending = { navController.navigate(Routes.TRENDING) },
                )
            }

            composable(Routes.REEL) {
                ReelScreen(
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(
                Routes.PLAYER,
                // The player is a sheet, not another page: it grows out of the
                // bottom edge when the mini player is tapped or dragged up, and
                // collapses back into it when it is popped - including when the
                // player finishes its own swipe-down gesture and calls onBack.
                //
                // A short travel (a fifth of the height) with a scale from the
                // bottom edge reads as the capsule expanding, where the old
                // full-height linear slide read as a page being pushed. Both
                // curves are springs, so the motion keeps its energy at the
                // end instead of stopping dead.
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                        initialOffset = { full -> full / 5 },
                    ) + scaleIn(
                        initialScale = 0.94f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                    ) + fadeIn(tween(140))
                },
                popExitTransition = {
                    // Closing shrinks toward the bottom edge with slightly more
                    // stiffness than the entry, and fades a little faster than
                    // it moves, so the mini player underneath is revealed
                    // rather than covered by a sliding panel.
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
                        targetOffset = { full -> full / 4 },
                    ) + scaleOut(
                        targetScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 1f),
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
                    ) + fadeOut(tween(170))
                },
            ) {
                NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { name ->
                        navController.navigate(Routes.collection(Routes.COLLECTION_ARTIST, name))
                    },
                    onOpenAlbum = { name, albumId ->
                        navController.navigate(Routes.album(name, albumId))
                    },
                    onOpenEqualizer = { navController.navigate(Routes.SETTINGS_EQUALIZER) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEqualizer = { navController.navigate(Routes.SETTINGS_EQUALIZER) },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    onOpenPlayback = { navController.navigate(Routes.SETTINGS_PLAYBACK) },
                    onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    onOpenContent = { navController.navigate(Routes.SETTINGS_CONTENT) },
                    onOpenLyrics = { navController.navigate(Routes.SETTINGS_LYRICS) },
                    onOpenData = { navController.navigate(Routes.SETTINGS_DATA) },
                    onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
                    onOpenProfile = { navController.navigate(Routes.SETTINGS_PROFILE) },
                )
            }

            composable(Routes.SETTINGS_PLAYBACK) {
                PlaybackSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_EQUALIZER) {
                EqualizerScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_APPEARANCE) {
                AppearanceSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_CONTENT) {
                ContentSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                )
            }

            composable(Routes.SETTINGS_LYRICS) {
                LyricsSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_DATA) {
                DataSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
            }

            composable(Routes.SETTINGS_ABOUT) {
                AboutSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LOGIN) {
                MusicLoginScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PLAYLIST_PATTERN) { entry ->
                val playlistId = entry.arguments?.getString(Routes.PLAYLIST_ARG)
                if (playlistId.isNullOrBlank()) {
                    // Nothing to open: say so instead of showing the wrong page.
                    EmptyState(
                        title = stringResource(R.string.playlist_missing_title),
                        description = stringResource(R.string.playlist_missing_body),
                        actionLabel = stringResource(R.string.cd_back),
                        onAction = { navController.popBackStack() },
                    )
                } else {
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                        onOpenArtist = { artist ->
                            navController.navigate(
                                Routes.collection(Routes.COLLECTION_ARTIST, artist),
                            )
                        },
                        onOpenAlbum = { album, albumId ->
                            navController.navigate(Routes.album(album, albumId))
                        },
                    )
                }
            }

            composable(Routes.COLLECTION_PATTERN) { entry ->
                val name = entry.arguments?.getString(Routes.COLLECTION_ARG_NAME)
                if (name.isNullOrBlank()) {
                    navController.popBackStack()
                } else {
                    CollectionScreen(
                        onBack = { navController.popBackStack() },
                        onOpenArtist = { artist ->
                            navController.navigate(
                                Routes.collection(Routes.COLLECTION_ARTIST, artist),
                            )
                        },
                        onOpenAlbum = { album, albumId ->
                            navController.navigate(Routes.album(album, albumId))
                        },
                    )
                }
            }
        }

        if (showMiniPlayer) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                if (showBar) {
                    FloatingTabBar(
                        currentRoute = route,
                        items = SpartialNavItems,
                        onSelect = { target ->
                            if (target != route) {
                                navController.navigate(target) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                // Already here: ask the screen to scroll back
                                // to the top rather than reloading it.
                                ScrollTopBus.request(target)
                            }
                        },
                        hazeState = hazeState,
                        collapsed = collapsed,
                        // The collapsed tab circle expands the bar again.
                        onExpand = { BarCollapseBus.set(false) },
                        // The mini capsule lives inside the bar in both
                        // states: stacked below the pill when expanded, in
                        // the row between the circles when collapsed.
                        miniPlayer = {
                            playerState.current?.let { track ->
                                MiniPlayer(
                                    track = track,
                                    isPlaying = playerState.isPlaying,
                                    isBuffering = playerState.isBuffering,
                                    onClick = { navController.navigate(Routes.PLAYER) },
                                    onToggle = playerViewModel::togglePlayPause,
                                    onPrevious = playerViewModel::previous,
                                    onNext = playerViewModel::next,
                                    progress = playerState.progress,
                                    hazeState = hazeState,
                                )
                            }
                        },
                    )
                } else {
                    // Full-screen destinations keep the mini player alone.
                    playerState.current?.let { track ->
                        MiniPlayer(
                            track = track,
                            isPlaying = playerState.isPlaying,
                            isBuffering = playerState.isBuffering,
                            onClick = { navController.navigate(Routes.PLAYER) },
                            onToggle = playerViewModel::togglePlayPause,
                            onPrevious = playerViewModel::previous,
                            onNext = playerViewModel::next,
                            progress = playerState.progress,
                            hazeState = hazeState,
                            onPlayNext = { playerViewModel.playNext(track) },
                            onAddToQueue = { playerViewModel.addToQueue(track) },
                            onAddToPlaylist = { AddToPlaylistBus.request(track) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }

        if (message != null) {
            MessageBanner(
                message = message,
                onDismiss = playerViewModel::clearMessage,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            )
        }

        if (notice != null) {
            MessageBanner(
                message = when (notice) {
                    is PlaylistNotice.Added ->
                        stringResource(R.string.playlist_added_to, notice.playlistName)

                    is PlaylistNotice.AlreadyExists ->
                        stringResource(R.string.playlist_already_in, notice.playlistName)

                    PlaylistNotice.Failed -> stringResource(R.string.playlist_add_failed)
                },
                onDismiss = pickerViewModel::clearNotice,
                actionLabel = if (notice.canUndo) stringResource(R.string.action_undo) else null,
                onAction = (notice as? PlaylistNotice.Added)?.let { added ->
                    { pickerViewModel.undoAdd(added) }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            )
        }

        addRequest?.let { requested ->
            PlaylistPickerDialog(
                track = requested,
                onDismiss = { AddToPlaylistBus.clear() },
                viewModel = pickerViewModel,
            )
        }
    }
}
