package com.ryoustream.player.presentation.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AssMediaHandler
 *
 * Integrates [ass-media](https://github.com/peerless2012/libass-android/tree/master/lib_ass_media)
 * with ExoPlayer for full ASS/SSA subtitle rendering from embedded MKV tracks.
 *
 * ## How it works
 * - [AssHandler] (from `ass-media`) attaches as a [Player.Listener] to ExoPlayer.
 * - When ExoPlayer parses a MKV/MP4 and finds an ASS track, it calls
 *   [onTracksChanged] → [AssHandler.createTrack] with `format.initializationData`
 *   (the full ASS script header: [Script Info], [V4+ Styles], [Events] Format).
 * - As playback runs, ExoPlayer's internal text renderer feeds each dialogue line
 *   (via `readChunk`) with start/duration/bytes.
 * - [AssRender.renderFrame] uses libass to composite a full RGBA/ALPHA bitmap frame
 *   at the current position, preserving: custom fonts, animations, `\pos`, `\move`,
 *   karaoke, colour overrides, outlines — everything libass supports.
 *
 * ## Lifecycle
 * 1. Call [attach] once when ExoPlayer is created.
 * 2. Observe [renderState] in Compose — it carries the current [AssRender] from the handler.
 * 3. Render with [AssLibassOverlay] composable, passing [positionMs] from player position ticks.
 * 4. Call [detach] in DisposableEffect onDispose.
 *
 * ## Why NOT extracting raw bytes from the file
 * MKV embedded subtitles are stored as individual dialogue events within the Matroska
 * container. There is no contiguous ASS file to slice out at a byte offset. The correct
 * approach is to let ExoPlayer (via its MKV extractor) parse the container and feed each
 * event to the decoder/renderer — exactly what `ass-media` does by hooking Player.Listener.
 */
@OptIn(UnstableApi::class)
class AssMediaHandler {

    private var handler: AssHandler? = null

    /** Current AssRender, non-null once a track is detected by the handler. */
    val currentRender: AssRender?
        get() = handler?.render

    val videoTime: Long
        get() = handler?.videoTime ?: -1L

    /** Attach to an ExoPlayer instance. Must be called before playback starts. */
    fun attach(player: ExoPlayer) {
        if (handler != null) return
        handler = AssHandler(AssRenderType.OVERLAY_CANVAS).also { h ->
            h.init(player)
            Log.i(TAG, "AssHandler attached, render type = OVERLAY_CANVAS")
        }
    }

    /** Detach from the player. Call this in onDispose / onCleared. */
    fun detach(player: ExoPlayer?) {
        handler?.let { h ->
            player?.removeListener(h)
        }
        handler = null
        Log.i(TAG, "AssHandler detached")
    }

    companion object {
        private const val TAG = "AssMediaHandler"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compose overlay: renders AssFrame bitmaps via native Canvas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable overlay that renders libass frames using [AssMediaHandler].
 *
 * Uses [AssTexType.BITMAP_ALPHA] (single alpha bitmap + colour per span) for
 * correct colour rendering — the same approach used in [AssCanvasOverlay] from
 * the official `ass-media` sample.
 *
 * @param handler The [AssMediaHandler] driving this overlay.
 * @param positionMs Current playback position from ExoPlayer (milliseconds).
 */
@Composable
fun AssLibassOverlay(
    handler: AssMediaHandler,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    var frameW by remember { mutableIntStateOf(0) }
    var frameH by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<AssFrame?>(null) }

    // Re-render whenever position or size changes
    LaunchedEffect(positionMs, frameW, frameH) {
        if (frameW == 0 || frameH == 0) return@LaunchedEffect
        val render = handler.currentRender ?: return@LaunchedEffect
        frame = withContext(Dispatchers.Default) {
            try {
                // positionUs: ExoPlayer reports Ms, libass renderFrame takes Ms too
                render.renderFrame(positionMs, AssTexType.BITMAP_ALPHA)
            } catch (e: Throwable) {
                Log.e("AssLibassOverlay", "renderFrame: ${e.message}")
                null
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width != frameW || size.height != frameH) {
                    frameW = size.width
                    frameH = size.height
                    // Notify render of the new surface size
                    handler.currentRender?.setFrameSize(size.width, size.height)
                    handler.currentRender?.setStorageSize(size.width, size.height)
                }
            },
    ) {
        val images = frame?.images ?: return@Canvas
        drawIntoCanvas { canvas ->
            val nCanvas = canvas.nativeCanvas
            val paint = android.graphics.Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.SRC_OVER
                )
            }
            for (tex in images) {
                val bmp = tex.bitmap ?: continue
                // Decode ASS ABGR colour: R=bits[31:24], G=[23:16], B=[15:8], A=0xFF-[7:0]
                val r = (tex.color shr 24) and 0xFF
                val g = (tex.color shr 16) and 0xFF
                val b = (tex.color shr  8) and 0xFF
                val a = 0xFF - (tex.color and 0xFF)
                paint.color = (a shl 24) or (r shl 16) or (g shl 8) or b
                nCanvas.drawBitmap(bmp, tex.x.toFloat(), tex.y.toFloat(), paint)
            }
        }
    }
}
