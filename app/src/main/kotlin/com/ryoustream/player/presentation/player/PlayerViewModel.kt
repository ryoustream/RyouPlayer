package com.ryoustream.player.presentation.player

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.PlaybackState
import com.ryoustream.player.domain.model.RepeatMode
import com.ryoustream.player.domain.model.SubtitleStyle
import com.ryoustream.player.domain.model.AssCue
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.domain.usecase.GetMediaByUriUseCase
import com.ryoustream.player.domain.usecase.UpdatePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.addSubtitleUri
import `is`.xyz.mpv.disableTrack
import `is`.xyz.mpv.getDurationMs
import `is`.xyz.mpv.getTimeMs
import `is`.xyz.mpv.loadFile
import `is`.xyz.mpv.pause
import `is`.xyz.mpv.seekTo
import `is`.xyz.mpv.selectTrack
import `is`.xyz.mpv.setSpeed
import `is`.xyz.mpv.setVolumePct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject

// ─── Orientation Mode ─────────────────────────────────────────────────────────
enum class OrientationMode(val label: String) {
    AUTO("Auto Rotate"),
    SENSOR_VIDEO("Video Orientation"),
    LOCK_PORTRAIT("Lock Portrait"),
    LOCK_PORTRAIT_REVERSE("Lock Portrait Reverse"),
    LOCK_LANDSCAPE("Lock Landscape"),
    LOCK_LANDSCAPE_REVERSE("Lock Landscape Reverse"),
}

// ─── Track Info ───────────────────────────────────────────────────────────────
data class TrackInfo(
    val index: Int,         // display index (0-based)
    val mpvId: Int,         // mpv track id (1-based)
    val label: String,
    val language: String = "",
    val codec: String    = "",
    val isSelected: Boolean = false,
    val type: String = "",  // "audio" | "sub" | "video"
)

// ─── Player UI State ──────────────────────────────────────────────────────────
data class PlayerUiState(
    val playbackState: PlaybackState         = PlaybackState(),
    val showControls: Boolean                = true,
    val isLocked: Boolean                    = false,
    val aspectRatioMode: AspectRatioMode     = AspectRatioMode.FIT,
    val orientationMode: OrientationMode     = OrientationMode.SENSOR_VIDEO,
    val brightnessLevel: Float               = -1f,   // -1 = system default
    val volumeLevel: Float                   = 1f,
    val videoWidth: Int                      = 0,
    val videoHeight: Int                     = 0,
    // Subtitle
    val subtitleEnabled: Boolean             = true,
    val subtitleCues: List<AssCue>           = emptyList(),
    val subtitleStyle: SubtitleStyle         = SubtitleStyle(),
    val subtitleTracks: List<TrackInfo>      = emptyList(),
    val selectedSubtitleTrack: Int           = -1,    // mpvId, -1 = none
    val subtitleDelay: Long                  = 0L,
    // Audio
    val audioTracks: List<TrackInfo>         = emptyList(),
    val selectedAudioTrack: Int              = 1,     // mpvId
    val audioDelay: Long                     = 0L,
    // Video
    val videoTracks: List<TrackInfo>         = emptyList(),
    // Panels
    val showSubtitlePanel: Boolean           = false,
    val showAudioPanel: Boolean              = false,
    val showSubtitleStyleSheet: Boolean      = false,
    // Metadata
    val chapterMarks: List<Pair<Long, String>> = emptyList(),
    val mediaTitle: String                   = "",
    val error: String?                       = null,
    // Folder navigation
    val hasPrev:         Boolean                  = false,
    val hasNext:         Boolean                  = false,
    val autoNext:        Boolean                  = true,
    val showHiddenFiles: Boolean                  = false,
    // Video info overlay
    val showVideoInfo:   Boolean                  = false,
    val videoInfo:       Map<String, String>      = emptyMap(),
    // mpv-specific
    val mpvReady: Boolean                    = false,
    val isBuffering: Boolean                 = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMediaByUriUseCase: GetMediaByUriUseCase,
    private val updatePlaybackPositionUseCase: UpdatePlaybackPositionUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel(), MPVLib.EventObserver {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var positionJob: Job? = null
    private var hideJob: Job? = null
    private var currentMediaId = 0L
    private var pendingUri: Uri? = null

    // ── Folder navigation ────────────────────────────────────────────────────
    private var _folderFiles: List<Uri> = emptyList()
    private var _folderIndex: Int       = -1

    // Prevents auto-next chain: MPV_EVENT_END_FILE fires when a file is
    // REPLACED (not just when it ends naturally). Without this flag,
    // pressing next triggers END_FILE → auto-next → END_FILE → auto-next...
    // until the last episode. Set true before loadFile, cleared on FILE_LOADED.
    private var _intentionalLoad: Boolean = false

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun initializePlayer() {
        // Guard: tryLoad first — must succeed before any JNI call
        if (!MPVLib.tryLoad()) {
            _state.update { it.copy(error = "libplayer.so not found.\nRun scripts/download_mpv_libs.sh and rebuild.") }
            Log.e(TAG, "MPVLib not available — libmpv.so missing from jniLibs/arm64-v8a/")
            return
        }

        // Guard: if already initialized (e.g. ViewModel reused after config change),
        // skip re-init to prevent native crash from double create()+init()
        if (MPVLib.isInitialized.get()) {
            Log.d(TAG, "initializePlayer: already initialized")
            _state.update { it.copy(mpvReady = true) }
            pendingUri?.let { playUri(it); pendingUri = null }
            return
        }

        MPVLib.addObserver(this)

        // Wrap the entire create→setOption→init sequence in try-catch.
        // Any UnsatisfiedLinkError or native exception here would otherwise
        // propagate uncaught and force-close the app.
        val initOk = runCatching {
            // create() — ONE param (Context) confirmed from mpv-android main.cpp:
            //   jni_func(void, create, jobject appctx)
            // Passing extra args (configDir, cacheDir, logLvl) corrupts the
            // native stack → immediate crash. Context is the only parameter.
            MPVLib.create(context.applicationContext)

            // setOptionString() — ALL options MUST be set between create() and init()
            MPVLib.setOptionString("config-dir",       context.filesDir.absolutePath)
            MPVLib.setOptionString("cache-dir",        context.cacheDir.absolutePath)
            MPVLib.setOptionString("vo",                "gpu")
            MPVLib.setOptionString("gpu-context",       "android")
            MPVLib.setOptionString("opengl-es",         "yes")
            MPVLib.setOptionString("hwdec",             "mediacodec-copy")
            MPVLib.setOptionString("hwdec-codecs",      "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
            MPVLib.setOptionString("ao",                "audiotrack,opensles")
            MPVLib.setOptionString("tls-verify",        "no")
            MPVLib.setOptionString("demuxer-max-bytes", "128MiB")
            MPVLib.setOptionString("demuxer-readahead-secs", "10")
            MPVLib.setOptionString("cache",             "yes")
            MPVLib.setOptionString("cache-secs",        "30")
            // Subtitle rendering via mpv internal libass
            MPVLib.setOptionString("sub-auto",          "fuzzy")
            MPVLib.setOptionString("blend-subtitles",   "no")
            MPVLib.setOptionString("sub-ass",           "yes")
            MPVLib.setOptionString("sub-ass-override",  "no")
            MPVLib.setOptionString("sub-font-size",     "40")
            MPVLib.setOptionString("sub-border-size",   "2.5")
            MPVLib.setOptionString("sub-color",         "#FFFFFFFF")
            MPVLib.setOptionString("sub-border-color",  "#FF000000")
            MPVLib.setOptionString("sub-shadow-offset", "1")
            MPVLib.setOptionString("sub-shadow-color",  "#80000000")
            MPVLib.setOptionString("sub-margin-y",      "36")
            MPVLib.setOptionString("video-sync",        "audio")
            MPVLib.setOptionString("interpolation",     "no")

            // init() — starts the mpv core and rendering threads
            MPVLib.init()
        }.onFailure { e ->
            Log.e(TAG, "MPV init failed: ${e.javaClass.simpleName}: ${e.message}", e)
            MPVLib.removeObserver(this)
            _state.update { it.copy(error = "MPV init failed: ${e.message}") }
        }

        if (initOk.isFailure) return

        // Mark initialized AFTER init() returns — this is the gate for
        // MPVView.surfaceCreated() to call attachSurface() safely
        MPVLib.isInitialized.set(true)

        // Observe properties
        listOf(
            "pause"                  to MPVLib.MPV_FORMAT_FLAG,
            "time-pos"               to MPVLib.MPV_FORMAT_DOUBLE,
            "duration"               to MPVLib.MPV_FORMAT_DOUBLE,
            "media-title"            to MPVLib.MPV_FORMAT_STRING,
            "metadata/by-key/title"  to MPVLib.MPV_FORMAT_STRING,
            "track-list"             to MPVLib.MPV_FORMAT_STRING,
            "chapter-list"           to MPVLib.MPV_FORMAT_STRING,
            "video-params/w"         to MPVLib.MPV_FORMAT_INT64,
            "video-params/h"         to MPVLib.MPV_FORMAT_INT64,
            "sid"                    to MPVLib.MPV_FORMAT_INT64,
            "aid"                    to MPVLib.MPV_FORMAT_INT64,
            "speed"                  to MPVLib.MPV_FORMAT_DOUBLE,
            "volume"                 to MPVLib.MPV_FORMAT_DOUBLE,
        ).forEach { (name, fmt) -> MPVLib.observeProperty(name, fmt) }

        _state.update { it.copy(mpvReady = true) }
        Log.i(TAG, "initializePlayer: mpv ready")

        pendingUri?.let { playUri(it); pendingUri = null }
    }

    fun playUri(uri: Uri, startPosition: Long = 0L) {
        if (!MPVLib.isAvailable) return
        if (!_state.value.mpvReady) { pendingUri = uri; return }

        viewModelScope.launch {
            // Scan sibling files FIRST — _folderFiles must be ready before
            // MPV_EVENT_END_FILE fires (auto-next depends on it).
            val folderUris = scanFolderFiles(uri)
            _folderFiles  = folderUris

            // Robust index matching: try toString() first, then by media ID
            val idx = folderUris.indexOfFirst { it.toString() == uri.toString() }
                .takeIf { it >= 0 }
                ?: folderUris.indexOfFirst { extractMediaId(it) == extractMediaId(uri) }
                    .takeIf { it >= 0 }
                ?: 0
            _folderIndex = idx

            _loadAtIndex(idx, uri, startPosition)
        }
    }

    /**
     * Internal: load a file by folder index without re-scanning.
     * Used by playNext / playPrev / auto-next to avoid index drift.
     */
    private fun playAtIndex(idx: Int) {
        val uri = _folderFiles.getOrNull(idx) ?: return
        _folderIndex = idx
        viewModelScope.launch { _loadAtIndex(idx, uri, 0L) }
    }

    /** Core load: resolve title, update state, call mpv. */
    private suspend fun _loadAtIndex(idx: Int, uri: Uri, startMs: Long) {
        val domainItem   = getMediaByUriUseCase(uri)
        val displayTitle = domainItem?.displayName
            ?: resolveDisplayName(uri)   // query DISPLAY_NAME — never use lastPathSegment
            ?: uri.lastPathSegment ?: ""

        currentMediaId = domainItem?.id ?: 0L

        _state.update {
            it.copy(
                mediaTitle    = displayTitle,
                playbackState = it.playbackState.copy(mediaItem = domainItem),
                hasPrev       = idx > 0,
                hasNext       = idx < _folderFiles.lastIndex,
            )
        }

        val path = when (uri.scheme) {
            "content" -> uri.toString()
            "file"    -> uri.path ?: uri.toString()
            else      -> uri.toString()
        }

        _intentionalLoad = true   // guard against END_FILE chain reaction
        MPVLib.loadFile(path, startMs)
        showControlsTemporarily()
        startPositionUpdates()
    }

    /** Query actual filename — never expose raw media IDs as titles.
     *  OpenableColumns.DISPLAY_NAME works for MediaStore, SAF, and Downloads URIs. */
    private suspend fun resolveDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            when (uri.scheme) {
                "file" -> java.io.File(uri.path ?: return@withContext null).name
                "content" -> {
                    // OpenableColumns.DISPLAY_NAME is the universal column — works for
                    // content:// from Files app, Downloads, Gallery, SAF document picker.
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    /** Extract numeric media ID from a MediaStore content URI, or null. */
    private fun extractMediaId(uri: Uri): String? =
        if (uri.scheme == "content") uri.lastPathSegment else null

    /** Play the next video in the same folder (no re-scan, instant index update). */
    fun playNext() {
        val next = _folderIndex + 1
        if (next < _folderFiles.size) playAtIndex(next)
    }

    /** Play the previous video, or restart current if > 3 s in (no re-scan). */
    fun playPrev() {
        if (_state.value.playbackState.currentPosition > 3_000L) {
            MPVLib.seekTo(0)
            return
        }
        val prev = _folderIndex - 1
        if (prev >= 0) playAtIndex(prev)
    }

    /** Stop playback entirely (without navigating away). */
    fun stop() {
        savePosition()
        stopPositionUpdates()
        runCatching { MPVLib.command(arrayOf("stop")) }
        _state.update { it.copy(
            playbackState = it.playbackState.copy(isPlaying = false, isPaused = false),
            isBuffering   = false,
        )}
    }

    /** Toggle auto-next setting. */
    fun toggleAutoNext() = _state.update { it.copy(autoNext = !it.autoNext) }

    /** Toggle hidden files visibility (re-scans on next playUri). */
    fun toggleShowHiddenFiles() = _state.update { it.copy(showHiddenFiles = !it.showHiddenFiles) }

    /** Show/hide the video info overlay. Fetches fresh metadata from mpv when shown. */
    fun toggleVideoInfo() {
        val showing = !_state.value.showVideoInfo
        _state.update { it.copy(showVideoInfo = showing) }
        if (showing) viewModelScope.launch { fetchVideoInfo() }
    }

    private suspend fun fetchVideoInfo() {
        if (!MPVLib.isInitialized.get()) return
        // Resolve current URI for proper filename — mpv's "filename" property returns
        // the raw numeric media ID (e.g. "11038492") for content:// MediaStore URIs.
        val currentUri = _folderFiles.getOrNull(_folderIndex)
            ?: _state.value.playbackState.mediaItem?.uri
        val info = buildMap<String, String> {
            fun mpvStr(prop: String) = runCatching {
                MPVLib.getPropertyString(prop)
            }.getOrNull()?.takeIf { it.isNotBlank() }

            // Use resolved display name (works for MediaStore, SAF, file://)
            // Fall back to mpv filename/no-ext only if URI resolution fails.
            val resolvedName = currentUri?.let { resolveDisplayName(it) }
                ?: mpvStr("filename/no-ext")
                ?: mpvStr("filename")
            resolvedName?.let { put("File", it) }
            mpvStr("path")?.let { put("Path", it) }

            val w = mpvStr("video-params/w"); val h = mpvStr("video-params/h")
            if (w != null && h != null) put("Resolution", "${w}×${h}")

            mpvStr("video-codec")?.let { put("Video Codec", it) }
            mpvStr("video-format")?.let { put("Video Format", it) }
            mpvStr("container-fps")?.let {
                put("FPS", "%.2f".format(it.toDoubleOrNull() ?: 0.0))
            }
            mpvStr("video-bitrate")?.let {
                val kbps = (it.toLongOrNull() ?: 0L) / 1000L
                if (kbps > 0) put("Video Bitrate", "$kbps kbps")
            }
            mpvStr("audio-codec")?.let { put("Audio Codec", it) }
            mpvStr("audio-params/samplerate")?.let { put("Sample Rate", "$it Hz") }
            mpvStr("audio-params/channels")?.let { put("Channels", it) }
            mpvStr("audio-bitrate")?.let {
                val kbps = (it.toLongOrNull() ?: 0L) / 1000L
                if (kbps > 0) put("Audio Bitrate", "$kbps kbps")
            }
            mpvStr("file-size")?.let {
                val mb = (it.toLongOrNull() ?: 0L) / 1_048_576L
                if (mb > 0) put("File Size", "$mb MB")
            }
            mpvStr("file-format")?.let { put("Container", it) }
            mpvStr("duration")?.let {
                val secs = it.toDoubleOrNull()?.toLong() ?: 0L
                put("Duration", "%d:%02d:%02d".format(secs/3600, (secs%3600)/60, secs%60))
            }
            val subCount  = _state.value.subtitleTracks.size
            val audCount  = _state.value.audioTracks.size
            if (subCount > 0)  put("Subtitle Tracks", "$subCount")
            if (audCount > 0)  put("Audio Tracks", "$audCount")
        }
        _state.update { it.copy(videoInfo = info) }
    }

    /** Scan the parent folder (MediaStore or filesystem) for video siblings.
     *  Uses natural (numeric-aware) sort so "2.mkv" comes before "10.mkv". */
    private suspend fun scanFolderFiles(current: Uri): List<Uri> = withContext(Dispatchers.IO) {
        // Read pref fresh from repository each scan so Settings changes take effect immediately
        val showHidden = runCatching { settingsRepository.showHiddenFiles.first() }.getOrDefault(false)
        try {
            when (current.scheme) {
                "content" -> {
                    val bucketId = context.contentResolver.query(
                        current,
                        arrayOf(MediaStore.Video.Media.BUCKET_ID),
                        null, null, null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?: return@withContext listOf(current)

                    // Fetch _ID + DISPLAY_NAME so we can natural-sort in Kotlin
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
                        "${MediaStore.Video.Media.BUCKET_ID} = ?",
                        arrayOf(bucketId),
                        null, // sort in Kotlin for natural order
                    )?.use { c ->
                        val idCol   = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        buildList {
                            while (c.moveToNext()) {
                                val name = c.getString(nameCol) ?: ""
                                if (!showHidden && name.startsWith(".")) continue
                                add(name to ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)))
                            }
                        }
                    }
                    ?.sortedWith(Comparator { a, b -> naturalCompare(a.first, b.first) })
                    ?.map { it.second }
                    ?: listOf(current)
                }
                "file" -> {
                    val parent = java.io.File(current.path ?: return@withContext listOf(current)).parentFile
                        ?: return@withContext listOf(current)
                    val videoExts = setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","ts","3gp")
                    parent.listFiles { f ->
                        f.isFile &&
                        f.extension.lowercase() in videoExts &&
                        (showHidden || !f.name.startsWith("."))
                    }
                    ?.sortedWith(Comparator { a, b -> naturalCompare(a.name, b.name) })
                    ?.map { Uri.fromFile(it) }
                    ?: listOf(current)
                }
                else -> listOf(current)
            }
        } catch (e: Exception) {
            Log.w(TAG, "scanFolderFiles: $e")
            listOf(current)
        }
    }

    /**
     * Natural (numeric-aware) comparator.
     * "2.mkv" < "10.mkv" < "11.mkv" — unlike plain alphabetical which gives
     * "10.mkv" < "11.mkv" < "2.mkv".
     */
    private fun naturalCompare(a: String, b: String): Int {
        var ia = 0; var ib = 0
        while (ia < a.length && ib < b.length) {
            val ca = a[ia]; val cb = b[ib]
            if (ca.isDigit() && cb.isDigit()) {
                // Compare numeric segments by value, then by length
                val na = a.drop(ia).takeWhile { it.isDigit() }
                val nb = b.drop(ib).takeWhile { it.isDigit() }
                val diff = na.trimStart('0').ifEmpty { "0" }
                    .compareTo(nb.trimStart('0').ifEmpty { "0" }, ignoreCase = false)
                    .let { if (it != 0) it else na.length - nb.length }
                if (diff != 0) return diff
                ia += na.length; ib += nb.length
            } else {
                val diff = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (diff != 0) return diff
                ia++; ib++
            }
        }
        return a.length - b.length
    }

    private var _pendingSeekMs: Long = 0L

    // ─── MPVLib.EventObserver ─────────────────────────────────────────────────

    override fun eventProperty(property: String) {
        when (property) {
            "track-list"   -> refreshTracks()
            "chapter-list" -> refreshChapters()
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "video-params/w" -> _state.update { it.copy(videoWidth  = value.toInt()) }
            "video-params/h" -> _state.update { it.copy(videoHeight = value.toInt()) }
            "sid" -> _state.update { it.copy(
                selectedSubtitleTrack = value.toInt(),
                // sid=0 means mpv disabled/cleared subtitles
                subtitleEnabled = value > 0,
            )}
            "aid" -> _state.update { it.copy(selectedAudioTrack    = value.toInt()) }
        }
    }

    // Required: libplayer.so calls this for MPV_FORMAT_DOUBLE properties.
    // Descriptor (Ljava/lang/String;D)V confirmed from binary inspection.
    // Missing this method causes GetStaticMethodID to throw NoSuchMethodError
    // inside init_methods_cache() → create() aborts → app crashes immediately.
    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> _state.update { it.copy(
                playbackState = it.playbackState.copy(currentPosition = (value * 1000).toLong())
            )}
            "duration" -> _state.update { it.copy(
                playbackState = it.playbackState.copy(duration = (value * 1000).toLong())
            )}
            "speed" -> _state.update { it.copy(
                playbackState = it.playbackState.copy(playbackSpeed = value.toFloat())
            )}
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> {
                _state.update { s ->
                    s.copy(playbackState = s.playbackState.copy(
                        isPlaying = !value,
                        isPaused  = value,
                    ))
                }
                if (!value) startPositionUpdates() else stopPositionUpdates()
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "media-title", "metadata/by-key/title" -> {
                if (value.isNotBlank())
                    _state.update { it.copy(mediaTitle = value) }
            }
            "speed" -> {
                value.toDoubleOrNull()?.let { spd ->
                    _state.update { it.copy(
                        playbackState = it.playbackState.copy(playbackSpeed = spd.toFloat())
                    )}
                }
            }
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_FILE_LOADED -> {
                _intentionalLoad = false  // new file fully loaded — END_FILE hereafter = natural end
                _state.update { it.copy(
                    isBuffering = false,
                    playbackState = it.playbackState.copy(isPlaying = true, isPaused = false),
                )}
                if (_pendingSeekMs > 0L) {
                    MPVLib.seekTo(_pendingSeekMs)
                    _pendingSeekMs = 0L
                }
                refreshTracks()
                refreshChapters()
                startPositionUpdates()
            }
            MPVLib.MPV_EVENT_START_FILE -> {
                _state.update { it.copy(isBuffering = true) }
            }
            MPVLib.MPV_EVENT_END_FILE -> {
                stopPositionUpdates()
                _state.update { it.copy(isBuffering = false) }
                // Skip auto-next when WE triggered the file change (_intentionalLoad).
                // MPV_EVENT_END_FILE fires for BOTH natural end AND file-replace.
                if (_intentionalLoad) {
                    _intentionalLoad = false
                } else {
                    savePosition()
                    val s = _state.value
                    if (s.autoNext && s.playbackState.repeatMode != RepeatMode.ONE) {
                        val nextIdx = _folderIndex + 1
                        val wrap    = s.playbackState.repeatMode == RepeatMode.ALL
                        when {
                            nextIdx < _folderFiles.size   -> playAtIndex(nextIdx)
                            wrap && _folderFiles.size > 1 -> playAtIndex(0)
                        }
                    }
                }
            }
            MPVLib.MPV_EVENT_SEEK -> {
                _state.update { it.copy(isBuffering = true) }
            }
            MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                _state.update { it.copy(isBuffering = false) }
                startPositionUpdates()
            }
            MPVLib.MPV_EVENT_IDLE -> {
                stopPositionUpdates()
            }
        }
    }

    // ─── Track parsing ─────────────────────────────────────────────────────────

    private fun refreshTracks() {
        val json = MPVLib.getPropertyString("track-list") ?: return
        val audio   = mutableListOf<TrackInfo>()
        val sub     = mutableListOf<TrackInfo>()
        val video   = mutableListOf<TrackInfo>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o    = arr.getJSONObject(i)
                val type = o.optString("type")
                val id   = o.optInt("id")
                val lang = o.optString("lang", "")
                val title = o.optString("title", "")
                val codec = o.optString("codec", "")
                val sel  = o.optBoolean("selected", false)
                val label = buildString {
                    when (type) {
                        "audio" -> append("Audio ${audio.size + 1}")
                        "sub"   -> append("Sub ${sub.size + 1}")
                        else    -> append("Video ${video.size + 1}")
                    }
                    if (title.isNotBlank()) append(": $title")
                    if (lang.isNotBlank() && lang != "und") append(" [$lang]")
                    if (codec.isNotBlank()) append(" ($codec)")
                }
                val info = TrackInfo(
                    index      = when (type) { "audio" -> audio.size; "sub" -> sub.size; else -> video.size },
                    mpvId      = id,
                    label      = label,
                    language   = lang,
                    codec      = codec,
                    isSelected = sel,
                    type       = type,
                )
                when (type) {
                    "audio" -> audio.add(info)
                    "sub"   -> sub.add(info)
                    "video" -> video.add(info)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshTracks: ${e.message}")
        }
        _state.update { it.copy(
            audioTracks    = audio,
            subtitleTracks = sub,
            videoTracks    = video,
            selectedAudioTrack    = audio.firstOrNull { it.isSelected }?.mpvId ?: 1,
            selectedSubtitleTrack = sub.firstOrNull   { it.isSelected }?.mpvId ?: -1,
            subtitleEnabled = sub.any { it.isSelected },
        )}
    }

    private fun refreshChapters() {
        val json = MPVLib.getPropertyString("chapter-list") ?: return
        val chapters = mutableListOf<Pair<Long, String>>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o    = arr.getJSONObject(i)
                val timeS = o.optDouble("time", -1.0)
                val title = o.optString("title", "Chapter ${i + 1}")
                if (timeS >= 0) chapters.add((timeS * 1000).toLong() to title)
            }
        } catch (_: Exception) {}
        if (chapters.isNotEmpty())
            _state.update { it.copy(chapterMarks = chapters) }
    }

    // ─── Track selection ──────────────────────────────────────────────────────

    fun selectAudioTrack(info: TrackInfo) {
        MPVLib.selectTrack("audio", info.mpvId)
        _state.update { it.copy(selectedAudioTrack = info.mpvId) }
    }

    fun selectSubtitleTrack(info: TrackInfo?) {
        if (info == null) {
            MPVLib.disableTrack("sub")
            _state.update { it.copy(subtitleEnabled = false, selectedSubtitleTrack = -1) }
            return
        }
        MPVLib.selectTrack("sub", info.mpvId)
        _state.update { it.copy(subtitleEnabled = true, selectedSubtitleTrack = info.mpvId) }
    }

    // ─── External subtitle ────────────────────────────────────────────────────

    fun loadExternalSubtitleFromUri(uri: Uri) {
        // mpv handles content:// URIs directly via sub-add
        MPVLib.addSubtitleUri(uri.toString())
        _state.update { it.copy(subtitleEnabled = true) }
    }

    fun enableExternalSubtitle() {
        // Re-enable last subtitle (already loaded via sub-add)
        _state.update { it.copy(subtitleEnabled = true) }
    }

    fun setSubtitleDelay(delayMs: Long) {
        if (MPVLib.isInitialized.get())
            MPVLib.setPropertyDouble("sub-delay", delayMs / 1000.0)
        _state.update { it.copy(subtitleDelay = delayMs) }
    }

    fun setAudioDelay(delayMs: Long) {
        if (MPVLib.isInitialized.get())
            MPVLib.setPropertyDouble("audio-delay", delayMs / 1000.0)
        _state.update { it.copy(audioDelay = delayMs) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _state.update { it.copy(subtitleStyle = style) }
        if (MPVLib.isInitialized.get()) {
            MPVLib.setPropertyDouble("sub-font-size",   style.fontSize.value.toDouble())
            MPVLib.setPropertyDouble("sub-border-size", style.outlineWidth.toDouble())
        }
    }

    fun toggleSubtitle() {
        val newEnabled = !_state.value.subtitleEnabled
        if (newEnabled) {
            val sid = _state.value.selectedSubtitleTrack
            if (sid > 0) MPVLib.selectTrack("sub", sid)
        } else {
            MPVLib.disableTrack("sub")
        }
        _state.update { it.copy(subtitleEnabled = newEnabled) }
    }

    // ─── Playback controls ────────────────────────────────────────────────────

    fun playPause() {
        val paused = _state.value.playbackState.isPaused
        // Optimistic UI — flip state immediately so the icon responds on tap.
        // mpv will confirm via "pause" property callback which will re-sync.
        _state.update { s -> s.copy(playbackState = s.playbackState.copy(
            isPaused  = !paused,
            isPlaying = paused,
        ))}
        MPVLib.pause(!paused)
    }

    fun seekTo(ms: Long) {
        MPVLib.seekTo(ms)
        _state.update { it.copy(playbackState = it.playbackState.copy(currentPosition = ms)) }
    }

    fun seekForward(seconds: Int = 10) {
        val cur = _state.value.playbackState.currentPosition
        val dur = _state.value.playbackState.duration
        MPVLib.seekTo((cur + seconds * 1000L).coerceAtMost(dur))
    }

    fun seekBackward(seconds: Int = 10) {
        val cur = _state.value.playbackState.currentPosition
        MPVLib.seekTo((cur - seconds * 1000L).coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        MPVLib.setSpeed(speed.toDouble())
        _state.update { it.copy(playbackState = it.playbackState.copy(playbackSpeed = speed)) }
    }

    fun toggleRepeatMode() {
        val cur = _state.value.playbackState.repeatMode
        val next = when (cur) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.NONE
        }
        MPVLib.setPropertyString("loop-file", when (next) {
            RepeatMode.ONE -> "inf"
            else           -> "no"
        })
        MPVLib.setPropertyString("loop-playlist", when (next) {
            RepeatMode.ALL -> "inf"
            else           -> "no"
        })
        _state.update { it.copy(playbackState = it.playbackState.copy(repeatMode = next)) }
    }

    fun toggleShuffle() {
        val cur = _state.value.playbackState.shuffleEnabled
        _state.update { it.copy(playbackState = it.playbackState.copy(shuffleEnabled = !cur)) }
    }

    fun cycleAspectRatio() {
        val modes   = AspectRatioMode.values()
        val current = _state.value.aspectRatioMode
        val next    = modes[(modes.indexOf(current) + 1) % modes.size]
        val ratio = when (next) {
            AspectRatioMode.FIT       -> "-1"       // mpv auto aspect
            AspectRatioMode.FILL      -> "no"        // mpv stretch to fill
            AspectRatioMode.CROP      -> "no"        // handled via panscan below
            AspectRatioMode.STRETCH   -> "no"        // no correction
            AspectRatioMode.RATIO_4_3 -> "4:3"
            AspectRatioMode.RATIO_16_9 -> "16:9"
            AspectRatioMode.RATIO_21_9 -> "21:9"
        }
        val panscan = if (next == AspectRatioMode.CROP) "1.0" else "0.0"
        MPVLib.setPropertyString("video-aspect-override", ratio)
        MPVLib.setPropertyString("panscan", panscan)
        _state.update { it.copy(aspectRatioMode = next) }
    }

    fun setOrientationMode(mode: OrientationMode) { _state.update { it.copy(orientationMode = mode) } }
    fun toggleLock()     { _state.update { it.copy(isLocked = !it.isLocked) } }
    fun setBrightness(l: Float) { _state.update { it.copy(brightnessLevel = l.coerceIn(0.01f, 1f)) } }
    fun setVolume(level: Float) {
        val pct = (level * 100f).toInt().coerceIn(0, 100)
        MPVLib.setVolumePct(pct)
        _state.update { it.copy(volumeLevel = level.coerceIn(0f, 1f)) }
    }

    fun toggleControls() {
        val showing = _state.value.showControls
        _state.update { it.copy(showControls = !showing) }
        if (!showing) scheduleHideControls()
    }

    fun showControlsTemporarily() {
        _state.update { it.copy(showControls = true) }
        scheduleHideControls()
    }

    // ─── Panels ───────────────────────────────────────────────────────────────
    fun showSubtitlePanel()      { _state.update { it.copy(showSubtitlePanel = true, showControls = true) } }
    fun hideSubtitlePanel()      { _state.update { it.copy(showSubtitlePanel = false) }; scheduleHideControls() }
    fun showAudioPanel()         { _state.update { it.copy(showAudioPanel = true, showControls = true) } }
    fun hideAudioPanel()         { _state.update { it.copy(showAudioPanel = false) }; scheduleHideControls() }
    fun showSubtitleStyleSheet() { _state.update { it.copy(showSubtitleStyleSheet = true) } }
    fun hideSubtitleStyleSheet() { _state.update { it.copy(showSubtitleStyleSheet = false) } }

    // ─── Position tracking ────────────────────────────────────────────────────

    // Position is updated via eventProperty(String, Double) callbacks for
    // "time-pos" and "duration" (MPV_FORMAT_DOUBLE observed properties).
    // No polling needed — this removes the dual-update instability where
    // both the poller and the callback updated currentPosition simultaneously,
    // causing seek-bar jitter and gesture conflicts.
    private fun startPositionUpdates() { /* driven by mpv property events */ }
    private fun stopPositionUpdates()  { positionJob?.cancel(); positionJob = null }

    private fun scheduleHideControls() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(3500L)
            if (_state.value.playbackState.isPlaying && !_state.value.isLocked) {
                _state.update { it.copy(showControls = false) }
            }
        }
    }

    fun savePosition() {
        val posMs = _state.value.playbackState.currentPosition
        val durMs = _state.value.playbackState.duration
        if (currentMediaId > 0L && posMs > 3000L) {
            viewModelScope.launch {
                updatePlaybackPositionUseCase(currentMediaId, posMs, durMs)
            }
        }
    }

    override fun onCleared() {
        savePosition()
        stopPositionUpdates()
        hideJob?.cancel()
        if (MPVLib.isAvailable) {
            MPVLib.removeObserver(this)
            if (MPVLib.isInitialized.get()) {
                runCatching { MPVLib.command(arrayOf("stop")) }
            }
            MPVLib.destroyMpv()  // safe: guards double-destroy internally
        }
        _state.update { it.copy(mpvReady = false) }
        super.onCleared()
    }

    companion object { private const val TAG = "PlayerViewModel" }
}
