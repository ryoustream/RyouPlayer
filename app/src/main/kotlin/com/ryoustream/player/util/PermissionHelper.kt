package com.ryoustream.player.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Helper for checking runtime permissions.
 *
 * Storage strategy:
 *   Android 11+ (API 30+) — MANAGE_EXTERNAL_STORAGE ("All files access").
 *     Grants full read/write access including hidden files and folders
 *     that contain a .nomedia marker (which the MediaStore index skips).
 *     User must approve via the system Settings page.
 *   Android 10 (API 29)   — READ_EXTERNAL_STORAGE (still useful for legacy paths).
 *   Android 9 and below   — READ_EXTERNAL_STORAGE.
 *   Android 13+ (API 33+) — Granular READ_MEDIA_VIDEO / READ_MEDIA_AUDIO are
 *     still kept as supplements in case the user later revokes All-Files access.
 */
object PermissionHelper {

    // ── Storage ──────────────────────────────────────────────────────────────

    /**
     * True if the app has "All files access" (MANAGE_EXTERNAL_STORAGE).
     * Only meaningful on Android 11+ (API 30+); always false on older builds.
     */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }

    /**
     * True if the app has sufficient storage permission to browse media.
     *
     * Priority:
     *   1. MANAGE_EXTERNAL_STORAGE on API 30+  (ideal — covers hidden files)
     *   2. READ_MEDIA_VIDEO on API 33+          (fallback if user denied #1)
     *   3. READ_EXTERNAL_STORAGE on API ≤ 32   (legacy)
     *
     * Note: READ_MEDIA_AUDIO is intentionally excluded — this is a video player
     * and MANAGE_EXTERNAL_STORAGE already grants full storage access.
     */
    fun hasStoragePermission(context: Context): Boolean {
        // Best: all-files access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()) {
            return true
        }
        // Fallback: granular video permission only (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        }
        // Legacy: READ_EXTERNAL_STORAGE (API ≤ 32)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Granular media permissions used as a fallback when All-Files is denied.
     * Only READ_MEDIA_VIDEO — this is a video player; audio permission removed.
     */
    fun granularMediaPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS
        else null

    fun hasNotificationPermission(context: Context): Boolean {
        val perm = notificationPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}
