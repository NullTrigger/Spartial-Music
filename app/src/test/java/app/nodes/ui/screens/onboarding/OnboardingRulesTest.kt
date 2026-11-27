/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wizard's two pure rules: what a username may be, and how the wheel turns. */
class OnboardingRulesTest {

    @Test
    fun `username trims, defaults and rejects what it must`() {
        // Empty input becomes the default listener.
        assertEquals("Listener", OnboardingRules.resolvedUsername(""))
        assertEquals("Listener", OnboardingRules.resolvedUsername("   "))
        // Surrounding spaces are dropped; the middle of a name survives.
        assertEquals("Bob", OnboardingRules.resolvedUsername("  Bob  "))
        assertEquals("Bob Smith", OnboardingRules.resolvedUsername("Bob Smith"))
        // Allowed punctuation and both length bounds.
        assertTrue(OnboardingRules.isValidUsername("bo_b.x-1"))
        assertTrue(OnboardingRules.isValidUsername("ab"))
        assertTrue(OnboardingRules.isValidUsername("a".repeat(20)))
        // One character is too short, twenty-one is too long.
        assertFalse(OnboardingRules.isValidUsername("a"))
        assertFalse(OnboardingRules.isValidUsername("a".repeat(21)))
        // Nothing else may through: symbols, emoji, newlines.
        assertFalse(OnboardingRules.isValidUsername("bob<script>"))
        assertFalse(OnboardingRules.isValidUsername("bob*"))
        assertFalse(OnboardingRules.isValidUsername("bob\nsmith"))
        assertNull(OnboardingRules.resolvedUsername("bob*"))
    }

    @Test
    fun `avatar wheel wraps in both directions and clamps`() {
        assertEquals(2, OnboardingRules.nextAvatar(1))
        assertEquals(1, OnboardingRules.nextAvatar(OnboardingRules.MAX_AVATAR))
        assertEquals(OnboardingRules.MAX_AVATAR, OnboardingRules.previousAvatar(1))
        assertEquals(8, OnboardingRules.previousAvatar(9))
        assertEquals(5, OnboardingRules.previousAvatar(6))
        // Twelve steps forward from the first avatar is a full turn.
        var id = OnboardingRules.MIN_AVATAR
        repeat(9) { id = OnboardingRules.nextAvatar(id) }
        assertEquals(OnboardingRules.MIN_AVATAR, id)
        // A value from storage that is out of range is pulled back in.
        assertEquals(OnboardingRules.MIN_AVATAR, OnboardingRules.clampAvatar(0))
        assertEquals(OnboardingRules.MAX_AVATAR, OnboardingRules.clampAvatar(42))
    }
}
