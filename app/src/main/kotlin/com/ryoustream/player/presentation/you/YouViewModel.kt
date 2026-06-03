package com.ryoustream.player.presentation.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouUiState(
    val totalVideos: Int = 0,
    val totalDuration: Long = 0L,
    val favoriteCount: Int = 0,
    val inProgressCount: Int = 0,
)

@HiltViewModel
class YouViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouUiState())
    val uiState: StateFlow<YouUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            mediaRepository.getAllVideos()
                .catch {}
                .collect { videos ->
                    _uiState.update {
                        it.copy(
                            totalVideos = videos.size,
                            totalDuration = videos.sumOf { v -> v.duration },
                            favoriteCount = videos.count { v -> v.isFavorite },
                            inProgressCount = videos.count { v -> v.isInProgress },
                        )
                    }
                }
        }
    }
}
