/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the wizard may appear. The two inputs are what the activity can read
 * without guessing: the stored completion flag, and whether the device already
 * carries a library database.
 */
class OnboardingGateTest {

    @Test
    fun `onboarding is for a first launch only`() {
        // A brand new install: no completion flag, no library data.
        assertTrue(OnboardingRules.showOnboarding(onboardingCompleted = false, existingInstall = false))
        // The wizard has been finished: never show it again.
        assertFalse(OnboardingRules.showOnboarding(onboardingCompleted = true, existingInstall = false))
        // An update from an earlier build: library data is already on disk, so
        // the returning listener goes straight to Home.
        assertFalse(OnboardingRules.showOnboarding(onboardingCompleted = false, existingInstall = true))
        // Flag set and data present: still no wizard.
        assertFalse(OnboardingRules.showOnboarding(onboardingCompleted = true, existingInstall = true))
    }
}
