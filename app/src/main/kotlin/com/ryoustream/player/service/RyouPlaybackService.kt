package com.ryoustream.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ryoustream.player.R
import com.ryoustream.player.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import `is`.xyz.mpv.MPVLib

/**
 * RyouPlaybackService — lightweight foreground service for mpv background playback.
 *
 * Responsibilities:
 *  - Keeps the process alive while mpv is playing (foreground notification)
 *  - Manages Android AudioFocus so other apps duck/pause correctly
 *  - Exposes MediaSessionCompat for lock-screen controls & Bluetooth HFP
 *  - Handles headset unplug (AUDIO_BECOMING_NOISY) by pausing mpv
 *
 * NO ExoPlayer / DefaultTrackSelector dependency here.
 * mpv is the sole playback engine; this service wraps it for OS integration.
 */
@AndroidEntryPoint
class RyouPlaybackService : Service() {

    // ── Binder for optional local binding ────────────────────────────────────
    inner class LocalBinder : Binder() {
        fun getService(): RyouPlaybackService = this@RyouPlaybackService
    }

    private val binder = LocalBinder()

    // ── System services ───────────────────────────────────────────────────────
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSessionCompat

    // ── Audio focus ───────────────────────────────────────────────────────────
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // ── Noisy receiver (headset unplug) ───────────────────────────────────────
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (MPVLib.isAvailable) MPVLib.setPropertyBoolean("pause", true)
                updateNotification(isPlaying = false)
            }
        }
    }
    private var noisyRegistered = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        audioManager   = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        initMediaSession()
        startForeground(NOTIFICATION_ID, buildNotification(isPlaying = false))
        registerNoisyReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY  -> { requestAudioFocus(); mpvPause(false) }
            ACTION_PAUSE -> { mpvPause(true) }
            ACTION_STOP  -> { mpvPause(true); abandonAudioFocus(); stopSelf() }
            ACTION_UPDATE_STATE -> {
                val playing = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                updateNotification(playing)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        unregisterNoisyReceiver()
        abandonAudioFocus()
        mediaSession.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ── MediaSession ──────────────────────────────────────────────────────────

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { requestAudioFocus(); mpvPause(false) }
                override fun onPause() { mpvPause(true) }
                override fun onStop()  { mpvPause(true); abandonAudioFocus(); stopSelf() }
                override fun onSeekTo(pos: Long) {
                    if (MPVLib.isAvailable) MPVLib.command(arrayOf("seek", (pos / 1000.0).toString(), "absolute"))
                }
                override fun onSkipToNext()     { if (MPVLib.isAvailable) MPVLib.command(arrayOf("playlist-next")) }
                override fun onSkipToPrevious() { if (MPVLib.isAvailable) MPVLib.command(arrayOf("playlist-prev")) }
            })
            isActive = true
        }
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ryou Player Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val launchIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause",
                pendingIntent(ACTION_PAUSE, 1),
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play",
                pendingIntent(ACTION_PLAY, 2),
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Ryou Player")
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setContentIntent(launchIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(isPlaying)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous, "Previous",
                    pendingIntent(ACTION_PREV, 3),
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next, "Next",
                    pendingIntent(ACTION_NEXT, 4),
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel, "Stop",
                    pendingIntent(ACTION_STOP, 5),
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(pendingIntent(ACTION_STOP, 5))
            )
            .build()
    }

    fun updateNotification(isPlaying: Boolean) {
        updatePlaybackState(isPlaying)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun pendingIntent(action: String, reqCode: Int): PendingIntent =
        PendingIntent.getService(
            this, reqCode,
            Intent(this, RyouPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ── Audio Focus ───────────────────────────────────────────────────────────

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setOnAudioFocusChangeListener(::onAudioFocusChange)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                ::onAudioFocusChange,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(::onAudioFocusChange)
        }
        hasAudioFocus = false
    }

    private fun onAudioFocusChange(focusChange: Int) {
        if (!MPVLib.isAvailable) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN            -> { MPVLib.setPropertyBoolean("pause", false); MPVLib.setPropertyInt("volume", 100) }
            AudioManager.AUDIOFOCUS_LOSS            -> { MPVLib.setPropertyBoolean("pause", true); abandonAudioFocus() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT  -> MPVLib.setPropertyBoolean("pause", true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> MPVLib.setPropertyInt("volume", 40)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mpvPause(paused: Boolean) {
        if (MPVLib.isAvailable) MPVLib.setPropertyBoolean("pause", paused)
        updateNotification(isPlaying = !paused)
    }

    private fun registerNoisyReceiver() {
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(
                this,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (noisyRegistered) {
            try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
            noisyRegistered = false
        }
    }

    companion object {
        private const val TAG            = "RyouPlaybackService"
        private const val CHANNEL_ID     = "ryou_playback"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY         = "com.ryoustream.player.PLAY"
        const val ACTION_PAUSE        = "com.ryoustream.player.PAUSE"
        const val ACTION_STOP         = "com.ryoustream.player.STOP"
        const val ACTION_NEXT         = "com.ryoustream.player.NEXT"
        const val ACTION_PREV         = "com.ryoustream.player.PREV"
        const val ACTION_UPDATE_STATE = "com.ryoustream.player.UPDATE_STATE"
        const val EXTRA_IS_PLAYING    = "is_playing"

        fun start(context: Context) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, RyouPlaybackService::class.java),
            )

        fun notifyPlaying(context: Context, isPlaying: Boolean) =
            ContextCompat.startForegroundService(
                context,
                Intent(context, RyouPlaybackService::class.java).apply {
                    action = ACTION_UPDATE_STATE
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                },
            )
    }
}
