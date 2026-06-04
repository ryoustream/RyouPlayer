package com.ryoustream.player.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.BuildConfig
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.util.AppUpdateChecker
import com.ryoustream.player.util.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

// ─── Update Check State ───────────────────────────────────────────────────────

enum class DownloadState { IDLE, DOWNLOADING, DONE, FAILED }

data class UpdateCheckState(
    val isChecking: Boolean         = false,
    val result: UpdateInfo?         = null,
    val error: String?              = null,
    val checked: Boolean            = false,
    val downloadState: DownloadState = DownloadState.IDLE,
    val downloadProgress: Int       = 0,
    val downloadId: Long            = -1L,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsUiState(
    // Playback
    val hardwareDecoding: Boolean   = true,
    val subtitleEnabled: Boolean    = true,
    val subtitleFontSize: Int       = 16,
    val rememberPosition: Boolean   = true,
    val gestureSeek: Boolean        = true,
    val gestureBrightness: Boolean  = true,
    val gestureVolume: Boolean      = true,
    val doubleTapSeconds: Int       = 10,
    val pipEnabled: Boolean         = true,
    val backgroundPlay: Boolean     = false,
    val defaultSpeed: Float         = 1.0f,
    // UI
    val themeMode: String           = "SYSTEM",
    val amoledMode: Boolean         = false,
    val useSystemColor: Boolean     = true,
    val animationsEnabled: Boolean  = true,
    val ignoreNotch: Boolean        = false,
    // Advanced
    val networkBuffer: Int          = 32,
    val cacheSize: Int              = 256,
    val codecPreference: String     = "AUTO",
    val showHiddenFiles: Boolean    = false,
    val ignoreNomedia: Boolean      = false,
    // Update
    val updateCheck: UpdateCheckState = UpdateCheckState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    init {
        // Block 1: core playback settings (up to 5 flows)
        viewModelScope.launch {
            combine(
                settingsRepository.hardwareDecodingEnabled,
                settingsRepository.subtitleEnabled,
                settingsRepository.subtitleFontSize,
                settingsRepository.rememberPosition,
                settingsRepository.gestureSeekEnabled,
            ) { hw, sub, subSize, remPos, gesSeek ->
                _uiState.update {
                    it.copy(
                        hardwareDecoding = hw,
                        subtitleEnabled  = sub,
                        subtitleFontSize = subSize,
                        rememberPosition = remPos,
                        gestureSeek      = gesSeek,
                    )
                }
            }.collect()
        }
        // Block 2: gestures + playback extras
        viewModelScope.launch {
            combine(
                settingsRepository.gestureBrightnessEnabled,
                settingsRepository.gestureVolumeEnabled,
                settingsRepository.doubleTapSeekSeconds,
                settingsRepository.pipEnabled,
                settingsRepository.backgroundPlayEnabled,
            ) { br, vol, dtSec, pip, bgPlay ->
                _uiState.update {
                    it.copy(
                        gestureBrightness = br,
                        gestureVolume     = vol,
                        doubleTapSeconds  = dtSec,
                        pipEnabled        = pip,
                        backgroundPlay    = bgPlay,
                    )
                }
            }.collect()
        }
        // Block 3: UI settings + hidden files
        viewModelScope.launch {
            combine(
                settingsRepository.themeMode,
                settingsRepository.amoledMode,
                settingsRepository.useSystemColor,
                settingsRepository.animationsEnabled,
                settingsRepository.showHiddenFiles,
            ) { theme, amoled, sysColor, anim, hidden ->
                _uiState.update {
                    it.copy(
                        themeMode       = theme,
                        amoledMode      = amoled,
                        useSystemColor  = sysColor,
                        animationsEnabled = anim,
                        showHiddenFiles = hidden,
                    )
                }
            }.collect()
        }
        // Block 4: remaining individual settings
        viewModelScope.launch {
            settingsRepository.ignoreNotch.collect { v ->
                _uiState.update { it.copy(ignoreNotch = v) }
            }
        }
        viewModelScope.launch {
            settingsRepository.ignoreNomedia.collect { v ->
                _uiState.update { it.copy(ignoreNomedia = v) }
            }
        }
        // Block 5: fix — defaultPlaybackSpeed was never loaded!
        viewModelScope.launch {
            settingsRepository.defaultPlaybackSpeed.collect { v ->
                _uiState.update { it.copy(defaultSpeed = v) }
            }
        }
        // Block 6: fix — codecPreference was never loaded!
        viewModelScope.launch {
            settingsRepository.codecPreference.collect { v ->
                _uiState.update { it.copy(codecPreference = v) }
            }
        }
    }

    // ── Setters ───────────────────────────────────────────────────────────────
    fun setHardwareDecoding(v: Boolean)   = viewModelScope.launch { settingsRepository.setHardwareDecoding(v) }
    fun setSubtitleEnabled(v: Boolean)    = viewModelScope.launch { settingsRepository.setSubtitleEnabled(v) }
    fun setSubtitleFontSize(v: Int)       = viewModelScope.launch { settingsRepository.setSubtitleFontSize(v) }
    fun setRememberPosition(v: Boolean)   = viewModelScope.launch { settingsRepository.setRememberPosition(v) }
    fun setGestureSeek(v: Boolean)        = viewModelScope.launch { settingsRepository.setGestureSeek(v) }
    fun setGestureBrightness(v: Boolean)  = viewModelScope.launch { settingsRepository.setGestureBrightness(v) }
    fun setGestureVolume(v: Boolean)      = viewModelScope.launch { settingsRepository.setGestureVolume(v) }
    fun setDoubleTapSeconds(v: Int)       = viewModelScope.launch { settingsRepository.setDoubleTapSeekSeconds(v) }
    fun setPipEnabled(v: Boolean)         = viewModelScope.launch { settingsRepository.setPipEnabled(v) }
    fun setBackgroundPlay(v: Boolean)     = viewModelScope.launch { settingsRepository.setBackgroundPlay(v) }
    fun setDefaultSpeed(v: Float)         = viewModelScope.launch { settingsRepository.setDefaultPlaybackSpeed(v) }
    fun setThemeMode(v: String)           = viewModelScope.launch { settingsRepository.setThemeMode(v) }
    fun setAmoledMode(v: Boolean)         = viewModelScope.launch { settingsRepository.setAmoledMode(v) }
    fun setUseSystemColor(v: Boolean)     = viewModelScope.launch { settingsRepository.setUseSystemColor(v) }
    fun setAnimations(v: Boolean)         = viewModelScope.launch { settingsRepository.setAnimationsEnabled(v) }
    fun setIgnoreNotch(v: Boolean)        = viewModelScope.launch { settingsRepository.setIgnoreNotch(v) }
    fun setShowHiddenFiles(v: Boolean)    = viewModelScope.launch { settingsRepository.setShowHiddenFiles(v) }
    fun setIgnoreNomedia(v: Boolean)      = viewModelScope.launch { settingsRepository.setIgnoreNomedia(v) }
    fun setCodecPreference(v: String)     = viewModelScope.launch { settingsRepository.setCodecPreference(v) }
    fun resetDefaults()                   = viewModelScope.launch { settingsRepository.resetToDefaults() }

    // ── Update / Download ─────────────────────────────────────────────────────

    fun checkForUpdate() {
        if (_uiState.value.updateCheck.isChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateCheck = UpdateCheckState(isChecking = true)) }
            AppUpdateChecker.checkForUpdate(BuildConfig.VERSION_FULL).fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(updateCheck = UpdateCheckState(
                            isChecking = false,
                            result     = info,
                            checked    = true,
                        ))
                    }
                },
                onFailure = { _ ->
                    _uiState.update {
                        it.copy(updateCheck = UpdateCheckState(
                            isChecking = false,
                            error      = "Gagal memeriksa pembaruan. Periksa koneksi internet.",
                            checked    = true,
                        ))
                    }
                },
            )
        }
    }

    fun startDownload(context: android.content.Context, apkUrl: String, versionName: String) {
        val uc = _uiState.value.updateCheck
        if (uc.downloadState == DownloadState.DOWNLOADING) return

        val downloadId = AppUpdateChecker.downloadApk(context, apkUrl, versionName)
        if (downloadId == -1L) {
            _uiState.update {
                it.copy(updateCheck = uc.copy(downloadState = DownloadState.FAILED))
            }
            return
        }

        _uiState.update {
            it.copy(updateCheck = uc.copy(
                downloadState    = DownloadState.DOWNLOADING,
                downloadId       = downloadId,
                downloadProgress = 0,
            ))
        }

        // Poll download progress
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(600)
                val (progress, status) = AppUpdateChecker.getDownloadProgress(context, downloadId)
                val newState = when (status) {
                    AppUpdateChecker.DownloadStatus.DONE   -> DownloadState.DONE
                    AppUpdateChecker.DownloadStatus.FAILED -> DownloadState.FAILED
                    else                                   -> DownloadState.DOWNLOADING
                }
                _uiState.update { s ->
                    s.copy(updateCheck = s.updateCheck.copy(
                        downloadProgress = if (progress >= 0) progress else s.updateCheck.downloadProgress,
                        downloadState    = newState,
                    ))
                }
                if (newState == DownloadState.DONE || newState == DownloadState.FAILED) break
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
    }
}

// ─── Settings Navigation (Opsi B: in-screen AnimatedContent) ─────────────────

enum class SettingsPage {
    PLAYBACK, SUBTITLE, GESTURE, APPEARANCE, FILE, ADVANCED
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    var currentPage by remember { mutableStateOf<SettingsPage?>(null) }
    val context = LocalContext.current

    // BackHandler: intercept back on sub-pages
    BackHandler(enabled = currentPage != null) { currentPage = null }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            if (targetState != null) {
                // Entering sub-page: slide in from right
                slideInHorizontally(tween(250)) { it } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(150))
            } else {
                // Going back to index: slide in from left
                slideInHorizontally(tween(250)) { -it / 3 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally(tween(250)) { it } + fadeOut(tween(150))
            }
        },
        label = "settings_nav",
    ) { page ->
        when (page) {
            null ->
                SettingsIndexPage(
                    onNavigate   = { currentPage = it },
                    onBack       = onBack,
                    onAboutClick = onAboutClick,
                )
            SettingsPage.PLAYBACK    -> PlaybackSettingsPage(s, viewModel) { currentPage = null }
            SettingsPage.SUBTITLE    -> SubtitleSettingsPage(s, viewModel) { currentPage = null }
            SettingsPage.GESTURE     -> GestureSettingsPage(s, viewModel) { currentPage = null }
            SettingsPage.APPEARANCE  -> AppearanceSettingsPage(s, viewModel) { currentPage = null }
            SettingsPage.FILE        -> FileSettingsPage(s, viewModel) { currentPage = null }
            SettingsPage.ADVANCED    -> AdvancedSettingsPage(s, viewModel, context, onAboutClick) { currentPage = null }
        }
    }
}

// ─── Settings Index Page ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsIndexPage(
    onNavigate:   (SettingsPage) -> Unit,
    onBack:       () -> Unit,
    onAboutClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsNavCard(
                    icon     = Icons.Default.PlayCircle,
                    title    = "Pemutaran",
                    subtitle = "Kecepatan, ingat posisi, PiP, background…",
                    onClick  = { onNavigate(SettingsPage.PLAYBACK) },
                )
            }
            item {
                SettingsNavCard(
                    icon     = Icons.Default.Subtitles,
                    title    = "Subtitle",
                    subtitle = "Font, ukuran, tampilkan subtitle…",
                    onClick  = { onNavigate(SettingsPage.SUBTITLE) },
                )
            }
            item {
                SettingsNavCard(
                    icon     = Icons.Default.TouchApp,
                    title    = "Gerakan",
                    subtitle = "Seek, kecerahan, volume…",
                    onClick  = { onNavigate(SettingsPage.GESTURE) },
                )
            }
            item {
                SettingsNavCard(
                    icon     = Icons.Default.Palette,
                    title    = "Tampilan",
                    subtitle = "Tema, AMOLED, animasi, notch…",
                    onClick  = { onNavigate(SettingsPage.APPEARANCE) },
                )
            }
            item {
                SettingsNavCard(
                    icon     = Icons.Default.FolderOpen,
                    title    = "File & Media",
                    subtitle = "File tersembunyi, .nomedia…",
                    onClick  = { onNavigate(SettingsPage.FILE) },
                )
            }
            item {
                SettingsNavCard(
                    icon     = Icons.Default.Tune,
                    title    = "Lanjutan",
                    subtitle = "Codec, hardware decode, pembaruan…",
                    onClick  = { onNavigate(SettingsPage.ADVANCED) },
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                // Tentang: versi app
                ListItem(
                    headlineContent   = { Text("RyouPlayer") },
                    supportingContent = {
                        Text(
                            "v${BuildConfig.VERSION_FULL}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        TextButton(onClick = onAboutClick) { Text("Lihat perubahan") }
                    },
                )
            }
        }
    }
}

// ─── Settings NavCard ─────────────────────────────────────────────────────────

@Composable
private fun SettingsNavCard(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    title:   String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon container
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null,
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Sub-Page: Pemutaran ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSettingsPage(
    s:        SettingsUiState,
    vm:       SettingsViewModel,
    onBack:   () -> Unit,
) {
    var showSpeedDialog     by remember { mutableStateOf(false) }
    var showDoubleTapDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { SubPageTopBar("Pemutaran", onBack) }) { ip ->
        LazyColumn(Modifier.fillMaxSize().padding(ip),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SwitchSetting(Icons.Default.Memory, "Hardware Decoding",
                    "Gunakan GPU untuk decode video (disarankan)",
                    s.hardwareDecoding, vm::setHardwareDecoding)
            }
            item {
                SwitchSetting(Icons.Default.History, "Ingat Posisi",
                    "Lanjutkan dari posisi terakhir",
                    s.rememberPosition, vm::setRememberPosition)
            }
            item {
                ValueSetting(Icons.Default.Speed, "Kecepatan Default",
                    "${s.defaultSpeed}×", "Kecepatan putar awal saat membuka video",
                    onClick = { showSpeedDialog = true })
            }
            item {
                ValueSetting(Icons.Default.TouchApp, "Detik Double-tap",
                    "${s.doubleTapSeconds} dtk", "Durasi maju/mundur saat double-tap",
                    onClick = { showDoubleTapDialog = true })
            }
            item {
                SwitchSetting(Icons.Default.MusicNote, "Background Playback",
                    "Lanjutkan audio saat layar mati",
                    s.backgroundPlay, vm::setBackgroundPlay)
            }
            item {
                SwitchSetting(Icons.Default.PictureInPictureAlt, "Picture-in-Picture",
                    "Tampilkan player kecil saat keluar aplikasi",
                    s.pipEnabled, vm::setPipEnabled)
            }
        }
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
        AlertDialog(onDismissRequest = { showSpeedDialog = false },
            title = { Text("Kecepatan Default") },
            text = {
                Column { speeds.forEach { spd ->
                    Row(Modifier.fillMaxWidth()
                        .clickable { vm.setDefaultSpeed(spd); showSpeedDialog = false }
                        .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.defaultSpeed == spd,
                            { vm.setDefaultSpeed(spd); showSpeedDialog = false })
                        Spacer(Modifier.width(8.dp)); Text("${spd}×")
                    }
                }}
            },
            confirmButton = { TextButton({ showSpeedDialog = false }) { Text("Tutup") } })
    }

    if (showDoubleTapDialog) {
        AlertDialog(onDismissRequest = { showDoubleTapDialog = false },
            title = { Text("Detik Double-tap") },
            text = {
                Column { listOf(5, 10, 15, 20, 30).forEach { sec ->
                    Row(Modifier.fillMaxWidth()
                        .clickable { vm.setDoubleTapSeconds(sec); showDoubleTapDialog = false }
                        .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.doubleTapSeconds == sec,
                            { vm.setDoubleTapSeconds(sec); showDoubleTapDialog = false })
                        Spacer(Modifier.width(8.dp)); Text("$sec detik")
                    }
                }}
            },
            confirmButton = { TextButton({ showDoubleTapDialog = false }) { Text("Tutup") } })
    }
}

// ─── Sub-Page: Subtitle ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleSettingsPage(
    s:      SettingsUiState,
    vm:     SettingsViewModel,
    onBack: () -> Unit,
) {
    var showFontSizeDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { SubPageTopBar("Subtitle", onBack) }) { ip ->
        LazyColumn(Modifier.fillMaxSize().padding(ip),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SwitchSetting(Icons.Default.Subtitles, "Tampilkan Subtitle",
                    "Aktifkan subtitle secara default",
                    s.subtitleEnabled, vm::setSubtitleEnabled)
            }
            item {
                ValueSetting(Icons.Default.TextFields, "Ukuran Font Subtitle",
                    "${s.subtitleFontSize}sp", "Ukuran teks subtitle (10–36sp)",
                    onClick = { showFontSizeDialog = true })
            }
        }
    }

    if (showFontSizeDialog) {
        var sliderValue by remember { mutableFloatStateOf(s.subtitleFontSize.toFloat()) }
        AlertDialog(
            onDismissRequest = { vm.setSubtitleFontSize(sliderValue.roundToInt()); showFontSizeDialog = false },
            title = { Text("Ukuran Font Subtitle") },
            text = {
                Column {
                    Text("${sliderValue.roundToInt()}sp",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Slider(sliderValue, { sliderValue = it }, valueRange = 10f..36f, steps = 25)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("10sp", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("36sp", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton({ vm.setSubtitleFontSize(sliderValue.roundToInt()); showFontSizeDialog = false }) { Text("OK") }
            },
            dismissButton = { TextButton({ showFontSizeDialog = false }) { Text("Batal") } },
        )
    }
}

// ─── Sub-Page: Gerakan ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureSettingsPage(
    s:      SettingsUiState,
    vm:     SettingsViewModel,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { SubPageTopBar("Gerakan", onBack) }) { ip ->
        LazyColumn(Modifier.fillMaxSize().padding(ip),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SwitchSetting(Icons.Default.SwipeRight, "Seek (Geser Horizontal)",
                    "Geser horizontal untuk maju/mundur",
                    s.gestureSeek, vm::setGestureSeek)
            }
            item {
                SwitchSetting(Icons.Default.Brightness6, "Kecerahan (Geser Kiri)",
                    "Geser vertikal di sisi kiri untuk kecerahan",
                    s.gestureBrightness, vm::setGestureBrightness)
            }
            item {
                SwitchSetting(Icons.AutoMirrored.Filled.VolumeUp, "Volume (Geser Kanan)",
                    "Geser vertikal di sisi kanan untuk volume",
                    s.gestureVolume, vm::setGestureVolume)
            }
        }
    }
}

// ─── Sub-Page: Tampilan ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsPage(
    s:      SettingsUiState,
    vm:     SettingsViewModel,
    onBack: () -> Unit,
) {
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { SubPageTopBar("Tampilan", onBack) }) { ip ->
        LazyColumn(Modifier.fillMaxSize().padding(ip),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                ValueSetting(Icons.Default.DarkMode, "Tema",
                    when (s.themeMode) { "DARK" -> "Gelap"; "LIGHT" -> "Terang"; else -> "Sistem" },
                    "Pilih tema tampilan aplikasi",
                    onClick = { showThemeDialog = true })
            }
            item {
                SwitchSetting(Icons.Default.PhoneAndroid, "AMOLED / Pure Black",
                    "Latar belakang hitam pekat di mode gelap",
                    s.amoledMode, vm::setAmoledMode,
                    enabled = s.themeMode != "LIGHT")
            }
            item {
                SwitchSetting(Icons.Default.Palette, "Dynamic Color",
                    "Gunakan warna wallpaper (Android 12+)",
                    s.useSystemColor, vm::setUseSystemColor)
            }
            item {
                SwitchSetting(Icons.Default.Bolt, "Animasi UI",
                    "Aktifkan animasi transisi antarmuka",
                    s.animationsEnabled, vm::setAnimations)
            }
            item {
                SwitchSetting(Icons.Default.Fullscreen, "Abaikan Notch",
                    "Perluas video ke area notch dan punch-hole kamera",
                    s.ignoreNotch, vm::setIgnoreNotch)
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(onDismissRequest = { showThemeDialog = false },
            title = { Text("Tema") },
            text = {
                Column { listOf("SYSTEM" to "Ikuti sistem", "LIGHT" to "Terang", "DARK" to "Gelap")
                    .forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { vm.setThemeMode(value); showThemeDialog = false }
                            .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(s.themeMode == value,
                                { vm.setThemeMode(value); showThemeDialog = false })
                            Spacer(Modifier.width(8.dp)); Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showThemeDialog = false }) { Text("Tutup") } })
    }
}

// ─── Sub-Page: File & Media ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSettingsPage(
    s:      SettingsUiState,
    vm:     SettingsViewModel,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { SubPageTopBar("File & Media", onBack) }) { ip ->
        LazyColumn(Modifier.fillMaxSize().padding(ip),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                SwitchSetting(Icons.Default.FolderOpen, "Tampilkan File Tersembunyi",
                    "Tampilkan file dan folder yang diawali '.'",
                    s.showHiddenFiles, vm::setShowHiddenFiles)
            }
            item {
                SwitchSetting(Icons.Default.VisibilityOff, "Abaikan .nomedia",
                    "Tampilkan folder .nomedia (perlu re-indeks manual)",
                    s.ignoreNomedia, vm::setIgnoreNomedia)
            }
        }
    }
}

// ─── Sub-Page: Lanjutan ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSettingsPage(
    s:            SettingsUiState,
    vm:           SettingsViewModel,
    context:      android.content.Context,
    onAboutClick: () -> Unit,
    onBack:       () -> Unit,
) {
    var showResetDialog  by remember { mutableStateOf(false) }
    var showCodecDialog  by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { SubPageTopBar("Lanjutan", onBack) }) { ip ->
        LazyColumn(Modifier.fillMaxSize().padding(ip),
            contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                ValueSetting(Icons.Default.Tune, "Preferensi Codec",
                    when (s.codecPreference) { "SOFTWARE" -> "Software"; "HARDWARE" -> "Hardware"; else -> "Otomatis" },
                    "Pilihan decoder video", onClick = { showCodecDialog = true })
            }
            item {
                val uc = s.updateCheck
                ListItem(
                    headlineContent = { Text("Periksa Pembaruan") },
                    supportingContent = {
                        when {
                            uc.isChecking         -> Text("Memeriksa…")
                            uc.error != null       -> Text(uc.error, color = MaterialTheme.colorScheme.error)
                            uc.result?.hasUpdate == true ->
                                Text("Pembaruan tersedia: v${uc.result.latestVersion}",
                                    color = MaterialTheme.colorScheme.primary)
                            uc.result != null     -> Text("Sudah versi terbaru (v${uc.result.currentVersion})")
                            else                   -> Text("Ketuk untuk memeriksa pembaruan")
                        }
                    },
                    leadingContent = {
                        if (uc.isChecking)
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else
                            Icon(
                                if (uc.result?.hasUpdate == true) Icons.Default.SystemUpdate else Icons.Default.Sync,
                                null,
                                tint = if (uc.result?.hasUpdate == true)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    },
                    modifier = Modifier.clickable(enabled = !uc.isChecking) {
                        vm.checkForUpdate(); showUpdateDialog = true
                    },
                )
            }
            item {
                ClickableSetting(Icons.Default.Info, "Tentang Aplikasi",
                    "Versi ${BuildConfig.VERSION_FULL}",
                    onClick = onAboutClick)
            }
            item {
                ClickableSetting(Icons.Default.Refresh, "Reset ke Default",
                    "Kembalikan semua pengaturan ke awal",
                    onClick = { showResetDialog = true },
                    tint    = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showCodecDialog) {
        AlertDialog(onDismissRequest = { showCodecDialog = false },
            title = { Text("Preferensi Codec") },
            text = {
                Column { listOf("AUTO" to "Otomatis", "SOFTWARE" to "Software", "HARDWARE" to "Hardware")
                    .forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { vm.setCodecPreference(value); showCodecDialog = false }
                            .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(s.codecPreference == value,
                                { vm.setCodecPreference(value); showCodecDialog = false })
                            Spacer(Modifier.width(8.dp)); Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showCodecDialog = false }) { Text("Tutup") } })
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title   = { Text("Reset Pengaturan") },
            text    = { Text("Semua pengaturan akan dikembalikan ke default. Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.resetDefaults(); showResetDialog = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton({ showResetDialog = false }) { Text("Batal") } },
        )
    }

    if (showUpdateDialog) {
        val uc = s.updateCheck
        UpdateDialog(
            state    = uc,
            onDismiss = { showUpdateDialog = false },
            onDownload = {
                val apkUrl  = uc.result?.apkDownloadUrl
                val version = uc.result?.latestVersion ?: ""
                if (apkUrl != null) {
                    vm.startDownload(context, apkUrl, version)
                } else {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse(uc.result?.releasePageUrl ?: "https://github.com/ryoustream/RyouPlayer/releases"))
                    )
                }
            },
            onInstall = {
                val version = uc.result?.latestVersion ?: ""
                val installed = AppUpdateChecker.installApk(context, version)
                if (!installed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }
            },
            onOpenReleasePage = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse(uc.result?.releasePageUrl ?: "https://github.com/ryoustream/RyouPlayer/releases"))
                )
                showUpdateDialog = false
            },
        )
    }
}

// ─── SubPageTopBar ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPageTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
        },
    )
}

// ─── Update Dialog (terpisah agar lebih rapi) ─────────────────────────────────

@Composable
private fun UpdateDialog(
    state: UpdateCheckState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    val uc = state
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            when {
                uc.isChecking -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                uc.result?.hasUpdate == true -> Icon(Icons.Default.SystemUpdate, null,
                    tint = MaterialTheme.colorScheme.primary)
                else -> Icon(Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.secondary)
            }
        },
        title = {
            Text(when {
                uc.isChecking              -> "Memeriksa Pembaruan…"
                uc.result?.hasUpdate == true -> "Pembaruan Tersedia"
                uc.error != null           -> "Gagal Memeriksa"
                uc.result != null          -> "Sudah Terbaru"
                else                       -> "Memeriksa Pembaruan…"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    uc.isChecking ->
                        Text("Sedang memeriksa versi terbaru di GitHub…")
                    uc.error != null ->
                        Text(uc.error)
                    uc.result != null && uc.result.hasUpdate -> {
                        Text("Versi ${uc.result.latestVersion} tersedia.\nVersi terpasang: ${uc.result.currentVersion}")

                        // Download progress
                        if (uc.downloadState == DownloadState.DOWNLOADING) {
                            Spacer(Modifier.height(4.dp))
                            Text("Mengunduh… ${uc.downloadProgress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(
                                progress = { uc.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (uc.downloadState == DownloadState.DONE) {
                            Text("Unduhan selesai! Ketuk Pasang untuk melanjutkan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        } else if (uc.downloadState == DownloadState.FAILED) {
                            Text("Unduhan gagal. Coba unduh manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    uc.result != null ->
                        Text("RyouPlayer ${uc.result.currentVersion} sudah versi terbaru.")
                    else ->
                        Text("Sedang memeriksa…")
                }
            }
        },
        confirmButton = {
            when {
                uc.result?.hasUpdate == true && uc.downloadState == DownloadState.IDLE -> {
                    // Tombol unduh
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (uc.result.apkDownloadUrl != null) "Unduh Otomatis" else "Unduh Manual")
                    }
                }
                uc.result?.hasUpdate == true && uc.downloadState == DownloadState.DOWNLOADING -> {
                    // Sedang mengunduh
                    OutlinedButton(onClick = onDismiss) { Text("Tutup") }
                }
                uc.result?.hasUpdate == true && uc.downloadState == DownloadState.DONE -> {
                    // Tombol pasang
                    Button(onClick = onInstall) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pasang Sekarang")
                    }
                }
                uc.result?.hasUpdate == true && uc.downloadState == DownloadState.FAILED -> {
                    TextButton(onClick = onOpenReleasePage) { Text("Unduh Manual") }
                }
                !uc.isChecking -> {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        },
        dismissButton = when {
            uc.result?.hasUpdate == true && uc.downloadState == DownloadState.IDLE ->
                ({ TextButton(onClick = onDismiss) { Text("Nanti") } })
            uc.result?.hasUpdate == true && uc.downloadState == DownloadState.DONE ->
                ({ TextButton(onClick = onOpenReleasePage) { Text("Lihat Rilis") } })
            else -> null
        },
    )
}

// ─── Setting Components ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        modifier = Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange, enabled = enabled)
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun ClickableSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    ListItem(
        headlineContent = { Text(title, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}

@Composable
private fun ValueSetting(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String = "",
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle.isNotBlank()) ({
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }) else null,
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
    )
}
