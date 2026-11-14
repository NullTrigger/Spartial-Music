/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import app.nodes.core.data.AppSettings
import app.nodes.core.crash.CrashReport
import app.nodes.core.crash.CrashStore
import app.nodes.core.data.SettingsRepository
import app.nodes.ui.navigation.SpartialApp
import app.nodes.ui.screens.crash.CrashActivity
import app.nodes.ui.screens.crash.CrashDialog
import app.nodes.ui.screens.onboarding.OnboardingScreen
import app.nodes.ui.theme.SpartialBlack
import app.nodes.ui.theme.SpartialTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    @Volatile
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !settingsLoaded }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // The previous run died: ask before Home appears. The report itself
            // is only read if the user decides to look at it.
            var pendingCrash by remember {
                mutableStateOf(CrashReport.pendingReport(this@MainActivity))
            }
            var appSettings by remember { mutableStateOf<AppSettings?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                settings.settings.collect { current ->
                    appSettings = current
                    settingsLoaded = true
                }
            }

            val current = appSettings
            SpartialTheme(dynamicColor = current?.dynamicColor == true) {
                when {
                    current == null -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(SpartialBlack),
                    )

                    !current.onboardingCompleted -> OnboardingScreen(
                        onAllSet = {
                            scope.launch { settings.setOnboardingCompleted(true) }
                        },
                    )

                    else -> SpartialApp()
                }
            }

            val crash = pendingCrash
            if (crash != null) {
                CrashDialog(
                    report = crash,
                    onView = {
                        val text = CrashStore.read(crash.file)
                        CrashReport.markSeen(this@MainActivity)
                        pendingCrash = null
                        val intent = Intent(this@MainActivity, CrashActivity::class.java)
                        if (text != null) intent.putExtra(CrashActivity.EXTRA_REPORT, text)
                        startActivity(intent)
                    },
                    onDismiss = {
                        CrashReport.markSeen(this@MainActivity)
                        pendingCrash = null
                    },
                )
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
