package com.ryoustream.player.presentation.settings

import android.content.Intent
import android.net.Uri
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
    /** True once a check has been attempted (success or failure). */
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
    fun setIgnoreNomedia(v: Boolean) = viewModelScope.launch { settingsRepository.setIgnoreNomedia(v) }
    fun resetDefaults() = viewModelScope.launch { settingsRepository.resetToDefaults() }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── PLAYBACK ──────────────────────────────────────────────────────
            item { SectionHeader("Playback") }
            item {
                SwitchSetting(
                    icon = Icons.Default.Memory,
                    title = "Hardware Decoding",
                    subtitle = "Use GPU for video decoding (recommended)",
                    checked = s.hardwareDecoding,
                    onCheckedChange = viewModel::setHardwareDecoding,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Subtitles,
                    title = "Subtitles",
                    subtitle = "Enable subtitle display by default",
                    checked = s.subtitleEnabled,
                    onCheckedChange = viewModel::setSubtitleEnabled,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.History,
                    title = "Remember Position",
                    subtitle = "Resume videos from where you left off",
                    checked = s.rememberPosition,
                    onCheckedChange = viewModel::setRememberPosition,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.PictureInPictureAlt,
                    title = "Picture-in-Picture",
                    subtitle = "Float player when leaving the app",
                    checked = s.pipEnabled,
                    onCheckedChange = viewModel::setPipEnabled,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.MusicNote,
                    title = "Background Playback",
                    subtitle = "Continue audio when screen is off",
                    checked = s.backgroundPlay,
                    onCheckedChange = viewModel::setBackgroundPlay,
                )
            }

            // ── GESTURES ─────────────────────────────────────────────────────
            item { SectionHeader("Gestures") }
            item {
                SwitchSetting(
                    icon = Icons.Default.SwipeRight,
                    title = "Seek Gesture",
                    subtitle = "Horizontal swipe to seek",
                    checked = s.gestureSeek,
                    onCheckedChange = viewModel::setGestureSeek,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Brightness6,
                    title = "Brightness Gesture",
                    subtitle = "Vertical swipe on left side",
                    checked = s.gestureBrightness,
                    onCheckedChange = viewModel::setGestureBrightness,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Volume Gesture",
                    subtitle = "Vertical swipe on right side",
                    checked = s.gestureVolume,
                    onCheckedChange = viewModel::setGestureVolume,
                )
            }

            // ── APPEARANCE ────────────────────────────────────────────────────
            item { SectionHeader("Appearance") }
            item {
                ClickableSetting(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = when (s.themeMode) {
                        "DARK" -> "Dark"
                        "LIGHT" -> "Light"
                        else -> "Follow system"
                    },
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.PhoneAndroid,
                    title = "AMOLED / Pure Black",
                    subtitle = "True black background in dark mode",
                    checked = s.amoledMode,
                    onCheckedChange = viewModel::setAmoledMode,
                    enabled = s.themeMode != "LIGHT",
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Palette,
                    title = "Dynamic Color (Material You)",
                    subtitle = "Use wallpaper colors (Android 12+)",
                    checked = s.useSystemColor,
                    onCheckedChange = viewModel::setUseSystemColor,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Bolt,
                    title = "Animations",
                    subtitle = "Enable UI transition animations",
                    checked = s.animationsEnabled,
                    onCheckedChange = viewModel::setAnimations,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.Fullscreen,
                    title = "Ignore Notch / Display Cutout",
                    subtitle = "Extend video behind notch and hole-punch cameras",
                    checked = s.ignoreNotch,
                    onCheckedChange = viewModel::setIgnoreNotch,
                )
            }
            // ── ADVANCED ──────────────────────────────────────────────────────
            item { SectionHeader("Advanced") }
            item {
                SwitchSetting(
                    icon = Icons.Default.FolderOpen,
                    title = "Show Hidden Files",
                    subtitle = "Include files and folders starting with '.'",
                    checked = s.showHiddenFiles,
                    onCheckedChange = viewModel::setShowHiddenFiles,
                )
            }
            item {
                SwitchSetting(
                    icon = Icons.Default.VisibilityOff,
                    title = "Ignore .nomedia",
                    subtitle = "Mark intent to show .nomedia folders (Android limits MediaStore access; files must be re-indexed manually)",
                    checked = s.ignoreNomedia,
                    onCheckedChange = viewModel::setIgnoreNomedia,
                )
            }
            item {
                ClickableSetting(
                    icon = Icons.Default.Refresh,
                    title = "Reset to Defaults",
                    subtitle = "Restore all settings",
                    onClick = { showResetDialog = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            // ── ABOUT ─────────────────────────────────────────────────────────
            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Ryou Player") },
                    supportingContent = { Text(BuildConfig.VERSION_FULL) },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
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

        // Theme dialog
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Theme") },
                text = {
                    Column {
                        listOf("SYSTEM" to "Follow system", "LIGHT" to "Light", "DARK" to "Dark")
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
                    TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
                },
            )
        }

        // Reset dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Settings") },
                text = { Text("All settings will be restored to defaults. This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.resetDefaults(); showResetDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reset") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
