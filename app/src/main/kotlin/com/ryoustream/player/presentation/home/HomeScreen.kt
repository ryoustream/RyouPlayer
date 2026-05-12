package com.ryoustream.player.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.presentation.components.EmptyStateView
import com.ryoustream.player.presentation.components.VideoCardGrid
import com.ryoustream.player.presentation.components.VideoCardList
import com.ryoustream.player.presentation.components.VideoCardShimmer
import com.ryoustream.player.presentation.components.VideoOptionsMenu
import com.ryoustream.player.presentation.components.VideoPropertiesDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    folderId: Long? = null,
    folderTitle: String? = null,
    onMediaClick: (MediaItem) -> Unit,
    onFolderClick: (MediaFolder) -> Unit,
    onSettingsClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Collapse search when query cleared externally
    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isEmpty() && searchExpanded) {
            // keep expanded — user may still be typing
        }
    }

    val displayVideos = when {
        folderId != null -> uiState.videos.filter { it.folderId == folderId }
        uiState.searchQuery.isNotEmpty() -> uiState.videos
        uiState.selectedTab == HomeTab.RECENT -> uiState.recentVideos
        uiState.selectedTab == HomeTab.FAVORITES -> uiState.favoriteVideos
        else -> uiState.videos
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = !searchExpanded,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Text(
                            text = when {
                                folderTitle != null -> folderTitle
                                else -> "Ryou Player"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("Search videos…", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        )
                        LaunchedEffect(searchExpanded) {
                            if (searchExpanded) focusRequester.requestFocus()
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null && !searchExpanded) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Search icon — expands inline
                    if (searchExpanded) {
                        IconButton(onClick = {
                            searchExpanded = false
                            viewModel.clearSearch()
                            keyboard?.hide()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }

                    // View-mode toggle (not in search / folders tab)
                    if (!searchExpanded && uiState.selectedTab != HomeTab.FOLDERS) {
                        IconButton(onClick = { viewModel.onViewModeToggle() }) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.GRID)
                                    Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle view",
                            )
                        }
                    }

                    // Sort menu
                    if (!searchExpanded) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
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
                                                Icon(Icons.Default.Check, null)
                                        },
                                        onClick = {
                                            viewModel.onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.onRescanMedia() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Tabs (not when searching or inside folder)
            if (folderId == null && uiState.searchQuery.isEmpty() && !searchExpanded) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                tabPositions[uiState.selectedTab.ordinal]
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    divider = {},
                ) {
                    HomeTab.values().forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            text = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Content
            when {
                uiState.isLoading -> LoadingGrid()

                uiState.selectedTab == HomeTab.FOLDERS && folderId == null -> {
                    if (uiState.folders.isEmpty()) {
                        EmptyContent(HomeTab.FOLDERS, viewModel::onRescanMedia)
                    } else {
                        FolderGrid(folders = uiState.folders, onFolderClick = onFolderClick)
                    }
                }

                displayVideos.isEmpty() ->
                    EmptyContent(
                        tab = if (folderId != null) HomeTab.ALL else uiState.selectedTab,
                        onRescan = viewModel::onRescanMedia,
                    )

                uiState.viewMode == ViewMode.GRID ->
                    VideoGrid(displayVideos, onMediaClick, { viewModel.onToggleFavorite(it) })

                else ->
                    VideoList(displayVideos, onMediaClick, { viewModel.onToggleFavorite(it) })
            }
        }
    }
}

@Composable
private fun FolderGrid(folders: List<MediaFolder>, onFolderClick: (MediaFolder) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = folders, key = { it.id }) { folder ->
            FolderCard(folder = folder, onClick = { onFolderClick(folder) })
        }
    }
}

@Composable
private fun FolderCard(folder: MediaFolder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (folder.thumbnailUri != null) {
                    AsyncImage(
                        model = folder.thumbnailUri,
                        contentDescription = folder.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                }
                Icon(
                    Icons.Default.Folder, null,
                    tint = if (folder.thumbnailUri != null) Color.White.copy(0.9f)
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Text(
                        "${folder.mediaCount}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${folder.mediaCount} videos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VideoGrid(
    items: List<MediaItem>, onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }
    var propertiesItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.id }) { item ->
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
            onAddToPlaylist = { /* TODO */ },
            onProperties = { propertiesItem = item; menuItem = null },
        )
    }
    propertiesItem?.let { item ->
        VideoPropertiesDialog(item = item, onDismiss = { propertiesItem = null })
    }
}

@Composable
private fun VideoList(
    items: List<MediaItem>, onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }
    var propertiesItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id }) { item ->
            VideoCardList(
                item = item,
                onClick = { onMediaClick(item) },
                onFavoriteToggle = { onFavoriteToggle(item.id) },
                onMoreClick = { menuItem = item },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 148.dp), thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
    menuItem?.let { item ->
        VideoOptionsMenu(
            item = item, expanded = true, onDismiss = { menuItem = null },
            onPlay = { onMediaClick(item) },
            onToggleFavorite = { onFavoriteToggle(item.id) },
            onAddToPlaylist = { },
            onProperties = { propertiesItem = item; menuItem = null },
        )
    }
    propertiesItem?.let { item ->
        VideoPropertiesDialog(item = item, onDismiss = { propertiesItem = null })
    }
}

@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { items(6) { VideoCardShimmer() } }
}

@Composable
private fun EmptyContent(tab: HomeTab, onRescan: () -> Unit) {
    EmptyStateView(
        icon = {
            Icon(
                imageVector = when (tab) {
                    HomeTab.FAVORITES -> Icons.Outlined.FavoriteBorder
                    HomeTab.RECENT -> Icons.Outlined.History
                    HomeTab.FOLDERS -> Icons.Outlined.Folder
                    else -> Icons.Outlined.VideoLibrary
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = when (tab) {
            HomeTab.FAVORITES -> "No favorites yet"
            HomeTab.RECENT -> "No recent videos"
            HomeTab.FOLDERS -> "No folders found"
            else -> "No videos found"
        },
        subtitle = when (tab) {
            HomeTab.FAVORITES -> "Tap the heart on any video to add it here"
            HomeTab.RECENT -> "Your recently played videos appear here"
            HomeTab.FOLDERS -> "No video folders found on this device"
            else -> "No video files found on this device"
        },
        action = if (tab == HomeTab.ALL || tab == HomeTab.FOLDERS) {
            { Button(onClick = onRescan) { Text("Scan for Videos") } }
        } else null,
    )
}
