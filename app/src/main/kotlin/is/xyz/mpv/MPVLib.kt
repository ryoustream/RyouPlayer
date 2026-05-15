@file:Suppress("FunctionName")
package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MPVLib — JNI bridge to libmpv.so from mpv-android.
 *
 * LIFECYCLE RULES (violating these causes native crashes):
 *   1. tryLoad()  — must be called first. Loads the .so file.
 *   2. create()   — creates the mpv context. Call ONCE per session.
 *   3. setOptionString() — set options. Must be AFTER create(), BEFORE init().
 *   4. init()     — initialises the renderer. Call ONCE after all options are set.
 *   5. attachSurface() — can ONLY be called AFTER init() returns.
 *   6. destroy()  — tears down the mpv context. After this, create() can be
 *                   called again for a new session.
 *
 * isAvailable    = libmpv.so is loaded in the JVM
 * isInitialized  = create() + init() have completed successfully
 *                  (safe to call attachSurface / loadfile / properties)
 */
object MPVLib {

    // ── State flags ───────────────────────────────────────────────────────────

    var isAvailable: Boolean = false
        private set

    /** True only after create() + init() have both completed. */
    val isInitialized: AtomicBoolean = AtomicBoolean(false)

    fun tryLoad(): Boolean {
        if (isAvailable) return true
        isAvailable = try {
            System.loadLibrary("mpv")
            Log.i(TAG, "libmpv.so loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "libmpv.so not found in jniLibs — run scripts/download_mpv_libs.sh: ${e.message}")
            false
        }
        return isAvailable
    }

    // ── JNI external functions ────────────────────────────────────────────────
    // Names MUST match the C JNI symbols in libmpv.so exactly.

    @JvmStatic external fun create(ctx: Context?, configDir: String, cacheDir: String, logLvl: String)
    @JvmStatic external fun init()
    @JvmStatic external fun destroy()

    @JvmStatic external fun attachSurface(surface: Surface)
    @JvmStatic external fun detachSurface()

    /** Returns mpv error code (0 = success, negative = error). */
    @JvmStatic external fun command(args: Array<String?>): Int

    /** Set option string BEFORE init(). Returns mpv error code. */
    @JvmStatic external fun setOptionString(name: String, value: String): Int

    @JvmStatic external fun getPropertyInt(property: String): Int?
    @JvmStatic external fun setPropertyInt(property: String, value: Int)
    @JvmStatic external fun getPropertyBoolean(property: String): Boolean?
    @JvmStatic external fun setPropertyBoolean(property: String, value: Boolean)
    @JvmStatic external fun getPropertyString(property: String): String?
    @JvmStatic external fun setPropertyString(property: String, value: String)
    @JvmStatic external fun getPropertyDouble(property: String): Double?
    @JvmStatic external fun setPropertyDouble(property: String, value: Double)

    @JvmStatic external fun observeProperty(name: String, format: Int)

    // ── Format constants ──────────────────────────────────────────────────────

    const val MPV_FORMAT_NONE   = 0
    const val MPV_FORMAT_STRING = 1
    const val MPV_FORMAT_FLAG   = 3
    const val MPV_FORMAT_INT64  = 4
    const val MPV_FORMAT_DOUBLE = 5

    // ── Event ID constants ────────────────────────────────────────────────────

    const val MPV_EVENT_NONE             = 0
    const val MPV_EVENT_SHUTDOWN         = 1
    const val MPV_EVENT_START_FILE       = 6
    const val MPV_EVENT_END_FILE         = 7
    const val MPV_EVENT_FILE_LOADED      = 8
    const val MPV_EVENT_IDLE             = 11
    const val MPV_EVENT_SEEK             = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE  = 22

    // ── Safe init/destroy helpers ─────────────────────────────────────────────

    /**
     * Safe create+init sequence.
     * Guards against double-init: if already initialized, does nothing.
     * Returns true if initialization succeeded (or was already done).
     */
    fun initMpv(context: Context, logLvl: String = "warn"): Boolean {
        if (!isAvailable && !tryLoad()) return false
        if (isInitialized.get()) {
            Log.d(TAG, "initMpv: already initialized, skipping")
            return true
        }
        return try {
            create(
                context.applicationContext,
                context.filesDir.absolutePath,
                context.cacheDir.absolutePath,
                logLvl,
            )
            init()
            isInitialized.set(true)
            Log.i(TAG, "initMpv: success")
            true
        } catch (e: Exception) {
            Log.e(TAG, "initMpv failed: ${e.message}", e)
            false
        }
    }

    /**
     * Safe destroy. Resets isInitialized so a new session can be started.
     * Call this only when the player is fully closed (ViewModel.onCleared).
     */
    fun destroyMpv() {
        if (!isInitialized.get()) return
        try {
            isInitialized.set(false)  // set BEFORE destroy() to stop any callbacks
            destroy()
            Log.i(TAG, "destroyMpv: success")
        } catch (e: Exception) {
            Log.e(TAG, "destroyMpv failed: ${e.message}", e)
        }
    }

    // ── Observer system ───────────────────────────────────────────────────────

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

    // ── JNI callbacks (called from native thread — DO NOT rename) ─────────────

    @JvmStatic fun eventProperty(property: String, value: Long)    { if (isInitialized.get()) observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String, value: Boolean) { if (isInitialized.get()) observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String, value: String)  { if (isInitialized.get()) observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String)                 { if (isInitialized.get()) observers.forEach { it.eventProperty(property) } }
    @JvmStatic fun event(eventId: Int)                             { if (isInitialized.get()) observers.forEach { it.event(eventId) } }

    private const val TAG = "MPVLib"
}
