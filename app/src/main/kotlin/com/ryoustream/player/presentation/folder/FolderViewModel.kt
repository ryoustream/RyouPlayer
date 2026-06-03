package com.ryoustream.player.presentation.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiState(
    val isLoading: Boolean = true,
    val videos: List<MediaItem> = emptyList(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: MediaSortOrder = MediaSortOrder.NAME_ASC,
    val searchQuery: String = "",
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {

    private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        viewModelScope.launch {
            mediaRepository.getAllVideos(_uiState.value.sortOrder)
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { allVideos ->
                    val folderVideos = allVideos.filter { it.folderId == folderId }
                    _uiState.update {
                        it.copy(isLoading = false, videos = folderVideos)
                    }
                }
        }
    }

    val filteredVideos: List<MediaItem>
        get() {
            val q = _uiState.value.searchQuery.trim()
            return if (q.isBlank()) _uiState.value.videos
            else _uiState.value.videos.filter { it.displayName.contains(q, ignoreCase = true) }
        }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun clearSearch() = _uiState.update { it.copy(searchQuery = "") }

    fun onViewModeToggle() = _uiState.update {
        it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
    }

    fun onSortOrderChange(order: MediaSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        loadVideos()
    }

    fun onToggleFavorite(mediaId: Long) {
        viewModelScope.launch { toggleFavoriteUseCase(mediaId) }
    }
}
