package com.ryoustream.player.presentation.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryoustream.player.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tentang") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // App icon menggunakan drawable resource
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "App icon",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "RyouPlayer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "v${BuildConfig.VERSION_FULL}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Build #${BuildConfig.BUILD_NUMBER} · ${BuildConfig.COMMIT_HASH} · ${BuildConfig.BUILD_DATE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { HorizontalDivider() }

            // ── Informasi Aplikasi ────────────────────────────────────────────
            item { AboutSectionHeader("Informasi Aplikasi") }
            item { AboutInfoRow("Nama Paket", BuildConfig.APPLICATION_ID) }
            item { AboutInfoRow("Versi", BuildConfig.VERSION_FULL) }
            item { AboutInfoRow("Kode Versi", BuildConfig.VERSION_CODE.toString()) }
            item { AboutInfoRow("Build Number", "#${BuildConfig.BUILD_NUMBER}") }
            item { AboutInfoRow("Commit", BuildConfig.COMMIT_HASH) }
            item { AboutInfoRow("Tanggal Build", BuildConfig.BUILD_DATE) }
            item { AboutInfoRow("Target SDK", "35 (Android 15)") }
            item { AboutInfoRow("Min SDK", "24 (Android 7.0)") }

            // ── Pengembang ────────────────────────────────────────────────────
            item { AboutSectionHeader("Pengembang") }
            item { AboutInfoRow("Dibuat oleh", "Ryoustream") }
            item {
                AboutLinkRow(
                    label = "GitHub",
                    value = "github.com/ryoustream/RyouPlayer",
                    url = "https://github.com/ryoustream/RyouPlayer",
                )
            }
            item {
                AboutLinkRow(
                    label = "Releases",
                    value = "github.com/ryoustream/RyouPlayer/releases",
                    url = "https://github.com/ryoustream/RyouPlayer/releases",
                )
            }

            // ── Teknologi ─────────────────────────────────────────────────────
            item { AboutSectionHeader("Teknologi") }
            item { AboutInfoRow("Mesin Putar", "mpv + libass (libplayer.so)") }
            item { AboutInfoRow("UI Framework", "Jetpack Compose") }
            item { AboutInfoRow("Arsitektur", "MVVM + Clean Architecture") }
            item { AboutInfoRow("DI", "Hilt") }
            item { AboutInfoRow("Database", "Room") }
            item { AboutInfoRow("Preferensi", "DataStore") }
            item { AboutInfoRow("Image Loading", "Coil") }

            // ── Catatan Rilis ─────────────────────────────────────────────────
            item { AboutSectionHeader("Catatan Rilis") }
            RELEASE_NOTES.forEach { (version, notes) ->
                item(key = "release_$version") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            version,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        notes.forEach { note ->
                            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                Text(
                                    "• ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { context.openUrl("https://github.com/ryoustream/RyouPlayer") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Lihat di GitHub")
                    }
                    OutlinedButton(
                        onClick = { context.openUrl("https://github.com/ryoustream/RyouPlayer/issues/new") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.BugReport, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Laporkan Bug")
                    }
                }
            }
        }
    }
}

// ── Komponen helper ───────────────────────────────────────────────────────────

@Composable
private fun AboutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.clickable {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(label, value)
            )
        },
    )
}

@Composable
private fun AboutLinkRow(label: String, value: String, url: String) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.OpenInNew,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable { context.openUrl(url) },
    )
}

// ── Data catatan rilis ────────────────────────────────────────────────────────

private val RELEASE_NOTES = listOf(
    "v1.3.0" to listOf(
        "Rombak UI — navigasi bawah gaya YouTube",
        "Home feed dengan card thumbnail besar",
        "Screen About App dengan info lengkap",
        "Filter chip: Semua, Terbaru, Favorit, Lanjutkan",
        "FolderScreen mandiri dengan VM per-folder",
        "Optimasi scrolling dan animasi",
    ),
    "v1.2.x" to listOf(
        "Perbaikan media controls hilang saat keluar recents",
        "Fix display cutout setting tidak berfungsi",
        "Update checker dari GitHub Releases",
        "Navigasi folder, auto-next episode",
        "Kontrol gestur: seek, brightness, volume",
        "Pilihan audio & subtitle multi-track",
        "Folder list/grid toggle",
    ),
)

// ── Extension ─────────────────────────────────────────────────────────────────

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
