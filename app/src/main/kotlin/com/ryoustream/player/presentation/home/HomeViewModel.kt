package com.ryoustream.player.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val videos: List<MediaItem> = emptyList(),
    val recentVideos: List<MediaItem> = emptyList(),
    val favoriteVideos: List<MediaItem> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: MediaSortOrder = MediaSortOrder.DATE_ADDED_DESC,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectedTab: HomeTab = HomeTab.ALL,
    val error: String? = null,
)

enum class HomeTab(val label: String) {
    ALL("All"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    FOLDERS("Folders"),
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllVideosUseCase: GetAllVideosUseCase,
    private val getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val searchMediaUseCase: SearchMediaUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val rescanMediaUseCase: RescanMediaUseCase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(MediaSortOrder.DATE_ADDED_DESC)

    init {
        loadAllVideos()
        loadRecent()
        loadFavorites()
        loadFolders()
    }

    private fun loadAllVideos() {
        viewModelScope.launch {
            searchQuery
                .debounce(300L)
                .flatMapLatest { query ->
                    if (query.isBlank()) getAllVideosUseCase(sortOrder.value)
                    else searchMediaUseCase(query)
                }
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { videos ->
                    _uiState.update { it.copy(isLoading = false, videos = videos) }
                }
        }
    }

    private fun loadRecent() {
        viewModelScope.launch {
            getRecentlyPlayedUseCase(30)
                .catch { }
                .collect { recent ->
                    _uiState.update { it.copy(recentVideos = recent) }
                }
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            getFavoritesUseCase()
                .catch { }
                .collect { favorites ->
                    _uiState.update { it.copy(favoriteVideos = favorites) }
                }
        }
    }

    // FIX: Load actual folders from MediaStore
    private fun loadFolders() {
        viewModelScope.launch {
            mediaRepository.getAllFolders()
                .catch { }
                .collect { folders ->
                    _uiState.update { it.copy(folders = folders) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortOrderChange(order: MediaSortOrder) {
        sortOrder.value = order
        _uiState.update { it.copy(sortOrder = order) }
        loadAllVideos()
    }

    fun onTabSelected(tab: HomeTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onViewModeToggle() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun onToggleFavorite(mediaId: Long) {
        viewModelScope.launch { toggleFavoriteUseCase(mediaId) }
    }

    fun onRescanMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            rescanMediaUseCase()
            // Reload after scan
            loadAllVideos()
            loadFolders()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
    }
}
