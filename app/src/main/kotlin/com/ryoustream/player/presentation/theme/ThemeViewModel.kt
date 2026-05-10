package com.ryoustream.player.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ThemeState(
    val themeMode: String = "SYSTEM",   // "SYSTEM", "DARK", "LIGHT"
    val amoledMode: Boolean = false,
    val useDynamicColor: Boolean = true,
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeState: StateFlow<ThemeState> = combine(
        settingsRepository.themeMode,
        settingsRepository.amoledMode,
        settingsRepository.useSystemColor,
    ) { mode, amoled, dynamic ->
        ThemeState(
            themeMode = mode,
            amoledMode = amoled,
            useDynamicColor = dynamic,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeState(),
    )
}
