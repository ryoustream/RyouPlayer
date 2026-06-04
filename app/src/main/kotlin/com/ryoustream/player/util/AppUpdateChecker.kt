package com.ryoustream.player.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * AppUpdateChecker — checks GitHub Releases for a newer version of RyouPlayer.
 *
 * Hits the public GitHub API (no auth required for public repos):
 *   GET https://api.github.com/repos/ryoustream/RyouPlayer/releases/latest
 *
 * Compares the returned `tag_name` (e.g. "v1.3.002") against the running
 * [currentVersion] (e.g. "1.3.001") using numeric segment comparison.
 *
 * Auto-install flow:
 *   1. checkForUpdate()  → UpdateInfo (with apkDownloadUrl if available)
 *   2. downloadApk()     → downloads APK to Downloads dir via DownloadManager
 *   3. installApk()      → triggers system installer via FileProvider intent
 */
data class UpdateInfo(
    /** Version string of the latest GitHub release, without leading "v". */
    val latestVersion: String,
    /** Currently installed version. */
    val currentVersion: String,
    /** GitHub HTML release page. */
    val releasePageUrl: String,
    /** Release notes / changelog body from GitHub (may be empty). */
    val releaseNotes: String,
    /** True when [latestVersion] is strictly newer than [currentVersion]. */
    val hasUpdate: Boolean,
    /** Direct APK download URL from GitHub release assets (null if no APK asset found). */
    val apkDownloadUrl: String? = null,
)

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val RELEASES_API =
        "https://api.github.com/repos/ryoustream/RyouPlayer/releases/latest"
    private const val TIMEOUT_MS = 10_000
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /**
     * Fetch the latest GitHub release and compare against [currentVersion].
     * Must be called from a coroutine; performs network I/O on [Dispatchers.IO].
     */
    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = fetchJson(RELEASES_API)
                val obj  = JSONObject(json)

                val tagName = obj.getString("tag_name").trimStart('v', 'V')
                val htmlUrl = obj.optString("html_url", "https://github.com/ryoustream/RyouPlayer/releases")
                val body    = obj.optString("body", "")

                // Parse assets array for APK download URL
                val apkUrl = runCatching {
                    val assets = obj.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name  = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                return@runCatching asset.optString("browser_download_url")
                                    .takeIf { it.isNotBlank() }
                            }
                        }
                    }
                    null
                }.getOrNull()

                UpdateInfo(
                    latestVersion  = tagName,
                    currentVersion = currentVersion,
                    releasePageUrl = htmlUrl,
                    releaseNotes   = body,
                    hasUpdate      = isNewer(tagName, currentVersion),
                    apkDownloadUrl = apkUrl,
                )
            }.onFailure { e ->
                Log.w(TAG, "Update check failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

    /**
     * Download APK using DownloadManager.
     * Returns the download ID, or -1 on failure.
     * The file is saved to the public Downloads directory.
     */
    fun downloadApk(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (Int) -> Unit = {},
    ): Long {
        return runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "RyouPlayer-${versionName}.apk"

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("RyouPlayer $versionName")
                setDescription("Mengunduh pembaruan…")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            dm.enqueue(request)
        }.onFailure { e ->
            Log.e(TAG, "Download failed: ${e.message}")
        }.getOrDefault(-1L)
    }

    /**
     * Wait for DownloadManager to complete the given download [downloadId].
     * Registers a one-shot broadcast receiver for ACTION_DOWNLOAD_COMPLETE.
     * Returns the local file Uri, or null on failure.
     */
    suspend fun waitForDownload(context: Context, downloadId: Long): Uri? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                        if (id != downloadId) return

                        ctx?.unregisterReceiver(this)

                        val dm = ctx?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = dm?.query(query)
                        val uri = runCatching {
                            if (cursor != null && cursor.moveToFirst()) {
                                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    val uriStr = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                    Uri.parse(uriStr)
                                } else null
                            } else null
                        }.getOrNull()
                        cursor?.close()

                        if (cont.isActive) cont.resume(uri)
                    }
                }

                val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(receiver, filter)
                }

                cont.invokeOnCancellation {
                    runCatching { context.unregisterReceiver(receiver) }
                }
            }
        }

    /**
     * Install an APK from the public Downloads directory by filename.
     * Requires REQUEST_INSTALL_PACKAGES permission + FileProvider.
     */
    fun installApk(context: Context, versionName: String): Boolean {
        return runCatching {
            val fileName = "RyouPlayer-${versionName}.apk"
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (!apkFile.exists()) return false

            val authority = "${context.packageName}$AUTHORITY_SUFFIX"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        }.onFailure { e ->
            Log.e(TAG, "Install failed: ${e.message}")
        }.getOrDefault(false)
    }

    /**
     * Check if DownloadManager download with [downloadId] is still running.
     * Returns progress 0-100, or -1 if unknown/failed.
     */
    fun getDownloadProgress(context: Context, downloadId: Long): Pair<Int, DownloadStatus> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query  = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return -1 to DownloadStatus.FAILED

        return try {
            if (!cursor.moveToFirst()) return -1 to DownloadStatus.FAILED
            val status    = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total     = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))

            val progress = if (total > 0) ((downloaded * 100) / total).toInt() else -1

            val dlStatus = when (status) {
                DownloadManager.STATUS_RUNNING   -> DownloadStatus.RUNNING
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.DONE
                DownloadManager.STATUS_FAILED    -> DownloadStatus.FAILED
                DownloadManager.STATUS_PAUSED    -> DownloadStatus.PAUSED
                else                             -> DownloadStatus.PENDING
            }
            progress to dlStatus
        } finally {
            cursor.close()
        }
    }

    enum class DownloadStatus { PENDING, RUNNING, PAUSED, DONE, FAILED }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fetchJson(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod  = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
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
     * Numeric segment comparison: "1.3.9" > "1.3.8" → true.
     * Zero-padded strings handled correctly: "1.3.009" > "1.3.008".
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
        return false
    }
}
