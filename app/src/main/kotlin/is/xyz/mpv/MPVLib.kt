package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MPVLib — JNI bridge to libmpv/libplayer from mpv-android.
 *
 * Signatures are derived directly from the official mpv-android source:
 *   app/src/main/java/is/xyz/mpv/MPVLib.java
 *   app/src/main/jni/main.cpp
 *   app/src/main/jni/property.cpp
 *
 * LIFECYCLE:
 *   1. tryLoad()          — loads the .so. Must succeed first.
 *   2. create(ctx)        — ONE param (Context). Creates mpv_handle.
 *   3. setOptionString()  — set options AFTER create(), BEFORE init().
 *   4. init()             — starts mpv core + event thread.
 *   5. attachSurface()    — only AFTER init() returns.
 *   6. destroy()          — tears down mpv. Resets isInitialized.
 *
 * SETTER TYPES — confirmed from property.cpp:
 *   jni_func(void, setPropertyInt,     jstring, jobject)  ← boxed Integer
 *   jni_func(void, setPropertyDouble,  jstring, jobject)  ← boxed Double
 *   jni_func(void, setPropertyBoolean, jstring, jobject)  ← boxed Boolean
 *   Kotlin nullable (Int?, Double?, Boolean?) compiles to jobject. ✓
 *
 * GETTER TYPES — confirmed from property.cpp:
 *   jni_func(jobject, getPropertyInt, ...)     ← returns Integer | null
 *   jni_func(jobject, getPropertyDouble, ...)  ← returns Double  | null
 *   jni_func(jobject, getPropertyBoolean, ...) ← returns Boolean | null
 *
 * create() — confirmed from main.cpp:
 *   jni_func(void, create, jobject appctx)  ← ONE parameter only.
 *   Passing extra args crashes the native stack.
 *
 * command() — confirmed from main.cpp:
 *   jni_func(void, command, jobjectArray jarray)  ← returns void.
 */
object MPVLib {

    // ── State ─────────────────────────────────────────────────────────────────

    var isAvailable: Boolean = false
        private set

    val isInitialized: AtomicBoolean = AtomicBoolean(false)

    /**
     * Try to load the native library.
     * mpv-android ships either "player" (newer) or "mpv" (older) — try both.
     */
    fun tryLoad(): Boolean {
        if (isAvailable) return true
        for (lib in listOf("player", "mpv")) {
            try {
                System.loadLibrary(lib)
                isAvailable = true
                Log.i(TAG, "lib${lib}.so loaded")
                return true
            } catch (_: UnsatisfiedLinkError) {
                Log.w(TAG, "lib${lib}.so not found, trying next…")
            }
        }
        Log.e(TAG, "No mpv native library found (tried libplayer.so, libmpv.so)")
        return false
    }

    // ── Native declarations — names MUST match JNI symbols in the .so ─────────

    /** ONE param (Context). main.cpp: jni_func(void, create, jobject appctx) */
    @JvmStatic external fun create(appctx: Context)

    @JvmStatic external fun init()
    @JvmStatic external fun destroy()

    @JvmStatic external fun attachSurface(surface: Surface)
    @JvmStatic external fun detachSurface()

    /** Returns void. main.cpp: jni_func(void, command, jobjectArray jarray) */
    @JvmStatic external fun command(args: Array<String?>)

    /** Returns int error code. */
    @JvmStatic external fun setOptionString(name: String, value: String): Int

    // Getters — return jobject (nullable boxed). property.cpp: jni_func(jobject, ...)
    @JvmStatic external fun getPropertyInt(property: String): Int?
    @JvmStatic external fun getPropertyBoolean(property: String): Boolean?
    @JvmStatic external fun getPropertyString(property: String): String?
    @JvmStatic external fun getPropertyDouble(property: String): Double?

    // Setters — take jobject (nullable boxed). property.cpp: jni_func(void, ..., jobject value)
    @JvmStatic external fun setPropertyInt(property: String, value: Int?)
    @JvmStatic external fun setPropertyBoolean(property: String, value: Boolean?)
    @JvmStatic external fun setPropertyString(property: String, value: String)
    @JvmStatic external fun setPropertyDouble(property: String, value: Double?)

    @JvmStatic external fun observeProperty(name: String, format: Int)

    // ── Constants ─────────────────────────────────────────────────────────────

    const val MPV_FORMAT_NONE   = 0
    const val MPV_FORMAT_STRING = 1
    const val MPV_FORMAT_FLAG   = 3
    const val MPV_FORMAT_INT64  = 4
    const val MPV_FORMAT_DOUBLE = 5

    const val MPV_EVENT_NONE             = 0
    const val MPV_EVENT_SHUTDOWN         = 1
    const val MPV_EVENT_START_FILE       = 6
    const val MPV_EVENT_END_FILE         = 7
    const val MPV_EVENT_FILE_LOADED      = 8
    const val MPV_EVENT_IDLE             = 11
    const val MPV_EVENT_SEEK             = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE  = 22

    // ── Safe lifecycle helpers ────────────────────────────────────────────────

    fun initMpv(context: Context): Boolean {
        if (!isAvailable && !tryLoad()) return false
        if (isInitialized.get()) { Log.d(TAG, "initMpv: already done"); return true }
        return try {
            // create() takes ONE Context — confirmed from main.cpp
            create(context.applicationContext)
            isInitialized.set(true)
            Log.i(TAG, "initMpv: create() OK")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "initMpv create() failed: $e"); false
        }
    }

    fun destroyMpv() {
        if (!isInitialized.get()) return
        isInitialized.set(false)
        try { destroy() } catch (e: Throwable) { Log.e(TAG, "destroyMpv: $e") }
    }

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

    // JNI callbacks — called from native event thread. DO NOT rename.
    @JvmStatic fun eventProperty(property: String, value: Long)    { if (isInitialized.get()) observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String, value: Boolean) { if (isInitialized.get()) observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String, value: String)  { if (isInitialized.get()) observers.forEach { it.eventProperty(property, value) } }
    @JvmStatic fun eventProperty(property: String)                 { if (isInitialized.get()) observers.forEach { it.eventProperty(property) } }
    @JvmStatic fun event(eventId: Int)                             { if (isInitialized.get()) observers.forEach { it.event(eventId) } }

    private const val TAG = "MPVLib"
}
