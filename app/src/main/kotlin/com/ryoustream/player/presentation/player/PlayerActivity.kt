package com.ryoustream.player.presentation.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import com.ryoustream.player.presentation.theme.RyouPlayerTheme

/**
 * PlayerActivity
 *
 * Dedicated Activity for video playback.
 * Always runs in landscape mode.
 * Supports Picture-in-Picture.
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private var mediaUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Parse URI from intent
        mediaUri = when {
            intent?.action == Intent.ACTION_VIEW -> intent.data
            intent?.hasExtra(EXTRA_MEDIA_URI) == true -> Uri.parse(intent.getStringExtra(EXTRA_MEDIA_URI))
            else -> null
        }

        val uri = mediaUri ?: run { finish(); return }

        setContent {
            RyouPlayerTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    PlayerScreen(
                        mediaUri = uri,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP when user presses home
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureModeIfPossible()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPictureInPictureModeIfPossible() {
        if (!isInPictureInPictureMode) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // UI adjusts automatically based on PiP mode via Compose state
    }

    companion object {
        const val EXTRA_MEDIA_URI = "extra_media_uri"

        fun createIntent(activity: ComponentActivity, uri: Uri): Intent =
            Intent(activity, PlayerActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_URI, uri.toString())
            }
    }
}
