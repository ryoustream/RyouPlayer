package com.ryoustream.player.presentation.player

import android.graphics.Bitmap
import android.util.Log
import io.github.peerless2012.ass.AssLibrary
import io.github.peerless2012.ass.AssRenderer
import io.github.peerless2012.ass.AssTrack

/**
 * AssJniRenderer
 *
 * Kotlin wrapper around [peerless2012/libass-android](https://github.com/peerless2012/libass-android).
 * Maven: `io.github.peerless2012:ass-kt:0.3.0`
 *
 * Uses native libass via JNI for accurate ASS/SSA rendering (karaoke, \clip,
 * \move, transforms, etc.). Falls back to Kotlin renderer automatically if the
 * native library fails to load.
 *
 * ## Lifecycle
 * ```kotlin
 * val r = AssJniRenderer()
 * if (r.isAvailable) {
 *     r.loadData(subtitleBytes)
 *     r.setFrameSize(videoW, videoH)
 *     val bmp = Bitmap.createBitmap(videoW, videoH, Bitmap.Config.ARGB_8888)
 *     r.renderFrame(positionMs, bmp)
 *     r.destroy()
 * }
 * ```
 */
class AssJniRenderer {

    val isAvailable: Boolean

    private var library:  AssLibrary?  = null
    private var renderer: AssRenderer? = null
    private var track:    AssTrack?    = null

    init {
        isAvailable = try {
            library  = AssLibrary.create()
            renderer = library?.createRenderer()
            true
        } catch (e: Throwable) {
            Log.w(TAG, "libass unavailable — falling back to Kotlin renderer: ${e.message}")
            false
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Load subtitle content from raw bytes (.ass / .ssa).
     * @return true on success.
     */
    fun loadData(data: ByteArray): Boolean {
        if (!isAvailable) return false
        return try {
            track?.release()
            track = library?.createTrackFromData(data)
            track != null
        } catch (e: Throwable) {
            Log.e(TAG, "loadData failed: ${e.message}")
            false
        }
    }

    /**
     * Set video frame size — must be called before [renderFrame].
     */
    fun setFrameSize(width: Int, height: Int) {
        renderer?.setFrameSize(width, height)
    }

    /**
     * Render the subtitle overlay for [positionMs] into [bitmap] (ARGB_8888).
     * @return number of subtitle image segments drawn; 0 = no active subtitle.
     */
    fun renderFrame(positionMs: Long, bitmap: Bitmap): Int {
        val r = renderer ?: return 0
        val t = track    ?: return 0
        return try {
            r.renderFrame(t, positionMs, bitmap)
        } catch (e: Throwable) {
            Log.e(TAG, "renderFrame error: ${e.message}")
            0
        }
    }

    /**
     * Release all native resources. Must be called when done.
     */
    fun destroy() {
        track?.release();    track    = null
        renderer?.release(); renderer = null
        library?.release();  library  = null
    }

    companion object {
        private const val TAG = "AssJniRenderer"
    }
}
