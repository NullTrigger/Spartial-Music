/*
 * Copyright (C) 2026 The Nodes Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.nodes.core.data.AppSettings
import app.nodes.core.crash.CrashReport
import app.nodes.core.data.SettingsRepository
import app.nodes.ui.navigation.NodesApp
import app.nodes.ui.screens.crash.CrashActivity
import app.nodes.ui.screens.onboarding.OnboardingScreen
import app.nodes.ui.theme.NodesBlack
import app.nodes.ui.theme.NodesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    @Volatile
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !settingsLoaded }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The previous run died: show what happened before Home appears. The
        // page reads the saved report itself and clears the pending marker.
        if (CrashReport.hasPending(this)) {
            startActivity(Intent(this, CrashActivity::class.java))
        }

        setContent {
            var appSettings by remember { mutableStateOf<AppSettings?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                settings.settings.collect { current ->
                    appSettings = current
                    settingsLoaded = true
                }
            }

            val current = appSettings
            NodesTheme(dynamicColor = current?.dynamicColor == true) {
                when {
                    current == null -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(NodesBlack),
                    )

                    !current.onboardingCompleted -> OnboardingScreen(
                        onAllSet = {
                            scope.launch { settings.setOnboardingCompleted(true) }
                        },
                    )

                    else -> NodesApp()
                }
            }

            NotificationPermissionRequest()
        }
    }
}

/**
 * Media notifications need POST_NOTIFICATIONS on Android 13+. Asked once, and
 * declining only costs the notification, not playback.
 */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
