package com.ryoustream.player.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Update Check State ───────────────────────────────────────────────────────

data class UpdateCheckState(
    val isChecking: Boolean     = false,
    val result: UpdateInfo?     = null,
    val error: String?          = null,
    val checked: Boolean        = false,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SettingsUiState(
    // Playback
    val hardwareDecoding: Boolean = true,
    val subtitleEnabled: Boolean = true,
    val subtitleFontSize: Int = 16,
    val rememberPosition: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val doubleTapSeconds: Int = 10,
    val pipEnabled: Boolean = true,
    val backgroundPlay: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    // UI
    val themeMode: String = "SYSTEM",
    val amoledMode: Boolean = false,
    val useSystemColor: Boolean = true,
    val animationsEnabled: Boolean = true,
    val ignoreNotch: Boolean = false,
    // Advanced
    val networkBuffer: Int = 32,
    val cacheSize: Int = 256,
    val codecPreference: String = "AUTO",
    val showHiddenFiles: Boolean = false,
    val ignoreNomedia: Boolean = false,
    // Update
    val updateCheck: UpdateCheckState = UpdateCheckState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
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
                        subtitleEnabled = sub,
                        subtitleFontSize = subSize,
                        rememberPosition = remPos,
                        gestureSeek = gesSeek,
                    )
                }
            }.collect()
        }
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
                        gestureVolume = vol,
                        doubleTapSeconds = dtSec,
                        pipEnabled = pip,
                        backgroundPlay = bgPlay,
                    )
                }
            }.collect()
        }
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
                        themeMode = theme,
                        amoledMode = amoled,
                        useSystemColor = sysColor,
                        animationsEnabled = anim,
                        showHiddenFiles = hidden,
                    )
                }
            }.collect()
        }
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
    }

    fun setHardwareDecoding(v: Boolean) = viewModelScope.launch { settingsRepository.setHardwareDecoding(v) }
    fun setSubtitleEnabled(v: Boolean) = viewModelScope.launch { settingsRepository.setSubtitleEnabled(v) }
    fun setRememberPosition(v: Boolean) = viewModelScope.launch { settingsRepository.setRememberPosition(v) }
    fun setGestureSeek(v: Boolean) = viewModelScope.launch { settingsRepository.setGestureSeek(v) }
    fun setGestureBrightness(v: Boolean) = viewModelScope.launch { settingsRepository.setGestureBrightness(v) }
    fun setGestureVolume(v: Boolean) = viewModelScope.launch { settingsRepository.setGestureVolume(v) }
    fun setPipEnabled(v: Boolean) = viewModelScope.launch { settingsRepository.setPipEnabled(v) }
    fun setBackgroundPlay(v: Boolean) = viewModelScope.launch { settingsRepository.setBackgroundPlay(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { settingsRepository.setThemeMode(v) }
    fun setAmoledMode(v: Boolean) = viewModelScope.launch { settingsRepository.setAmoledMode(v) }
    fun setUseSystemColor(v: Boolean) = viewModelScope.launch { settingsRepository.setUseSystemColor(v) }
    fun setAnimations(v: Boolean) = viewModelScope.launch { settingsRepository.setAnimationsEnabled(v) }
    fun setIgnoreNotch(v: Boolean) = viewModelScope.launch { settingsRepository.setIgnoreNotch(v) }
    fun setShowHiddenFiles(v: Boolean) = viewModelScope.launch { settingsRepository.setShowHiddenFiles(v) }
    fun setIgnoreNomedia(v: Boolean) = viewModelScope.launch { settingsRepository.setIgnoreNomedia(v) }
    fun resetDefaults() = viewModelScope.launch { settingsRepository.resetToDefaults() }

    fun checkForUpdate() {
        if (_uiState.value.updateCheck.isChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(updateCheck = UpdateCheckState(isChecking = true)) }
            AppUpdateChecker.checkForUpdate(BuildConfig.VERSION_FULL).fold(
                onSuccess = { info ->
                    _uiState.update { it.copy(updateCheck = UpdateCheckState(
                        isChecking = false,
                        result     = info,
                        checked    = true,
                    ))}
                },
                onFailure = { _ ->
                    _uiState.update { it.copy(updateCheck = UpdateCheckState(
                        isChecking = false,
                        error      = "Gagal memeriksa pembaruan. Periksa koneksi internet.",
                        checked    = true,
                    ))}
                },
            )
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAboutClick: () -> Unit = {},       // B5: navigate ke AboutScreen
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    var showResetDialog      by remember { mutableStateOf(false) }
    var showThemeDialog      by remember { mutableStateOf(false) }
    var showUpdateDialog     by remember { mutableStateOf(false) }
    var showSpeedDialog      by remember { mutableStateOf(false) }
    var showDoubleTapDialog  by remember { mutableStateOf(false) }
    var showFontSizeDialog   by remember { mutableStateOf(false) }
    var showCodecDialog      by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── PEMUTARAN ─────────────────────────────────────────────────────
            item { SectionHeader("Pemutaran") }
            item {
                SwitchSetting(
                    icon = Icons.Default.Memory,
                    title = "Hardware Decoding",
                    subtitle = "Gunakan GPU untuk decode video (disarankan)",
                    checked = s.hardwareDecoding,
                    onCheckedChange = viewModel::setHardwareDecoding,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.History,
                    title = "Ingat Posisi",
                    subtitle = "Lanjutkan dari posisi terakhir",
                    checked = s.rememberPosition,
                    onCheckedChange = viewModel::setRememberPosition,
                )
            }
            item {
                ValueSetting(
                    icon    = Icons.Default.Speed,
                    title   = "Kecepatan Default",
                    value   = "${s.defaultSpeed}×",
                    subtitle = "Kecepatan putar awal saat membuka video",
                    onClick = { showSpeedDialog = true },
                )
            }
            item {
                ValueSetting(
                    icon    = Icons.Default.TouchApp,
                    title   = "Detik Double-tap",
                    value   = "${s.doubleTapSeconds} dtk",
                    subtitle = "Durasi maju/mundur saat double-tap",
                    onClick = { showDoubleTapDialog = true },
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.MusicNote,
                    title = "Background Playback",
                    subtitle = "Lanjutkan audio saat layar mati",
                    checked = s.backgroundPlay,
                    onCheckedChange = viewModel::setBackgroundPlay,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.PictureInPictureAlt,
                    title = "Picture-in-Picture",
                    subtitle = "Tampilkan player kecil saat keluar aplikasi",
                    checked = s.pipEnabled,
                    onCheckedChange = viewModel::setPipEnabled,
                )
            }

            // ── SUBTITLE ──────────────────────────────────────────────────────
            item { SectionHeader("Subtitle") }
            item {
                SwitchSetting(
                    icon = Icons.Default.Subtitles,
                    title = "Tampilkan Subtitle",
                    subtitle = "Aktifkan subtitle secara default",
                    checked = s.subtitleEnabled,
                    onCheckedChange = viewModel::setSubtitleEnabled,
                )
            }
            item {
                ValueSetting(
                    icon    = Icons.Default.TextFields,
                    title   = "Ukuran Font Subtitle",
                    value   = "${s.subtitleFontSize}sp",
                    subtitle = "Ukuran teks subtitle (10–36sp)",
                    onClick = { showFontSizeDialog = true },
                )
            }

            // ── GERAKAN ───────────────────────────────────────────────────────
            item { SectionHeader("Gerakan") }
            item {
                SwitchSetting(
                    icon = Icons.Default.SwipeRight,
                    title = "Seek (Geser Horizontal)",
                    subtitle = "Geser horizontal untuk maju/mundur",
                    checked = s.gestureSeek,
                    onCheckedChange = viewModel::setGestureSeek,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Brightness6,
                    title = "Kecerahan (Geser Kiri)",
                    subtitle = "Geser vertikal di sisi kiri untuk kecerahan",
                    checked = s.gestureBrightness,
                    onCheckedChange = viewModel::setGestureBrightness,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Volume (Geser Kanan)",
                    subtitle = "Geser vertikal di sisi kanan untuk volume",
                    checked = s.gestureVolume,
                    onCheckedChange = viewModel::setGestureVolume,
                )
            }

            // ── TAMPILAN ──────────────────────────────────────────────────────
            item { SectionHeader("Tampilan") }
            item {
                ValueSetting(
                    icon    = Icons.Default.DarkMode,
                    title   = "Tema",
                    value   = when (s.themeMode) {
                        "DARK"  -> "Gelap"
                        "LIGHT" -> "Terang"
                        else    -> "Sistem"
                    },
                    subtitle = "Pilih tema tampilan aplikasi",
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.PhoneAndroid,
                    title = "AMOLED / Pure Black",
                    subtitle = "Latar belakang hitam pekat di mode gelap",
                    checked = s.amoledMode,
                    onCheckedChange = viewModel::setAmoledMode,
                    enabled = s.themeMode != "LIGHT",
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Palette,
                    title = "Dynamic Color",
                    subtitle = "Gunakan warna wallpaper (Android 12+)",
                    checked = s.useSystemColor,
                    onCheckedChange = viewModel::setUseSystemColor,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Bolt,
                    title = "Animasi UI",
                    subtitle = "Aktifkan animasi transisi antarmuka",
                    checked = s.animationsEnabled,
                    onCheckedChange = viewModel::setAnimations,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Fullscreen,
                    title = "Abaikan Notch",
                    subtitle = "Perluas video ke area notch dan punch-hole kamera",
                    checked = s.ignoreNotch,
                    onCheckedChange = viewModel::setIgnoreNotch,
                )
            }

            // ── FILE & MEDIA ──────────────────────────────────────────────────
            item { SectionHeader("File & Media") }
            item {
                SwitchSetting(
                    icon = Icons.Default.FolderOpen,
                    title = "Tampilkan File Tersembunyi",
                    subtitle = "Tampilkan file dan folder yang diawali '.'",
                    checked = s.showHiddenFiles,
                    onCheckedChange = viewModel::setShowHiddenFiles,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.VisibilityOff,
                    title = "Abaikan .nomedia",
                    subtitle = "Tampilkan folder .nomedia (perlu re-indeks manual)",
                    checked = s.ignoreNomedia,
                    onCheckedChange = viewModel::setIgnoreNomedia,
                )
            }

            // ── LANJUTAN ─────────────────────────────────────────────────────
            item { SectionHeader("Lanjutan") }
            item {
                ValueSetting(
                    icon    = Icons.Default.Tune,
                    title   = "Preferensi Codec",
                    value   = when (s.codecPreference) {
                        "SOFTWARE" -> "Software"
                        "HARDWARE" -> "Hardware"
                        else        -> "Otomatis"
                    },
                    subtitle = "Pilihan decoder video",
                    onClick = { showCodecDialog = true },
                )
            }
            item {
                ClickableSetting(
                    icon = Icons.Default.Refresh,
                    title = "Reset ke Default",
                    subtitle = "Kembalikan semua pengaturan ke awal",
                    onClick = { showResetDialog = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            // ── TENTANG ───────────────────────────────────────────────────────
            item { SectionHeader("Tentang") }
            // B5: link ke AboutScreen bukan duplikat ListItem versi
            item {
                ClickableSetting(
                    icon = Icons.Default.Info,
                    title = "Tentang Aplikasi",
                    subtitle = "Versi ${BuildConfig.VERSION_FULL}",
                    onClick = onAboutClick,
                )
            }
            item {
                val uc = s.updateCheck
                ListItem(
                    headlineContent = { Text("Periksa Pembaruan") },
                    supportingContent = {
                        when {
                            uc.isChecking -> Text("Memeriksa…")
                            uc.error != null -> Text(uc.error, color = MaterialTheme.colorScheme.error)
                            uc.result != null && uc.result.hasUpdate ->
                                Text("Pembaruan tersedia: v${uc.result.latestVersion}",
                                    color = MaterialTheme.colorScheme.primary)
                            uc.result != null ->
                                Text("Sudah versi terbaru (v${uc.result.currentVersion})")
                            else -> Text("Ketuk untuk memeriksa pembaruan")
                        }
                    },
                    leadingContent = {
                        if (uc.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (uc.result?.hasUpdate == true)
                                    Icons.Default.SystemUpdate
                                else
                                    Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (uc.result?.hasUpdate == true)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = !uc.isChecking) {
                        viewModel.checkForUpdate()
                        showUpdateDialog = true
                    },
                )
            }
        } // end LazyColumn

        // ── Dialogs ───────────────────────────────────────────────────────────

        // Tema
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Tema") },
                text = {
                    Column {
                        listOf("SYSTEM" to "Ikuti sistem", "LIGHT" to "Terang", "DARK" to "Gelap")
                            .forEach { (value, label) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setThemeMode(value)
                                            showThemeDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = s.themeMode == value,
                                        onClick = { viewModel.setThemeMode(value); showThemeDialog = false }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) { Text("Tutup") }
                },
            )
        }

        // Kecepatan default
        if (showSpeedDialog) {
            val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Kecepatan Default") },
                text = {
                    Column {
                        speeds.forEach { spd ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showSpeedDialog = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = s.defaultSpeed == spd,
                                    onClick  = { showSpeedDialog = false },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("${spd}×")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) { Text("Tutup") }
                },
            )
        }

        // Double-tap seconds
        if (showDoubleTapDialog) {
            val options = listOf(5, 10, 15, 20, 30)
            AlertDialog(
                onDismissRequest = { showDoubleTapDialog = false },
                title = { Text("Detik Double-tap") },
                text = {
                    Column {
                        options.forEach { sec ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showDoubleTapDialog = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = s.doubleTapSeconds == sec,
                                    onClick  = { showDoubleTapDialog = false },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$sec detik")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDoubleTapDialog = false }) { Text("Tutup") }
                },
            )
        }

        // Font size subtitle
        if (showFontSizeDialog) {
            AlertDialog(
                onDismissRequest = { showFontSizeDialog = false },
                title = { Text("Ukuran Font Subtitle") },
                text = {
                    Column {
                        Text("${s.subtitleFontSize}sp", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = s.subtitleFontSize.toFloat(),
                            onValueChange = { },
                            valueRange = 10f..36f,
                            steps = 25,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFontSizeDialog = false }) { Text("OK") }
                },
            )
        }

        // Codec preference
        if (showCodecDialog) {
            AlertDialog(
                onDismissRequest = { showCodecDialog = false },
                title = { Text("Preferensi Codec") },
                text = {
                    Column {
                        listOf("AUTO" to "Otomatis", "SOFTWARE" to "Software", "HARDWARE" to "Hardware")
                            .forEach { (value, label) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { showCodecDialog = false }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = s.codecPreference == value,
                                        onClick  = { showCodecDialog = false },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCodecDialog = false }) { Text("Tutup") }
                },
            )
        }

        // Reset dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Pengaturan") },
                text = { Text("Semua pengaturan akan dikembalikan ke default. Tindakan ini tidak dapat dibatalkan.") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.resetDefaults(); showResetDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Batal") }
                },
            )
        }

        // Update dialog
        if (showUpdateDialog) {
            val uc = s.updateCheck
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                icon = {
                    when {
                        uc.isChecking -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        uc.result?.hasUpdate == true -> Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                title = {
                    Text(when {
                        uc.isChecking            -> "Memeriksa Pembaruan…"
                        uc.result?.hasUpdate == true -> "Pembaruan Tersedia"
                        uc.error != null         -> "Gagal Memeriksa"
                        uc.result != null        -> "Sudah Terbaru"
                        else                     -> "Memeriksa Pembaruan…"
                    })
                },
                text = {
                    when {
                        uc.isChecking -> Text("Sedang memeriksa versi terbaru di GitHub…")
                        uc.error != null -> Text(uc.error)
                        uc.result != null && uc.result.hasUpdate ->
                            Text("Versi ${uc.result.latestVersion} tersedia.\nVersi terpasang: ${uc.result.currentVersion}")
                        uc.result != null ->
                            Text("RyouPlayer ${uc.result.currentVersion} sudah versi terbaru.")
                        else -> Text("Sedang memeriksa…")
                    }
                },
                confirmButton = {
                    if (uc.result?.hasUpdate == true) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uc.result.releasePageUrl)))
                            showUpdateDialog = false
                        }) { Text("Unduh") }
                    } else if (!uc.isChecking) {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("OK") }
                    }
                },
                dismissButton = if (uc.result?.hasUpdate == true) ({
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Nanti") }
                }) else null,
            )
        }
    }
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

/** Section 14: Komponen baru untuk setting yang punya nilai (bukan switch). */
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
