package com.ryoustream.player.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.ViewMode
import com.ryoustream.player.presentation.components.EmptyStateView
import com.ryoustream.player.presentation.components.VideoCardGrid
import com.ryoustream.player.presentation.components.VideoCardList
import com.ryoustream.player.presentation.components.VideoCardShimmer

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSortMenu by remember { mutableStateOf(false) }

    val displayItems = when {
        folderId != null -> uiState.videos.filter { it.folderId == folderId }
        uiState.selectedTab == HomeTab.RECENT -> uiState.recentVideos
        uiState.selectedTab == HomeTab.FAVORITES -> uiState.favoriteVideos
        else -> uiState.videos
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        Text("${uiState.videos.size} results", maxLines = 1)
                    } else {
                        Text(folderTitle ?: "Ryou Player", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // View mode toggle
                    IconButton(onClick = { viewModel.onViewModeToggle() }) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.GRID)
                                Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view mode",
                        )
                    }
                    // Sort
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
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
                                    }
                                )
                            }
                        }
                    }
                    // Rescan
                    IconButton(onClick = { viewModel.onRescanMedia() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan media")
                    }
                    // Settings
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar
            SearchBar(
                uiState = uiState,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
            )

            // Tabs (only on main screen, not folder detail)
            if (folderId == null && uiState.searchQuery.isEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    divider = {},
                ) {
                    HomeTab.values().forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.onTabSelected(tab) },
                            text = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Content
            when {
                uiState.isLoading -> LoadingGrid()
                displayItems.isEmpty() -> EmptyContent(
                    tab = if (folderId != null) HomeTab.ALL else uiState.selectedTab,
                    onRescan = viewModel::onRescanMedia,
                )
                uiState.viewMode == ViewMode.GRID -> VideoGrid(
                    items = displayItems,
                    onMediaClick = onMediaClick,
                    onFavoriteToggle = { viewModel.onToggleFavorite(it) },
                )
                else -> VideoList(
                    items = displayItems,
                    onMediaClick = onMediaClick,
                    onFavoriteToggle = { viewModel.onToggleFavorite(it) },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    uiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search videos...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(
                visible = uiState.searchQuery.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    )
}

@Composable
private fun VideoGrid(
    items: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
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
            )
        }
    }
}

@Composable
private fun VideoList(
    items: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.id }) { item ->
            VideoCardList(
                item = item,
                onClick = { onMediaClick(item) },
                onFavoriteToggle = { onFavoriteToggle(item.id) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 148.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(6) { VideoCardShimmer() }
    }
}

@Composable
private fun EmptyContent(tab: HomeTab, onRescan: () -> Unit) {
    EmptyStateView(
        icon = {
            Icon(
                imageVector = when (tab) {
                    HomeTab.FAVORITES -> Icons.Outlined.FavoriteBorder
                    HomeTab.RECENT -> Icons.Outlined.History
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
            else -> "No videos found"
        },
        subtitle = when (tab) {
            HomeTab.FAVORITES -> "Tap the heart icon on any video to add it here"
            HomeTab.RECENT -> "Your recently played videos will appear here"
            else -> "No video files found on this device"
        },
        action = if (tab == HomeTab.ALL) {
            { Button(onClick = onRescan) { Text("Scan for Videos") } }
        } else null,
    )
}
