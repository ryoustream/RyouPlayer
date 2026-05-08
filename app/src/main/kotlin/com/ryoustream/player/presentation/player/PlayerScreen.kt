package com.ryoustream.player.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import kotlin.math.abs

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
    val seekPreview by viewModel.seekPreviewPosition.collectAsStateWithLifecycle()

    // Gesture state
    var isDraggingSeek by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }
    var volumeIndicator by remember { mutableFloatStateOf(0.5f) }
    var brightnessIndicator by remember { mutableFloatStateOf(brightnessLevel) }
    var seekDelta by remember { mutableLongStateOf(0L) }

    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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

    // Init player + load media
    LaunchedEffect(mediaUri) {
        viewModel.initializeController()
        viewModel.playUri(mediaUri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { viewModel.toggleControls() },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            if (offset.x < size.width / 2) viewModel.seekBackward(10)
                            else viewModel.seekForward(10)
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { isDraggingSeek = true },
                    onDragEnd = {
                        if (isDraggingSeek) {
                            viewModel.seekTo(
                                (playbackState.currentPosition + seekDelta)
                                    .coerceIn(0L, playbackState.duration)
                            )
                        }
                        isDraggingSeek = false
                        seekDelta = 0L
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        seekDelta += (dragAmount * 200).toLong()
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDraggingVolume = offset.x > size.width / 2
                        isDraggingBrightness = offset.x <= size.width / 2
                    },
                    onDragEnd = {
                        isDraggingVolume = false
                        isDraggingBrightness = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        val delta = -dragAmount / 800f
                        if (isDraggingVolume) {
                            volumeIndicator = (volumeIndicator + delta).coerceIn(0f, 1f)
                        } else if (isDraggingBrightness) {
                            brightnessIndicator = (brightnessIndicator + delta).coerceIn(0f, 1f)
                            viewModel.setBrightness(brightnessIndicator)
                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                screenBrightness = brightnessIndicator
                            }
                        }
                    }
                )
            }
    ) {
        // ── ExoPlayer Surface ────────────────────────────────────────────────
        PlayerSurface(
            viewModel = viewModel,
            aspectRatioMode = aspectRatioMode,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Seek overlay gradient ────────────────────────────────────────────
        if (isDraggingSeek) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    val previewPos = (playbackState.currentPosition + seekDelta)
                        .coerceIn(0L, playbackState.duration)
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = if (seekDelta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = MediaItem.formatDuration(previewPos),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (seekDelta >= 0) "+${seekDelta / 1000}s" else "${seekDelta / 1000}s",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // ── Volume indicator ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isDraggingVolume,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GestureIndicator(
                icon = if (volumeIndicator > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                value = volumeIndicator,
                label = "${(volumeIndicator * 100).toInt()}%",
            )
        }

        // ── Brightness indicator ────────────────────────────────────────────
        AnimatedVisibility(
            visible = isDraggingBrightness,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GestureIndicator(
                icon = Icons.Default.Brightness6,
                value = brightnessIndicator,
                label = "${(brightnessIndicator * 100).toInt()}%",
            )
        }

        // ── Player Controls ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
        ) {
            PlayerControlsOverlay(
                playbackState = playbackState,
                isLocked = isLocked,
                aspectRatioMode = aspectRatioMode,
                onBack = {
                    viewModel.saveCurrentPosition()
                    onBack()
                },
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

        // ── Lock overlay ────────────────────────────────────────────────────
        if (isLocked) {
            LockOverlay(onUnlock = viewModel::toggleLock)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(
    viewModel: PlayerViewModel,
    aspectRatioMode: AspectRatioMode,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        update = { playerView ->
            playerView.resizeMode = when (aspectRatioMode) {
                AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
    )
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
        // Top gradient + bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )
        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // ── Top Bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = playbackState.mediaItem?.displayName ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            // Aspect ratio
            TextButton(onClick = onToggleAspect) {
                Text(aspectRatioMode.label, color = Color.White, fontSize = 12.sp)
            }
            // Speed
            Box {
                TextButton(onClick = { showSpeedMenu = true }) {
                    Text("${playbackState.playbackSpeed}x", color = Color.White, fontSize = 12.sp)
                }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                    speeds.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            leadingIcon = {
                                if (playbackState.playbackSpeed == speed)
                                    Icon(Icons.Default.Check, null)
                            },
                            onClick = { onSpeedChange(speed); showSpeedMenu = false }
                        )
                    }
                }
            }
            // Lock
            IconButton(onClick = onToggleLock) {
                Icon(
                    if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock",
                    tint = Color.White,
                )
            }
        }

        // ── Center Controls ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Replay 10
            IconButton(onClick = onSeekBackward, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = "Seek back 10s",
                    tint = Color.White, modifier = Modifier.size(36.dp))
            }
            // Play/Pause
            FloatingActionButton(
                onClick = onPlayPause,
                modifier = Modifier.size(64.dp),
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
            ) {
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            // Forward 10
            IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = "Seek forward 10s",
                    tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // ── Bottom Bar ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = MediaItem.formatDuration(playbackState.currentPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = MediaItem.formatDuration(playbackState.duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Seekbar
            Slider(
                value = playbackState.progress,
                onValueChange = { progress ->
                    onSeekTo((progress * playbackState.duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            // Bottom actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (playbackState.repeatMode) {
                            RepeatMode.NONE -> Color.White.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled)
                            MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Subtitle placeholder
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Subtitles, contentDescription = "Subtitles",
                        tint = Color.White.copy(alpha = 0.8f))
                }
                // Audio tracks placeholder
                IconButton(onClick = {}) {
                    Icon(Icons.Default.AudioFile, contentDescription = "Audio tracks",
                        tint = Color.White.copy(alpha = 0.8f))
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            LinearProgressIndicator(
                progress = { value },
                modifier = Modifier.width(4.dp).height(80.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        IconButton(
            onClick = onUnlock,
            modifier = Modifier
                .padding(start = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color.White)
        }
    }
}
