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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.media3.ui.SubtitleView
import com.ryoustream.player.domain.model.AspectRatioMode
import com.ryoustream.player.domain.model.MediaItem as DomainMediaItem
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()

    // Gesture delta state
    var seekDelta by remember { mutableLongStateOf(0L) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingBrightness by remember { mutableStateOf(false) }

    // Subtitle file picker
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.loadExternalSubtitleFromUri(it) } }

    // Keep screen on
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Hide system UI (immersive)
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
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ── Orientation handling ────────────────────────────────────────────────
    val videoWidth  = state.videoWidth.takeIf { it > 0 } ?: (state.playbackState.mediaItem?.width  ?: 0)
    val videoHeight = state.videoHeight.takeIf { it > 0 } ?: (state.playbackState.mediaItem?.height ?: 0)
    LaunchedEffect(state.orientationMode, videoWidth, videoHeight) {
        activity?.requestedOrientation = when (state.orientationMode) {
            OrientationMode.AUTO ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientationMode.SENSOR_VIDEO -> {
                // Lock to axis matching the video's aspect ratio
                if (videoWidth >= videoHeight)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            OrientationMode.LOCK_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationMode.LOCK_PORTRAIT_REVERSE ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            OrientationMode.LOCK_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationMode.LOCK_LANDSCAPE_REVERSE ->
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

    // Reset orientation when leaving
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // Brightness
    LaunchedEffect(state.brightnessLevel) {
        if (state.brightnessLevel >= 0f) {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = state.brightnessLevel
            }
        }
    }

    // Init + play
    LaunchedEffect(mediaUri) {
        viewModel.initializePlayer()
        viewModel.playUri(mediaUri)
    }

    // ── Safe edge widths for gestures (avoid navigation gesture area) ──────
    val safeEdge = 48.dp  // px from edges = no gesture zone

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // ── Single / Double tap ─────────────────────────────────────
            .pointerInput(state.isLocked) {
                detectTapGestures(
                    onTap = { viewModel.toggleControls() },
                    onDoubleTap = { offset ->
                        if (!state.isLocked) {
                            val safeEdgePx = 48f * 3f  // ~48dp in px (approx for gesture dead zone)
                            val zone = size.width / 2f
                            when {
                                offset.x < safeEdgePx || offset.x > size.width - safeEdgePx -> { /* edge — ignore */ }
                                offset.x < zone -> viewModel.seekBackward(10)
                                else            -> viewModel.seekForward(10)
                            }
                            viewModel.showControlsTemporarily()
                        }
                    }
                )
            }
            // ── Horizontal drag = seek (with safe edge margins) ─────────
            .pointerInput(state.isLocked) {
                if (state.isLocked) return@pointerInput
                val safeEdgePx = 48f * 3f  // ~48dp in px (approx for gesture dead zone)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (offset.x > safeEdgePx && offset.x < size.width - safeEdgePx)
                            isDraggingSeek = true
                        seekDelta = 0L
                    },
                    onDragEnd = {
                        if (isDraggingSeek) {
                            viewModel.seekTo(
                                (state.playbackState.currentPosition + seekDelta)
                                    .coerceIn(0L, state.playbackState.duration)
                            )
                        }
                        isDraggingSeek = false; seekDelta = 0L
                    },
                    onHorizontalDrag = { _, delta ->
                        if (isDraggingSeek) seekDelta += (delta * 200).toLong()
                    },
                )
            }
            // ── Vertical drag = brightness (left) / volume (right) ──────
            .pointerInput(state.isLocked) {
                if (state.isLocked) return@pointerInput
                val safeEdgePx = 48f * 3f  // ~48dp in px (approx for gesture dead zone)
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val inSafeX = offset.x > safeEdgePx && offset.x < size.width - safeEdgePx
                        isDraggingVolume     = inSafeX && offset.x > size.width / 2f
                        isDraggingBrightness = inSafeX && offset.x <= size.width / 2f
                    },
                    onDragEnd = { isDraggingVolume = false; isDraggingBrightness = false },
                    onVerticalDrag = { _, delta ->
                        val d = -delta / 600f
                        if (isDraggingVolume)     viewModel.setVolume(state.volumeLevel + d)
                        if (isDraggingBrightness) viewModel.setBrightness(
                            (state.brightnessLevel.let { if (it < 0) 0.5f else it }) + d
                        )
                    },
                )
            }
    ) {
        // ── Video Surface ─────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Keep SubtitleView visible — ExoPlayer renders ASS/SSA/SRT natively
                    // including fonts, animations, positioning from MKV embedded tracks
                    subtitleView?.apply {
                        setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f)
                    }
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = when (state.aspectRatioMode) {
                    AspectRatioMode.FILL    -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.CROP    -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else                    -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                // Show SubtitleView when embedded track selected OR subtitle enabled
                // Hide only when user explicitly disabled subtitles (Off)
                view.subtitleView?.visibility = if (
                    state.subtitleEnabled && state.selectedSubtitleTrack >= 0
                ) android.view.View.VISIBLE else android.view.View.GONE
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── ASS/Custom Subtitle overlay (external .ass/.srt only) ────────────
        // Only used for externally loaded subtitle files, NOT for embedded MKV tracks
        // (those are rendered natively by ExoPlayer's SubtitleView above)
        if (state.subtitleEnabled && state.subtitleCues.isNotEmpty() && state.selectedSubtitleTrack == -1) {
            val adjustedMs = state.playbackState.currentPosition + state.subtitleDelay
            AssSubtitleRenderer(
                cues        = state.subtitleCues,
                positionMs  = adjustedMs,
                style       = state.subtitleStyle,
                modifier    = Modifier.fillMaxSize().padding(bottom = 80.dp),
            )
        }

        // ── Seek preview ──────────────────────────────────────────────────
        AnimatedVisibility(visible = isDraggingSeek, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                val preview = (state.playbackState.currentPosition + seekDelta)
                    .coerceIn(0, state.playbackState.duration)
                SeekPreview(seekDelta = seekDelta, previewMs = preview)
            }
        }

        // ── Volume indicator ──────────────────────────────────────────────
        AnimatedVisibility(
            visible  = isDraggingVolume,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            GestureIndicatorBar(
                icon  = if (state.volumeLevel > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                value = state.volumeLevel,
                label = "${(state.volumeLevel * 100).toInt()}%",
            )
        }

        // ── Brightness indicator ──────────────────────────────────────────
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

        // ── Buffering spinner ─────────────────────────────────────────────
        if (state.playbackState.isBuffering && !isDraggingSeek) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                strokeWidth = 3.dp,
            )
        }

        // ── Player Controls ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.showControls && !isDraggingSeek,
            enter   = fadeIn(tween(150)),
            exit    = fadeOut(tween(150)),
        ) {
            PlayerControls(
                state     = state,
                onBack    = { viewModel.savePosition(); onBack() },
                viewModel = viewModel,
                onSubtitlePick = { subtitlePicker.launch("*/*") },
            )
        }

        // ── Lock overlay ──────────────────────────────────────────────────
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

        // ── Subtitle Panel ────────────────────────────────────────────────
        if (state.showSubtitlePanel) {
            SubtitlePanel(
                state      = state,
                viewModel  = viewModel,
                onPickFile = { subtitlePicker.launch("*/*") },
                onDismiss  = viewModel::hideSubtitlePanel,
            )
        }

        // ── Audio Panel ───────────────────────────────────────────────────
        if (state.showAudioPanel) {
            AudioPanel(
                state     = state,
                viewModel = viewModel,
                onDismiss = viewModel::hideAudioPanel,
            )
        }

        // ── Subtitle Style Sheet ──────────────────────────────────────────
        if (state.showSubtitleStyleSheet) {
            SubtitleStyleSheet(
                currentStyle  = state.subtitleStyle,
                onStyleChange = viewModel::setSubtitleStyle,
                onDismiss     = viewModel::hideSubtitleStyleSheet,
            )
        }
    }
}

// ─── Main Controls Overlay ────────────────────────────────────────────────────

@Composable
private fun PlayerControls(
    state:          PlayerUiState,
    onBack:         () -> Unit,
    viewModel:      PlayerViewModel,
    onSubtitlePick: () -> Unit,
) {
    val pb     = state.playbackState
    var showOrientationMenu by remember { mutableStateOf(false) }
    var showSpeedMenu       by remember { mutableStateOf(false) }
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

    Box(Modifier.fillMaxSize()) {
        // Top gradient
        Box(
            Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
        )
        // Bottom gradient
        Box(
            Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
        )

        // ── Top Bar ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                state.mediaTitle.ifEmpty { pb.mediaItem?.displayName ?: "" },
                color  = Color.White,
                style  = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            // Aspect ratio
            TextButton(onClick = viewModel::cycleAspectRatio) {
                Text(state.aspectRatioMode.label, color = Color.White, fontSize = 11.sp)
            }
            // Speed
            Box {
                TextButton(onClick = { showSpeedMenu = true }) {
                    Text("${pb.playbackSpeed}×", color = Color.White, fontSize = 11.sp)
                }
                DropdownMenu(showSpeedMenu, { showSpeedMenu = false }) {
                    speeds.forEach { spd ->
                        DropdownMenuItem(
                            text = { Text("${spd}×") },
                            leadingIcon = {
                                if (pb.playbackSpeed == spd) Icon(Icons.Default.Check, null)
                            },
                            onClick = { viewModel.setPlaybackSpeed(spd); showSpeedMenu = false },
                        )
                    }
                }
            }
            // Orientation
            Box {
                IconButton(onClick = { showOrientationMenu = true }) {
                    Icon(Icons.Default.ScreenRotation, "Orientation", tint = Color.White)
                }
                DropdownMenu(showOrientationMenu, { showOrientationMenu = false }) {
                    OrientationMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = {
                                if (state.orientationMode == mode) Icon(Icons.Default.Check, null)
                            },
                            onClick = { viewModel.setOrientationMode(mode); showOrientationMenu = false },
                        )
                    }
                }
            }
            // Lock
            IconButton(onClick = viewModel::toggleLock) {
                Icon(
                    if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    "Lock", tint = Color.White,
                )
            }
        }

        // ── Center Controls ───────────────────────────────────────────────
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.seekBackward(10) }, Modifier.size(52.dp)) {
                Icon(Icons.Default.Replay10, "−10s", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            FloatingActionButton(
                onClick          = viewModel::playPause,
                modifier         = Modifier.size(62.dp),
                containerColor   = Color.White.copy(alpha = 0.15f),
                contentColor     = Color.White,
                elevation        = FloatingActionButtonDefaults.elevation(0.dp),
            ) {
                if (pb.isBuffering) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        if (pb.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(38.dp),
                    )
                }
            }
            IconButton(onClick = { viewModel.seekForward(10) }, Modifier.size(52.dp)) {
                Icon(Icons.Default.Forward10, "+10s", tint = Color.White, modifier = Modifier.size(38.dp))
            }
        }

        // ── Bottom Bar ────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp).padding(bottom = 8.dp),
        ) {
            // Time row
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
            // Seekbar
            Slider(
                value         = pb.progress,
                onValueChange = { viewModel.seekTo((it * pb.duration).toLong()) },
                modifier      = Modifier.fillMaxWidth(),
                colors        = SliderDefaults.colors(
                    thumbColor        = Color.White,
                    activeTrackColor  = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor= Color.White.copy(0.3f),
                ),
            )
            // Bottom action row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Left: repeat, shuffle
                Row {
                    IconButton(onClick = viewModel::toggleRepeatMode) {
                        Icon(
                            when (pb.repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                RepeatMode.ALL -> Icons.Default.Repeat
                                else           -> Icons.Default.Repeat
                            },
                            "Repeat",
                            tint = if (pb.repeatMode == RepeatMode.NONE)
                                Color.White.copy(0.45f) else MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle, "Shuffle",
                            tint = if (pb.shuffleEnabled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(0.45f),
                        )
                    }
                }
                // Right: subtitle, audio
                Row {
                    // Subtitle toggle + panel
                    IconButton(onClick = viewModel::showSubtitlePanel) {
                        Icon(
                            Icons.Default.Subtitles, "Subtitles",
                            tint = if (state.subtitleEnabled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(0.65f),
                        )
                    }
                    // Audio track panel
                    IconButton(onClick = viewModel::showAudioPanel) {
                        Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White.copy(0.85f))
                    }
                }
            }
        }
    }
}

// ─── Subtitle Panel ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitlePanel(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onPickFile:() -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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

            // Delay control
            var delayText by remember { mutableStateOf(state.subtitleDelay.toString()) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Delay (ms):", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(90.dp))
                IconButton(onClick = {
                    val d = (state.subtitleDelay - 500L)
                    viewModel.setSubtitleDelay(d)
                    delayText = d.toString()
                }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(18.dp))
                }
                Text(
                    "${state.subtitleDelay}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(70.dp),
                )
                IconButton(onClick = {
                    val d = (state.subtitleDelay + 500L)
                    viewModel.setSubtitleDelay(d)
                    delayText = d.toString()
                }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                }
                TextButton(onClick = { viewModel.setSubtitleDelay(0L); delayText = "0" }) {
                    Text("Reset")
                }
            }
            HorizontalDivider()

            // Disable
            ListItem(
                headlineContent = { Text("Off") },
                leadingContent  = {
                    RadioButton(
                        selected = !state.subtitleEnabled || state.selectedSubtitleTrack == -1,
                        onClick  = { viewModel.selectSubtitleTrack(null) },
                    )
                },
                modifier = Modifier.clickable { viewModel.selectSubtitleTrack(null) },
            )

            // Embedded tracks
            if (state.subtitleTracks.isNotEmpty()) {
                Text(
                    "Embedded", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
                state.subtitleTracks.forEach { track ->
                    ListItem(
                        headlineContent = { Text(track.label) },
                        leadingContent  = {
                            RadioButton(
                                selected = state.subtitleEnabled && state.selectedSubtitleTrack == track.index,
                                onClick  = { viewModel.selectSubtitleTrack(track) },
                            )
                        },
                        modifier = Modifier.clickable { viewModel.selectSubtitleTrack(track) },
                    )
                }
            }

            // External loaded cues
            if (state.subtitleCues.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text("External file (${state.subtitleCues.size} cues)") },
                    leadingContent  = {
                        RadioButton(
                            selected = state.subtitleEnabled && state.selectedSubtitleTrack == -1 && state.subtitleCues.isNotEmpty(),
                            onClick  = { viewModel.toggleSubtitle() },
                        )
                    },
                    modifier = Modifier.clickable { viewModel.toggleSubtitle() },
                )
            }

            HorizontalDivider()
            // Pick external file
            ListItem(
                headlineContent = { Text("Load subtitle file…") },
                leadingContent  = { Icon(Icons.Default.FolderOpen, null) },
                modifier        = Modifier.clickable { onPickFile(); onDismiss() },
            )
        }
    }
}

// ─── Audio Panel ──────────────────────────────────────────────────────────────

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
                        headlineContent  = { Text(track.label) },
                        supportingContent = {
                            if (track.language.isNotEmpty())
                                Text(track.language, style = MaterialTheme.typography.labelSmall)
                        },
                        leadingContent = {
                            RadioButton(
                                selected = state.selectedAudioTrack == track.index,
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

// ─── Seek Preview ─────────────────────────────────────────────────────────────

@Composable
private fun SeekPreview(seekDelta: Long, previewMs: Long) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.78f),
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

// ─── Gesture Indicator ────────────────────────────────────────────────────────

@Composable
private fun GestureIndicatorBar(
    icon:  androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    label: String,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.68f)) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .background(Color.White)
                )
            }
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}


