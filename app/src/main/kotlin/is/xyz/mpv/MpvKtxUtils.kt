package `is`.xyz.mpv

import android.util.Log

private const val TAG = "MpvKtx"

// ─── Safe wrappers ────────────────────────────────────────────────────────────
// All helpers return safe defaults rather than throwing if mpv is not ready.

fun MPVLib.getDurationMs(): Long =
    runCatching { getPropertyDouble("duration")?.let { (it * 1000).toLong() } }.getOrNull() ?: 0L

fun MPVLib.getTimeMs(): Long =
    runCatching { getPropertyDouble("time-pos")?.let { (it * 1000).toLong() } }.getOrNull() ?: 0L

fun MPVLib.pause(paused: Boolean) =
    runCatching { setPropertyBoolean("pause", paused) }.onFailure { Log.w(TAG, "pause: $it") }

fun MPVLib.seekTo(posMs: Long) {
    val sec = posMs / 1000.0
    runCatching { command(arrayOf("seek", sec.toString(), "absolute", "exact")) }
        .onFailure { Log.w(TAG, "seekTo: $it") }
}

fun MPVLib.setSpeed(speed: Double) =
    runCatching { setPropertyDouble("speed", speed) }.onFailure { Log.w(TAG, "speed: $it") }

fun MPVLib.setVolumePct(pct: Int) =
    runCatching { setPropertyInt("volume", pct.coerceIn(0, 200)) }
        .onFailure { Log.w(TAG, "volume: $it") }

fun MPVLib.selectTrack(type: String, trackId: Int) {
    val prop = when (type) { "audio" -> "aid"; "sub" -> "sid"; else -> "vid" }
    runCatching { setPropertyInt(prop, trackId) }.onFailure { Log.w(TAG, "selectTrack: $it") }
}

fun MPVLib.disableTrack(type: String) {
    val prop = when (type) { "audio" -> "aid"; "sub" -> "sid"; else -> "vid" }
    runCatching { setPropertyString(prop, "no") }.onFailure { Log.w(TAG, "disableTrack: $it") }
}

fun MPVLib.addSubtitleUri(uri: String) {
    runCatching { command(arrayOf("sub-add", uri, "select")) }
        .onFailure { Log.w(TAG, "sub-add: $it") }
}

fun MPVLib.loadFile(path: String, startMs: Long = 0L) {
    if (startMs > 0L) {
        runCatching {
            command(arrayOf("loadfile", path, "replace",
                "0", "start=${startMs / 1000.0}"))
        }.onFailure {
            // Fallback: plain loadfile, then seek on file-loaded
            runCatching { command(arrayOf("loadfile", path)) }
        }
    } else {
        runCatching { command(arrayOf("loadfile", path)) }
            .onFailure { Log.e(TAG, "loadfile: $it") }
    }
}
