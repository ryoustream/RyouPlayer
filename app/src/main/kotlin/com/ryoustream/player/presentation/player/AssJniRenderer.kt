package com.ryoustream.player.presentation.player

import android.graphics.Bitmap
import android.util.Log

/**
 * AssJniRenderer
 *
 * Kotlin wrapper around the native libass JNI bridge (ass_jni.cpp).
 *
 * ## Usage
 * ```kotlin
 * val renderer = AssJniRenderer()
 * if (renderer.isAvailable) {
 *     renderer.loadData(subtitleBytes)
 *     renderer.setFrameSize(videoWidth, videoHeight)
 *
 *     val bmp = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
 *     renderer.renderFrame(positionMs, bmp)   // fills bmp in-place
 *
 *     renderer.destroy()
 * }
 * ```
 *
 * ## Prebuilt libass.so
 * The native library must be present in app/src/main/jniLibs/{abi}/libass.so.
 * If missing, [isAvailable] is false and callers should fall back to the
 * pure-Kotlin [AssSubtitleRenderer].
 *
 * Prebuilt download:
 *   https://github.com/bMaximus/libass-android/releases
 */
class AssJniRenderer {

    /** true when both the JNI bridge (ryouass) and libass are loaded */
    val isAvailable: Boolean

    private var handle: Long = 0L

    init {
        isAvailable = tryLoad()
        if (isAvailable) {
            handle = nCreate()
            if (handle == 0L) {
                Log.e(TAG, "nCreate() returned null handle — renderer unavailable")
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Load subtitle content from raw bytes.
     * Accepts ASS/SSA formats (libass parses them directly).
     * @return true on success.
     */
    fun loadData(data: ByteArray): Boolean {
        if (!isAvailable || handle == 0L) return false
        return nLoadData(handle, data)
    }

    /**
     * Set the video frame dimensions — must be called before [renderFrame].
     */
    fun setFrameSize(width: Int, height: Int) {
        if (!isAvailable || handle == 0L) return
        nSetFrameSize(handle, width, height)
    }

    /**
     * Render subtitle overlay for [positionMs] into [bitmap].
     * [bitmap] must be ARGB_8888 and sized to the video frame dimensions.
     *
     * @return number of ASS_Image segments blended (0 = no active subtitle).
     */
    fun renderFrame(positionMs: Long, bitmap: Bitmap): Int {
        if (!isAvailable || handle == 0L) return 0
        return nRenderFrame(handle, positionMs, bitmap)
    }

    /**
     * Release all native resources. Must be called when done.
     */
    fun destroy() {
        if (!isAvailable || handle == 0L) return
        nDestroy(handle)
        handle = 0L
    }

    // ── Native declarations ────────────────────────────────────────────────

    private external fun nCreate(): Long
    private external fun nDestroy(handle: Long)
    private external fun nLoadData(handle: Long, data: ByteArray): Boolean
    private external fun nSetFrameSize(handle: Long, w: Int, h: Int)
    private external fun nRenderFrame(handle: Long, posMs: Long, bitmap: Bitmap): Int

    // ── Companion ──────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AssJniRenderer"

        /** Cached result of library load attempt */
        private var loadResult: Boolean? = null

        private fun tryLoad(): Boolean {
            if (loadResult != null) return loadResult!!
            loadResult = try {
                System.loadLibrary("ass")      // prebuilt libass.so
                System.loadLibrary("ryouass")  // our JNI bridge
                Log.i(TAG, "libass + ryouass loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "libass unavailable — falling back to Kotlin renderer: ${e.message}")
                false
            }
            return loadResult!!
        }
    }
}
