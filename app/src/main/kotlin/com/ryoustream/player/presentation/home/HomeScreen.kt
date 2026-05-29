package com.ryoustream.player.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ryoustream.player.domain.model.*
import com.ryoustream.player.presentation.components.*

// ─────────────────────────────────────────────────────────────────────────────
// Root HomeScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    folderId: Long? = null,
    folderTitle: String? = null,
    onMediaClick: (MediaItem) -> Unit,
    onFolderClick: (MediaFolder) -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {},
    onStreamClick: (NetworkStream) -> Unit = {},
    onSettingsClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val isFolderDetail = folderId != null

    Scaffold(
        topBar = {
            RyouTopBar(
                title = folderTitle ?: "Ryou Player",
                searchExpanded = searchExpanded,
                searchQuery = uiState.searchQuery,
                sortOrder = uiState.sortOrder,
                viewMode = uiState.viewMode,
                selectedTab = uiState.selectedTab,
                showSortMenu = showSortMenu,
                isFolderDetail = isFolderDetail,
                hasBackNavigation = onBack != null,
                focusRequester = focusRequester,
                onSearchExpand = { searchExpanded = true },
                onSearchCollapse = {
                    searchExpanded = false
                    viewModel.clearSearch()
                    keyboard?.hide()
                },
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSortMenuOpen = { showSortMenu = true },
                onSortMenuDismiss = { showSortMenu = false },
                onSortOrderChange = { viewModel.onSortOrderChange(it); showSortMenu = false },
                onViewModeToggle = viewModel::onViewModeToggle,
                onRescan = viewModel::onRescanMedia,
                onSettings = onSettingsClick,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (!isFolderDetail && !searchExpanded) {
                AnimatedVisibility(
                    visible = uiState.selectedTab == HomeTab.STREAM ||
                              uiState.selectedTab == HomeTab.PLAYLIST,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    FloatingActionButton(
                        onClick = {
                            when (uiState.selectedTab) {
                                HomeTab.STREAM   -> viewModel.onShowAddStreamDialog()
                                HomeTab.PLAYLIST -> viewModel.onShowCreatePlaylistDialog()
                                else             -> Unit
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isFolderDetail && uiState.searchQuery.isEmpty() && !searchExpanded) {
                RyouTabRow(
                    selectedTab  = uiState.selectedTab,
                    onTabSelected = viewModel::onTabSelected,
                )
            }

            // Pull-to-refresh wraps the entire tab content area.
            // Swipe is ignored during search by passing a no-op onRefresh.
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh    = { if (!searchExpanded) viewModel.onRefresh() },
                modifier     = Modifier.fillMaxSize(),
            ) {
                AnimatedContent(
                    targetState = when {
                        isFolderDetail -> "folder_detail"
                        searchExpanded && uiState.searchQuery.isNotEmpty() -> "search"
                        else -> uiState.selectedTab.name
                    },
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInHorizontally { it / 20 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "tab_content",
                    modifier = Modifier.fillMaxSize(),
                ) { target ->
                    when (target) {
                        "folder_detail" -> FolderDetailContent(
                            folderId        = folderId!!,
                            uiState         = uiState,
                            onMediaClick    = onMediaClick,
                            onFavoriteToggle = viewModel::onToggleFavorite,
                        )
                        "search" -> VideoContent(
                            items           = uiState.videos,
                            viewMode        = uiState.viewMode,
                            onMediaClick    = onMediaClick,
                            onFavoriteToggle = viewModel::onToggleFavorite,
                            emptyIcon       = Icons.Outlined.SearchOff,
                            emptyTitle      = "No results for \"${uiState.searchQuery}\"",
                            emptySubtitle   = "Try different keywords",
                        )
                        HomeTab.FOLDERS.name -> FolderTabContent(
                            folders     = uiState.folders,
                            isLoading   = uiState.isLoading,
                            onFolderClick = onFolderClick,
                            onRescan    = viewModel::onRescanMedia,
                        )
                        HomeTab.ALL.name -> VideoContent(
                            items           = uiState.videos,
                            viewMode        = uiState.viewMode,
                            isLoading       = uiState.isLoading,
                            onMediaClick    = onMediaClick,
                            onFavoriteToggle = viewModel::onToggleFavorite,
                            emptyIcon       = Icons.Outlined.VideoLibrary,
                            emptyTitle      = "No videos found",
                            emptySubtitle   = "No video files found on this device",
                            onRescan        = viewModel::onRescanMedia,
                        )
                        HomeTab.RECENT.name -> VideoContent(
                            items           = uiState.recentVideos,
                            viewMode        = uiState.viewMode,
                            onMediaClick    = onMediaClick,
                            onFavoriteToggle = viewModel::onToggleFavorite,
                            emptyIcon       = Icons.Outlined.History,
                            emptyTitle      = "No recent videos",
                            emptySubtitle   = "Your recently played videos appear here",
                        )
                        HomeTab.STREAM.name -> StreamTabContent(
                            streams          = uiState.streams,
                            onStreamClick    = onStreamClick,
                            onDeleteStream   = viewModel::onDeleteStream,
                            onToggleFavorite = viewModel::onToggleStreamFavorite,
                            onAddClick       = viewModel::onShowAddStreamDialog,
                        )
                        HomeTab.PLAYLIST.name -> PlaylistTabContent(
                            playlists        = uiState.playlists,
                            onPlaylistClick  = onPlaylistClick,
                            onDeletePlaylist = viewModel::onDeletePlaylist,
                            onCreateClick    = viewModel::onShowCreatePlaylistDialog,
                        )
                        else -> Unit
                    }
                }
            } // PullToRefreshBox
        }
    }

    if (uiState.showAddStreamDialog) {
        AddStreamDialog(
            url        = uiState.streamDialogUrl,
            name       = uiState.streamDialogName,
            onUrlChange  = viewModel::onStreamUrlChange,
            onNameChange = viewModel::onStreamNameChange,
            onConfirm  = viewModel::onAddStream,
            onDismiss  = viewModel::onDismissAddStreamDialog,
        )
    }

    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            name         = uiState.newPlaylistName,
            onNameChange = viewModel::onNewPlaylistNameChange,
            onConfirm    = viewModel::onCreatePlaylist,
            onDismiss    = viewModel::onDismissCreatePlaylistDialog,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RyouTopBar(
    title: String,
    searchExpanded: Boolean,
    searchQuery: String,
    sortOrder: MediaSortOrder,
    viewMode: ViewMode,
    selectedTab: HomeTab,
    showSortMenu: Boolean,
    isFolderDetail: Boolean,
    hasBackNavigation: Boolean,
    focusRequester: FocusRequester,
    onSearchExpand: () -> Unit,
    onSearchCollapse: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortMenuOpen: () -> Unit,
    onSortMenuDismiss: () -> Unit,
    onSortOrderChange: (MediaSortOrder) -> Unit,
    onViewModeToggle: () -> Unit,
    onRescan: () -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?,
) {
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = searchExpanded,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "search_title",
            ) { isSearching ->
                if (isSearching) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = {
                            Text("Search videos…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { }),
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                } else {
                    Text(
                        text       = title,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        navigationIcon = {
            if (hasBackNavigation && !searchExpanded) {
                IconButton(onClick = { onBack?.invoke() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (searchExpanded) {
                IconButton(onClick = onSearchCollapse) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                }
            } else {
                IconButton(onClick = onSearchExpand) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                if (!isFolderDetail &&
                    selectedTab != HomeTab.FOLDERS &&
                    selectedTab != HomeTab.STREAM &&
                    selectedTab != HomeTab.PLAYLIST
                ) {
                    IconButton(onClick = onViewModeToggle) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.GRID)
                                Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                        )
                    }
                }
                if (isFolderDetail ||
                    selectedTab == HomeTab.ALL ||
                    selectedTab == HomeTab.RECENT
                ) {
                    Box {
                        IconButton(onClick = onSortMenuOpen) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = onSortMenuDismiss) {
                            MediaSortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = {
                                        if (sortOrder == order)
                                            Icon(Icons.Default.Check, null,
                                                modifier = Modifier.size(18.dp))
                                    },
                                    onClick = { onSortOrderChange(order) },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Row — 5 tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RyouTabRow(selectedTab: HomeTab, onTabSelected: (HomeTab) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding      = 8.dp,
        containerColor   = MaterialTheme.colorScheme.surface,
        contentColor     = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedTab.ordinal < tabPositions.size) {
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    height   = 3.dp,
                    color    = MaterialTheme.colorScheme.primary,
                )
            }
        },
        divider = {
            HorizontalDivider(
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        },
    ) {
        HomeTab.values().forEach { tab ->
            val selected = selectedTab == tab
            Tab(
                selected = selected,
                onClick  = { onTabSelected(tab) },
                modifier = Modifier.height(48.dp),
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(
                        imageVector = tabIcon(tab),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        tab.label,
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: HomeTab): ImageVector = when (tab) {
    HomeTab.FOLDERS  -> Icons.Outlined.Folder
    HomeTab.ALL      -> Icons.Outlined.VideoLibrary
    HomeTab.RECENT   -> Icons.Outlined.History
    HomeTab.STREAM   -> Icons.Outlined.Cast
    HomeTab.PLAYLIST -> Icons.AutoMirrored.Outlined.PlaylistPlay
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderTabContent(
    folders: List<MediaFolder>,
    isLoading: Boolean,
    onFolderClick: (MediaFolder) -> Unit,
    onRescan: () -> Unit,
) {
    when {
        isLoading -> LoadingGrid()
        folders.isEmpty() -> EmptyStateView(
            icon = {
                Icon(Icons.Outlined.Folder, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            title    = "No folders found",
            subtitle = "No video folders found on this device",
            action   = { Button(onClick = onRescan) { Text("Scan for Videos") } },
        )
        else -> LazyVerticalGrid(
            columns              = GridCells.Adaptive(minSize = 160.dp),
            contentPadding       = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement  = Arrangement.spacedBy(10.dp),
            modifier             = Modifier.fillMaxSize(),
        ) {
            items(items = folders, key = { it.id }) { folder ->
                FolderCard(folder = folder, onClick = { onFolderClick(folder) })
            }
        }
    }
}

@Composable
private fun FolderCard(folder: MediaFolder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape   = RoundedCornerShape(16.dp),
        colors  = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (folder.thumbnailUri != null) {
                    AsyncImage(
                        model            = folder.thumbnailUri,
                        contentDescription = folder.name,
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.45f)))
                        )
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (folder.thumbnailUri != null)
                        Color.Black.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Folder, null,
                            tint = if (folder.thumbnailUri != null) Color.White
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    shape    = RoundedCornerShape(6.dp),
                    color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                ) {
                    Text(
                        "${folder.mediaCount}",
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    folder.name,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${folder.mediaCount} ${if (folder.mediaCount == 1) "video" else "videos"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder Detail
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderDetailContent(
    folderId: Long,
    uiState: HomeUiState,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    val folderVideos = remember(uiState.videos, folderId) {
        uiState.videos.filter { it.folderId == folderId }
    }
    VideoContent(
        items            = folderVideos,
        viewMode         = uiState.viewMode,
        isLoading        = uiState.isLoading,
        onMediaClick     = onMediaClick,
        onFavoriteToggle = onFavoriteToggle,
        emptyIcon        = Icons.Outlined.Folder,
        emptyTitle       = "Empty folder",
        emptySubtitle    = "No videos found in this folder",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Video Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoContent(
    items: List<MediaItem>,
    viewMode: ViewMode,
    isLoading: Boolean = false,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onRescan: (() -> Unit)? = null,
) {
    when {
        isLoading -> LoadingGrid()
        items.isEmpty() -> EmptyStateView(
            icon = {
                Icon(emptyIcon, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            title    = emptyTitle,
            subtitle = emptySubtitle,
            action   = if (onRescan != null) {
                { Button(onClick = onRescan) { Text("Scan for Videos") } }
            } else null,
        )
        viewMode == ViewMode.GRID -> VideoGrid(items, onMediaClick, onFavoriteToggle)
        else                      -> VideoList(items, onMediaClick, onFavoriteToggle)
    }
}

@Composable
private fun VideoGrid(
    items: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    var menuItem       by remember { mutableStateOf<MediaItem?>(null) }
    var propertiesItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 160.dp),
        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp),
        modifier              = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.id }) { item ->
            VideoCardGrid(
                item            = item,
                onClick         = { onMediaClick(item) },
                onFavoriteToggle = { onFavoriteToggle(item.id) },
                onMoreClick     = { menuItem = item },
            )
        }
    }
    menuItem?.let { item ->
        VideoOptionsMenu(
            item             = item, expanded = true, onDismiss = { menuItem = null },
            onPlay           = { onMediaClick(item) },
            onToggleFavorite = { onFavoriteToggle(item.id) },
            onAddToPlaylist  = { },
            onProperties     = { propertiesItem = item; menuItem = null },
        )
    }
    propertiesItem?.let { item ->
        VideoPropertiesDialog(item = item, onDismiss = { propertiesItem = null })
    }
}

@Composable
private fun VideoList(
    items: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    var menuItem       by remember { mutableStateOf<MediaItem?>(null) }
    var propertiesItem by remember { mutableStateOf<MediaItem?>(null) }

    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id }) { item ->
            VideoCardList(
                item             = item,
                onClick          = { onMediaClick(item) },
                onFavoriteToggle = { onFavoriteToggle(item.id) },
                onMoreClick      = { menuItem = item },
            )
            HorizontalDivider(
                modifier  = Modifier.padding(start = 148.dp), thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
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

// ─────────────────────────────────────────────────────────────────────────────
// Stream Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreamTabContent(
    streams: List<NetworkStream>,
    onStreamClick: (NetworkStream) -> Unit,
    onDeleteStream: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    if (streams.isEmpty()) {
        EmptyStateView(
            icon = {
                Icon(Icons.Outlined.Cast, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            title    = "No streams yet",
            subtitle = "Add an HTTP, HLS, DASH or RTSP stream URL",
            action   = {
                Button(onClick = onAddClick) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Stream")
                }
            },
        )
    } else {
        LazyColumn(
            contentPadding      = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier            = Modifier.fillMaxSize(),
        ) {
            items(streams, key = { it.id }) { stream ->
                StreamCard(
                    stream           = stream,
                    onClick          = { onStreamClick(stream) },
                    onDelete         = { onDeleteStream(stream.id) },
                    onToggleFavorite = { onToggleFavorite(stream.id) },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun StreamCard(
    stream: NetworkStream,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        shape   = RoundedCornerShape(14.dp),
        colors  = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.Cast, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stream.name,
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    ) {
                        Text(stream.protocol.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stream.url, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options",
                        modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { onClick(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (stream.isFavorite) "Remove from favorites" else "Add to favorites") },
                        leadingIcon = {
                            Icon(
                                if (stream.isFavorite) Icons.Filled.Favorite
                                else Icons.Outlined.FavoriteBorder, null)
                        },
                        onClick = { onToggleFavorite(); showMenu = false },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { onDelete(); showMenu = false },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistTabContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreateClick: () -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyStateView(
            icon = {
                Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            title    = "No playlists yet",
            subtitle = "Create a playlist to organize your videos",
            action   = {
                Button(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Playlist")
                }
            },
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier       = Modifier.fillMaxSize(),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist        = playlist,
                    onClick         = { onPlaylistClick(playlist) },
                    onDelete        = { onDeletePlaylist(playlist.id) },
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(start = 88.dp), thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun PlaylistRow(
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
            val dur = if (playlist.totalDuration > 0)
                " · " + MediaItem.formatDuration(playlist.totalDuration) else ""
            Text(
                "${playlist.itemCount} ${if (playlist.itemCount == 1) "video" else "videos"}$dur",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp), shape = RoundedCornerShape(10.dp),
                color    = MaterialTheme.colorScheme.primaryContainer,
            ) {
                if (playlist.thumbnailUri != null) {
                    AsyncImage(model = playlist.thumbnailUri, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp))
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
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { onDelete(); showMenu = false },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddStreamDialog(
    url: String, name: String,
    onUrlChange: (String) -> Unit, onNameChange: (String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.Outlined.Cast, null) },
        title            = { Text("Add Stream") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = onUrlChange,
                    label = { Text("Stream URL") },
                    placeholder = { Text("https:// · rtsp:// · .m3u8") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text("Name (optional)") }, placeholder = { Text("My Stream") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onConfirm() }),
                )
            }
        },
        confirmButton  = { TextButton(onClick = onConfirm, enabled = url.isNotBlank()) { Text("Add") } },
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreatePlaylistDialog(
    name: String, onNameChange: (String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null) },
        title            = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = onNameChange,
                label = { Text("Playlist name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm() }),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shimmer loading placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns               = GridCells.Adaptive(minSize = 160.dp),
        contentPadding        = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp),
    ) { items(8) { VideoCardShimmer() } }
}
