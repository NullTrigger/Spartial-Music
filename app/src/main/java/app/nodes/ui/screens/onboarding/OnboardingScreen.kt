/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.nodes.R

/**
 * Community links shared by the onboarding screens and the Settings profile
 * editor.
 *
 * The old single-screen onboarding is replaced by the three-screen flow in
 * [OnboardingWelcomeScreen], [OnboardingProfileScreen] and
 * [OnboardingAllSetScreen]; what is left here is the one thing that flow still
 * needs, which is opening a URL in the user's own browser.
 *
 * Only `ACTION_VIEW` is used: no WebView, no custom tab, no tracking redirect.
 * The app never learns whether the link was followed.
 */
fun openCommunity(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, R.string.onboarding_links_missing, Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, R.string.onboarding_links_missing, Toast.LENGTH_SHORT).show()
        } else {
            throw error
        }
    }
}
