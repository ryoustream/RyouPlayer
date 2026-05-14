package `is`.xyz.mpv

/**
 * Kotlin-friendly helpers on top of MPVLib.
 *
 * All MPVLib property getters return nullable boxed types (Int?, Boolean?, Double?)
 * matching the native jobject return. Use ?. operators throughout.
 */

/** Read duration (seconds). Returns 0 when not available. */
fun MPVLib.getDurationSec(): Long =
    runCatching { getPropertyDouble("duration")?.toLong() }.getOrNull() ?: 0L

/** Read current position (seconds). */
fun MPVLib.getTimeSec(): Long =
    runCatching { getPropertyDouble("time-pos")?.toLong() }.getOrNull() ?: 0L

/** Read current position (milliseconds). */
fun MPVLib.getTimeMs(): Long = getTimeSec() * 1000L

/** Read duration (milliseconds). */
fun MPVLib.getDurationMs(): Long = getDurationSec() * 1000L

/** Pause / resume. */
fun MPVLib.pause(paused: Boolean) = setPropertyBoolean("pause", paused)

/** Seek to absolute position in seconds. */
fun MPVLib.seekTo(posMs: Long) {
    val sec = posMs / 1000.0
    command(arrayOf("seek", sec.toString(), "absolute", "exact"))
}

/** Set playback speed multiplier. */
fun MPVLib.setSpeed(speed: Double) = setPropertyDouble("speed", speed)

/** Set/get volume (0–100). */
fun MPVLib.setVolumePct(pct: Int) = setPropertyInt("volume", pct.coerceIn(0, 100))

/** Get track-list as raw string (for parsing). */
fun MPVLib.getTrackList(): String = getPropertyString("track-list") ?: "[]"

/** Select a track by ID. type = "audio" | "sub" | "video". */
fun MPVLib.selectTrack(type: String, trackId: Int) {
    val prop = when (type) {
        "audio" -> "aid"
        "sub"   -> "sid"
        else    -> "vid"
    }
    setPropertyInt(prop, trackId)
}

/** Disable a track type. */
fun MPVLib.disableTrack(type: String) {
    val prop = when (type) { "audio" -> "aid"; "sub" -> "sid"; else -> "vid" }
    setPropertyString(prop, "no")
}

/** Load an external subtitle file. */
fun MPVLib.addSubtitleFile(path: String) {
    command(arrayOf("sub-add", path, "select"))
}

/** Load an external subtitle from content:// URI via mpv's sub-add. */
fun MPVLib.addSubtitleUri(uri: String) {
    command(arrayOf("sub-add", uri, "select"))
}
