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
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.domain.repository.StreamRepository
import com.ryoustream.player.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeFilter(val label: String) {
    ALL("Semua"),
    RECENT("Terbaru"),
    FAVORITES("Favorit"),
    IN_PROGRESS("Lanjutkan"),
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val videos: List<MediaItem> = emptyList(),
    val recentVideos: List<MediaItem> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val streams: List<NetworkStream> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: MediaSortOrder = MediaSortOrder.NAME_ASC,
    val viewMode: ViewMode = ViewMode.GRID,
    val folderViewMode: ViewMode = ViewMode.GRID,
    val activeFilter: HomeFilter = HomeFilter.ALL,
    // Stream dialog
    val showAddStreamDialog: Boolean = false,
    val streamDialogUrl: String = "",
    val streamDialogName: String = "",
    // Playlist dialog
    val showCreatePlaylistDialog: Boolean = false,
    val newPlaylistName: String = "",
    val error: String? = null,
) {
    val inProgressVideos: List<MediaItem>
        get() = videos.filter { it.isInProgress }

    val filteredVideos: List<MediaItem>
        get() = when (activeFilter) {
            HomeFilter.ALL         -> videos
            HomeFilter.RECENT      -> recentVideos
            HomeFilter.FAVORITES   -> videos.filter { it.isFavorite }
            HomeFilter.IN_PROGRESS -> videos.filter { it.isInProgress }
        }
}

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
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private val sortOrder  = MutableStateFlow(MediaSortOrder.NAME_ASC)

    // ── Refresh trigger ──────────────────────────────────────────────────────
    // Bumping this value forces the background collectors in loadAllVideos()
    // and loadFolders() to re-fetch from the repository.  Using a Long
    // (System.currentTimeMillis) guarantees each bump is a distinct value so
    // StateFlow actually emits a new item and flatMapLatest fires.
    private val _refreshKey = MutableStateFlow(0L)

    init {
        loadAllVideos()
        loadFolders()
        loadRecent()
        loadStreams()
        loadPlaylists()
        observeSettingsChanges()
    }

    // ── Background collectors ────────────────────────────────────────────────

    private fun loadAllVideos() {
        viewModelScope.launch {
            // Combine search query (debounced) AND refresh trigger so that
            // pull-to-refresh / rescan / sort-change all flow through the same
            // pipeline without spawning competing coroutines.
            combine(
                searchQuery.debounce(300L),
                _refreshKey,
            ) { query, _ -> query }
                .flatMapLatest { query ->
                    if (query.isBlank()) getAllVideosUseCase(sortOrder.value)
                    else searchMediaUseCase(query)
                }
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { videos ->
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            isRefreshing = false,   // clear both spinners when data arrives
                            videos       = videos,
                        )
                    }
                }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _refreshKey
                .flatMapLatest { mediaRepository.getAllFolders() }
                .catch { }
                .collect { folders ->
                    _uiState.update {
                        it.copy(folders = folders.sortedBy { f -> f.name.lowercase() })
                    }
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

    /**
     * Otomatis refresh ketika setting .nomedia atau hidden files diubah.
     * drop(1) agar tidak trigger saat init (emisi nilai awal).
     */
    private fun observeSettingsChanges() {
        viewModelScope.launch {
            combine(
                settingsRepository.showHiddenFiles,
                settingsRepository.ignoreNomedia,
            ) { hidden, nomedia -> hidden to nomedia }
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    // Setting berubah → refresh semua
                    _uiState.update { it.copy(isRefreshing = true) }
                    rescanMediaUseCase()
                    _refreshKey.value = System.currentTimeMillis()
                }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────
    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "") }
    }

    // ── Sort / View ───────────────────────────────────────────────────────────
    fun onSortOrderChange(order: MediaSortOrder) {
        sortOrder.value = order
        _uiState.update { it.copy(sortOrder = order) }
        // Bump refresh key so the existing loadAllVideos() collector re-fetches
        // with the new sort order — no competing coroutine needed.
        _refreshKey.value = System.currentTimeMillis()
    }

    fun onFilterSelected(filter: HomeFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun onViewModeToggle() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun onFolderViewModeToggle() {
        _uiState.update {
            it.copy(folderViewMode = if (it.folderViewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────
    fun onToggleFavorite(mediaId: Long) {
        viewModelScope.launch { toggleFavoriteUseCase(mediaId) }
    }

    // ── Rescan ────────────────────────────────────────────────────────────────
    fun onRescanMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            rescanMediaUseCase()
            // Bump key — loadAllVideos() / loadFolders() collectors will re-emit
            // and clear isLoading when data arrives.
            _refreshKey.value = System.currentTimeMillis()
        }
    }

    /**
     * Pull-to-refresh handler.
     *
     * Sets isRefreshing = true, runs a MediaStore rescan, then bumps the
     * refresh key so the background collectors pick up fresh data.
     * isRefreshing is cleared in the collect {} block of loadAllVideos() once
     * the new data actually arrives — not immediately after launching work.
     */
    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            rescanMediaUseCase()
            _refreshKey.value = System.currentTimeMillis()
            // isRefreshing = false is set inside loadAllVideos()'s collect block
            // when the new videos emission arrives.
        }
    }

    // ── Stream CRUD ───────────────────────────────────────────────────────────
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
                    url  = url,
                    protocol = when {
                        url.startsWith("rtsp")    -> StreamProtocol.RTSP
                        url.contains(".m3u8")     -> StreamProtocol.HLS
                        url.contains(".mpd")      -> StreamProtocol.DASH
                        url.startsWith("http://") -> StreamProtocol.HTTP
                        else                      -> StreamProtocol.HTTPS
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

    // ── Playlist CRUD ─────────────────────────────────────────────────────────
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
