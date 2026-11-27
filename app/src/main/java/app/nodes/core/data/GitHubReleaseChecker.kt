/*
 * Copyright (C) 2026 The Spartial Music Authors.
 * Licensed under GPL-3.0-or-later. See LICENSE.
 */

package app.nodes.core.data

import app.nodes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseUpdate(
    val currentVersion: String,
    val latestVersion: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val updateAvailable: Boolean,
)

@Singleton
class GitHubReleaseChecker @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun check(): ReleaseUpdate = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Spartial-Music-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub returned HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val tag = json.optString("tag_name").ifBlank {
                throw IllegalStateException("The GitHub release has no version tag")
            }
            val version = tag.removePrefix("v").removePrefix("V")
            val releaseUrl = json.optString("html_url").ifBlank { RELEASE_PAGE_URL }
            val apkUrl = matchingApkUrl(json.optJSONArray("assets"), BuildConfig.FLAVOR, version)
            val currentVersion = BuildConfig.VERSION_NAME.substringBefore('-')
            ReleaseUpdate(
                currentVersion = currentVersion,
                latestVersion = version,
                releaseUrl = releaseUrl,
                apkUrl = apkUrl,
                updateAvailable = compareVersions(version, currentVersion) > 0,
            )
        }
    }

    private fun matchingApkUrl(assets: org.json.JSONArray?, flavor: String, version: String): String? {
        val expectedNames = when (flavor) {
            "gms" -> listOf("Spartial-Music-GMS.apk", "nodes-gms-$version.apk")
            "foss" -> listOf("nodes-foss-$version.apk", "Spartial-Music-FOSS.apk")
            else -> return null
        }
        if (assets == null) return null
        val byName = (0 until assets.length()).mapNotNull { index ->
            assets.optJSONObject(index)?.let { asset ->
                asset.optString("name") to asset.optString("browser_download_url")
            }
        }.toMap()
        return expectedNames.firstNotNullOfOrNull { name -> byName[name]?.takeIf(String::isNotBlank) }
    }

    companion object {
        private const val RELEASES_LATEST_URL =
            "https://api.github.com/repos/NullTrigger/Spartial-Music/releases/latest"
        private const val RELEASE_PAGE_URL =
            "https://github.com/NullTrigger/Spartial-Music/releases/latest"

        internal fun compareVersions(first: String, second: String): Int {
            fun parts(version: String) = version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }

            val firstParts = parts(first)
            val secondParts = parts(second)
            for (index in 0 until maxOf(firstParts.size, secondParts.size)) {
                val comparison = (firstParts.getOrNull(index) ?: 0)
                    .compareTo(secondParts.getOrNull(index) ?: 0)
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}
