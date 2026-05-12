package com.ryoustream.player.presentation.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
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
import com.ryoustream.player.domain.model.Playlist
import com.ryoustream.player.domain.usecase.CreatePlaylistUseCase
import com.ryoustream.player.domain.usecase.DeletePlaylistUseCase
import com.ryoustream.player.domain.usecase.GetPlaylistsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class LibraryUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val newPlaylistName: String = "",
    val error: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            getPlaylistsUseCase().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists, isLoading = false) }
            }
        }
    }

    fun onShowCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun onDismissCreateDialog() = _uiState.update { it.copy(showCreateDialog = false, newPlaylistName = "") }
    fun onNameChange(name: String) = _uiState.update { it.copy(newPlaylistName = name) }

    fun onCreatePlaylist() {
        val name = _uiState.value.newPlaylistName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            createPlaylistUseCase(name)
            _uiState.update { it.copy(showCreateDialog = false, newPlaylistName = "") }
        }
    }

    fun onDeletePlaylist(id: Long) {
        viewModelScope.launch { deletePlaylistUseCase(id) }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMediaClick: (MediaItem) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
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
                onClick = viewModel::onShowCreateDialog,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Playlist") },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.playlists.isEmpty()) {
            EmptyLibrary(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onCreate = viewModel::onShowCreateDialog,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item {
                    Text(
                        "Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(uiState.playlists, key = { it.id }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) },
                        onDelete = { viewModel.onDeletePlaylist(playlist.id) },
                    )
                }
            }
        }

        // Create playlist dialog
        if (uiState.showCreateDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissCreateDialog,
                title = { Text("New Playlist") },
                text = {
                    OutlinedTextField(
                        value = uiState.newPlaylistName,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::onCreatePlaylist,
                        enabled = uiState.newPlaylistName.isNotBlank(),
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissCreateDialog) { Text("Cancel") }
                },
            )
        }
    }
}

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
                "${playlist.itemCount} videos · ${
                    if (playlist.totalDuration > 0)
                        com.ryoustream.player.domain.model.MediaItem.formatDuration(playlist.totalDuration)
                    else "Empty"
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
                            Icons.Default.PlaylistPlay,
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
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
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
private fun EmptyLibrary(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a playlist to organize your videos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreate) { Text("Create Playlist") }
    }
}
