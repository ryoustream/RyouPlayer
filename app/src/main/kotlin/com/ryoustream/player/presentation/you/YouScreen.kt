package com.ryoustream.player.presentation.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryoustream.player.domain.model.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouScreen(
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anda") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                YouStatsHeader(
                    totalVideos = uiState.totalVideos,
                    totalDuration = uiState.totalDuration,
                    favoriteCount = uiState.favoriteCount,
                    inProgressCount = uiState.inProgressCount,
                    modifier = Modifier.padding(16.dp),
                )
            }

            item { YouSectionHeader("Pengaturan") }
            item { YouListItem("Tampilan & Tema", Icons.Default.Palette, onNavigateSettings) }
            item { YouListItem("Pemutaran", Icons.Default.PlayCircle, onNavigateSettings) }
            item { YouListItem("Penyimpanan & Media", Icons.Default.Storage, onNavigateSettings) }
            item { YouListItem("Lanjutan", Icons.Default.Tune, onNavigateSettings) }

            item { YouSectionHeader("Lainnya") }
            item { YouListItem("Tentang RyouPlayer", Icons.Default.Info, onNavigateAbout) }
        }
    }
}

@Composable
private fun YouSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun YouListItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun YouStatsHeader(
    totalVideos: Int,
    totalDuration: Long,
    favoriteCount: Int,
    inProgressCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(totalVideos.toString(), "Video", Icons.Outlined.VideoFile)
            StatItem(MediaItem.formatDuration(totalDuration), "Durasi", Icons.Outlined.Timer)
            StatItem(favoriteCount.toString(), "Favorit", Icons.Outlined.FavoriteBorder)
            StatItem(inProgressCount.toString(), "Berlangsung", Icons.Outlined.PlayCircle)
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
