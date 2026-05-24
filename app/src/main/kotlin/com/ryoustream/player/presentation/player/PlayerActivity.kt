package com.ryoustream.player.presentation.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.presentation.theme.RyouPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * PlayerActivity — full-screen mpv-based player.
 *
 * Display-cutout behaviour is applied SYNCHRONOUSLY before setContent so
 * the setting from preferences actually takes effect. Using a coroutine here
 * caused a race condition where setContent ran before the window attribute
 * was applied, so the theme's default (NEVER) was used instead.
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 1. Display cutout — read user preference ─────────────────────────────
        //    SHORT_EDGES = extend video behind notch (default on).
        //    NEVER = keep content away from notch (safe for hole-punch devices).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lifecycleScope.launch {
                settingsRepository.ignoreNotch.collect { ignore ->
                    applyNotchMode(ignore)
                }
            }
            applyNotchMode(true) // default to SHORT_EDGES before pref loads
        }

        // ── 2. Edge-to-edge (status + nav bars transparent) ─────────────────────
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ── 4. Parse incoming URI ────────────────────────────────────────────────
        val mediaUri: Uri = when {
            intent?.action == Intent.ACTION_VIEW -> intent.data
            intent?.hasExtra(EXTRA_MEDIA_URI) == true ->
                Uri.parse(intent.getStringExtra(EXTRA_MEDIA_URI))
            else -> null
        } ?: run { finish(); return }

        // ── 5. Compose UI ────────────────────────────────────────────────────────
        setContent {
            RyouPlayerTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = Color.Black,
                ) {
                    PlayerScreen(
                        mediaUri = mediaUri,
                        onBack   = { finish() },
                    )
                }
            }
        }
    }

    /**
     * Apply display-cutout mode to the window.
     *
     * [ignoreNotch] = true  → SHORT_EDGES  → video extends into the notch area.
     * [ignoreNotch] = false → NEVER        → content is letterboxed away from the notch.
     *
     * This must be called BEFORE setContent and NOT inside a coroutine/LaunchedEffect,
     * otherwise the theme default wins and the setting has no effect.
     */
    private fun applyNotchMode(ignoreNotch: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (ignoreNotch) {
                    // Extend video surface into the camera-notch area
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    // Keep content clear of the notch — truly respected value
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }
        }
    }

    // ── PiP ──────────────────────────────────────────────────────────────────

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipIfPossible()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipIfPossible() {
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
    }

    companion object {
        const val EXTRA_MEDIA_URI = "extra_media_uri"

        fun createIntent(context: Context, uri: Uri): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_URI, uri.toString())
            }
    }
}
