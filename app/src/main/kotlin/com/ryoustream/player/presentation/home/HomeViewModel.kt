package com.ryoustream.player.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.NetworkStream
import com.ryoustream.player.domain.model.Playlist
import com.ryoustream.player.domain.model.StreamProtocol
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.repository.PlaylistRepository
import com.ryoustream.player.domain.repository.StreamRepository
import com.ryoustream.player.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Tab 0 = Folder, Tab 1 = All, Tab 2 = Recent, Tab 3 = Stream, Tab 4 = Playlist
enum class HomeTab(val label: String) {
    FOLDERS("Folder"),
    ALL("All"),
    RECENT("Recent"),
    STREAM("Stream"),
    PLAYLIST("Playlist"),
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val videos: List<MediaItem> = emptyList(),
    val recentVideos: List<MediaItem> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val streams: List<NetworkStream> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
    // Default: A-Z by name, Folder tab shown first
    val sortOrder: MediaSortOrder = MediaSortOrder.NAME_ASC,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectedTab: HomeTab = HomeTab.FOLDERS,
    // Stream dialog
    val showAddStreamDialog: Boolean = false,
    val streamDialogUrl: String = "",
    val streamDialogName: String = "",
    // Playlist dialog
    val showCreatePlaylistDialog: Boolean = false,
    val newPlaylistName: String = "",
    val error: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllVideosUseCase: GetAllVideosUseCase,
    private val getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    private val searchMediaUseCase: SearchMediaUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val rescanMediaUseCase: RescanMediaUseCase,
    private val mediaRepository: MediaRepository,
    private val streamRepository: StreamRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(MediaSortOrder.NAME_ASC) // A-Z default

    init {
        loadAllVideos()
        loadRecent()
        loadFolders()
        loadStreams()
        loadPlaylists()
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
            getRecentlyPlayedUseCase(50)
                .catch { }
                .collect { recent -> _uiState.update { it.copy(recentVideos = recent) } }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            mediaRepository.getAllFolders()
                .catch { }
                .collect { folders ->
                    // Sort A-Z by default
                    _uiState.update {
                        it.copy(folders = folders.sortedBy { f -> f.name.lowercase() })
                    }
                }
        }
    }

    private fun loadStreams() {
        viewModelScope.launch {
            streamRepository.getAllStreams()
                .catch { }
                .collect { streams -> _uiState.update { it.copy(streams = streams) } }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists()
                .catch { }
                .collect { playlists -> _uiState.update { it.copy(playlists = playlists) } }
        }
    }

    // ── Search ──────────────────────────────────────────────────────────────
    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
    }

    // ── Sort / View ──────────────────────────────────────────────────────────
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

    // ── Favorites ────────────────────────────────────────────────────────────
    fun onToggleFavorite(mediaId: Long) {
        viewModelScope.launch { toggleFavoriteUseCase(mediaId) }
    }

    // ── Rescan ───────────────────────────────────────────────────────────────
    fun onRescanMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            rescanMediaUseCase()
            loadAllVideos()
            loadFolders()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ── Stream CRUD ──────────────────────────────────────────────────────────
    fun onShowAddStreamDialog() = _uiState.update { it.copy(showAddStreamDialog = true) }
    fun onDismissAddStreamDialog() = _uiState.update {
        it.copy(showAddStreamDialog = false, streamDialogUrl = "", streamDialogName = "")
    }
    fun onStreamUrlChange(url: String) = _uiState.update { it.copy(streamDialogUrl = url) }
    fun onStreamNameChange(name: String) = _uiState.update { it.copy(streamDialogName = name) }

    fun onAddStream() {
        val url = _uiState.value.streamDialogUrl.trim()
        if (url.isBlank()) return
        val name = _uiState.value.streamDialogName.trim().ifBlank { url }
        viewModelScope.launch {
            streamRepository.addStream(
                NetworkStream(
                    name = name,
                    url = url,
                    protocol = when {
                        url.startsWith("rtsp") -> StreamProtocol.RTSP
                        url.contains(".m3u8") -> StreamProtocol.HLS
                        url.contains(".mpd") -> StreamProtocol.DASH
                        url.startsWith("http://") -> StreamProtocol.HTTP
                        else -> StreamProtocol.HTTPS
                    },
                )
            )
            onDismissAddStreamDialog()
        }
    }

    fun onDeleteStream(id: Long) {
        viewModelScope.launch { streamRepository.deleteStream(id) }
    }

    fun onToggleStreamFavorite(id: Long) {
        viewModelScope.launch { streamRepository.toggleFavorite(id) }
    }

    // ── Playlist CRUD ────────────────────────────────────────────────────────
    fun onShowCreatePlaylistDialog() = _uiState.update { it.copy(showCreatePlaylistDialog = true) }
    fun onDismissCreatePlaylistDialog() = _uiState.update {
        it.copy(showCreatePlaylistDialog = false, newPlaylistName = "")
    }
    fun onNewPlaylistNameChange(name: String) = _uiState.update { it.copy(newPlaylistName = name) }

    fun onCreatePlaylist() {
        val name = _uiState.value.newPlaylistName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
            onDismissCreatePlaylistDialog()
        }
    }

    fun onDeletePlaylist(id: Long) {
        viewModelScope.launch { playlistRepository.deletePlaylist(id) }
    }
}
