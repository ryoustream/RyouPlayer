package com.ryoustream.player.presentation.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.PlaybackState
import com.ryoustream.player.domain.model.RepeatMode
import com.ryoustream.player.domain.model.SubtitleStyle
import com.ryoustream.player.domain.model.AssCue
import com.ryoustream.player.domain.model.AssParser
import com.ryoustream.player.domain.usecase.GetMediaByUriUseCase
import com.ryoustream.player.domain.usecase.UpdatePlaybackPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Orientation Mode ─────────────────────────────────────────────────────────
enum class OrientationMode(val label: String) {
    AUTO("Auto Rotate"),                          // sensor-based auto
    SENSOR_VIDEO("Video Orientation"),            // lock to video aspect ratio (portrait/landscape based on content)
    LOCK_PORTRAIT("Lock Portrait"),
    LOCK_PORTRAIT_REVERSE("Lock Portrait Reverse"),
    LOCK_LANDSCAPE("Lock Landscape"),
    LOCK_LANDSCAPE_REVERSE("Lock Landscape Reverse"),
}

// ─── Track Info ───────────────────────────────────────────────────────────────
data class TrackInfo(
    val index: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String = "",
    val isSelected: Boolean = false,
)

// ─── Player UI State ──────────────────────────────────────────────────────────
data class PlayerUiState(
    val playbackState: PlaybackState          = PlaybackState(),
    val showControls: Boolean                 = true,
    val isLocked: Boolean                     = false,
    val aspectRatioMode: AspectRatioMode      = AspectRatioMode.FIT,
    val orientationMode: OrientationMode      = OrientationMode.SENSOR_VIDEO,
    val brightnessLevel: Float                = -1f, // -1 = system default
    val volumeLevel: Float                    = 1f,
    val videoWidth: Int                       = 0,
    val videoHeight: Int                      = 0,
    // Subtitle
    val subtitleEnabled: Boolean              = true,
    val subtitleCues: List<AssCue>            = emptyList(),
    val subtitleStyle: SubtitleStyle          = SubtitleStyle(),
    val subtitleTracks: List<TrackInfo>       = emptyList(),
    val selectedSubtitleTrack: Int            = -1,
    val subtitleDelay: Long                   = 0L,
    // Audio
    val audioTracks: List<TrackInfo>          = emptyList(),
    val selectedAudioTrack: Int               = 0,
    // Video
    val videoTracks: List<TrackInfo>          = emptyList(),
    // Panels
    val showSubtitlePanel: Boolean            = false,
    val showAudioPanel: Boolean               = false,
    val showSpeedMenu: Boolean                = false,
    val showSubtitleStyleSheet: Boolean       = false,
    // MKV Metadata
    val chapterMarks: List<Pair<Long, String>> = emptyList(),
    val mediaTitle: String                    = "",
    val error: String?                        = null,
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMediaByUriUseCase: GetMediaByUriUseCase,
    private val updatePlaybackPositionUseCase: UpdatePlaybackPositionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // Compat accessors for PlayerScreen
    val playbackState get() = _state.map { it.playbackState }
    val showControls  get() = _state.map { it.showControls }
    val isLocked      get() = _state.map { it.isLocked }
    val aspectRatioMode get() = _state.map { it.aspectRatioMode }
    val brightnessLevel get() = _state.map { it.brightnessLevel }

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private var positionJob: Job? = null
    private var hideJob: Job? = null
    private var currentMediaId = 0L
    private var currentUri: Uri? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayback()
            if (playbackState == Player.STATE_READY) startPositionUpdates()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayback()
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.message) }
        }
        override fun onTracksChanged(tracks: Tracks) {
            loadTracks(tracks)
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _state.update { it.copy(
                mediaTitle = mediaMetadata.title?.toString() ?: ""
            )}
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            // Read chapters from timeline (MKV chapters, MP4 chapters)
            if (timeline.isEmpty) return
            val window = androidx.media3.common.Timeline.Window()
            timeline.getWindow(0, window)
            val chapters = mutableListOf<Pair<Long, String>>()
            try {
                // ExoPlayer exposes chapters as timeline periods in some formats
                for (i in 0 until timeline.periodCount) {
                    val period = androidx.media3.common.Timeline.Period()
                    timeline.getPeriod(i, period)
                    val posMs = period.positionInWindowMs
                    val name  = period.id?.toString() ?: "Chapter ${i + 1}"
                    if (posMs >= 0) chapters.add(posMs to name)
                }
            } catch (_: Exception) {}
            if (chapters.isNotEmpty()) {
                _state.update { it.copy(chapterMarks = chapters) }
            }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            updatePlayback()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updatePlayback()
        }
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _state.update { it.copy(
                    videoWidth  = videoSize.width,
                    videoHeight = videoSize.height,
                )}
            }
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun initializePlayer() {
        if (_player.value != null) return
        val exo = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also {
                it.addListener(playerListener)
                // Ensure volume is at max on init
                it.volume = 1f
            }
        _player.value = exo
    }

    fun playUri(uri: Uri, startPosition: Long = 0L) {
        viewModelScope.launch {
            currentUri = uri
            val domainItem = getMediaByUriUseCase(uri)
            currentMediaId = domainItem?.id ?: 0L

            val mediaItem = MediaItem.fromUri(uri)
            _player.value?.apply {
                setMediaItem(mediaItem, startPosition)
                prepare()
                playWhenReady = true
            }

            val state = _state.value.playbackState.copy(
                mediaItem = domainItem,
            )
            _state.update { it.copy(
                playbackState = state,
                mediaTitle    = domainItem?.displayName ?: uri.lastPathSegment ?: "",
            )}
            showControlsTemporarily()

            // Try load external subtitles
            domainItem?.path?.let { path ->
                loadExternalSubtitle(uri, path)
            }
        }
    }

    // ─── Tracks ───────────────────────────────────────────────────────────────

    private fun loadTracks(tracks: Tracks) {
        val audioTracks   = mutableListOf<TrackInfo>()
        val subtitleTracks= mutableListOf<TrackInfo>()
        val videoTracks   = mutableListOf<TrackInfo>()

        tracks.groups.forEachIndexed { gi, group ->
            val type = group.type
            for (ti in 0 until group.length) {
                val format   = group.getTrackFormat(ti)
                val selected = group.isTrackSelected(ti)
                val lang     = format.language ?: ""
                val label    = format.label ?: when (type) {
                    C.TRACK_TYPE_AUDIO    -> "Audio ${audioTracks.size + 1}"
                    C.TRACK_TYPE_TEXT     -> "Sub ${subtitleTracks.size + 1}"
                    C.TRACK_TYPE_VIDEO    -> "Video ${videoTracks.size + 1}"
                    else -> "Track"
                }
                val info = TrackInfo(
                    index      = when(type) {
                        C.TRACK_TYPE_AUDIO -> audioTracks.size
                        C.TRACK_TYPE_TEXT  -> subtitleTracks.size
                        else               -> videoTracks.size
                    },
                    groupIndex = gi, trackIndex = ti,
                    label = "$label${if (lang.isNotEmpty()) " [$lang]" else ""}",
                    language  = lang,
                    isSelected = selected,
                )
                when (type) {
                    C.TRACK_TYPE_AUDIO -> audioTracks.add(info)
                    C.TRACK_TYPE_TEXT  -> subtitleTracks.add(info)
                    C.TRACK_TYPE_VIDEO -> videoTracks.add(info)
                }
            }
        }
        _state.update { it.copy(
            audioTracks    = audioTracks,
            subtitleTracks = subtitleTracks,
            videoTracks    = videoTracks,
            selectedAudioTrack   = audioTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0),
            selectedSubtitleTrack= subtitleTracks.indexOfFirst { it.isSelected },
        )}
    }

    fun selectAudioTrack(info: TrackInfo) {
        val player = _player.value ?: return
        val group  = player.currentTracks.groups.getOrNull(info.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(info.trackIndex))
            )
            .build()
        if (player.volume == 0f) player.volume = 1f
        _state.update { it.copy(
            selectedAudioTrack = info.index,
            audioTracks = it.audioTracks.map { t -> t.copy(isSelected = t.index == info.index) }
        )}
    }

    fun selectSubtitleTrack(info: TrackInfo?) {
        val player = _player.value ?: return
        if (info == null) {
            // Disable ALL subtitles
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
                .build()
            _state.update { it.copy(
                subtitleEnabled       = false,
                selectedSubtitleTrack = -1,
                subtitleCues          = emptyList(),
                subtitleTracks = it.subtitleTracks.map { t -> t.copy(isSelected = false) }
            )}
            return
        }
        val group = player.currentTracks.groups.getOrNull(info.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setDisabledTrackTypes(emptySet())
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(info.trackIndex))
            )
            .build()
        _state.update { it.copy(
            subtitleEnabled       = true,
            selectedSubtitleTrack = info.index,
            subtitleCues          = emptyList(), // clear external cues when embedded selected
            subtitleTracks = it.subtitleTracks.map { t -> t.copy(isSelected = t.index == info.index) }
        )}
    }

    // ─── External Subtitle Loading ────────────────────────────────────────────

    private fun loadExternalSubtitle(videoUri: Uri, videoPath: String) {
        viewModelScope.launch {
            val basePath = videoPath.substringBeforeLast(".")
            val extensions = listOf(".ass", ".ssa", ".srt", ".vtt")
            for (ext in extensions) {
                val subFile = java.io.File(basePath + ext)
                if (subFile.exists()) {
                    try {
                        val content = subFile.readText()
                        val cues = when (ext.lowercase()) {
                            ".ass", ".ssa" -> AssParser.parse(content)
                            ".srt"         -> AssParser.parseSrt(content)
                            else           -> emptyList()
                        }
                        if (cues.isNotEmpty()) {
                            _state.update { it.copy(subtitleCues = cues, subtitleEnabled = true) }
                            break
                        }
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }

    fun loadExternalSubtitleFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@launch
                val name = uri.lastPathSegment ?: ""
                val cues = when {
                    name.endsWith(".ass", true) || name.endsWith(".ssa", true) ->
                        AssParser.parse(content)
                    name.endsWith(".srt", true) ->
                        AssParser.parseSrt(content)
                    else -> AssParser.parse(content).ifEmpty { AssParser.parseSrt(content) }
                }
                _state.update { it.copy(subtitleCues = cues, subtitleEnabled = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to load subtitle: ${e.message}") }
            }
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        _state.update { it.copy(subtitleDelay = delayMs) }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _state.update { it.copy(subtitleStyle = style) }
    }

    fun toggleSubtitle() {
        _state.update { it.copy(subtitleEnabled = !it.subtitleEnabled) }
    }

    // Switch to external subtitle cues (disable embedded track selection)
    fun enableExternalSubtitle() {
        val player = _player.value
        // Disable embedded text tracks so they don't overlap
        player?.trackSelectionParameters = player?.trackSelectionParameters
            ?.buildUpon()
            ?.setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
            ?.build() ?: return
        _state.update { it.copy(
            subtitleEnabled       = true,
            selectedSubtitleTrack = -1,
            subtitleTracks        = it.subtitleTracks.map { t -> t.copy(isSelected = false) }
        )}
    }

    // ─── Playback Controls ────────────────────────────────────────────────────

    fun playPause() {
        _player.value?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(ms: Long) {
        _player.value?.seekTo(ms)
        _state.update { it.copy(playbackState = it.playbackState.copy(currentPosition = ms)) }
    }

    fun seekForward(seconds: Int = 10) {
        _player.value?.let {
            it.seekTo((it.currentPosition + seconds * 1000L).coerceAtMost(it.duration.coerceAtLeast(0)))
        }
    }

    fun seekBackward(seconds: Int = 10) {
        _player.value?.let {
            it.seekTo((it.currentPosition - seconds * 1000L).coerceAtLeast(0))
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _player.value?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackState = it.playbackState.copy(playbackSpeed = speed)) }
    }

    fun toggleRepeatMode() {
        _player.value?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleShuffle() { _player.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleAspectRatio() {
        val modes   = AspectRatioMode.values()
        val current = _state.value.aspectRatioMode
        _state.update { it.copy(aspectRatioMode = modes[(modes.indexOf(current) + 1) % modes.size]) }
    }

    fun setOrientationMode(mode: OrientationMode) { _state.update { it.copy(orientationMode = mode) } }

    fun toggleLock()      { _state.update { it.copy(isLocked = !it.isLocked) } }
    fun toggleControls()  {
        val showing = _state.value.showControls
        _state.update { it.copy(showControls = !showing) }
        if (!showing) scheduleHideControls()
    }

    fun showControlsTemporarily() {
        _state.update { it.copy(showControls = true) }
        scheduleHideControls()
    }

    fun setBrightness(level: Float) { _state.update { it.copy(brightnessLevel = level.coerceIn(0.01f, 1f)) } }
    fun setVolume(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        _player.value?.volume = clamped
        _state.update { it.copy(volumeLevel = clamped) }
    }

    // ─── Panels ───────────────────────────────────────────────────────────────
    fun showSubtitlePanel()     { _state.update { it.copy(showSubtitlePanel = true, showControls = true) } }
    fun hideSubtitlePanel()     { _state.update { it.copy(showSubtitlePanel = false) }; scheduleHideControls() }
    fun showAudioPanel()        { _state.update { it.copy(showAudioPanel = true, showControls = true) } }
    fun hideAudioPanel()        { _state.update { it.copy(showAudioPanel = false) }; scheduleHideControls() }
    fun showSubtitleStyleSheet(){ _state.update { it.copy(showSubtitleStyleSheet = true) } }
    fun hideSubtitleStyleSheet(){ _state.update { it.copy(showSubtitleStyleSheet = false) } }
    fun showSpeedMenu()         { _state.update { it.copy(showSpeedMenu = true) } }
    fun hideSpeedMenu()         { _state.update { it.copy(showSpeedMenu = false) } }

    // ─── Position tracking ────────────────────────────────────────────────────

    private fun updatePlayback() {
        val p = _player.value ?: return
        _state.update { s ->
            s.copy(playbackState = s.playbackState.copy(
                isPlaying      = p.isPlaying,
                isPaused       = !p.isPlaying,
                isBuffering    = p.playbackState == Player.STATE_BUFFERING,
                repeatMode     = when (p.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.NONE
                },
                shuffleEnabled = p.shuffleModeEnabled,
            ))
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionJob = viewModelScope.launch {
            while (isActive) {
                _player.value?.let { p ->
                    _state.update { s ->
                        s.copy(playbackState = s.playbackState.copy(
                            currentPosition = p.currentPosition,
                            duration        = p.duration.coerceAtLeast(0),
                            bufferingPercent= p.bufferedPercentage,
                        ))
                    }
                }
                delay(500L)
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
        val pos      = _player.value?.currentPosition ?: return
        val duration = _player.value?.duration ?: 0L
        if (currentMediaId > 0L && pos > 3000L) {
            viewModelScope.launch {
                updatePlaybackPositionUseCase(currentMediaId, pos, duration)
            }
        }
    }

    override fun onCleared() {
        savePosition()
        stopPositionUpdates()
        hideJob?.cancel()
        _player.value?.apply { removeListener(playerListener); release() }
        _player.value = null
        super.onCleared()
    }
}
