package com.ryoustream.player.presentation.folder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.presentation.components.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderName: String,
    onMediaClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val displayVideos = remember(uiState.videos, uiState.searchQuery) {
        val q = uiState.searchQuery.trim()
        if (q.isBlank()) uiState.videos
        else uiState.videos.filter { it.displayName.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = searchExpanded,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "folder_search_title",
                    ) { isSearching ->
                        if (isSearching) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                placeholder = {
                                    Text("Cari di $folderName…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {}),
                            )
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        } else {
                            Text(
                                folderName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!searchExpanded) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                        }
                    }
                },
                actions = {
                    if (searchExpanded) {
                        IconButton(onClick = {
                            searchExpanded = false
                            viewModel.clearSearch()
                            keyboard?.hide()
                        }) {
                            Icon(Icons.Default.Close, "Tutup pencarian")
                        }
                    } else {
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Default.Search, "Cari")
                        }
                        IconButton(onClick = viewModel::onViewModeToggle) {
                            Icon(
                                if (uiState.viewMode == ViewMode.GRID)
                                    Icons.AutoMirrored.Filled.ViewList
                                else Icons.Default.GridView,
                                "Toggle tampilan",
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "Urutkan")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                MediaSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        leadingIcon = {
                                            if (uiState.sortOrder == order)
                                                Icon(Icons.Default.Check, null,
                                                    modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            viewModel.onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingGrid()
                displayVideos.isEmpty() -> EmptyStateView(
                    icon = {
                        Icon(Icons.Outlined.Folder, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    title = "Folder kosong",
                    subtitle = "Tidak ada video ditemukan di folder ini",
                )
                uiState.viewMode == ViewMode.GRID -> FolderVideoGrid(
                    videos = displayVideos,
                    onMediaClick = onMediaClick,
                    onFavoriteToggle = viewModel::onToggleFavorite,
                )
                else -> FolderVideoList(
                    videos = displayVideos,
                    onMediaClick = onMediaClick,
                    onFavoriteToggle = viewModel::onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun FolderVideoGrid(
    videos: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = videos, key = { it.id }) { item ->
            VideoCardGrid(
                item = item,
                onClick = { onMediaClick(item) },
                onFavoriteToggle = { onFavoriteToggle(item.id) },
                onMoreClick = { menuItem = item },
            )
        }
    }

    menuItem?.let { item ->
        VideoOptionsMenu(
            item = item,
            expanded = true,
            onDismiss = { menuItem = null },
            onPlay = { onMediaClick(item) },
            onToggleFavorite = { onFavoriteToggle(item.id) },
            onAddToPlaylist = {},
            onProperties = { menuItem = null },
        )
    }
}

@Composable
private fun FolderVideoList(
    videos: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = videos, key = { it.id }) { item ->
            VideoCardList(
                item = item,
                onClick = { onMediaClick(item) },
                onFavoriteToggle = { onFavoriteToggle(item.id) },
                onMoreClick = { menuItem = item },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 148.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }

    menuItem?.let { item ->
        VideoOptionsMenu(
            item = item,
            expanded = true,
            onDismiss = { menuItem = null },
            onPlay = { onMediaClick(item) },
            onToggleFavorite = { onFavoriteToggle(item.id) },
            onAddToPlaylist = {},
            onProperties = { menuItem = null },
        )
    }
}

// Reuse LoadingGrid dari HomeScreen — definisikan ulang secara lokal
@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(8) { VideoCardShimmer() }
    }
}
