package com.ryoustream.player.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        // ── TopSearchBar (sticky) ─────────────────────────────────────────────
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

        // ── FilterChipRow (hanya saat tidak search) ───────────────────────────
        if (!searchExpanded) {
            FilterChipRow(
                selected = uiState.activeFilter,
                onSelect = viewModel::onFilterSelected,
                modifier = Modifier.padding(vertical = 8.dp),
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
                HomeSectionHeader("Lanjutkan Menonton")
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
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
    )
}

// ── Mini card untuk section "Lanjutkan Menonton" (horizontal scroll) ──────────

@Composable
private fun InProgressCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(10.dp),
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
                    modifier = Modifier.fillMaxSize(),
                )
                // Progress bar
                LinearProgressIndicator(
                    progress = { item.watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
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
