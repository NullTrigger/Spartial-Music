/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.navigation

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nodes.core.crash.AppSnapshot
import app.nodes.ui.components.MessageBanner
import app.nodes.ui.components.MiniPlayer
import app.nodes.ui.components.NodesBottomBar
import app.nodes.ui.components.NodesNavItems
import app.nodes.ui.screens.diagnostics.DiagnosticsScreen
import app.nodes.ui.screens.home.HomeScreen
import app.nodes.ui.screens.library.LibraryScreen
import app.nodes.ui.screens.login.MusicLoginScreen
import app.nodes.ui.screens.onboarding.OnboardingScreen
import app.nodes.ui.screens.player.NowPlayingScreen
import app.nodes.ui.screens.player.PlayerViewModel
import app.nodes.ui.screens.playlist.PlaylistDetailScreen
import app.nodes.ui.screens.search.SearchScreen
import app.nodes.ui.screens.sessions.SessionsScreen
import app.nodes.ui.screens.settings.SettingsScreen
import app.nodes.ui.screens.trending.TrendingScreen
import app.nodes.ui.theme.NodesBlack
import kotlinx.coroutines.delay

@Composable
fun NodesApp() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBar = route in setOf(Routes.HOME, Routes.TRENDING, Routes.SESSIONS, Routes.LIBRARY)
    val message = playerState.message

    LaunchedEffect(message) {
        if (message != null) {
            delay(5_000)
            playerViewModel.clearMessage()
        }
    }

    // The crash handler cannot ask the nav controller anything, so the current
    // route is copied somewhere a dying process can still read it.
    LaunchedEffect(route) { AppSnapshot.route = route }

    Box(
        Modifier
            .fillMaxSize()
            .background(NodesBlack),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    onOpenCollection = { collectionId ->
                        navController.navigate(Routes.playlist(collectionId))
                    },
                    onOpenSection = { section ->
                        section.tracks.firstOrNull()?.let { track ->
                            playerViewModel.play(track, section.tracks)
                            navController.navigate(Routes.PLAYER)
                        }
                    },
                )
            }

            composable(Routes.TRENDING) { TrendingScreen() }

            composable(Routes.SESSIONS) { SessionsScreen() }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenPlaylist = { playlistId -> navController.navigate(Routes.playlist(playlistId)) },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PLAYER) {
                NowPlayingScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
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
                    OnboardingScreen(onAllSet = { navController.popBackStack() })
                } else {
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        if (showBar) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                playerState.current?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        onClick = { navController.navigate(Routes.PLAYER) },
                        onToggle = playerViewModel::togglePlayPause,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                NodesBottomBar(
                    currentRoute = route,
                    items = NodesNavItems,
                    onSelect = { target ->
                        if (target != route) {
                            navController.navigate(target) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                )
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
    }
}
