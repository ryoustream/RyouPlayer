package com.ryoustream.player.presentation.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getMediaByUriUseCase: GetMediaByUriUseCase,
    private val updatePlaybackPositionUseCase: UpdatePlaybackPositionUseCase,
) : ViewModel() {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _showControls = MutableStateFlow(true)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private val _brightnessLevel = MutableStateFlow(0.5f)
    val brightnessLevel: StateFlow<Float> = _brightnessLevel.asStateFlow()

    // Expose the ExoPlayer directly so PlayerView can attach to it
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var controlsAutoHideJob: Job? = null
    private var currentMediaId: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                it.copy(
                    isBuffering = state == Player.STATE_BUFFERING,
                    isPlaying = _player.value?.isPlaying == true,
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
    }

    fun initializePlayer() {
        if (_player.value != null) return
        val exoPlayer = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { it.addListener(playerListener) }
        _player.value = exoPlayer
    }

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

            _player.value?.apply {
                setMediaItem(mediaItem, startPosition)
                prepare()
                playWhenReady = true
            }

            _playbackState.update { it.copy(mediaItem = domainItem) }
            showControlsTemporarily()
        }
    }

    fun playPause() {
        _player.value?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        _player.value?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPosition = positionMs) }
    }

    fun seekForward(seconds: Int = 10) {
        _player.value?.let {
            val newPos = (it.currentPosition + seconds * 1000L).coerceAtMost(
                it.duration.coerceAtLeast(0L)
            )
            it.seekTo(newPos)
        }
    }

    fun seekBackward(seconds: Int = 10) {
        _player.value?.let {
            val newPos = (it.currentPosition - seconds * 1000L).coerceAtLeast(0L)
            it.seekTo(newPos)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _player.value?.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(playbackSpeed = speed) }
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

    fun toggleShuffle() {
        _player.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleAspectRatio() {
        val modes = AspectRatioMode.values()
        val current = _aspectRatioMode.value
        _aspectRatioMode.value = modes[(modes.indexOf(current) + 1) % modes.size]
    }

    fun toggleLock() { _isLocked.value = !_isLocked.value }

    fun toggleControls() {
        _showControls.value = !_showControls.value
        if (_showControls.value) scheduleControlsAutoHide()
    }

    fun showControlsTemporarily() {
        _showControls.value = true
        scheduleControlsAutoHide()
    }

    fun setBrightness(level: Float) { _brightnessLevel.value = level.coerceIn(0f, 1f) }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                _player.value?.let { p ->
                    _playbackState.update {
                        it.copy(
                            currentPosition = p.currentPosition,
                            duration = p.duration.coerceAtLeast(0L),
                            bufferingPercent = p.bufferedPercentage,
                        )
                    }
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
            delay(3500L)
            if (_playbackState.value.isPlaying && !_isLocked.value) {
                _showControls.value = false
            }
        }
    }

    fun saveCurrentPosition() {
        val position = _player.value?.currentPosition ?: return
        val duration = _player.value?.duration ?: 0L
        if (currentMediaId > 0L && position > 5000L) {
            viewModelScope.launch {
                updatePlaybackPositionUseCase(currentMediaId, position, duration)
            }
        }
    }

    override fun onCleared() {
        saveCurrentPosition()
        stopPositionUpdates()
        controlsAutoHideJob?.cancel()
        _player.value?.apply {
            removeListener(playerListener)
            release()
        }
        _player.value = null
        super.onCleared()
    }
}
