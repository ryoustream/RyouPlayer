package com.ryoustream.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class RyouPlayerApp : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val playbackChannel = NotificationChannel(
            CHANNEL_PLAYBACK, "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows media playback controls"
            setShowBadge(false)
            setSound(null, null)
        }
        val scannerChannel = NotificationChannel(
            CHANNEL_SCANNER, "Media Scanner",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Media library scanning"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(playbackChannel, scannerChannel))
    }

    companion object {
        const val CHANNEL_PLAYBACK = "ryou_playback"
        const val CHANNEL_SCANNER = "ryou_scanner"
    }
}
