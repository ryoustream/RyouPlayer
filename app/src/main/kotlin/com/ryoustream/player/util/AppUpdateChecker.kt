package com.ryoustream.player.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppUpdateChecker — checks GitHub Releases for a newer version of RyouPlayer.
 *
 * Hits the public GitHub API (no auth required for public repos):
 *   GET https://api.github.com/repos/ryoustream/RyouPlayer/releases/latest
 *
 * Compares the returned `tag_name` (e.g. "v1.2.009") against the running
 * [currentVersion] (e.g. "1.2.008") using a numeric segment comparison so
 * "1.2.009" > "1.2.008" even though "009" > "008" lexicographically is wrong.
 *
 * Usage (from a ViewModel):
 *   val result = AppUpdateChecker.checkForUpdate(BuildConfig.VERSION_FULL)
 *   result.onSuccess { info -> if (info.hasUpdate) { ... } }
 */
data class UpdateInfo(
    /** Version string of the latest GitHub release, without leading "v" (e.g. "1.2.009"). */
    val latestVersion: String,
    /** Currently installed version (e.g. "1.2.008"). */
    val currentVersion: String,
    /** GitHub HTML release page — open in browser for download. */
    val releasePageUrl: String,
    /** Release notes / changelog body from GitHub (may be empty). */
    val releaseNotes: String,
    /** True when [latestVersion] is strictly newer than [currentVersion]. */
    val hasUpdate: Boolean,
)

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val RELEASES_API =
        "https://api.github.com/repos/ryoustream/RyouPlayer/releases/latest"
    private const val TIMEOUT_MS = 8_000

    /**
     * Fetch the latest GitHub release and compare against [currentVersion].
     * Must be called from a coroutine; performs network I/O on [Dispatchers.IO].
     *
     * @return [Result.success] with [UpdateInfo], or [Result.failure] on network/parse error.
     */
    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = fetchJson(RELEASES_API)
                val obj = JSONObject(json)

                // tag_name may be "v1.2.009" or "1.2.009" — strip leading 'v'
                val tagName = obj.getString("tag_name").trimStart('v', 'V')
                val htmlUrl = obj.optString("html_url", "https://github.com/ryoustream/RyouPlayer/releases")
                val body    = obj.optString("body", "")

                UpdateInfo(
                    latestVersion   = tagName,
                    currentVersion  = currentVersion,
                    releasePageUrl  = htmlUrl,
                    releaseNotes    = body,
                    hasUpdate       = isNewer(tagName, currentVersion),
                )
            }.onFailure { e ->
                Log.w(TAG, "Update check failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fetch URL contents as a String, with timeouts and Accept header for GitHub API. */
    private fun fetchJson(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod         = "GET"
            connectTimeout        = TIMEOUT_MS
            readTimeout           = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Numeric segment comparison: "1.2.9" > "1.2.8" → true.
     * Handles zero-padded strings correctly: "1.2.009" > "1.2.008".
     * Returns true only when [latest] is STRICTLY newer than [current].
     */
    private fun isNewer(latest: String, current: String): Boolean {
        fun segments(v: String): List<Int> =
            v.split(".").map { it.toIntOrNull() ?: 0 }

        val lSeg = segments(latest)
        val cSeg = segments(current)
        val len  = maxOf(lSeg.size, cSeg.size)

        for (i in 0 until len) {
            val l = lSeg.getOrElse(i) { 0 }
            val c = cSeg.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false  // equal
    }
}
