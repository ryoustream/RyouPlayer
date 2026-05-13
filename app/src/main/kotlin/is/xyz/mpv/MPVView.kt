package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * MPVView — SurfaceView that wires its Surface to libmpv.
 * Used inside AndroidView from PlayerScreen.
 */
class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
        MPVLib.attachSurface(h.surface)
        // Do NOT set "vo" here — it was already set as an option before MPVLib.init()
        // and calling setPropertyString before the renderer is ready can cause crashes.
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        Log.d(TAG, "surfaceChanged ${w}x${h2}")
        // "set_property" is not a valid mpv command — use setPropertyString directly.
        MPVLib.setPropertyString("android-surface-size", "${w}x${h2}")
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "MPVView"
    }
}
