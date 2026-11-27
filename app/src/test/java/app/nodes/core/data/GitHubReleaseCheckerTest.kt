/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun comparesNumericVersionComponents() {
        assertEquals(1, GitHubReleaseChecker.compareVersions("1.10.0", "1.9.9"))
        assertEquals(-1, GitHubReleaseChecker.compareVersions("1.2.0", "1.2.1"))
        assertEquals(0, GitHubReleaseChecker.compareVersions("v2.0", "2.0.0"))
    }
}
