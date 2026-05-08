package com.ryoustream.player.presentation.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.PlaybackState
import com.ryoustream.player.domain.model.RepeatMode
import com.ryoustream.player.domain.usecase.GetMediaByUriUseCase
import com.ryoustream.player.domain.usecase.UpdatePlaybackPositionUseCase
import com.ryoustream.player.service.RyouPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PlayerViewModel
 *
 * Manages ExoPlayer state through MediaController.
 * Handles playback controls, position tracking, subtitle/audio track selection,
 * and gesture-based controls (brightness, volume, seek).
 */
@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMediaByUriUseCase: GetMediaByUriUseCase,
    private val updatePlaybackPositionUseCase: UpdatePlaybackPositionUseCase,
) : ViewModel() {

    // ─── UI State ─────────────────────────────────────────────────────────────
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private val _seekPreviewPosition = MutableStateFlow<Long?>(null)
    val seekPreviewPosition: StateFlow<Long?> = _seekPreviewPosition.asStateFlow()

    private val _brightnessLevel = MutableStateFlow(0.5f)
    val brightnessLevel: StateFlow<Float> = _brightnessLevel.asStateFlow()

    // ─── MediaController ──────────────────────────────────────────────────────
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // ─── Position tracking ────────────────────────────────────────────────────
    private var positionUpdateJob: Job? = null
    private var controlsAutoHideJob: Job? = null
    private var currentMediaId: Long = 0L

    // ─── Player Listener ──────────────────────────────────────────────────────
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                it.copy(
                    isBuffering = state == Player.STATE_BUFFERING,
                    isPlaying = mediaController?.isPlaying == true,
                )
            }
            if (state == Player.STATE_READY) startPositionUpdates()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying, isPaused = !isPlaying) }
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update { it.copy(error = error.message) }
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            item?.let { updateCurrentMedia(it) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playbackState.update {
                it.copy(
                    repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.NONE
                    }
                )
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playbackState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────
    fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, RyouPlaybackService::class.java)
        )
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener(
            {
                mediaController = mediaControllerFuture?.get()
                mediaController?.addListener(playerListener)
            },
            MoreExecutors.directExecutor()
        )
    }

    // ─── Playback Controls ────────────────────────────────────────────────────
    fun playUri(uri: Uri, startPosition: Long = 0L) {
        viewModelScope.launch {
            val domainItem = getMediaByUriUseCase(uri)
            currentMediaId = domainItem?.id ?: 0L

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(domainItem?.title ?: uri.lastPathSegment)
                        .build()
                )
                .build()

            mediaController?.apply {
                setMediaItem(mediaItem, startPosition)
                prepare()
                play()
            }

            _playbackState.update { it.copy(mediaItem = domainItem) }
        }
    }

    fun playPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPosition = positionMs) }
    }

    fun seekForward(seconds: Int = 10) {
        mediaController?.let { player ->
            val newPos = (player.currentPosition + seconds * 1000L)
                .coerceAtMost(player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
        }
    }

    fun seekBackward(seconds: Int = 10) {
        mediaController?.let { player ->
            val newPos = (player.currentPosition - seconds * 1000L).coerceAtLeast(0L)
            player.seekTo(newPos)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleRepeatMode() {
        mediaController?.let { player ->
            val nextMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            player.repeatMode = nextMode
        }
    }

    fun toggleShuffle() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleAspectRatio() {
        val modes = AspectRatioMode.values()
        val current = _aspectRatioMode.value
        val nextIndex = (modes.indexOf(current) + 1) % modes.size
        _aspectRatioMode.value = modes[nextIndex]
    }

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
    }

    fun toggleControls() {
        _showControls.value = !_showControls.value
        if (_showControls.value) scheduleControlsAutoHide()
    }

    fun showControlsTemporarily() {
        _showControls.value = true
        scheduleControlsAutoHide()
    }

    fun setBrightness(level: Float) {
        _brightnessLevel.value = level.coerceIn(0f, 1f)
    }

    // ─── Position tracking ────────────────────────────────────────────────────
    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val player = mediaController ?: break
                _playbackState.update {
                    it.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0L),
                        bufferingPercent = player.bufferedPercentage,
                    )
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun scheduleControlsAutoHide() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = viewModelScope.launch {
            delay(3000L)
            if (_playbackState.value.isPlaying && !_isLocked.value) {
                _showControls.value = false
            }
        }
    }

    private fun updateCurrentMedia(item: MediaItem) {
        // Could enrich with metadata from MediaStore here
    }

    // ─── Save position on pause/destroy ──────────────────────────────────────
    fun saveCurrentPosition() {
        val state = _playbackState.value
        val position = mediaController?.currentPosition ?: return
        val duration = mediaController?.duration ?: 0L
        if (currentMediaId > 0L && position > 0L) {
            viewModelScope.launch {
                updatePlaybackPositionUseCase(currentMediaId, position, duration)
            }
        }
    }

    override fun onCleared() {
        saveCurrentPosition()
        stopPositionUpdates()
        controlsAutoHideJob?.cancel()
        MediaController.releaseFuture(mediaControllerFuture ?: return)
        super.onCleared()
    }
}
