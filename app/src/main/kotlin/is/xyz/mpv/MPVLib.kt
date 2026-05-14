@file:Suppress("FunctionName")
package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MPVLib — JNI bridge to libmpv (libplayer.so from mpv-android).
 *
 * Package and class name MUST stay `is.xyz.mpv.MPVLib` to match the JNI
 * symbol table baked into the prebuilt `libplayer.so`:
 *   Java_is_xyz_mpv_MPVLib_init, Java_is_xyz_mpv_MPVLib_command, …
 *
 * Setup: put `libplayer.so` in app/src/main/jniLibs/arm64-v8a/
 * Download: run scripts/download_mpv_libs.sh
 */
object MPVLib {

    // ── Library load ──────────────────────────────────────────────────────────

    var isAvailable: Boolean = false
        private set

    init {
        isAvailable = try {
            System.loadLibrary("player")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "libplayer.so not found — run scripts/download_mpv_libs.sh then rebuild. ${e.message}")
            false
        }
    }

    // ── Init / Destroy ────────────────────────────────────────────────────────

    /**
     * Create mpv context. Call ONCE before [init].
     * @param context Application context (used for font / cache dir resolution)
     * @param logLvl  mpv log level: "warn", "error", "info", "debug", "v"
     */
    // Wrapper — keeps isAvailable guard; external MUST be named "create" to match
    // the JNI symbol Java_is_xyz_mpv_MPVLib_create baked into libplayer.so.
    fun setup(context: Context, logLvl: String = "warn") {
        if (!isAvailable) return
        create(
            context.applicationContext,
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
            logLvl,
        )
    }

    @JvmStatic external fun create(context: Context, configDir: String, cacheDir: String, logLvl: String)
    @JvmStatic external fun init()
    @JvmStatic external fun destroy()

    // ── Surface attachment ────────────────────────────────────────────────────

    @JvmStatic external fun attachSurface(surface: Surface)
    @JvmStatic external fun detachSurface()

    // ── Playback commands ─────────────────────────────────────────────────────

    /** Send an mpv command, e.g. `command(arrayOf("loadfile", path))` */
    @JvmStatic external fun command(args: Array<String>)

    // ── Options (set BEFORE init) ─────────────────────────────────────────────

    @JvmStatic external fun setOptionString(name: String, value: String)

    // ── Properties ────────────────────────────────────────────────────────────

    // Primitive getters MUST be non-nullable — native returns jint/jboolean/jdouble.
    // Nullable boxing (Int?) changes the JNI signature to Ljava/lang/Integer; which
    // doesn't match the native symbol → NoSuchMethodError crash at runtime.
    @JvmStatic external fun getPropertyInt(property: String): Int
    @JvmStatic external fun setPropertyInt(property: String, value: Int)
    @JvmStatic external fun getPropertyBoolean(property: String): Boolean
    @JvmStatic external fun setPropertyBoolean(property: String, value: Boolean)
    @JvmStatic external fun getPropertyString(property: String): String?
    @JvmStatic external fun setPropertyString(property: String, value: String)
    @JvmStatic external fun getPropertyDouble(property: String): Double
    @JvmStatic external fun setPropertyDouble(property: String, value: Double)

    // ── Property observation ──────────────────────────────────────────────────

    @JvmStatic external fun observeProperty(name: String, format: Int)

    const val MPV_FORMAT_NONE   = 0
    const val MPV_FORMAT_STRING = 1
    const val MPV_FORMAT_FLAG   = 3
    const val MPV_FORMAT_INT64  = 4
    const val MPV_FORMAT_DOUBLE = 5

    // ── Events ────────────────────────────────────────────────────────────────

    const val MPV_EVENT_NONE             = 0
    const val MPV_EVENT_SHUTDOWN         = 1
    const val MPV_EVENT_START_FILE       = 6
    const val MPV_EVENT_END_FILE         = 7
    const val MPV_EVENT_FILE_LOADED      = 8
    const val MPV_EVENT_IDLE             = 11
    const val MPV_EVENT_SEEK             = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE  = 22

    // ── Observer ──────────────────────────────────────────────────────────────

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun event(eventId: Int)
    }

    private val observers = CopyOnWriteArrayList<EventObserver>()

    fun addObserver(o: EventObserver)    { observers.add(o) }
    fun removeObserver(o: EventObserver) { observers.remove(o) }

    // ── JNI callbacks (called from native thread — do NOT rename) ─────────────

    @JvmStatic fun eventProperty(property: String, value: Long)    { observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String, value: Boolean) { observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String, value: String)  { observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String)                 { observers.forEach { it.eventProperty(property) } }
    @JvmStatic fun event(eventId: Int)                             { observers.forEach { it.event(eventId) } }

    private const val TAG = "MPVLib"
}
