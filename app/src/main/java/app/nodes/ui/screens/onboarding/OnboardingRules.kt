/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.ui.screens.onboarding

import android.content.Context
import app.nodes.core.data.db.SpartialDatabase

/**
 * The decisions the onboarding flow can make without Android, Compose or a
 * network: the username rules, the avatar wheel, and whether onboarding shows
 * at all. The object itself is framework-free so the rules are unit-testable;
 * [hasExistingLibraryData] is the one probe that needs a [Context].
 */
object OnboardingRules {

    /** Avatars are the bundled `avatar_1` .. [MAX_AVATAR] set. */
    const val MIN_AVATAR = 1
    const val MAX_AVATAR = 9
    const val DEFAULT_AVATAR = MIN_AVATAR

    /** Used when the username field is left empty. */
    const val DEFAULT_USERNAME = "Listener"

    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 20

    /** Letters, digits, space, dot, underscore and hyphen. */
    private val NAME_PATTERN =
        "^[\\p{L}\\p{N} ._-]{${MIN_NAME_LENGTH},${MAX_NAME_LENGTH}}$".toRegex()

    /** Trimmed, with [DEFAULT_USERNAME] standing in for an empty field. */
    fun normalizedUsername(raw: String): String =
        raw.trim().ifEmpty { DEFAULT_USERNAME }

    fun isValidUsername(raw: String): Boolean =
        NAME_PATTERN.matches(normalizedUsername(raw))

    /** The value to store, or null when the input is not acceptable. */
    fun resolvedUsername(raw: String): String? =
        normalizedUsername(raw).takeIf { NAME_PATTERN.matches(it) }

    fun clampAvatar(id: Int): Int = id.coerceIn(MIN_AVATAR, MAX_AVATAR)

    /** The avatar wheel wraps around, in both directions. */
    fun wrapAvatar(id: Int): Int {
        val span = MAX_AVATAR - MIN_AVATAR + 1
        return (id - MIN_AVATAR).mod(span) + MIN_AVATAR
    }

    fun nextAvatar(current: Int): Int = wrapAvatar(clampAvatar(current) + 1)

    fun previousAvatar(current: Int): Int = wrapAvatar(clampAvatar(current) - 1)

    /**
     * Onboarding only appears on a genuine first launch. An install that
     * already wrote settings or library data is an update, and an update must
     * never put a returning listener back through the flow.
     */
    fun showOnboarding(onboardingCompleted: Boolean, existingInstall: Boolean): Boolean =
        !onboardingCompleted && !existingInstall
}

/**
 * Whether this device already carries an install of the app, which is what
 * keeps an update from dropping a returning listener back into onboarding.
 *
 * The Room library database is the signal, not the settings file: reading the
 * settings DataStore writes its migration marker during application start-up,
 * so a brand new install has a settings file a few milliseconds after launch
 * and could never be told apart from an old one. Nothing queries Room before
 * the flow is decided, so an absent `nodes.db` really does mean first launch.
 */
fun hasExistingLibraryData(context: Context): Boolean {
    val database = context.getDatabasePath(SpartialDatabase.NAME)
    val parent = database.parentFile
    return database.exists() || parent?.listFiles()?.any { it.name.startsWith(database.name) } == true
}
