package com.ryoustream.player.presentation.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
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
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()

    // Bump to force re-fetch (same pattern as HomeViewModel)
    private val _refreshKey = MutableStateFlow(0L)

    init {
        observeVideos()
        observeSettingsChanges()
    }

    private fun observeVideos() {
        viewModelScope.launch {
            _refreshKey
                .flatMapLatest { _ ->
                    mediaRepository.getAllVideos(_uiState.value.sortOrder)
                }
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false, isRefreshing = false) } }
                .collect { allVideos ->
                    val folderVideos = allVideos.filter { it.folderId == folderId }
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            isRefreshing = false,
                            videos       = folderVideos,
                        )
                    }
                }
        }
    }

    /**
     * Otomatis refresh ketika setting .nomedia atau hidden files diubah.
     * Observasi dilakukan dengan skip(1) agar tidak trigger saat init.
     */
    private fun observeSettingsChanges() {
        viewModelScope.launch {
            combine(
                settingsRepository.showHiddenFiles,
                settingsRepository.ignoreNomedia,
            ) { hidden, nomedia -> hidden to nomedia }
                .drop(1)  // lewati emisi pertama (nilai awal saat subscribe)
                .distinctUntilChanged()
                .collect {
                    _refreshKey.value = System.currentTimeMillis()
                }
        }
    }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun clearSearch() = _uiState.update { it.copy(searchQuery = "") }

    fun onViewModeToggle() = _uiState.update {
        it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
    }

    fun onSortOrderChange(order: MediaSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        _refreshKey.value = System.currentTimeMillis()
    }

    /**
     * Pull-to-refresh: set isRefreshing = true, lalu bump refresh key.
     * isRefreshing akan di-clear saat data baru tiba di observeVideos().
     */
    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            _refreshKey.value = System.currentTimeMillis()
        }
    }

    fun onToggleFavorite(mediaId: Long) {
        viewModelScope.launch { toggleFavoriteUseCase(mediaId) }
    }
}
