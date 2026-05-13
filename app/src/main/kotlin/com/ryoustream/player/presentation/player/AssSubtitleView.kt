package com.ryoustream.player.presentation.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import com.ryoustream.player.domain.model.AssCue
import com.ryoustream.player.domain.model.SubtitleStyle
import io.github.peerless2012.ass.Ass as AssLib
import io.github.peerless2012.ass.AssFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AssSubtitleView
 *
 * Unified ASS/SSA subtitle overlay composable.
 *
 * ## Strategy
 * 1. **JNI path** — [AssJniRenderer] available (libass native loaded from `ass-kt` AAR).
 *    `renderFrame()` returns [AssFrame] containing `Array<AssTex>`, each with a
 *    pre-rendered Bitmap and its screen position (x, y). Composited via Canvas.
 *
 * 2. **Kotlin fallback** — libass failed to load. Delegates to [AssSubtitleRenderer]
 *    (pure-Kotlin Canvas-based outline renderer). Zero native dependency.
 *
 * @param rawBytes   Full bytes of the .ass / .ssa file.
 * @param cues       Pre-parsed cue list — used only by the Kotlin fallback path.
 * @param positionMs Current playback position in milliseconds.
 * @param style      Visual style for the Kotlin fallback renderer.
 */
@Composable
fun AssSubtitleView(
    rawBytes:   ByteArray?,
    cues:       List<AssCue>,
    positionMs: Long,
    style:      SubtitleStyle = SubtitleStyle(),
    modifier:   Modifier      = Modifier,
) {
    // Check once whether native libass is loadable
    val jniAvailable = remember {
        try { AssLib(); true } catch (_: Throwable) { false }
    }

    if (jniAvailable && rawBytes != null) {
        JniSubtitleOverlay(
            rawBytes   = rawBytes,
            positionMs = positionMs,
            modifier   = modifier,
        )
    } else {
        AssSubtitleRenderer(
            cues       = cues,
            positionMs = positionMs,
            style      = style,
            modifier   = modifier,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// JNI path: composites AssFrame.images (Array<AssTex>) onto a Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun JniSubtitleOverlay(
    rawBytes:   ByteArray,
    positionMs: Long,
    modifier:   Modifier = Modifier,
) {
    val renderer = remember { AssJniRenderer() }
    var loaded   by remember { mutableStateOf(false) }
    var frameW   by remember { mutableIntStateOf(0) }
    var frameH   by remember { mutableIntStateOf(0) }
    var frame    by remember { mutableStateOf<AssFrame?>(null) }

    // Load subtitle data once per rawBytes change
    LaunchedEffect(rawBytes) {
        withContext(Dispatchers.IO) {
            loaded = renderer.loadData(rawBytes)
        }
    }

    // Notify renderer when layout dimensions become known / change
    LaunchedEffect(frameW, frameH) {
        if (frameW > 0 && frameH > 0) {
            renderer.setFrameSize(frameW, frameH)
        }
    }

    // Render on every position tick
    LaunchedEffect(positionMs, loaded, frameW) {
        if (!loaded || frameW == 0) return@LaunchedEffect
        frame = withContext(Dispatchers.Default) {
            renderer.renderFrame(positionMs)
        }
    }

    DisposableEffect(Unit) {
        onDispose { renderer.destroy() }
    }

    // Capture layout size and composite subtitle bitmaps
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width != frameW || size.height != frameH) {
                    frameW = size.width
                    frameH = size.height
                }
            },
    ) {
        val images = frame?.images ?: return@Canvas
        drawIntoCanvas { canvas ->
            val nCanvas = canvas.nativeCanvas
            for (tex in images) {
                val bmp = tex.bitmap ?: continue
                // Each AssTex carries its own pre-rendered RGBA bitmap +
                // screen position from libass layout engine
                nCanvas.drawBitmap(bmp, tex.x.toFloat(), tex.y.toFloat(), null)
            }
        }
    }
}
