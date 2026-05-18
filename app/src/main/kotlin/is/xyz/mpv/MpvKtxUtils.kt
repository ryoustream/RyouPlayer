package `is`.xyz.mpv

import android.util.Log

private const val TAG = "MpvKtx"

// ─── Safe wrappers ────────────────────────────────────────────────────────────

fun MPVLib.getDurationMs(): Long =
    runCatching { getPropertyDouble("duration")?.let { (it * 1000).toLong() } }.getOrNull() ?: 0L

fun MPVLib.getTimeMs(): Long =
    runCatching { getPropertyDouble("time-pos")?.let { (it * 1000).toLong() } }.getOrNull() ?: 0L

// Use setPropertyString for all boolean/int/double setters.
// Avoids jobject vs primitive ambiguity — setPropertyString(jstring,jstring) is unambiguous.
// mpv accepts string representations for all property types ("yes"/"no", "100", "1.5", etc.)

fun MPVLib.pause(paused: Boolean) =
    runCatching { setPropertyString("pause", if (paused) "yes" else "no") }
        .onFailure { Log.w(TAG, "pause: $it") }

fun MPVLib.seekTo(posMs: Long) {
    val sec = posMs / 1000.0
    runCatching { command(arrayOf("seek", sec.toString(), "absolute", "exact")) }
        .onFailure { Log.w(TAG, "seekTo: $it") }
}

fun MPVLib.setSpeed(speed: Double) =
    runCatching { setPropertyString("speed", speed.toString()) }
        .onFailure { Log.w(TAG, "speed: $it") }

fun MPVLib.setVolumePct(pct: Int) =
    runCatching { setPropertyString("volume", pct.coerceIn(0, 200).toString()) }
        .onFailure { Log.w(TAG, "volume: $it") }

// selectTrack: use setPropertyString("sid"/"aid"/"vid", id.toString())
// setPropertyInt has jobject/primitive ambiguity — a string ID is always safe.
fun MPVLib.selectTrack(type: String, trackId: Int) {
    val prop = when (type) { "audio" -> "aid"; "sub" -> "sid"; else -> "vid" }
    runCatching { setPropertyString(prop, trackId.toString()) }
        .onFailure { Log.w(TAG, "selectTrack $type=$trackId: $it") }
}

fun MPVLib.disableTrack(type: String) {
    val prop = when (type) { "audio" -> "aid"; "sub" -> "sid"; else -> "vid" }
    runCatching { setPropertyString(prop, "no") }
        .onFailure { Log.w(TAG, "disableTrack: $it") }
}

fun MPVLib.addSubtitleUri(uri: String) {
    runCatching { command(arrayOf("sub-add", uri, "select")) }
        .onFailure { Log.w(TAG, "sub-add: $it") }
}

fun MPVLib.loadFile(path: String, startMs: Long = 0L) {
    if (startMs > 0L) {
        runCatching { command(arrayOf("loadfile", path, "replace", "start=${startMs / 1000.0}")) }
            .onFailure {
                runCatching { command(arrayOf("loadfile", path)) }
            }
    } else {
        runCatching { command(arrayOf("loadfile", path)) }
            .onFailure { Log.e(TAG, "loadfile: $it") }
    }
}
