package com.ryoustream.player.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
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

    // Capture density before modifier chain
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
                            // B7: edge zone = 20% of width
                            val edgePx = size.width * 0.20f
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
            .pointerInput(state.isLocked, state.showControls) {
                if (state.isLocked || state.showControls) return@pointerInput
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
            .pointerInput(state.isLocked, state.showControls) {
                if (state.isLocked || state.showControls) return@pointerInput
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
        if (state.mpvReady) {
            key("mpv_surface") {
                AndroidView(
                    factory = { ctx -> MPVView(ctx) },
                    modifier = Modifier.fillMaxSize(),
                    update = { /* surface managed by MPVView callbacks */ },
                )
            }
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
        if (state.showQueuePanel) {
            QueuePanel(state = state, viewModel = viewModel, onDismiss = viewModel::hideQueuePanel)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls Overlay
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(
    state:          PlayerUiState,
    onBack:         () -> Unit,
    viewModel:      PlayerViewModel,
    onSubtitlePick: () -> Unit,
) {
    val pb                 = state.playbackState
    var showMoreMenu      by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Gradient — top
        Box(
            Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(
                    listOf(Color.Black.copy(0.65f), Color.Transparent)))
        )
        // Gradient — bottom
        Box(
            Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(0.75f))))
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
            // PiP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pipActivity = LocalContext.current as? Activity
                IconButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pipActivity != null) {
                        @Suppress("DEPRECATION")
                        val params = android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        pipActivity.enterPictureInPictureMode(params)
                    }
                }) {
                    Icon(Icons.Default.PictureInPictureAlt, "PiP", tint = Color.White)
                }
            }
            // MoreVert
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
                DropdownMenu(showMoreMenu, { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (state.isLocked) "Unlock Controls" else "Lock Controls") },
                        leadingIcon = {
                            Icon(
                                if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                null, Modifier.size(18.dp),
                            )
                        },
                        onClick = { viewModel.toggleLock(); showMoreMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Video Info") },
                        leadingIcon = { Icon(Icons.Default.Info, null, Modifier.size(18.dp)) },
                        onClick = { viewModel.toggleVideoInfo(); showMoreMenu = false },
                    )
                }
            }
        }

        // ── Center transport ─────────────────────────────────────────────────
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick  = viewModel::playPrev,
                enabled  = state.hasPrev,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipPrevious, "Previous",
                    tint = if (state.hasPrev) Color.White else Color.White.copy(0.5f),
                    modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { viewModel.seekBackward(10) }, Modifier.size(56.dp)) {
                Icon(Icons.Default.Replay10, "−10s",
                    tint = Color.White, modifier = Modifier.size(42.dp))
            }
            FloatingActionButton(
                onClick        = viewModel::playPause,
                modifier       = Modifier.size(72.dp),
                containerColor = Color.White.copy(0.15f),
                contentColor   = Color.White,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                } else {
                    Icon(
                        if (pb.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(48.dp),
                    )
                }
            }
            IconButton(onClick = { viewModel.seekForward(10) }, Modifier.size(56.dp)) {
                Icon(Icons.Default.Forward10, "+10s",
                    tint = Color.White, modifier = Modifier.size(42.dp))
            }
            IconButton(
                onClick  = viewModel::playNext,
                enabled  = state.hasNext,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipNext, "Next",
                    tint = if (state.hasNext) Color.White else Color.White.copy(0.5f),
                    modifier = Modifier.size(32.dp))
            }
        }

        // ── Bottom section ───────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp).padding(bottom = 8.dp),
        ) {
            // Chapter ticks
            if (state.chapterMarks.isNotEmpty() && pb.duration > 0) {
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    state.chapterMarks.forEach { (timeMs, _) ->
                        val fraction = (timeMs.toFloat() / pb.duration.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .align(Alignment.CenterStart)
                                .offset(x = (fraction * 1f).dp)
                                .background(Color.White.copy(0.7f), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
            // Seeker
            Slider(
                value         = pb.progress,
                onValueChange = { viewModel.seekTo((it * pb.duration).toLong()) },
                modifier      = Modifier.fillMaxWidth(),
                colors        = SliderDefaults.colors(
                    thumbColor         = Color.White,
                    activeTrackColor   = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(0.3f),
                ),
                thumb = {
                    Box(Modifier.size(16.dp).background(Color.White, CircleShape))
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState  = sliderState,
                        modifier     = Modifier.height(4.dp),
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                    )
                },
            )
            // Timestamps
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    DomainMediaItem.formatDuration(pb.currentPosition),
                    color = Color.White, style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                val remaining = pb.duration - pb.currentPosition
                Text(
                    if (pb.duration > 0) "-${DomainMediaItem.formatDuration(remaining)}"
                    else DomainMediaItem.formatDuration(pb.duration),
                    color = Color.White.copy(0.65f), style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Bottom action row: Lock (kiri) · Settings (kanan) ────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Lock button
                IconButton(onClick = viewModel::toggleLock) {
                    Icon(
                        if (state.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock",
                        tint = if (state.isLocked) MaterialTheme.colorScheme.primary
                               else Color.White.copy(0.75f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Settings gear → buka bottom sheet
                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Pengaturan Player",
                        tint = Color.White.copy(0.85f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }

    // ── Player Settings Side Sheet ───────────────────────────────────────────
    PlayerSettingsSideSheet(
        visible        = showSettingsSheet,
        state          = state,
        viewModel      = viewModel,
        onSubtitlePick = onSubtitlePick,
        onDismiss      = { showSettingsSheet = false },
    )

    // ── Lock overlay ─────────────────────────────────────────────────────────
    if (state.isLocked) {
        Box(
            Modifier.fillMaxSize().clickable(onClick = viewModel::toggleLock),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
        }
    }
    if (state.showVideoInfo) {
        VideoInfoSheet(state = state, onDismiss = viewModel::toggleVideoInfo)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Side Sheet — Right panel, two-level navigator (Bstation × Netflix hybrid)
// ─────────────────────────────────────────────────────────────────────────────

private sealed class SettingPage {
    object Menu         : SettingPage()
    object Playback     : SettingPage()
    object SpeedPicker  : SettingPage()
    object OrientPicker : SettingPage()
    object RatioPicker  : SettingPage()
    object Subtitle     : SettingPage()
    object Audio        : SettingPage()
    object Queue        : SettingPage()
}

@Composable
private fun PlayerSettingsSideSheet(
    visible:        Boolean,
    state:          PlayerUiState,
    viewModel:      PlayerViewModel,
    onSubtitlePick: () -> Unit,
    onDismiss:      () -> Unit,
) {
    if (!visible) return

    val speeds   = remember { listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f) }
    val navStack = remember { mutableStateListOf<SettingPage>(SettingPage.Menu) }
    val pb       = state.playbackState

    fun push(page: SettingPage) { navStack.add(page) }
    fun pop()  { if (navStack.size > 1) navStack.removeLast() else onDismiss() }

    // Scrim + panel
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable(onClick = onDismiss),
    ) {
        // ── Side panel — consume click events so they don't fall to scrim ──
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(300.dp)
                .clickable(enabled = false) {},
            color          = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shape          = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        ) {
            val currentPage = navStack.lastOrNull() ?: SettingPage.Menu

            AnimatedContent(
                targetState   = currentPage,
                transitionSpec = {
                    val goingForward = initialState == SettingPage.Menu ||
                        (initialState == SettingPage.Playback &&
                         (targetState == SettingPage.SpeedPicker ||
                          targetState == SettingPage.OrientPicker ||
                          targetState == SettingPage.RatioPicker))
                    if (goingForward)
                        (slideInHorizontally(tween(240)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(180)) { -it / 3 } + fadeOut(tween(160)))
                    else
                        (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(180)) { it } + fadeOut(tween(160)))
                },
                label = "settings_nav",
            ) { page ->
                when (page) {
                    SettingPage.Menu -> SettingsMenuPage(
                        state      = state,
                        onNavigate = ::push,
                        onDismiss  = onDismiss,
                    )
                    SettingPage.Playback -> PlaybackPage(
                        state      = state,
                        viewModel  = viewModel,
                        onNavigate = ::push,
                        onBack     = ::pop,
                    )
                    SettingPage.SpeedPicker -> PickerPage(
                        title         = "Kecepatan",
                        items         = speeds.map { "${it}×" },
                        selectedIndex = speeds.indexOf(pb.playbackSpeed),
                        onSelect      = { viewModel.setPlaybackSpeed(speeds[it]); pop() },
                        onBack        = ::pop,
                    )
                    SettingPage.OrientPicker -> PickerPage(
                        title         = "Rotasi Layar",
                        items         = OrientationMode.entries.map { it.label },
                        selectedIndex = OrientationMode.entries.indexOf(state.orientationMode),
                        onSelect      = { viewModel.setOrientationMode(OrientationMode.entries[it]); pop() },
                        onBack        = ::pop,
                    )
                    SettingPage.RatioPicker -> {
                        val modes = AspectRatioMode.entries
                        PickerPage(
                            title         = "Rasio Layar",
                            items         = modes.map { it.label },
                            selectedIndex = modes.indexOf(state.aspectRatioMode),
                            onSelect      = { idx ->
                                val target  = modes[idx]
                                val steps   = (modes.indexOf(target) - modes.indexOf(state.aspectRatioMode) + modes.size) % modes.size
                                repeat(steps) { viewModel.cycleAspectRatio() }
                                pop()
                            },
                            onBack = ::pop,
                        )
                    }
                    SettingPage.Subtitle -> SubtitleSettingsPage(
                        state        = state,
                        viewModel    = viewModel,
                        onPickFile   = { onSubtitlePick(); onDismiss() },
                        onBack       = ::pop,
                        onStyleSheet = { viewModel.showSubtitleStyleSheet(); onDismiss() },
                    )
                    SettingPage.Audio -> AudioSettingsPage(
                        state     = state,
                        viewModel = viewModel,
                        onBack    = ::pop,
                    )
                    SettingPage.Queue -> QueueSettingsPage(
                        state     = state,
                        viewModel = viewModel,
                        onBack    = ::pop,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

// ── Menu Page ─────────────────────────────────────────────────────────────────
@Composable
private fun SettingsMenuPage(
    state:     PlayerUiState,
    onNavigate:(SettingPage) -> Unit,
    onDismiss: () -> Unit,
) {
    val pb = state.playbackState
    val subLabel = when {
        !state.subtitleEnabled -> "Off"
        else -> state.subtitleTracks.firstOrNull { it.mpvId == state.selectedSubtitleTrack }
                    ?.label?.take(12) ?: "On"
    }
    val audioLabel = state.audioTracks.firstOrNull { it.mpvId == state.selectedAudioTrack }
                         ?.label?.take(12) ?: "—"

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "Pengaturan",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close, "Tutup",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(bottom = 4.dp))

        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                SettingsNavRow(
                    emoji  = "▶",
                    label  = "Playback",
                    value  = "${pb.playbackSpeed}×",
                    onClick = { onNavigate(SettingPage.Playback) },
                )
            }
            item {
                SettingsNavRow(
                    emoji  = "💬",
                    label  = "Subtitle",
                    value  = subLabel,
                    onClick = { onNavigate(SettingPage.Subtitle) },
                )
            }
            item {
                SettingsNavRow(
                    emoji  = "🎵",
                    label  = "Audio",
                    value  = audioLabel,
                    onClick = { onNavigate(SettingPage.Audio) },
                )
            }
            item {
                SettingsNavRow(
                    emoji  = "📋",
                    label  = "Antrian",
                    value  = "${state.queueItems.size} file",
                    onClick = { onNavigate(SettingPage.Queue) },
                )
            }
        }
    }
}

// ── Playback Page ─────────────────────────────────────────────────────────────
@Composable
private fun PlaybackPage(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onNavigate:(SettingPage) -> Unit,
    onBack:    () -> Unit,
) {
    val pb = state.playbackState
    Column(Modifier.fillMaxSize()) {
        SubPageHeader("Playback", onBack)
        HorizontalDivider(Modifier.padding(bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                SettingsNavRow(
                    label  = "Kecepatan",
                    value  = "${pb.playbackSpeed}×",
                    onClick = { onNavigate(SettingPage.SpeedPicker) },
                )
            }
            item {
                SettingsValueRow(
                    label  = "Ulangi",
                    value  = when (pb.repeatMode) {
                        RepeatMode.NONE -> "Off"
                        RepeatMode.ONE  -> "Satu"
                        else            -> "Semua"
                    },
                    onClick = { viewModel.toggleRepeatMode() },
                )
            }
            item {
                SettingsSwitchRow(
                    label    = "Putar Otomatis",
                    checked  = state.autoNext,
                    onToggle = { viewModel.toggleAutoNext() },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
            item {
                SettingsNavRow(
                    label  = "Rotasi Layar",
                    value  = state.orientationMode.shortLabel,
                    onClick = { onNavigate(SettingPage.OrientPicker) },
                )
            }
            item {
                SettingsNavRow(
                    label  = "Rasio Layar",
                    value  = state.aspectRatioMode.label,
                    onClick = { onNavigate(SettingPage.RatioPicker) },
                )
            }
        }
    }
}

// ── Picker Page — generic radio-style list ────────────────────────────────────
@Composable
private fun PickerPage(
    title:         String,
    items:         List<String>,
    selectedIndex: Int,
    onSelect:      (Int) -> Unit,
    onBack:        () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SubPageHeader(title, onBack)
        HorizontalDivider(Modifier.padding(bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(items) { idx, label ->
                val selected = idx == selectedIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected)
                                MaterialTheme.colorScheme.primary.copy(0.09f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color      = if (selected) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurface,
                    )
                    if (selected) {
                        Icon(
                            Icons.Default.Check, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Subtitle Settings Page ────────────────────────────────────────────────────
@Composable
private fun SubtitleSettingsPage(
    state:        PlayerUiState,
    viewModel:    PlayerViewModel,
    onPickFile:   () -> Unit,
    onBack:       () -> Unit,
    onStyleSheet: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SubPageHeader("Subtitle", onBack)
        HorizontalDivider(Modifier.padding(bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            // Off
            item {
                TrackRow(
                    label    = "Off",
                    selected = !state.subtitleEnabled,
                    onClick  = { viewModel.selectSubtitleTrack(null) },
                )
            }
            // Embedded tracks
            if (state.subtitleTracks.isNotEmpty()) {
                item {
                    Text(
                        "Tersedia",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                items(state.subtitleTracks) { track ->
                    TrackRow(
                        label    = track.label,
                        selected = state.subtitleEnabled &&
                                   state.selectedSubtitleTrack == track.mpvId,
                        onClick  = { viewModel.selectSubtitleTrack(track) },
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            // Delay
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Delay", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick  = { viewModel.setSubtitleDelay(state.subtitleDelay - 500L) },
                            modifier = Modifier.size(36.dp),
                        ) { Icon(Icons.Default.Remove, null, Modifier.size(16.dp)) }
                        Text(
                            "${state.subtitleDelay}ms",
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick  = { viewModel.setSubtitleDelay(state.subtitleDelay + 500L) },
                            modifier = Modifier.size(36.dp),
                        ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            // Style Sheet
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStyleSheet)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Palette, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Gaya Subtitle", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(
                        Icons.Default.ChevronRight, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Load external file
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickFile)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Load file…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Audio Settings Page ───────────────────────────────────────────────────────
@Composable
private fun AudioSettingsPage(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onBack:    () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SubPageHeader("Audio", onBack)
        HorizontalDivider(Modifier.padding(bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            if (state.audioTracks.isEmpty()) {
                item {
                    Text(
                        "Tidak ada audio track",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "Track",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                items(state.audioTracks) { track ->
                    TrackRow(
                        label    = track.label,
                        selected = state.selectedAudioTrack == track.mpvId,
                        onClick  = { viewModel.selectAudioTrack(track) },
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            // Delay
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Delay Audio", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick  = { viewModel.setAudioDelay(state.audioDelay - 500L) },
                            modifier = Modifier.size(36.dp),
                        ) { Icon(Icons.Default.Remove, null, Modifier.size(16.dp)) }
                        Text(
                            "${state.audioDelay}ms",
                            style     = MaterialTheme.typography.bodySmall,
                            modifier  = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick  = { viewModel.setAudioDelay(state.audioDelay + 500L) },
                            modifier = Modifier.size(36.dp),
                        ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Queue Settings Page ───────────────────────────────────────────────────────
@Composable
private fun QueueSettingsPage(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onBack:    () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentQueueIndex) {
        if (state.queueItems.isNotEmpty()) {
            listState.animateScrollToItem(
                state.currentQueueIndex.coerceAtMost(state.queueItems.lastIndex)
            )
        }
    }
    Column(Modifier.fillMaxSize()) {
        SubPageHeader("Antrian (${state.queueItems.size})", onBack)
        HorizontalDivider()
        if (state.queueItems.isEmpty()) {
            Text(
                "Queue kosong",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                state    = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                itemsIndexed(
                    items = state.queueItems,
                    key   = { _, uri -> uri.toString() },
                ) { index, uri ->
                    val isActive = index == state.currentQueueIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActive)
                                    MaterialTheme.colorScheme.primary.copy(0.10f)
                                else Color.Transparent
                            )
                            .clickable { viewModel.jumpToQueue(index); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isActive) {
                            Icon(
                                Icons.Default.PlayArrow, "Playing",
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                "${index + 1}",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier  = Modifier.width(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = uri.lastPathSegment?.let {
                                try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                            } ?: uri.toString(),
                            style    = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color    = if (isActive) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ── Shared: Sub-page header with back button ──────────────────────────────────
@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Kembali",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.weight(1f),
        )
    }
}

// ── Shared: Nav row — label + value badge + chevron ───────────────────────────
@Composable
private fun SettingsNavRow(
    label:   String,
    value:   String,
    onClick: () -> Unit,
    emoji:   String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (emoji != null) {
            Text(emoji, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            label,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                value,
                style  = MaterialTheme.typography.bodySmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Shared: Value row — tap to cycle ─────────────────────────────────────────
@Composable
private fun SettingsValueRow(
    label:   String,
    value:   String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Shared: Switch row ────────────────────────────────────────────────────────
@Composable
private fun SettingsSwitchRow(
    label:   String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Subtitle Panel → ModalBottomSheet (Section 8)
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
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(8.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Subtitle", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::showSubtitleStyleSheet,
                        modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Palette, "Style", Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Close", Modifier.size(20.dp))
                    }
                }
            }

            // Delay strip
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { viewModel.setSubtitleDelay(state.subtitleDelay - 500L) },
                    Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
                }
                Text(
                    "Delay ${state.subtitleDelay}ms",
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { viewModel.setSubtitleDelay(state.subtitleDelay + 500L) },
                    Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                }
                if (state.subtitleDelay != 0L) {
                    TextButton(
                        onClick  = { viewModel.setSubtitleDelay(0L) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Reset", style = MaterialTheme.typography.labelMedium) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // Track list
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    TrackRow(
                        label    = "Off",
                        selected = !state.subtitleEnabled,
                        onClick  = { viewModel.selectSubtitleTrack(null); onDismiss() },
                    )
                }
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
                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPickFile(); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp))
                        Text("Load file…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio Panel → ModalBottomSheet (Section 9)
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
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(8.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Audio Track", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Close", Modifier.size(20.dp))
                }
            }

            // Delay strip
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { viewModel.setAudioDelay(state.audioDelay - 500L) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
                }
                Text(
                    "Delay ${state.audioDelay}ms",
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = { viewModel.setAudioDelay(state.audioDelay + 500L) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                }
                if (state.audioDelay != 0L) {
                    TextButton(
                        onClick  = { viewModel.setAudioDelay(0L) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Reset", style = MaterialTheme.typography.labelMedium) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // Track list
            if (state.audioTracks.isEmpty()) {
                Text(
                    "No audio tracks detected",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            "Tracks",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(state.audioTracks) { track ->
                        TrackRow(
                            label    = track.label,
                            selected = state.selectedAudioTrack == track.mpvId,
                            onClick  = { viewModel.selectAudioTrack(track); onDismiss() },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video Info Sheet → ModalBottomSheet (Section 10)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoInfoSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Video Info", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Close", Modifier.size(20.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (state.videoInfo.isEmpty()) {
                Text(
                    "Loading…",
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn {
                    items(state.videoInfo.entries.toList()) { (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                                maxLines = 3,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Queue Panel — ModalBottomSheet BARU (Section 11)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueuePanel(
    state:     PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()

    // B4: auto-scroll ke item aktif saat panel dibuka
    LaunchedEffect(state.currentQueueIndex) {
        if (state.queueItems.isNotEmpty()) {
            listState.animateScrollToItem(state.currentQueueIndex.coerceAtMost(state.queueItems.lastIndex))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 500.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "Queue (${state.queueItems.size} file)",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Close", Modifier.size(20.dp))
                }
            }
            HorizontalDivider()

            if (state.queueItems.isEmpty()) {
                Text(
                    "Queue kosong",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    state  = listState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(
                        items = state.queueItems,
                        key   = { _, uri -> uri.toString() },
                    ) { index, uri ->
                        val isActive = index == state.currentQueueIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isActive)
                                        MaterialTheme.colorScheme.primary.copy(0.12f)
                                    else Color.Transparent
                                )
                                .clickable { viewModel.jumpToQueue(index) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isActive) {
                                Icon(
                                    Icons.Default.PlayArrow, "Playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(18.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                text     = uri.lastPathSegment?.let {
                                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                                } ?: uri.toString(),
                                style    = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color    = if (isActive) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared: Track Row
// ─────────────────────────────────────────────────────────────────────────────

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
            modifier = Modifier.size(36.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pill Control Components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Frosted-glass pill container for grouped player controls.
 * Background: semi-transparent black, thin white border for glass effect.
 */
@Composable
private fun ControlPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(50.dp),
        color    = Color.Black.copy(alpha = 0.45f),
        border   = BorderStroke(0.5.dp, Color.White.copy(0.18f)),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            content               = content,
        )
    }
}

/**
 * Icon button inside a ControlPill.
 * Active state uses primary color tint; inactive uses White.copy(0.80f).
 */
@Composable
private fun RowScope.PillIconButton(
    icon:     ImageVector,
    onClick:  () -> Unit,
    active:   Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(0.80f)
    IconButton(
        onClick  = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Text button (speed display) inside a ControlPill.
 */
@Composable
private fun PillTextButton(
    text:    String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick        = onClick,
        modifier       = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Thin vertical divider inside a ControlPill.
 */
@Composable
private fun RowScope.PillDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(20.dp)
            .background(Color.White.copy(0.25f)),
    )
}

/**
 * Small frosted icon+label button — for Orientation and AspectRatio row.
 */
@Composable
private fun SmallIconButton(
    icon:    ImageVector,
    label:   String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(8.dp),
        color   = Color.Black.copy(alpha = 0.35f),
        border  = BorderStroke(0.5.dp, Color.White.copy(0.15f)),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(16.dp))
            Text(label, color = Color.White.copy(0.85f), fontSize = 11.sp)
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
