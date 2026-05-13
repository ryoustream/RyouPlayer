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
        // Force mpv to re-draw immediately
        MPVLib.setPropertyString("vo", "gpu")
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        Log.d(TAG, "surfaceChanged ${w}x${h2}")
        MPVLib.command(arrayOf("set_property", "android-surface-size", "${w}x${h2}"))
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "MPVView"
    }
}
