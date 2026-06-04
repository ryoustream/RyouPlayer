package com.ryoustream.player.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ryoustream.player.domain.model.*
import com.ryoustream.player.presentation.components.*

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — YouTube-style feed
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMediaClick: (MediaItem) -> Unit,
    onYouClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (searchExpanded) {
            // ── Inline search bar ────────────────────────────────────────────
            TopSearchBar(
                query = uiState.searchQuery,
                expanded = searchExpanded,
                onQueryChange = viewModel::onSearchQueryChange,
                onExpand = { searchExpanded = true },
                onCollapse = {
                    searchExpanded = false
                    viewModel.clearSearch()
                },
                onYouClick = onYouClick,
            )
        } else {
            // ── Modern TopBar (YouTube 2025 style) ───────────────────────────
            HomeTopBar(
                onSearchClick = { searchExpanded = true },
                onSettingsClick = onYouClick,
            )
            // ── FilterChipRow ────────────────────────────────────────────────
            FilterChipRow(
                selected = uiState.activeFilter,
                onSelect = viewModel::onFilterSelected,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // ── Content ───────────────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { if (!searchExpanded) viewModel.onRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> HomeFeedLoading()
                searchExpanded && uiState.searchQuery.isNotEmpty() -> {
                    HomeFeedList(
                        videos = uiState.videos,
                        onMediaClick = onMediaClick,
                        onFavoriteToggle = viewModel::onToggleFavorite,
                        emptyIcon = Icons.Outlined.SearchOff,
                        emptyTitle = "Tidak ada hasil untuk \"${uiState.searchQuery}\"",
                        emptySubtitle = "Coba kata kunci yang berbeda",
                    )
                }
                else -> {
                    HomeFeedContent(
                        uiState = uiState,
                        onMediaClick = onMediaClick,
                        onFavoriteToggle = viewModel::onToggleFavorite,
                        onRescan = viewModel::onRescanMedia,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeTopBar — YouTube 2025 style: Branding + Search + Avatar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Branding
        Text(
            text       = "RyouPlayer",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        // Search icon
        IconButton(onClick = onSearchClick) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Cari",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        // Avatar lingkaran → onClick buka Settings/You
        val initial = "R"  // branding initial
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home Feed — section layout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeFeedContent(
    uiState: HomeUiState,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onRescan: () -> Unit,
) {
    val displayVideos = uiState.filteredVideos

    if (displayVideos.isEmpty() && uiState.inProgressVideos.isEmpty()) {
        EmptyStateView(
            icon = {
                Icon(Icons.Outlined.VideoLibrary, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            title = "Tidak ada video",
            subtitle = "Tidak ada file video ditemukan di perangkat ini",
            action = { Button(onClick = onRescan) { Text("Pindai Video") } },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // ── Section: Lanjutkan Menonton (horizontal scroll) ────────────────────
        if (uiState.inProgressVideos.isNotEmpty() &&
            uiState.activeFilter == HomeFilter.ALL
        ) {
            item(key = "section_in_progress") {
                HomeSectionHeader(
                    title = "Lanjutkan Menonton",
                    actionLabel = "Lihat semua",
                    onAction = { /* viewModel dapat handle filter change */ },
                )
            }
            item(key = "in_progress_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(items = uiState.inProgressVideos, key = { it.id }) { video ->
                        InProgressCard(
                            item = video,
                            onClick = { onMediaClick(video) },
                        )
                    }
                }
            }
        }

        // ── Section header untuk filter aktif ─────────────────────────────────
        item(key = "section_main") {
            val label = when (uiState.activeFilter) {
                HomeFilter.ALL -> "Semua Video"
                HomeFilter.RECENT -> "Baru Ditambahkan"
                HomeFilter.FAVORITES -> "Favorit"
                HomeFilter.IN_PROGRESS -> "Sedang Berlangsung"
            }
            HomeSectionHeader(label)
        }

        // ── VideoCardYouTube list ─────────────────────────────────────────────
        if (displayVideos.isEmpty()) {
            item(key = "empty_filter") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Tidak ada video untuk filter ini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items = displayVideos, key = { it.id }) { video ->
                VideoCardYouTube(
                    item = video,
                    onClick = { onMediaClick(video) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun HomeFeedList(
    videos: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
) {
    if (videos.isEmpty()) {
        EmptyStateView(
            icon = {
                Icon(emptyIcon, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            title = emptyTitle,
            subtitle = emptySubtitle,
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = videos, key = { it.id }) { video ->
                VideoCardYouTube(
                    item = video,
                    onClick = { onMediaClick(video) },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(
                    text  = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ── Mini card untuk section "Lanjutkan Menonton" (horizontal scroll) ──────────

@Composable
private fun InProgressCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    model = item.thumbnailUri ?: item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
                // Gradient overlay bawah
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.55f)),
                                startY = 40f,
                            )
                        )
                )
                // Sisa waktu badge kanan atas
                if (item.duration > 0 && item.lastPlayedPosition > 0) {
                    val remaining = item.duration - item.lastPlayedPosition
                    if (remaining > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(0.75f),
                        ) {
                            Text(
                                text  = MediaItem.formatDuration(remaining),
                                color = Color.White,
                                fontSize = 10.sp,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                // Progress bar 4dp rounded di bawah
                LinearProgressIndicator(
                    progress = { item.watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    color    = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(0.3f),
                )
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    text     = item.displayName,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                if (item.folderName.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = item.folderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFeedLoading() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(6) { VideoCardShimmer() }
    }
}
