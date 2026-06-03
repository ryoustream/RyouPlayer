package com.ryoustream.player.presentation.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.NetworkStream
import com.ryoustream.player.domain.model.Playlist
import com.ryoustream.player.domain.model.StreamProtocol
import com.ryoustream.player.domain.usecase.CreatePlaylistUseCase
import com.ryoustream.player.domain.usecase.DeletePlaylistUseCase
import com.ryoustream.player.domain.usecase.DeleteStreamUseCase
import com.ryoustream.player.domain.usecase.GetAllStreamsUseCase
import com.ryoustream.player.domain.usecase.GetPlaylistsUseCase
import com.ryoustream.player.domain.usecase.SaveStreamUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Tab enum ─────────────────────────────────────────────────────────────────

enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlist"),
    STREAMS("Stream"),
}

// ─── State ────────────────────────────────────────────────────────────────────

data class LibraryUiState(
    val playlists: List<Playlist> = emptyList(),
    val streams: List<NetworkStream> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.PLAYLISTS,
    val isLoading: Boolean = true,
    // Playlist dialog
    val showCreatePlaylistDialog: Boolean = false,
    val newPlaylistName: String = "",
    // Stream dialog
    val showAddStreamDialog: Boolean = false,
    val newStreamName: String = "",
    val newStreamUrl: String = "",
    val error: String? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val getAllStreamsUseCase: GetAllStreamsUseCase,
    private val saveStreamUseCase: SaveStreamUseCase,
    private val deleteStreamUseCase: DeleteStreamUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getPlaylistsUseCase(),
                getAllStreamsUseCase(),
            ) { playlists, streams -> Pair(playlists, streams) }.collect { (playlists, streams) ->
                _uiState.update { it.copy(playlists = playlists, streams = streams, isLoading = false) }
            }
        }
    }

    // ── Tab ───────────────────────────────────────────────────────────────────
    fun onSelectTab(tab: LibraryTab) = _uiState.update { it.copy(selectedTab = tab) }

    // ── Playlist dialogs ──────────────────────────────────────────────────────
    fun onShowCreatePlaylistDialog() = _uiState.update { it.copy(showCreatePlaylistDialog = true) }
    fun onDismissCreatePlaylistDialog() = _uiState.update { it.copy(showCreatePlaylistDialog = false, newPlaylistName = "") }
    fun onPlaylistNameChange(name: String) = _uiState.update { it.copy(newPlaylistName = name) }

    fun onCreatePlaylist() {
        val name = _uiState.value.newPlaylistName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            createPlaylistUseCase(name)
            _uiState.update { it.copy(showCreatePlaylistDialog = false, newPlaylistName = "") }
        }
    }

    fun onDeletePlaylist(id: Long) {
        viewModelScope.launch { deletePlaylistUseCase(id) }
    }

    // ── Stream dialogs ────────────────────────────────────────────────────────
    fun onShowAddStreamDialog() = _uiState.update { it.copy(showAddStreamDialog = true) }
    fun onDismissAddStreamDialog() = _uiState.update {
        it.copy(showAddStreamDialog = false, newStreamName = "", newStreamUrl = "")
    }
    fun onStreamNameChange(name: String) = _uiState.update { it.copy(newStreamName = name) }
    fun onStreamUrlChange(url: String) = _uiState.update { it.copy(newStreamUrl = url) }

    fun onSaveStream() {
        val name = _uiState.value.newStreamName.trim()
        val url  = _uiState.value.newStreamUrl.trim()
        if (name.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            val protocol = when {
                url.startsWith("rtsp") -> StreamProtocol.RTSP
                url.endsWith(".m3u8")  -> StreamProtocol.HLS
                url.endsWith(".mpd")   -> StreamProtocol.DASH
                url.startsWith("smb")  -> StreamProtocol.SMB
                url.startsWith("ftp")  -> StreamProtocol.FTP
                url.startsWith("http://") -> StreamProtocol.HTTP
                else -> StreamProtocol.HTTPS
            }
            saveStreamUseCase(NetworkStream(name = name, url = url, protocol = protocol))
            _uiState.update { it.copy(showAddStreamDialog = false, newStreamName = "", newStreamUrl = "") }
        }
    }

    fun onDeleteStream(id: Long) {
        viewModelScope.launch { deleteStreamUseCase(id) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMediaClick: (MediaItem) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onStreamPlay: (NetworkStream) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (uiState.selectedTab == LibraryTab.PLAYLISTS)
                        viewModel.onShowCreatePlaylistDialog()
                    else
                        viewModel.onShowAddStreamDialog()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        if (uiState.selectedTab == LibraryTab.PLAYLISTS)
                            "Playlist Baru"
                        else
                            "Tambah Stream"
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Tab row ───────────────────────────────────────────────────────
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onSelectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.selectedTab) {
                    LibraryTab.PLAYLISTS -> PlaylistTab(
                        playlists = uiState.playlists,
                        onPlaylistClick = onPlaylistClick,
                        onDelete = viewModel::onDeletePlaylist,
                        onCreate = viewModel::onShowCreatePlaylistDialog,
                    )
                    LibraryTab.STREAMS -> StreamTab(
                        streams = uiState.streams,
                        onPlay = onStreamPlay,
                        onDelete = viewModel::onDeleteStream,
                        onAdd = viewModel::onShowAddStreamDialog,
                    )
                }
            }
        }

        // ── Create Playlist dialog ─────────────────────────────────────────────
        if (uiState.showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissCreatePlaylistDialog,
                title = { Text("Playlist Baru") },
                text = {
                    OutlinedTextField(
                        value = uiState.newPlaylistName,
                        onValueChange = viewModel::onPlaylistNameChange,
                        label = { Text("Nama playlist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::onCreatePlaylist,
                        enabled = uiState.newPlaylistName.isNotBlank(),
                    ) { Text("Buat") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissCreatePlaylistDialog) { Text("Batal") }
                },
            )
        }

        // ── Add Stream dialog ─────────────────────────────────────────────────
        if (uiState.showAddStreamDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissAddStreamDialog,
                title = { Text("Tambah Stream") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.newStreamName,
                            onValueChange = viewModel::onStreamNameChange,
                            label = { Text("Nama") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = uiState.newStreamUrl,
                            onValueChange = viewModel::onStreamUrlChange,
                            label = { Text("URL (http, rtsp, smb, ...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::onSaveStream,
                        enabled = uiState.newStreamName.isNotBlank() && uiState.newStreamUrl.isNotBlank(),
                    ) { Text("Simpan") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissAddStreamDialog) { Text("Batal") }
                },
            )
        }
    }
}

// ─── Playlist Tab ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistTab(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onDelete: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyLibrarySection(
            icon = { Icon(Icons.Outlined.VideoLibrary, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Belum ada playlist",
            subtitle = "Buat playlist untuk mengelola video kamu",
            actionLabel = "Buat Playlist",
            onAction = onCreate,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onDelete = { onDelete(playlist.id) },
                )
            }
        }
    }
}

// ─── Stream Tab ───────────────────────────────────────────────────────────────

@Composable
private fun StreamTab(
    streams: List<NetworkStream>,
    onPlay: (NetworkStream) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    if (streams.isEmpty()) {
        EmptyLibrarySection(
            icon = { Icon(Icons.Outlined.Wifi, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            title = "Belum ada stream",
            subtitle = "Tambahkan URL stream (HTTP, RTSP, HLS, SMB, ...)",
            actionLabel = "Tambah Stream",
            onAction = onAdd,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(streams, key = { it.id }) { stream ->
                StreamItem(
                    stream = stream,
                    onClick = { onPlay(stream) },
                    onDelete = { onDelete(stream.id) },
                )
            }
        }
    }
}

// ─── Item composables ─────────────────────────────────────────────────────────

@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${playlist.itemCount} video  •  ${
                    if (playlist.totalDuration > 0)
                        MediaItem.formatDuration(playlist.totalDuration)
                    else "Kosong"
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                if (playlist.thumbnailUri != null) {
                    AsyncImage(
                        model = playlist.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opsi")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { onDelete(); showMenu = false },
                    )
                }
            }
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 88.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun StreamItem(
    stream: NetworkStream,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(stream.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                stream.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        trailingContent = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClick) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opsi")
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { onDelete(); showMenu = false },
                    )
                }
            }
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 88.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyLibrarySection(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
