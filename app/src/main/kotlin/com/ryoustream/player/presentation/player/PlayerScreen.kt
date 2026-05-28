package com.ryoustream.player.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.MediaItem as DomainMediaItem
import com.ryoustream.player.domain.model.RepeatMode
import `is`.xyz.mpv.MPVView

// ─────────────────────────────────────────────────────────────────────────────
// PlayerScreen — powered by libmpv via MPVView
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlayerScreen(
    mediaUri: Uri,
    onBack:   () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context  = LocalContext.current
    val activity = context as? Activity
    val state    by viewModel.state.collectAsStateWithLifecycle()

    // ── Gesture state ────────────────────────────────────────────────────────
    var seekDelta           by remember { mutableLongStateOf(0L) }
    var isDraggingSeek      by remember { mutableStateOf(false) }
    var isDraggingVolume    by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }

    // ── Subtitle file picker ─────────────────────────────────────────────────
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.loadExternalSubtitleFromUri(it) } }

    // ── Keep screen on ───────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── Immersive / hide system UI ───────────────────────────────────────────
    DisposableEffect(Unit) {
        activity?.let { act ->
            WindowCompat.setDecorFitsSystemWindows(act.window, false)
            WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.let { act ->
                WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
                (act as? androidx.activity.ComponentActivity)?.apply {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }
            }
        }
    }

    // ── Orientation ──────────────────────────────────────────────────────────
    val videoW = state.videoWidth.takeIf { it > 0 } ?: (state.playbackState.mediaItem?.width  ?: 0)
    val videoH = state.videoHeight.takeIf { it > 0 } ?: (state.playbackState.mediaItem?.height ?: 0)
    LaunchedEffect(state.orientationMode, videoW, videoH) {
        activity?.requestedOrientation = when (state.orientationMode) {
            OrientationMode.AUTO                -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientationMode.SENSOR_VIDEO        ->
                if (videoW >= videoH) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else                  ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.LOCK_PORTRAIT       -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.LOCK_PORTRAIT_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            OrientationMode.LOCK_LANDSCAPE      -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationMode.LOCK_LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ── Brightness ───────────────────────────────────────────────────────────
    LaunchedEffect(state.brightnessLevel) {
        if (state.brightnessLevel >= 0f) {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = state.brightnessLevel
            }
        }
    }

    // ── Init mpv + play ──────────────────────────────────────────────────────
    LaunchedEffect(mediaUri) {
        viewModel.initializePlayer()
        viewModel.playUri(mediaUri)
    }

    // Capture density before modifier chain (can't use LocalDensity.current inside pointerInput)
    val pixelDensity = LocalDensity.current.density

    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // ── Tap / double-tap ─────────────────────────────────────────
            .pointerInput(state.isLocked) {
                detectTapGestures(
                    onTap       = { viewModel.toggleControls() },
                    onDoubleTap = { offset ->
                        if (!state.isLocked) {
                            val edgePx = 56f * pixelDensity
                            when {
                                offset.x < edgePx || offset.x > size.width - edgePx -> { /* edge: ignore */ }
                                offset.x < size.width / 2f -> viewModel.seekBackward(10)
                                else                        -> viewModel.seekForward(10)
                            }
                            viewModel.showControlsTemporarily()
                        }
                    },
                )
            }
            // ── Horizontal drag = seek ───────────────────────────────────
            .pointerInput(state.isLocked) {
                if (state.isLocked) return@pointerInput
                val edgePx = 56f * pixelDensity
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        isDraggingSeek = off.x > edgePx && off.x < size.width - edgePx
                        seekDelta = 0L
                    },
                    onDragEnd  = {
                        if (isDraggingSeek) viewModel.seekTo(
                            (state.playbackState.currentPosition + seekDelta)
                                .coerceIn(0L, state.playbackState.duration)
                        )
                        isDraggingSeek = false; seekDelta = 0L
                    },
                    onHorizontalDrag = { _, d ->
                        if (isDraggingSeek) seekDelta += (d * 200).toLong()
                    },
                )
            }
            // ── Vertical drag = brightness (left) / volume (right) ───────
            .pointerInput(state.isLocked) {
                if (state.isLocked) return@pointerInput
                val edgePx = 56f * pixelDensity
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        val safe = off.x > edgePx && off.x < size.width - edgePx
                        isDraggingVolume     = safe && off.x > size.width / 2f
                        isDraggingBrightness = safe && off.x <= size.width / 2f
                    },
                    onDragEnd = { isDraggingVolume = false; isDraggingBrightness = false },
                    onVerticalDrag = { _, d ->
                        val delta = -d / 600f
                        if (isDraggingVolume)
                            viewModel.setVolume(state.volumeLevel + delta)
                        if (isDraggingBrightness)
                            viewModel.setBrightness(
                                (state.brightnessLevel.let { if (it < 0) 0.5f else it }) + delta
                            )
                    },
                )
            },
    ) {

        // ── MPV Video Surface ────────────────────────────────────────────────
        // MPVView is a SurfaceView that forwards the Surface to libmpv.
        // mpv renders video AND subtitles (via libass) directly onto this surface.
        // IMPORTANT: only create MPVView AFTER MPVLib.init() has completed (mpvReady=true).
        // surfaceCreated fires immediately once the view is attached; calling
        // attachSurface() before init() is a guaranteed native crash.
        if (state.mpvReady) {
            AndroidView(
                factory = { ctx -> MPVView(ctx) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── mpv missing overlay ──────────────────────────────────────────────
        if (!state.mpvReady && state.error != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                    // Fix: state.error is a delegated property — can't smart-cast, use ?: ""
                    Text(state.error ?: "", color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
        }

        // ── Seek drag preview ────────────────────────────────────────────────
        AnimatedVisibility(visible = isDraggingSeek, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                val previewMs = (state.playbackState.currentPosition + seekDelta)
                    .coerceIn(0L, state.playbackState.duration)
                SeekPreview(seekDelta = seekDelta, previewMs = previewMs)
            }
        }

        // ── Volume indicator ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isDraggingVolume,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            GestureIndicatorBar(
                icon  = if (state.volumeLevel > 0f)
                    Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                value = state.volumeLevel,
                label = "${(state.volumeLevel * 100).toInt()}%",
            )
        }

        // ── Brightness indicator ─────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isDraggingBrightness,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 56.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            GestureIndicatorBar(
                icon  = Icons.Default.Brightness6,
                value = state.brightnessLevel.let { if (it < 0) 0.5f else it },
                label = "${((state.brightnessLevel.let { if (it < 0) 0.5f else it }) * 100).toInt()}%",
            )
        }

        // ── Buffering spinner ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = state.isBuffering && !isDraggingSeek,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            CircularProgressIndicator(
                color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(52.dp),
            )
        }

        // ── Controls overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showControls && !isDraggingSeek,
            enter   = fadeIn(tween(150)),
            exit    = fadeOut(tween(150)),
        ) {
            PlayerControls(
                state          = state,
                onBack         = { viewModel.savePosition(); onBack() },
                viewModel      = viewModel,
                onSubtitlePick = { subtitlePicker.launch("*/*") },
            )
        }

        // ── Lock overlay ─────────────────────────────────────────────────────
        if (state.isLocked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                IconButton(
                    onClick  = viewModel::toggleLock,
                    modifier = Modifier.padding(start = 20.dp)
                        .background(Color.Black.copy(0.5f), CircleShape),
                ) {
                    Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
                }
            }
        }

        // ── Bottom Sheets ────────────────────────────────────────────────────
        if (state.showVideoInfo) {
            VideoInfoSheet(state = state, onDismiss = viewModel::toggleVideoInfo)
        }
        if (state.showSubtitlePanel) {
            SubtitlePanel(
                state      = state,
                viewModel  = viewModel,
                onPickFile = { subtitlePicker.launch("*/*") },
                onDismiss  = viewModel::hideSubtitlePanel,
            )
        }
        if (state.showAudioPanel) {
            AudioPanel(state = state, viewModel = viewModel, onDismiss = viewModel::hideAudioPanel)
        }
        if (state.showSubtitleStyleSheet) {
            SubtitleStyleSheet(
                currentStyle  = state.subtitleStyle,
                onStyleChange = viewModel::setSubtitleStyle,
                onDismiss     = viewModel::hideSubtitleStyleSheet,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerControls(
    state:          PlayerUiState,
    onBack:         () -> Unit,
    viewModel:      PlayerViewModel,
    onSubtitlePick: () -> Unit,
) {
    val pb                  = state.playbackState
    var showOrientationMenu by remember { mutableStateOf(false) }
    var showSpeedMenu       by remember { mutableStateOf(false) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

    Box(Modifier.fillMaxSize()) {
        // Gradient — top
        Box(
            Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(
                    listOf(Color.Black.copy(0.78f), Color.Transparent)))
        )
        // Gradient — bottom
        Box(
            Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(0.88f))))
        )

        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                text     = state.mediaTitle.ifEmpty { pb.mediaItem?.displayName ?: "" },
                color    = Color.White,
                style    = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            // Aspect ratio cycle button
            TextButton(onClick = viewModel::cycleAspectRatio) {
                Text(state.aspectRatioMode.label, color = Color.White, fontSize = 11.sp)
            }
            // Speed menu
            Box {
                TextButton(onClick = { showSpeedMenu = true }) {
                    Text("${pb.playbackSpeed}×", color = Color.White, fontSize = 11.sp)
                }
                DropdownMenu(showSpeedMenu, { showSpeedMenu = false }) {
                    speeds.forEach { spd ->
                        DropdownMenuItem(
                            text = { Text("${spd}×") },
                            leadingIcon = {
                                if (pb.playbackSpeed == spd)
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            },
                            onClick = { viewModel.setPlaybackSpeed(spd); showSpeedMenu = false },
                        )
                    }
                }
            }
            // Orientation menu
            Box {
                IconButton(onClick = { showOrientationMenu = true }) {
                    Icon(Icons.Default.ScreenRotation, "Orientation", tint = Color.White)
                }
                DropdownMenu(showOrientationMenu, { showOrientationMenu = false }) {
                    OrientationMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = {
                                if (state.orientationMode == mode)
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            },
                            onClick = { viewModel.setOrientationMode(mode); showOrientationMenu = false },
                        )
                    }
                }
            }
            // Lock toggle
            IconButton(onClick = viewModel::toggleLock) {
                Icon(
                    if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    "Lock", tint = Color.White,
                )
            }
        }

        // ── Center transport ─────────────────────────────────────────────────
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // Skip Previous
            IconButton(
                onClick  = viewModel::playPrev,
                enabled  = state.hasPrev,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.SkipPrevious, "Previous",
                    tint = if (state.hasPrev) Color.White else Color.White.copy(0.3f),
                    modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { viewModel.seekBackward(10) }, Modifier.size(52.dp)) {
                Icon(Icons.Default.Replay10, "−10s",
                    tint = Color.White, modifier = Modifier.size(38.dp))
            }
            FloatingActionButton(
                onClick        = viewModel::playPause,
                modifier       = Modifier.size(64.dp),
                containerColor = Color.White.copy(0.15f),
                contentColor   = Color.White,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        if (pb.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(38.dp),
                    )
                }
            }
            IconButton(onClick = { viewModel.seekForward(10) }, Modifier.size(52.dp)) {
                Icon(Icons.Default.Forward10, "+10s",
                    tint = Color.White, modifier = Modifier.size(38.dp))
            }
            // Skip Next
            IconButton(
                onClick  = viewModel::playNext,
                enabled  = state.hasNext,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.SkipNext, "Next",
                    tint = if (state.hasNext) Color.White else Color.White.copy(0.3f),
                    modifier = Modifier.size(30.dp))
            }
        }

        // ── Bottom bar ───────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp).padding(bottom = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    DomainMediaItem.formatDuration(pb.currentPosition),
                    color = Color.White, style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    DomainMediaItem.formatDuration(pb.duration),
                    color = Color.White.copy(0.65f), style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value         = pb.progress,
                onValueChange = { viewModel.seekTo((it * pb.duration).toLong()) },
                modifier      = Modifier.fillMaxWidth(),
                colors        = SliderDefaults.colors(
                    thumbColor         = Color.White,
                    activeTrackColor   = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(0.3f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Left: repeat + shuffle + stop + autoNext
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::toggleRepeatMode) {
                        Icon(
                            when (pb.repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                else           -> Icons.Default.Repeat
                            },
                            "Repeat",
                            tint = if (pb.repeatMode == RepeatMode.NONE)
                                Color.White.copy(0.4f) else MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Auto-next toggle (skip to next file when video ends)
                    IconButton(onClick = viewModel::toggleAutoNext) {
                        Icon(
                            Icons.Default.SkipNext, "Auto-next",
                            tint = if (state.autoNext) MaterialTheme.colorScheme.primary
                                   else Color.White.copy(0.4f),
                        )
                    }
                    // Stop
                    IconButton(onClick = viewModel::stop) {
                        Icon(Icons.Default.Stop, "Stop", tint = Color.White.copy(0.75f))
                    }
                }
                // Right: info + subtitle + audio
                Row {
                    IconButton(onClick = viewModel::toggleVideoInfo) {
                        Icon(Icons.Default.Info, "Video Info", tint = Color.White.copy(0.85f))
                    }
                    IconButton(onClick = viewModel::showSubtitlePanel) {
                        Icon(
                            Icons.Default.Subtitles, "Subtitles",
                            tint = if (state.subtitleEnabled) MaterialTheme.colorScheme.primary
                                   else Color.White.copy(0.65f),
                        )
                    }
                    IconButton(onClick = viewModel::showAudioPanel) {
                        Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White.copy(0.85f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subtitle Panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubtitlePanel(
    state:      PlayerUiState,
    viewModel:  PlayerViewModel,
    onPickFile: () -> Unit,
    onDismiss:  () -> Unit,
) {
    // Compact floating popup — anchored to bottom-end, doesn't block the full video.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress    = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Position card at bottom-end corner of the screen
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier      = Modifier
                    .width(280.dp)
                    .padding(bottom = 72.dp, end = 8.dp),
                shape         = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color         = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(8.dp)) {

                    // ── Header ────────────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Subtitles", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = viewModel::showSubtitleStyleSheet,
                                modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Palette, "Style", Modifier.size(18.dp))
                            }
                            IconButton(onClick = onDismiss,
                                modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Close", Modifier.size(18.dp))
                            }
                        }
                    }

                    // ── Delay strip ───────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(onClick = { viewModel.setSubtitleDelay(state.subtitleDelay - 500L) },
                            Modifier.size(28.dp)) {
                            Icon(Icons.Default.Remove, null, Modifier.size(14.dp))
                        }
                        Text(
                            "Delay ${state.subtitleDelay}ms",
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = { viewModel.setSubtitleDelay(state.subtitleDelay + 500L) },
                            Modifier.size(28.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        }
                        if (state.subtitleDelay != 0L) {
                            TextButton(
                                onClick  = { viewModel.setSubtitleDelay(0L) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // ── Track list (scrollable) ───────────────────────────────
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        // Off
                        item {
                            TrackRow(
                                label    = "Off",
                                selected = !state.subtitleEnabled,
                                onClick  = { viewModel.selectSubtitleTrack(null); onDismiss() },
                            )
                        }
                        // Embedded tracks
                        if (state.subtitleTracks.isNotEmpty()) {
                            item {
                                Text(
                                    "Embedded",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(state.subtitleTracks) { track ->
                                TrackRow(
                                    label    = track.label,
                                    selected = state.subtitleEnabled &&
                                               state.selectedSubtitleTrack == track.mpvId,
                                    onClick  = { viewModel.selectSubtitleTrack(track); onDismiss() },
                                )
                            }
                        }
                        // Load external
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickFile(); onDismiss() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                                Text("Load file…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick  = onClick,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video Info Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoInfoSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress    = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier        = Modifier
                    .width(300.dp)
                    .padding(bottom = 72.dp, end = 8.dp),
                shape           = RoundedCornerShape(16.dp),
                tonalElevation  = 8.dp,
                shadowElevation = 8.dp,
                color           = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Video Info", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Close", Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    if (state.videoInfo.isEmpty()) {
                        Text(
                            "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        state.videoInfo.forEach { (label, value) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    label,
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(100.dp),
                                )
                                Text(
                                    value,
                                    style    = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio Panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AudioPanel(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    // Compact floating popup — mirrors SubtitlePanel layout, anchored to bottom-end.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress    = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier        = Modifier
                    .width(280.dp)
                    .padding(bottom = 72.dp, end = 8.dp),
                shape           = RoundedCornerShape(16.dp),
                tonalElevation  = 8.dp,
                shadowElevation = 8.dp,
                color           = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(8.dp)) {

                    // ── Header ────────────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Audio Track", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Close", Modifier.size(18.dp))
                        }
                    }

                    // ── Audio delay strip ─────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = { viewModel.setAudioDelay(state.audioDelay - 500L) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Remove, null, Modifier.size(14.dp))
                        }
                        Text(
                            "Delay ${state.audioDelay}ms",
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { viewModel.setAudioDelay(state.audioDelay + 500L) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        }
                        if (state.audioDelay != 0L) {
                            TextButton(
                                onClick  = { viewModel.setAudioDelay(0L) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // ── Track list (scrollable) ───────────────────────────────
                    if (state.audioTracks.isEmpty()) {
                        Text(
                            "No audio tracks detected",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            if (state.audioTracks.isNotEmpty()) {
                                item {
                                    Text(
                                        "Tracks",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
                                    )
                                }
                            }
                            items(state.audioTracks) { track ->
                                TrackRow(
                                    label    = track.label,
                                    selected = state.selectedAudioTrack == track.mpvId,
                                    onClick  = { viewModel.selectAudioTrack(track); onDismiss() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Seek Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SeekPreview(seekDelta: Long, previewMs: Long) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(0.78f),
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (seekDelta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                null, tint = Color.White, modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                DomainMediaItem.formatDuration(previewMs),
                color      = Color.White,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${if (seekDelta >= 0) "+" else ""}${seekDelta / 1000}s",
                color = Color.White.copy(0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gesture Indicator Bar (volume / brightness)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GestureIndicatorBar(icon: ImageVector, value: Float, label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(0.68f)) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .width(6.dp).height(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(0.3f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(value).background(Color.White))
            }
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}
