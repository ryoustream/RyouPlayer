package com.ryoustream.player.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ryoustream.player.R
import com.ryoustream.player.RyouPlayerApp
import com.ryoustream.player.domain.usecase.RescanMediaUseCase
import com.ryoustream.player.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MediaScannerService - foreground service that scans for media files
 */
@AndroidEntryPoint
class MediaScannerService : LifecycleService() {

    @Inject
    lateinit var rescanMediaUseCase: RescanMediaUseCase

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleScope.launch {
            rescanMediaUseCase()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, RyouPlayerApp.CHANNEL_SCANNER)
            .setContentTitle("Ryou Player")
            .setContentText("Scanning media library…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, MediaScannerService::class.java)
            context.startForegroundService(intent)
        }
    }
}
