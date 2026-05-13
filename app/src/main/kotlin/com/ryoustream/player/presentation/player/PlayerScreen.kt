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
import androidx.compose.ui.text.style.TextOverflow
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
        // No separate subtitle overlay is needed for embedded tracks.
        AndroidView(
            factory = { ctx -> MPVView(ctx) },
            modifier = Modifier.fillMaxSize(),
        )

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
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
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
                // Left: repeat + shuffle
                Row {
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
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle, "Shuffle",
                            tint = if (pb.shuffleEnabled) MaterialTheme.colorScheme.primary
                                   else Color.White.copy(0.4f),
                        )
                    }
                }
                // Right: subtitle + audio
                Row {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitlePanel(
    state:      PlayerUiState,
    viewModel:  PlayerViewModel,
    onPickFile: () -> Unit,
    onDismiss:  () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Subtitles", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = viewModel::showSubtitleStyleSheet) {
                        Icon(Icons.Default.Palette, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Style")
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
            HorizontalDivider()

            // ── Sub delay ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Delay:", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(56.dp))
                IconButton(onClick = { viewModel.setSubtitleDelay(state.subtitleDelay - 500L) },
                    Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(18.dp))
                }
                Text("${state.subtitleDelay}ms",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                IconButton(onClick = { viewModel.setSubtitleDelay(state.subtitleDelay + 500L) },
                    Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                }
                TextButton(onClick = { viewModel.setSubtitleDelay(0L) }) { Text("Reset") }
            }
            HorizontalDivider()

            // ── Off ──────────────────────────────────────────────────────────
            ListItem(
                headlineContent = { Text("Off") },
                leadingContent  = {
                    RadioButton(
                        selected = !state.subtitleEnabled,
                        onClick  = { viewModel.selectSubtitleTrack(null) },
                    )
                },
                modifier = Modifier.clickable { viewModel.selectSubtitleTrack(null) },
            )

            // ── Embedded tracks ──────────────────────────────────────────────
            if (state.subtitleTracks.isNotEmpty()) {
                Text(
                    "Embedded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
                state.subtitleTracks.forEach { track ->
                    ListItem(
                        headlineContent = { Text(track.label) },
                        leadingContent  = {
                            RadioButton(
                                // FIX: compare mpvId (not .index which is 0-based display index)
                                selected = state.subtitleEnabled &&
                                           state.selectedSubtitleTrack == track.mpvId,
                                onClick  = { viewModel.selectSubtitleTrack(track) },
                            )
                        },
                        modifier = Modifier.clickable { viewModel.selectSubtitleTrack(track) },
                    )
                }
            }

            HorizontalDivider()
            // ── Load external ─────────────────────────────────────────────────
            ListItem(
                headlineContent = { Text("Load subtitle file…") },
                leadingContent  = { Icon(Icons.Default.FolderOpen, null) },
                modifier        = Modifier.clickable { onPickFile(); onDismiss() },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio Panel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPanel(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Audio Track", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            HorizontalDivider()

            if (state.audioTracks.isEmpty()) {
                Text(
                    "No audio tracks detected",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                state.audioTracks.forEach { track ->
                    ListItem(
                        headlineContent   = { Text(track.label) },
                        supportingContent = {
                            if (track.language.isNotBlank())
                                Text(track.language, style = MaterialTheme.typography.labelSmall)
                        },
                        leadingContent = {
                            RadioButton(
                                // FIX: compare mpvId
                                selected = state.selectedAudioTrack == track.mpvId,
                                onClick  = { viewModel.selectAudioTrack(track) },
                            )
                        },
                        modifier = Modifier.clickable { viewModel.selectAudioTrack(track) },
                    )
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
