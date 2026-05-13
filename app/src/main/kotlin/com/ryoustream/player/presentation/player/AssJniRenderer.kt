package com.ryoustream.player.presentation.player

import android.util.Log
import io.github.peerless2012.ass.Ass
import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.AssTrack

/**
 * AssJniRenderer
 *
 * Kotlin wrapper around [peerless2012/libass-android](https://github.com/peerless2012/libass-android)
 * `ass-kt` module. Maven: `io.github.peerless2012:ass-kt:0.4.0`
 *
 * ## API summary (from actual source)
 * - [Ass]          — library context, factory for track + render
 * - [AssTrack]     — subtitle track; loaded via `readBuffer(ByteArray)`
 * - [AssRender]    — renderer; `renderFrame(posMs, BITMAP_RGBA)` → [AssFrame]
 * - [AssFrame]     — result; `images: Array<AssTex>?` each with `x,y,w,h,bitmap`
 *
 * ## Lifecycle
 * ```kotlin
 * val r = AssJniRenderer()
 * if (r.isAvailable) {
 *     r.loadData(bytes)
 *     r.setFrameSize(w, h)
 *     val frame = r.renderFrame(posMs)   // AssFrame? to draw
 *     r.destroy()
 * }
 * ```
 */
class AssJniRenderer {

    val isAvailable: Boolean

    private var ass:    Ass?       = null
    private var track:  AssTrack?  = null
    private var render: AssRender? = null

    init {
        isAvailable = try {
            ass    = Ass()
            track  = ass!!.createTrack()
            render = ass!!.createRender()
            true
        } catch (e: Throwable) {
            Log.w(TAG, "libass unavailable — falling back to Kotlin renderer: ${e.message}")
            false
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Load subtitle bytes (.ass / .ssa full file).
     * @return true on success.
     */
    fun loadData(data: ByteArray): Boolean {
        if (!isAvailable) return false
        return try {
            track?.readBuffer(data)
            render?.setTrack(track)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "loadData: ${e.message}")
            false
        }
    }

    /**
     * Set video frame dimensions — call before first [renderFrame].
     */
    fun setFrameSize(width: Int, height: Int) {
        render?.setFrameSize(width, height)
        render?.setStorageSize(width, height)
    }

    /**
     * Render subtitle overlay for [positionMs].
     * Returns [AssFrame] whose `images` array contains [AssTex] bitmaps with
     * their screen positions, or null when no subtitle is active.
     */
    fun renderFrame(positionMs: Long): AssFrame? {
        if (!isAvailable) return null
        return try {
            render?.renderFrame(positionMs, AssTexType.BITMAP_RGBA)
        } catch (e: Throwable) {
            Log.e(TAG, "renderFrame: ${e.message}")
            null
        }
    }

    /**
     * Release all native resources.
     */
    fun destroy() {
        render = null
        track  = null
        ass    = null
    }

    companion object {
        private const val TAG = "AssJniRenderer"
    }
}
