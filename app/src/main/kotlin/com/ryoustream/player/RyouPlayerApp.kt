package com.ryoustream.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * RyouPlayer Application class.
 *
 * Entry point for dependency injection (Hilt) and global app initialization.
 * Handles notification channels, app-level coroutine scope, and early init tasks.
 */
@HiltAndroidApp
@UnstableApi
class RyouPlayerApp : Application() {

    /** App-level coroutine scope, tied to the application lifecycle */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Playback channel
        val playbackChannel = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows media playback controls"
            setShowBadge(false)
            setSound(null, null)
        }

        // Download channel
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOAD,
            "Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows download progress"
            setShowBadge(true)
        }

        // Scanner channel
        val scannerChannel = NotificationChannel(
            CHANNEL_SCANNER,
            "Media Scanner",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Media library scanning"
            setShowBadge(false)
            setSound(null, null)
        }

        manager.createNotificationChannels(
            listOf(playbackChannel, downloadChannel, scannerChannel)
        )
    }

    companion object {
        const val CHANNEL_PLAYBACK = "ryou_playback"
        const val CHANNEL_DOWNLOAD = "ryou_download"
        const val CHANNEL_SCANNER = "ryou_scanner"
    }
}
