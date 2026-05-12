package com.ryoustream.player.presentation.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.ryoustream.player.domain.model.AssCue
import com.ryoustream.player.domain.model.SubtitleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AssSubtitleView
 *
 * Drop-in overlay for ASS/SSA subtitle rendering.
 *
 * ## Strategy
 * 1. **JNI path** — if [AssJniRenderer] is available (libass.so present in
 *    jniLibs/), subtitle frames are rendered natively to an ARGB Bitmap and
 *    composited here via `Image`. Supports all ASS features: \clip, \move,
 *    karaoke effects, per-character transforms.
 * 2. **Kotlin fallback** — if libass is absent, delegates to the pure-Kotlin
 *    [AssSubtitleRenderer] composable (outline + shadow via Canvas draw calls).
 *
 * Callers decide which path by checking [AssSubtitleView.jniAvailable].
 *
 * @param rawBytes   Raw bytes of the .ass/.ssa file — used only when JNI is active.
 * @param cues       Pre-parsed cues (from AssParser) — used only for Kotlin path.
 * @param positionMs Current playback position in milliseconds.
 * @param style      Visual style for the Kotlin fallback path.
 */
@Composable
fun AssSubtitleView(
    rawBytes:    ByteArray?,
    cues:        List<AssCue>,
    positionMs:  Long,
    style:       SubtitleStyle = SubtitleStyle(),
    modifier:    Modifier      = Modifier,
) {
    val useJni = remember { AssJniRenderer().also { it.destroy() }.isAvailable && rawBytes != null }

    if (useJni && rawBytes != null) {
        JniSubtitleOverlay(
            rawBytes   = rawBytes,
            positionMs = positionMs,
            modifier   = modifier,
        )
    } else {
        // Pure-Kotlin renderer — always available, no native dependency
        AssSubtitleRenderer(
            cues       = cues,
            positionMs = positionMs,
            style      = style,
            modifier   = modifier,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI path
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun JniSubtitleOverlay(
    rawBytes:   ByteArray,
    positionMs: Long,
    modifier:   Modifier = Modifier,
) {
    // One renderer per composition — created once, destroyed on leave
    val renderer = remember { AssJniRenderer() }
    var loaded   by remember { mutableStateOf(false) }
    var frameW   by remember { mutableIntStateOf(0) }
    var frameH   by remember { mutableIntStateOf(0) }
    var bitmap   by remember { mutableStateOf<Bitmap?>(null) }

    // Load subtitle data once
    LaunchedEffect(rawBytes) {
        withContext(Dispatchers.IO) {
            loaded = renderer.loadData(rawBytes)
        }
    }

    // Re-allocate bitmap when frame size changes
    LaunchedEffect(frameW, frameH) {
        if (frameW > 0 && frameH > 0) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
            renderer.setFrameSize(frameW, frameH)
        }
    }

    // Render each frame on position change
    LaunchedEffect(positionMs, loaded) {
        if (!loaded || frameW == 0) return@LaunchedEffect
        val bmp = bitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            renderer.renderFrame(positionMs, bmp)
        }
        // Force recomposition — copy to new reference so Compose detects change
        bitmap = bmp
    }

    // Release when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            renderer.destroy()
            bitmap?.recycle()
            bitmap = null
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap       = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier     = modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (size.width != frameW || size.height != frameH) {
                        frameW = size.width
                        frameH = size.height
                    }
                },
        )
    } ?: run {
        // Size probe — invisible box that captures dimensions before first render
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (size.width != frameW || size.height != frameH) {
                        frameW = size.width
                        frameH = size.height
                    }
                },
        )
    }
}

/** Convenience: check at call-site whether JNI rendering will be used */
val jniSubtitleAvailable: Boolean by lazy { AssJniRenderer().run { destroy(); isAvailable } }
