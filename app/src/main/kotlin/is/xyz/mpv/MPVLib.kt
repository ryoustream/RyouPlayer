@file:Suppress("FunctionName")
package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MPVLib — JNI bridge to libmpv.so from mpv-android.
 *
 * CRITICAL: Every `external fun` name here maps directly to a JNI C symbol:
 *   `create`      → Java_is_xyz_mpv_MPVLib_create
 *   `init`        → Java_is_xyz_mpv_MPVLib_init
 *   `command`     → Java_is_xyz_mpv_MPVLib_command
 *   etc.
 *
 * The function names, return types, and parameter types MUST match exactly
 * what is compiled into libmpv.so. If ANY name or signature differs,
 * System.loadLibrary will succeed but calling the function will throw
 * UnsatisfiedLinkError at runtime → isAvailable = false → blank screen.
 *
 * This interface mirrors mpv-android 2026-04-25 exactly.
 * Source: https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVLib.kt
 *
 * Setup: run scripts/download_mpv_libs.sh once before building.
 * Library name: libmpv.so  →  System.loadLibrary("mpv")
 */
object MPVLib {

    // ── Library load ──────────────────────────────────────────────────────────

    var isAvailable: Boolean = false
        private set

    fun tryLoad(): Boolean {
        if (isAvailable) return true
        isAvailable = try {
            System.loadLibrary("mpv")
            Log.i(TAG, "libmpv.so loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "libmpv.so missing — run scripts/download_mpv_libs.sh and rebuild: ${e.message}")
            false
        }
        return isAvailable
    }

    // ── JNI external functions ────────────────────────────────────────────────
    // These names MUST match the C symbols in libmpv.so exactly.
    // Return types MUST match: command/setOptionString return Int (mpv error code).

    /** Create the mpv context. Call once before [init]. */
    @JvmStatic external fun create(
        ctx:       Context?,
        configDir: String,
        cacheDir:  String,
        logLvl:    String,
    )

    /** Initialize mpv with the options set via [setOptionString]. */
    @JvmStatic external fun init()

    /** Destroy the mpv context. */
    @JvmStatic external fun destroy()

    // ── Surface ───────────────────────────────────────────────────────────────

    @JvmStatic external fun attachSurface(surface: Surface)
    @JvmStatic external fun detachSurface()

    // ── Command ───────────────────────────────────────────────────────────────

    /** Run an mpv command. Returns mpv error code (0 = success). */
    @JvmStatic external fun command(args: Array<String?>): Int

    // ── Options ───────────────────────────────────────────────────────────────

    /** Set an mpv option string BEFORE [init]. Returns mpv error code. */
    @JvmStatic external fun setOptionString(name: String, value: String): Int

    // ── Properties ────────────────────────────────────────────────────────────

    @JvmStatic external fun getPropertyInt(property: String): Int?
    @JvmStatic external fun setPropertyInt(property: String, value: Int)
    @JvmStatic external fun getPropertyBoolean(property: String): Boolean?
    @JvmStatic external fun setPropertyBoolean(property: String, value: Boolean)
    @JvmStatic external fun getPropertyString(property: String): String?
    @JvmStatic external fun setPropertyString(property: String, value: String)
    @JvmStatic external fun getPropertyDouble(property: String): Double?
    @JvmStatic external fun setPropertyDouble(property: String, value: Double)

    // ── Property observation ──────────────────────────────────────────────────

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
    // The C code calls these via JNI by exact name.

    @JvmStatic
    fun eventProperty(property: String, value: Long) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: Boolean) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String, value: String) {
        observers.forEach { it.eventProperty(property, value) }
    }

    @JvmStatic
    fun eventProperty(property: String) {
        observers.forEach { it.eventProperty(property) }
    }

    @JvmStatic
    fun event(eventId: Int) {
        observers.forEach { it.event(eventId) }
    }

    private const val TAG = "MPVLib"
}
