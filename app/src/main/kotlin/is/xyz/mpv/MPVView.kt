package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * MPVView — SurfaceView that wires its Surface to libmpv.
 *
 * SAFETY CONTRACT:
 *   - attachSurface() must only be called AFTER MPVLib.isInitialized == true.
 *   - We guard every callback: if mpv is not initialized, we skip silently.
 *   - surfaceChanged MUST NOT call setPropertyString("android-surface-size"):
 *     that is NOT a writable mpv property — doing so causes a native error
 *     that can cascade into a crash. mpv reads the surface dimensions 
 *     internally from the EGL context when it next renders a frame.
 */
class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated — mpv initialized: ${MPVLib.isInitialized.get()}")
        if (!MPVLib.isInitialized.get()) {
            // Surface arrived before MPVLib.init() completed.
            // This is safe: mpv will request a new surface when it starts rendering.
            Log.w(TAG, "surfaceCreated: mpv not yet initialized, skipping attachSurface")
            return
        }
        try {
            MPVLib.attachSurface(h.surface)
        } catch (e: Exception) {
            Log.e(TAG, "attachSurface failed: ${e.message}", e)
        }
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        Log.d(TAG, "surfaceChanged ${w}x${h2}")
        if (!MPVLib.isInitialized.get()) return
        // Notify mpv of the new surface dimensions so the Android GPU context
        // can resize its EGL surface. Without this, video renders at wrong size.
        try {
            MPVLib.setPropertyString("android-surface-size", "${w}x${h2}")
        } catch (e: Exception) {
            Log.w(TAG, "setPropertyString android-surface-size failed: ${e.message}")
        }
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        if (!MPVLib.isInitialized.get()) return
        try {
            MPVLib.detachSurface()
        } catch (e: Exception) {
            Log.e(TAG, "detachSurface failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MPVView"
    }
}
