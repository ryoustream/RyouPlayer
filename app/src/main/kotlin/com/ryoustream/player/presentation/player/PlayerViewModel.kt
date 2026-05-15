package com.ryoustream.player.presentation.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.PlaybackState
import com.ryoustream.player.domain.model.RepeatMode
import com.ryoustream.player.domain.model.SubtitleStyle
import com.ryoustream.player.domain.model.AssCue
import com.ryoustream.player.domain.usecase.GetMediaByUriUseCase
import com.ryoustream.player.domain.usecase.UpdatePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.addSubtitleUri
import `is`.xyz.mpv.disableTrack
import `is`.xyz.mpv.getDurationMs
import `is`.xyz.mpv.getTimeMs
import `is`.xyz.mpv.pause
import `is`.xyz.mpv.seekTo
import `is`.xyz.mpv.selectTrack
import `is`.xyz.mpv.setSpeed
import `is`.xyz.mpv.setVolumePct
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    // mpv-specific
    val mpvReady: Boolean                    = false,
    val isBuffering: Boolean                 = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMediaByUriUseCase: GetMediaByUriUseCase,
    private val updatePlaybackPositionUseCase: UpdatePlaybackPositionUseCase,
) : ViewModel(), MPVLib.EventObserver {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var positionJob: Job? = null
    private var hideJob: Job? = null
    private var currentMediaId = 0L
    private var pendingUri: Uri? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun initializePlayer() {
        // tryLoad() does System.loadLibrary("mpv") — must succeed before ANY JNI call
        if (!MPVLib.tryLoad()) {
            _state.update { it.copy(error = "libmpv.so not found.\nRun scripts/download_mpv_libs.sh and rebuild.") }
            Log.e(TAG, "MPVLib not available — libmpv.so missing from jniLibs")
            return
        }
        MPVLib.addObserver(this)

        // create() is the direct JNI external — symbol: Java_is_xyz_mpv_MPVLib_create
        // MUST pass nullable Context as first arg (exactly as mpv-android native expects)
        MPVLib.create(
            context.applicationContext,
            context.filesDir.absolutePath,
            context.cacheDir.absolutePath,
            "warn",
        )

        // ── Core mpv options (set before init) ─────────────────────────────
        MPVLib.setOptionString("vo",               "gpu")
        MPVLib.setOptionString("gpu-context",      "android")
        MPVLib.setOptionString("opengl-es",        "yes")
        MPVLib.setOptionString("hwdec",            "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs",     "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao",               "audiotrack,opensles")
        MPVLib.setOptionString("tls-verify",       "no")
        MPVLib.setOptionString("demuxer-max-bytes", "128MiB")
        MPVLib.setOptionString("demuxer-readahead-secs", "10")
        MPVLib.setOptionString("cache",            "yes")
        MPVLib.setOptionString("cache-secs",       "30")
        // ── Subtitle — mpv renders via libass onto the video surface ────────
        MPVLib.setOptionString("sub-auto",         "fuzzy")
        MPVLib.setOptionString("blend-subtitles",  "no")
        MPVLib.setOptionString("sub-ass",          "yes")
        MPVLib.setOptionString("sub-ass-override", "no")   // respect embedded .ass styles
        MPVLib.setOptionString("sub-font-size",    "40")
        MPVLib.setOptionString("sub-border-size",  "2.5")
        MPVLib.setOptionString("sub-color",        "#FFFFFFFF")
        MPVLib.setOptionString("sub-border-color", "#FF000000")
        MPVLib.setOptionString("sub-shadow-offset","1")
        MPVLib.setOptionString("sub-shadow-color", "#80000000")
        MPVLib.setOptionString("sub-margin-y",     "36")
        // ── Video quality ──────────────────────────────────────────────────
        MPVLib.setOptionString("video-sync",       "audio")
        MPVLib.setOptionString("interpolation",    "no")

        MPVLib.init()

        // Observe key properties
        listOf(
            "pause"             to MPVLib.MPV_FORMAT_FLAG,
            "time-pos"          to MPVLib.MPV_FORMAT_DOUBLE,
            "duration"          to MPVLib.MPV_FORMAT_DOUBLE,
            "media-title"       to MPVLib.MPV_FORMAT_STRING,
            "metadata/by-key/title" to MPVLib.MPV_FORMAT_STRING,
            "track-list"        to MPVLib.MPV_FORMAT_STRING,
            "chapter-list"      to MPVLib.MPV_FORMAT_STRING,
            "video-params/w"    to MPVLib.MPV_FORMAT_INT64,
            "video-params/h"    to MPVLib.MPV_FORMAT_INT64,
            "sid"               to MPVLib.MPV_FORMAT_INT64,
            "aid"               to MPVLib.MPV_FORMAT_INT64,
            "speed"             to MPVLib.MPV_FORMAT_DOUBLE,
            "volume"            to MPVLib.MPV_FORMAT_DOUBLE,
            "demuxer-cache-state" to MPVLib.MPV_FORMAT_NONE,
        ).forEach { (name, fmt) -> MPVLib.observeProperty(name, fmt) }

        _state.update { it.copy(mpvReady = true) }

        // If a URI was requested before init completed, play it now
        pendingUri?.let { playUri(it); pendingUri = null }
    }

    fun playUri(uri: Uri, startPosition: Long = 0L) {
        if (!MPVLib.isAvailable) return
        if (!_state.value.mpvReady) { pendingUri = uri; return }

        viewModelScope.launch {
            val domainItem = getMediaByUriUseCase(uri)
            currentMediaId = domainItem?.id ?: 0L

            _state.update {
                it.copy(
                    mediaTitle    = domainItem?.displayName ?: uri.lastPathSegment ?: "",
                    playbackState = it.playbackState.copy(mediaItem = domainItem),
                )
            }

            val path = when (uri.scheme) {
                "content" -> uri.toString()   // mpv handles content:// URIs natively
                "file"    -> uri.path ?: uri.toString()
                else      -> uri.toString()   // http/rtsp/hls/etc.
            }

            MPVLib.loadFile(path, startPosition)
            showControlsTemporarily()
            startPositionUpdates()
        }
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
            "sid" -> _state.update { it.copy(selectedSubtitleTrack = value.toInt()) }
            "aid" -> _state.update { it.copy(selectedAudioTrack    = value.toInt()) }
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
                _state.update { it.copy(isBuffering = false) }
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
                savePosition()
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
        MPVLib.setPropertyDouble("sub-delay", delayMs / 1000.0)
        _state.update { it.copy(subtitleDelay = delayMs) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _state.update { it.copy(subtitleStyle = style) }
        // Apply to mpv as well (these affect external .srt via mpv's renderer)
        MPVLib.setPropertyDouble("sub-font-size",    style.fontSize.value.toDouble())
        MPVLib.setPropertyDouble("sub-border-size",  style.outlineWidth.toDouble())
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

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val posMs = MPVLib.getTimeMs()
                val durMs = MPVLib.getDurationMs()
                _state.update { s ->
                    s.copy(playbackState = s.playbackState.copy(
                        currentPosition = posMs,
                        duration        = durMs,
                    ))
                }
                delay(200L)
            }
        }
    }

    private fun stopPositionUpdates() { positionJob?.cancel(); positionJob = null }

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
            runCatching { MPVLib.command(arrayOf("stop")) }
            runCatching { MPVLib.destroy() }
        }
        super.onCleared()
    }

    companion object { private const val TAG = "PlayerViewModel" }
}
