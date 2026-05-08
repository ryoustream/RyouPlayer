package com.ryoustream.player.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.domain.usecase.GetAllVideosUseCase
import com.ryoustream.player.domain.usecase.GetFavoritesUseCase
import com.ryoustream.player.domain.usecase.GetRecentlyPlayedUseCase
import com.ryoustream.player.domain.usecase.RescanMediaUseCase
import com.ryoustream.player.domain.usecase.SearchMediaUseCase
import com.ryoustream.player.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val videos: List<MediaItem> = emptyList(),
    val recentVideos: List<MediaItem> = emptyList(),
    val favoriteVideos: List<MediaItem> = emptyList(),
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(MediaSortOrder.DATE_ADDED_DESC)

    init {
        loadMedia()
    }

    private fun loadMedia() {
        // Load all videos with search + sort
        viewModelScope.launch {
            searchQuery
                .debounce(300L)
                .flatMapLatest { query ->
                    if (query.isBlank()) getAllVideosUseCase(sortOrder.value)
                    else searchMediaUseCase(query)
                }
                .collect { videos ->
                    _uiState.update {
                        it.copy(isLoading = false, videos = videos)
                    }
                }
        }

        // Load recently played
        viewModelScope.launch {
            getRecentlyPlayedUseCase(20).collect { recent ->
                _uiState.update { it.copy(recentVideos = recent) }
            }
        }

        // Load favorites
        viewModelScope.launch {
            getFavoritesUseCase().collect { favorites ->
                _uiState.update { it.copy(favoriteVideos = favorites) }
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
        viewModelScope.launch {
            toggleFavoriteUseCase(mediaId)
        }
    }

    fun onRescanMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            rescanMediaUseCase()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
    }
}
