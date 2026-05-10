package com.ryoustream.player.presentation.player

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.PlaybackState
import com.ryoustream.player.domain.model.RepeatMode

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    mediaUri: Uri,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val showControls by viewModel.showControls.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsStateWithLifecycle()
    val brightnessLevel by viewModel.brightnessLevel.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()

    var seekDelta by remember { mutableLongStateOf(0L) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var brightnessDisplay by remember { mutableFloatStateOf(brightnessLevel) }

    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Hide system UI
    DisposableEffect(Unit) {
        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
            WindowInsetsControllerCompat(it.window, it.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.let {
                WindowCompat.setDecorFitsSystemWindows(it.window, true)
                WindowInsetsControllerCompat(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Init player and load media
    LaunchedEffect(mediaUri) {
        viewModel.initializePlayer()
        viewModel.playUri(mediaUri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Single tap = toggle controls, double tap = seek
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { viewModel.toggleControls() },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            if (offset.x < size.width / 2) viewModel.seekBackward(10)
                            else viewModel.seekForward(10)
                            viewModel.showControlsTemporarily()
                        }
                    }
                )
            }
            // Horizontal swipe = seek
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { isDraggingSeek = true; seekDelta = 0L },
                    onDragEnd = {
                        if (isDraggingSeek) {
                            viewModel.seekTo(
                                (playbackState.currentPosition + seekDelta)
                                    .coerceIn(0L, playbackState.duration)
                            )
                        }
                        isDraggingSeek = false; seekDelta = 0L
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        seekDelta += (dragAmount * 150).toLong()
                    }
                )
            }
            // Vertical swipe = brightness (left) / volume (right)
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDraggingVolume = offset.x > size.width / 2
                        isDraggingBrightness = !isDraggingVolume
                    },
                    onDragEnd = { isDraggingVolume = false; isDraggingBrightness = false },
                    onVerticalDrag = { _, dragAmount ->
                        val delta = -dragAmount / 600f
                        if (isDraggingVolume) {
                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                        } else if (isDraggingBrightness) {
                            brightnessDisplay = (brightnessDisplay + delta).coerceIn(0.01f, 1f)
                            viewModel.setBrightness(brightnessDisplay)
                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                screenBrightness = brightnessDisplay
                            }
                        }
                    }
                )
            }
    ) {
        // ── ExoPlayer Surface ──────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                // KEY FIX: Actually attach the ExoPlayer to the PlayerView
                playerView.player = player
                playerView.resizeMode = when (aspectRatioMode) {
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Seek overlay ───────────────────────────────────────────────────────
        AnimatedVisibility(visible = isDraggingSeek, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                ) {
                    val previewPos = (playbackState.currentPosition + seekDelta)
                        .coerceIn(0L, playbackState.duration)
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            if (seekDelta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            null, tint = Color.White, modifier = Modifier.size(32.dp),
                        )
                        Text(
                            MediaItem.formatDuration(previewPos),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (seekDelta >= 0) "+${seekDelta / 1000}s" else "${seekDelta / 1000}s",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // ── Volume indicator ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isDraggingVolume,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            GestureIndicator(
                icon = if (volumeLevel > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                value = volumeLevel,
                label = "${(volumeLevel * 100).toInt()}%",
            )
        }

        // ── Brightness indicator ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = isDraggingBrightness,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            GestureIndicator(
                icon = Icons.Default.Brightness6,
                value = brightnessDisplay,
                label = "${(brightnessDisplay * 100).toInt()}%",
            )
        }

        // ── Buffering spinner ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = playbackState.isBuffering && !isDraggingSeek,
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }

        // ── Controls overlay ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls && !isDraggingSeek,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
        ) {
            PlayerControlsOverlay(
                playbackState = playbackState,
                isLocked = isLocked,
                aspectRatioMode = aspectRatioMode,
                onBack = { viewModel.saveCurrentPosition(); onBack() },
                onPlayPause = viewModel::playPause,
                onSeekForward = { viewModel.seekForward(10) },
                onSeekBackward = { viewModel.seekBackward(10) },
                onSeekTo = viewModel::seekTo,
                onToggleLock = viewModel::toggleLock,
                onToggleRepeat = viewModel::toggleRepeatMode,
                onToggleShuffle = viewModel::toggleShuffle,
                onToggleAspect = viewModel::toggleAspectRatio,
                onSpeedChange = viewModel::setPlaybackSpeed,
            )
        }

        // ── Lock indicator ─────────────────────────────────────────────────────
        if (isLocked) {
            LockOverlay(onUnlock = viewModel::toggleLock)
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    playbackState: PlaybackState,
    isLocked: Boolean,
    aspectRatioMode: AspectRatioMode,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleAspect: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)

    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
        )
        // Bottom gradient
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
        )

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                text = playbackState.mediaItem?.displayName ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            TextButton(onClick = onToggleAspect) {
                Text(aspectRatioMode.label, color = Color.White, fontSize = 12.sp)
            }
            Box {
                TextButton(onClick = { showSpeedMenu = true }) {
                    Text("${playbackState.playbackSpeed}x", color = Color.White, fontSize = 12.sp)
                }
                DropdownMenu(showSpeedMenu, { showSpeedMenu = false }) {
                    speeds.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            leadingIcon = { if (playbackState.playbackSpeed == speed) Icon(Icons.Default.Check, null) },
                            onClick = { onSpeedChange(speed); showSpeedMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = onToggleLock) {
                Icon(
                    if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    "Lock", tint = Color.White,
                )
            }
        }

        // Center controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSeekBackward, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Replay10, "Seek back", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FloatingActionButton(
                onClick = onPlayPause,
                modifier = Modifier.size(60.dp),
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            ) {
                Icon(
                    if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playbackState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp),
                )
            }
            IconButton(onClick = onSeekForward, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Forward10, "Seek forward", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // Bottom bar
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(MediaItem.formatDuration(playbackState.currentPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(MediaItem.formatDuration(playbackState.duration), color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = playbackState.progress,
                onValueChange = { onSeekTo((it * playbackState.duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(0.3f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    IconButton(onClick = onToggleRepeat) {
                        Icon(
                            when (playbackState.repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            "Repeat",
                            tint = when (playbackState.repeatMode) {
                                RepeatMode.NONE -> Color.White.copy(0.5f)
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    IconButton(onClick = onToggleShuffle) {
                        Icon(Icons.Default.Shuffle, "Shuffle",
                            tint = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(0.5f))
                    }
                }
                Row {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White.copy(0.8f))
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Tune, "Audio tracks", tint = Color.White.copy(0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    label: String,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.65f)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            LinearProgressIndicator(
                progress = { value },
                modifier = Modifier.width(4.dp).height(80.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White, trackColor = Color.White.copy(0.3f),
            )
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        IconButton(
            onClick = onUnlock,
            modifier = Modifier.padding(start = 16.dp)
                .background(Color.Black.copy(0.5f), CircleShape),
        ) {
            Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
        }
    }
}
