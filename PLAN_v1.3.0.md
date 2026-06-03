# RyouPlayer v1.3.0 — Rencana Major Update

> **Target version:** `1.3.001` → `versionMajor = 3`, `versionNameMinor = 1`
> **Build config:** ubah `versionMajor = 2` → `3`, reset `versionNameMinor = 1`

---

## Ringkasan

Tiga fokus utama:
1. **UI Rombak** — Navigasi & layout semirip YouTube Android terbaru
2. **Optimasi Fitur** — Semua fitur lama tetap ada, ditata lebih intuitif
3. **About App** — Screen khusus tentang aplikasi dengan info lengkap

---

## 1. Arsitektur Navigasi Baru

### Sekarang (v1.2.x)
```
MainActivity
└── Scaffold
    ├── TopAppBar (judul + search + sort + menu)
    ├── TabRow (Folders | Videos | Streams | Playlist)  ← tab di atas
    └── Content area
```

### Target (v1.3.0)
```
MainActivity
└── Scaffold
    ├── TopSearchBar (pill search, tidak berubah saat scroll)
    ├── Content area (per-destination)
    └── BottomNavigationBar  ← BARU: Home | Folder | Library | You
```

### Bottom Nav Destinations

| Tab | Icon | Konten |
|-----|------|--------|
| **Home** | `Icons.Default.Home` | Feed semua video — Recently Added, Continue Watching, All Videos |
| **Folders** | `Icons.Default.FolderOpen` | Semua folder (grid atau list) |
| **Library** | `Icons.Default.VideoLibrary` | Playlist + Network Streams |
| **You** | `Icons.Default.Person` | Settings + About + statistik singkat |

---

## 2. Perubahan File

### File Baru
```
presentation/
├── about/
│   └── AboutScreen.kt              ← BARU
├── home/
│   └── HomeScreen.kt               ← ROMBAK total
├── components/
│   ├── BottomNavBar.kt             ← BARU
│   ├── TopSearchBar.kt             ← BARU (pisah dari TopBar lama)
│   └── FilterChipRow.kt            ← BARU
└── you/
    └── YouScreen.kt                ← BARU (wrapper Settings + About)
```

### File Dimodifikasi
```
navigation/Navigation.kt            ← tambah route About, You; pasang BottomNav
presentation/MainActivity.kt        ← pasang BottomNavBar di Scaffold level
presentation/home/HomeViewModel.kt  ← hapus HomeTab enum, tambah HomeSection
presentation/settings/SettingsScreen.kt ← diakses dari YouScreen, bukan langsung
```

### File Tidak Berubah
```
player/PlayerActivity.kt            ← sudah baik
player/PlayerScreen.kt              ← sudah baik
player/PlayerViewModel.kt           ← sudah baik
service/RyouPlaybackService.kt      ← sudah diperbaiki di patch sebelumnya
util/AppUpdateChecker.kt            ← sudah ada
```

---

## 3. HomeScreen Baru (YouTube-style)

### Layout Struktur
```
┌──────────────────────────────┐
│  🔍 Cari video...        [≡] │  ← TopSearchBar (sticky)
├──────────────────────────────┤
│ [Semua] [Terbaru] [Favorit]  │  ← FilterChipRow (scrollable horizontal)
├──────────────────────────────┤
│                              │
│  Continue Watching           │  ← Section header (hanya jika ada data)
│  ┌────┐ ┌────┐ ┌────┐        │  ← LazyRow horizontal scroll
│  │    │ │    │ │    │        │
│  └────┘ └────┘ └────┘        │
│                              │
│  Recently Added              │
│  ┌──────────────────────┐    │
│  │  [thumbnail 16:9]    │    │  ← VideoCardYouTube (full width)
│  │  Judul Episode.mkv   │    │
│  │  Nama Folder • 24m   │    │
│  └──────────────────────┘    │
│  ┌──────────────────────┐    │
│  │  ...                 │    │
│  └──────────────────────┘    │
│                              │
├──────────────────────────────┤
│  🏠   📁   📚   👤          │  ← BottomNavigationBar
└──────────────────────────────┘
```

### VideoCardYouTube — Spesifikasi Komponen Baru

Menggantikan `VideoCardGrid` dan `VideoCardList` untuk Home feed.

```kotlin
// presentation/components/VideoCards.kt — tambah di bawah yang sudah ada

@Composable
fun VideoCardYouTube(
    item: MediaItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().combinedClickable(...)) {

        // 1. Thumbnail — full width, aspect ratio 16:9
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(...)
            // Duration badge — pojok kanan bawah
            Text(
                text = item.durationFormatted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 12.sp,
            )
            // Progress bar jika lastPlayedPosition > 0
            if (item.lastPlayedPosition > 0) {
                LinearProgressIndicator(
                    progress = item.watchProgress,
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }

        // 2. Info row — mirip YouTube
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Folder icon avatar (seperti channel avatar YouTube)
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            // Judul + metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // "Nama Folder • Resolusi • Ukuran"
                    text = buildMetadataString(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // More options
            IconButton(onClick = onMoreClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More",
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Helper: "Folder Name • 1080p • 450 MB"
private fun buildMetadataString(item: MediaItem): String = buildString {
    item.folderName?.let { append(it) }
    if (item.resolution != VideoResolution.UNKNOWN) append(" • ${item.resolution.label}")
    if (item.fileSize > 0) append(" • ${item.fileSizeFormatted}")
}
```

### FilterChipRow — Spesifikasi

```kotlin
// presentation/components/FilterChipRow.kt

enum class HomeFilter { ALL, RECENT, FAVORITES, CONTINUE_WATCHING }

@Composable
fun FilterChipRow(
    selected: HomeFilter,
    onSelect: (HomeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        val items = listOf(
            HomeFilter.ALL to "Semua",
            HomeFilter.RECENT to "Terbaru",
            HomeFilter.FAVORITES to "Favorit",
            HomeFilter.CONTINUE_WATCHING to "Lanjutkan",
        )
        items(items) { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) },
            )
        }
    }
}
```

### TopSearchBar — Spesifikasi

```kotlin
// presentation/components/TopSearchBar.kt
// Pill-style search bar — sticky di atas, tidak ikut scroll

@Composable
fun TopSearchBar(
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onCastClick: () -> Unit = {},  // opsional
    modifier: Modifier = Modifier,
) {
    AnimatedContent(targetState = expanded) { isExpanded ->
        if (isExpanded) {
            // Full search field
            SearchBar(...)
        } else {
            // Collapsed: logo + search icon + settings icon
            Row(
                modifier = modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RyouPlayer", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onExpand) {
                    Icon(Icons.Default.Search, contentDescription = "Cari")
                }
                // Profile/avatar — navigasi ke YouScreen
                IconButton(onClick = { /* nav to You */ }) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Profil")
                }
            }
        }
    }
}
```

---

## 4. BottomNavBar — Spesifikasi

```kotlin
// presentation/components/BottomNavBar.kt

sealed class BottomNavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    object Home    : BottomNavDest("home",    "Beranda", Icons.Outlined.Home,         Icons.Filled.Home)
    object Folders : BottomNavDest("folders", "Folder",  Icons.Outlined.FolderOpen,   Icons.Filled.Folder)
    object Library : BottomNavDest("library", "Pustaka", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary)
    object You     : BottomNavDest("you",     "Anda",    Icons.Outlined.Person,       Icons.Filled.Person)
}

@Composable
fun RyouBottomNavBar(
    currentRoute: String?,
    onNavigate: (BottomNavDest) -> Unit,
) {
    NavigationBar {
        listOf(
            BottomNavDest.Home,
            BottomNavDest.Folders,
            BottomNavDest.Library,
            BottomNavDest.You,
        ).forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.iconSelected else dest.icon,
                        contentDescription = dest.label,
                    )
                },
                label = { Text(dest.label, fontSize = 11.sp) },
            )
        }
    }
}
```

### Integrasi di MainActivity / Navigation.kt

```kotlin
// Navigation.kt — Scaffold dengan BottomNavBar di level root

@Composable
fun RyouNavGraph(...) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    // Destinations yang menampilkan BottomNavBar
    val showBottomNav = currentRoute in listOf("home", "folders", "library", "you")

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                RyouBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(...) { ... }
    }
}
```

---

## 5. YouScreen — Spesifikasi

Screen baru yang menggabungkan Settings + About.

```kotlin
// presentation/you/YouScreen.kt

@Composable
fun YouScreen(
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit,
) {
    LazyColumn {
        // Header statistik singkat
        item {
            YouStatsHeader()  // Total video, total durasi, favorit count
        }

        // Quick links
        item { SectionHeader("Pengaturan") }
        item { ListItem("Tampilan & Tema", Icons.Default.Palette, onClick = onNavigateSettings) }
        item { ListItem("Pemutaran", Icons.Default.PlayCircle, onClick = onNavigateSettings) }
        item { ListItem("Penyimpanan", Icons.Default.Storage, onClick = onNavigateSettings) }
        item { ListItem("Lanjutan", Icons.Default.Tune, onClick = onNavigateSettings) }

        item { SectionHeader("Lainnya") }
        item { ListItem("Tentang RyouPlayer", Icons.Default.Info, onClick = onNavigateAbout) }
        item { ListItem("Periksa Pembaruan", Icons.Default.SystemUpdate, onClick = { /* check */ }) }
    }
}
```

---

## 6. AboutScreen — Spesifikasi LENGKAP

Screen baru dengan info aplikasi detail. Route: `Screen.About`.

### Layout

```
┌──────────────────────────────┐
│  ←  Tentang                  │  ← TopAppBar dengan back
├──────────────────────────────┤
│                              │
│     [  LOGO APP  ]           │  ← App icon besar (72dp)
│     RyouPlayer               │  ← App name, bold
│     v1.3.001                 │  ← Version
│     Build #XX • hash abc1234 │  ← Build info dari BuildConfig
│                              │
│ ─────────────────────────── │
│                              │
│  Aplikasi                    │  ← Section
│  Paket        com.ryou...    │
│  Versi        1.3.001        │
│  Build        #73 (2025...)  │
│  Target SDK   35             │
│  Min SDK      24             │
│                              │
│  Pengembang                  │  ← Section
│  Nama         Ryoustream     │
│  GitHub       ryoustream/... │  ← Tappable → buka browser
│  Lisensi      MIT            │  ← Tappable → dialog lisensi
│                              │
│  Teknologi                   │  ← Section
│  Engine       mpv + libass   │
│  UI           Jetpack Compose│
│  DI           Hilt           │
│  Database     Room           │
│                              │
│  Catatan Rilis               │  ← Section (hardcoded tiap release)
│  v1.3.0 — ...                │
│  v1.2.x — ...                │
│                              │
│  [  Lihat di GitHub  ]       │  ← OutlinedButton
│  [ Laporkan Bug      ]       │  ← OutlinedButton
│                              │
└──────────────────────────────┘
```

### Implementasi

```kotlin
// presentation/about/AboutScreen.kt

@Composable
fun AboutScreen(onBack: () -> Unit) {
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = "App icon",
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("RyouPlayer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("v${BuildConfig.VERSION_FULL}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text("Build #${BuildConfig.BUILD_NUMBER} • ${BuildConfig.COMMIT_HASH} • ${BuildConfig.BUILD_DATE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item { HorizontalDivider() }

            // ── Info Aplikasi ─────────────────────────────────────────────────
            item { AboutSectionHeader("Informasi Aplikasi") }
            item { AboutInfoRow("Nama Paket",  BuildConfig.APPLICATION_ID) }
            item { AboutInfoRow("Versi",       BuildConfig.VERSION_FULL) }
            item { AboutInfoRow("Kode Versi",  BuildConfig.VERSION_CODE.toString()) }
            item { AboutInfoRow("Build Number","#${BuildConfig.BUILD_NUMBER}") }
            item { AboutInfoRow("Commit",      BuildConfig.COMMIT_HASH) }
            item { AboutInfoRow("Tanggal Build",BuildConfig.BUILD_DATE) }
            item { AboutInfoRow("Target SDK",  "35 (Android 15)") }
            item { AboutInfoRow("Min SDK",     "24 (Android 7.0)") }

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
            item { AboutInfoRow("Mesin Putar",    "mpv + libass (libplayer.so)") }
            item { AboutInfoRow("UI Framework",   "Jetpack Compose") }
            item { AboutInfoRow("Arsitektur",     "MVVM + Clean Architecture") }
            item { AboutInfoRow("DI",             "Hilt") }
            item { AboutInfoRow("Database",       "Room") }
            item { AboutInfoRow("Preferensi",     "DataStore") }
            item { AboutInfoRow("Image Loading",  "Coil") }

            // ── Lisensi ───────────────────────────────────────────────────────
            item { AboutSectionHeader("Lisensi") }
            item {
                ListItem(
                    headlineContent = { Text("Lisensi Sumber Terbuka") },
                    supportingContent = { Text("MIT License • Lihat lisensi komponen pihak ketiga") },
                    leadingContent = { Icon(Icons.Default.Gavel, null) },
                    modifier = Modifier.clickable { /* tampilkan LicensesDialog */ },
                )
            }

            // ── Catatan Rilis ─────────────────────────────────────────────────
            item { AboutSectionHeader("Catatan Rilis") }
            RELEASE_NOTES.forEach { (version, notes) ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(version, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        notes.forEach { note ->
                            Row {
                                Text("• ", style = MaterialTheme.typography.bodySmall)
                                Text(note, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            item {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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

// ── Komponen Helper ───────────────────────────────────────────────────────────

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
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.clickable { /* copy value ke clipboard */ },
    )
}

@Composable
private fun AboutLinkRow(label: String, value: String, url: String) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingIcon = { Icon(Icons.Default.OpenInNew, null, Modifier.size(14.dp)) },
        modifier = Modifier.clickable { context.openUrl(url) },
    )
}

// ── Data Catatan Rilis (update tiap release) ──────────────────────────────────

private val RELEASE_NOTES = listOf(
    "v1.3.0" to listOf(
        "Rombak UI — navigasi bawah gaya YouTube",
        "Home feed dengan card thumbnail besar",
        "Screen About App dengan info lengkap",
        "Filter chip: Semua, Terbaru, Favorit, Lanjutkan",
        "Optimasi scrolling dan animasi",
    ),
    "v1.2.x" to listOf(
        "Perbaikan media controls hilang saat keluar recents",
        "Fix display cutout setting tidak berfungsi",
        "Update checker dari GitHub Releases",
        "Navigasi folder, auto-next episode",
        "Kontrol gestur: seek, brightness, volume",
        "Pilihan audio & subtitle multi-track",
    ),
)

// ── Extension ─────────────────────────────────────────────────────────────────

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
```

### Daftarkan Route di Navigation.kt

```kotlin
// Tambah di sealed class Screen:
object About : Screen("about")
object You   : Screen("you")

// Tambah di NavHost:
composable(Screen.About.route) {
    AboutScreen(onBack = { navController.popBackStack() })
}
composable(Screen.You.route) {
    YouScreen(
        onNavigateSettings = { navController.navigate(Screen.Settings.route) },
        onNavigateAbout    = { navController.navigate(Screen.About.route) },
    )
}
```

---

## 7. Perubahan build.gradle.kts

```kotlin
// ── SEBELUM ──
val versionMajor = 2
val versionNameMinor: Int = 8
// History: ...008 = folder list/grid toggle

// ── SESUDAH ──
val versionMajor = 3
val versionNameMinor: Int = 1  // reset ke 1 pada major bump
// History (v1.3.x):
//   001 = YouTube-style UI rombak, About screen, bottom nav, home feed
```

---

## 8. BuildConfig Baru yang Dibutuhkan AboutScreen

```kotlin
// app/build.gradle.kts — tambah di defaultConfig block:
buildConfigField("String", "APPLICATION_ID", "\"${applicationId}\"")
buildConfigField("int",    "VERSION_CODE",    "${calculatedVersionCode}")
// BUILD_DATE, COMMIT_HASH, BUILD_NUMBER, VERSION_FULL sudah ada
```

---

## 9. Urutan Implementasi

Kerjakan secara berurutan — tiap langkah adalah PR terpisah.

```
Step 1 ── BottomNavBar.kt + integrasi di Navigation.kt
          Route: home, folders, library, you
          Test: navigasi antar tab tanpa crash

Step 2 ── TopSearchBar.kt + FilterChipRow.kt
          Pasang ke HomeScreen menggantikan TopBar lama
          Test: search masih berfungsi

Step 3 ── VideoCardYouTube.kt
          Tambah komponen baru (jangan hapus yang lama dulu)
          Test: tampil benar di light + dark mode

Step 4 ── HomeScreen.kt rombak total
          - Hapus TabRow
          - Pasang section: Continue Watching (LazyRow) + Recently Added (LazyColumn)
          - Pakai VideoCardYouTube
          Test: semua video muncul, click → player

Step 5 ── YouScreen.kt + Settings dipindah ke sub-route
          Test: semua setting masih bisa diakses

Step 6 ── AboutScreen.kt
          BuildConfig baru
          Test: semua info tampil, link GitHub bisa dibuka

Step 7 ── build.gradle.kts: bump versionMajor=3, versionNameMinor=1
          Update RELEASE_NOTES di AboutScreen
          Tag: v1.3.001
```

---

## 10. Catatan Penting

### Yang TIDAK Berubah
- `PlayerActivity.kt` — tidak perlu disentuh
- `PlayerScreen.kt` + `PlayerViewModel.kt` — tidak perlu disentuh
- `RyouPlaybackService.kt` — sudah diperbaiki
- MPV config, subtitle, audio track selection — tidak berubah
- Database schema, DataStore keys — tidak berubah

### Yang Perlu Hati-Hati
- `HomeViewModel.kt`: hapus `HomeTab` enum setelah UI baru selesai
  (jangan hapus dulu sebelum VideoCardYouTube siap)
- `FolderDetailScreen` masih pakai `HomeScreen` yang sama —
  pastikan tetap berfungsi setelah rombak
- `SettingsScreen` dipanggil dari dua tempat (YouScreen + deep link Settings) —
  pastikan `onBack` masih berfungsi di kedua konteks

### Kompatibilitas
- Min SDK tetap 24 (Android 7.0)
- `NavigationBar` (M3) tersedia sejak Compose 1.2 — sudah included
- `FilterChip` tersedia sejak M3 1.0 — sudah included

---

## 11. HomeViewModel — Migrasi State Lengkap

### Enum Dihapus
```kotlin
// HAPUS seluruh enum ini dari HomeViewModel.kt:
enum class HomeTab(val label: String) {
    FOLDERS("Folder"),
    ALL("All"),
    RECENT("Recent"),
    STREAM("Stream"),
    PLAYLIST("Playlist"),
}
```

### Enum & State Baru
```kotlin
// TAMBAH di HomeViewModel.kt (atau FilterChipRow.kt):
enum class HomeFilter(val label: String) {
    ALL("Semua"),
    RECENT("Terbaru"),
    FAVORITES("Favorit"),
    IN_PROGRESS("Lanjutkan"),  // lastPlayedPosition > 0 && watchProgress < 0.95
}

// HomeUiState — field yang DIPERTAHANKAN:
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val videos: List<MediaItem> = emptyList(),
    val recentVideos: List<MediaItem> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val streams: List<NetworkStream> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = \"\",
    val sortOrder: MediaSortOrder = MediaSortOrder.NAME_ASC,
    val viewMode: ViewMode = ViewMode.GRID,
    val folderViewMode: ViewMode = ViewMode.GRID,
    val error: String? = null,
    // Stream & playlist dialogs (tidak berubah)
    val showAddStreamDialog: Boolean = false,
    val streamDialogUrl: String = \"\",
    val streamDialogName: String = \"\",
    val showCreatePlaylistDialog: Boolean = false,
    val newPlaylistName: String = \"\",

    // BARU — menggantikan selectedTab
    val activeFilter: HomeFilter = HomeFilter.ALL,
)

// HAPUS dari HomeUiState:
//   val selectedTab: HomeTab = HomeTab.FOLDERS   ← dihapus

// HAPUS dari HomeViewModel:
//   fun onTabSelected(tab: HomeTab)              ← dihapus

// TAMBAH ke HomeViewModel:
fun onFilterSelected(filter: HomeFilter) {
    _uiState.update { it.copy(activeFilter = filter) }
}

// Computed: video yang sedang dalam progress (untuk section "Lanjutkan")
val HomeUiState.inProgressVideos: List<MediaItem>
    get() = videos.filter { it.isInProgress }

// Computed: list yang ditampilkan sesuai filter aktif
val HomeUiState.filteredVideos: List<MediaItem>
    get() = when (activeFilter) {
        HomeFilter.ALL        -> videos
        HomeFilter.RECENT     -> recentVideos
        HomeFilter.FAVORITES  -> videos.filter { it.isFavorite }
        HomeFilter.IN_PROGRESS -> videos.filter { it.isInProgress }
    }
```

---

## 12. MediaItem — Extension Properties yang Dibutuhkan

Tambahkan di `MediaModels.kt` dalam `data class MediaItem`:

```kotlin
// Sudah ada:
//   val durationFormatted: String  → formatDuration(duration)
//   val sizeFormatted: String      → formatSize(size)

// TAMBAH — progress playback (0.0 – 1.0)
val watchProgress: Float
    get() = if (duration > 0L) lastPlayedPosition.toFloat() / duration.toFloat() else 0f

// TAMBAH — true jika video sudah mulai diputar tapi belum selesai
//   threshold: sudah > 5% dan belum > 95% selesai
val isInProgress: Boolean
    get() = watchProgress > 0.05f && watchProgress < 0.95f

// TAMBAH — alias yang lebih deskriptif untuk size
val fileSizeFormatted: String
    get() = sizeFormatted  // delegasi ke yang sudah ada
```

---

## 13. FolderScreen — Pisah dari HomeScreen

### Situasi Sekarang
`FolderDetailScreen` menggunakan `HomeScreen` dengan parameter `folderId`:
```kotlin
// Navigation.kt — SEKARANG
composable(Screen.FolderDetail.route) { backStackEntry ->
    HomeScreen(
        folderId    = folderId,
        folderTitle = folderName,
        // ...
    )
}
```

`HomeScreen` kemudian deteksi `isFolderDetail = folderId != null` dan
mengubah perilaku TopBar + menyembunyikan tab. Ini coupling yang tinggi.

### Solusi v1.3.0
Pisah menjadi `FolderScreen.kt` yang mandiri. `HomeScreen` tidak lagi
menerima `folderId` parameter.

```kotlin
// presentation/folder/FolderScreen.kt — BARU

@Composable
fun FolderScreen(
    folderId: Long,
    folderName: String,
    onMediaClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    // Layout sama dengan VideoContent yang sudah ada,
    // tapi di-scope ke folder tertentu
    // ViewModel: inject folderId via SavedStateHandle
}

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : ViewModel() {
    // Ambil folderId dari SavedStateHandle
    private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    // Flow video dalam folder ini saja
    // (filter dari getAllVideos atau query DB per-folder)
}
```

### Perubahan Navigation.kt untuk FolderScreen
```kotlin
// TAMBAH ke sealed class Screen:
object FolderDetail : Screen("folder/{folderId}/{folderName}") {
    fun createRoute(folderId: Long, name: String): String { ... }
}

// UBAH composable FolderDetail:
composable(Screen.FolderDetail.route, ...) { backStackEntry ->
    // SEBELUM: HomeScreen(folderId = ...)
    // SESUDAH:
    FolderScreen(
        folderId    = folderId,
        folderName  = folderName,
        onMediaClick = { launchPlayer(context, it.uri) },
        onBack       = { navController.popBackStack() },
    )
}
```

---

## 14. LibraryScreen — Rombak Penuh

### Sekarang
`LibraryScreen.kt` hanya menampilkan **playlist** saja. `Stream` ada di HomeScreen tab.

### Target v1.3.0
`LibraryScreen` menjadi satu layar untuk **Playlist + Stream**, mirip tab
"Pustaka" di YouTube. Dua sub-section dengan header.

```kotlin
// presentation/library/LibraryScreen.kt — ROMBAK

@Composable
fun LibraryScreen(
    onMediaClick: (MediaItem) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onStreamClick: (NetworkStream) -> Unit,    // BARU
    onAddStream: () -> Unit,                   // BARU (FAB)
    onCreatePlaylist: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = { /* judul "Pustaka" */ },
        floatingActionButton = {
            // FAB dengan speed dial: + Playlist, + Stream
            ExtendedFloatingActionButton(
                onClick = { showAddMenu = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Tambah") },
            )
        }
    ) {
        LazyColumn {
            // ── Continue Watching ─────────────────────────
            if (inProgressVideos.isNotEmpty()) {
                item { SectionHeader("Lanjutkan Menonton") }
                item {
                    LazyRow { /* VideoCardSmall untuk setiap video */ }
                }
            }

            // ── Playlist ──────────────────────────────────
            item { SectionHeader("Playlist") }
            if (playlists.isEmpty()) {
                item { EmptyStateInline("Belum ada playlist") }
            } else {
                items(playlists) { PlaylistCard(it, onPlaylistClick) }
            }

            // ── Network Stream ────────────────────────────
            item { SectionHeader("Stream Jaringan") }
            if (streams.isEmpty()) {
                item { EmptyStateInline("Belum ada stream tersimpan") }
            } else {
                items(streams) { StreamCard(it, onStreamClick) }
            }
        }
    }
}

// LibraryViewModel: inject PlaylistRepository + StreamRepository
// (sudah ada di HomeViewModel, pindahkan ke sini)
```

---

## 15. Migrasi: Apa yang Dipindah dari HomeViewModel

Setelah LibraryScreen punya VM sendiri, `HomeViewModel` menjadi lebih ringan.

| State / Function | Sekarang | Setelah v1.3.0 |
|-----------------|----------|----------------|
| `videos`        | HomeViewModel | HomeViewModel ✓ |
| `recentVideos`  | HomeViewModel | HomeViewModel ✓ |
| `folders`       | HomeViewModel | **FolderViewModel** (scope per folder) |
| `streams`       | HomeViewModel | **LibraryViewModel** |
| `playlists`     | HomeViewModel | **LibraryViewModel** |
| `selectedTab`   | HomeViewModel | **DIHAPUS** (bottom nav gantikan) |
| `onAddStream`   | HomeViewModel | **LibraryViewModel** |
| `onCreatePlaylist` | HomeViewModel | **LibraryViewModel** |

`HomeViewModel` setelah migrasi hanya mengelola:
```
videos, recentVideos, searchQuery, sortOrder, viewMode, activeFilter,
onToggleFavorite, onRescanMedia, onRefresh
```

---

## 16. YouStatsHeader — Spesifikasi Komponen

Di `YouScreen`, bagian atas menampilkan statistik singkat.

```kotlin
@Composable
fun YouStatsHeader(
    totalVideos: Int,
    totalDuration: Long,    // milliseconds, sum semua video
    favoriteCount: Int,
    inProgressCount: Int,
) {
    // Card dengan 4 metric kecil, layout 2x2
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(
                value = totalVideos.toString(),
                label = "Video",
                icon  = Icons.Outlined.VideoFile,
            )
            StatItem(
                value = formatDuration(totalDuration),
                label = "Durasi",
                icon  = Icons.Outlined.Timer,
            )
            StatItem(
                value = favoriteCount.toString(),
                label = "Favorit",
                icon  = Icons.Outlined.Favorite,
            )
            StatItem(
                value = inProgressCount.toString(),
                label = "Berlangsung",
                icon  = Icons.Outlined.PlayCircle,
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

Data diambil dari `YouViewModel` yang inject `MediaRepository` + `ToggleFavoriteUseCase`.

---

## 17. Optimasi Performa UI

### LazyList Key
Semua `LazyColumn` dan `LazyVerticalGrid` **wajib** menggunakan `key`:
```kotlin
// WAJIB — tanpa key, Compose tidak bisa recycle item saat data berubah
items(items = videos, key = { it.id }) { video -> VideoCardYouTube(...) }
items(items = folders, key = { it.id }) { folder -> FolderCard(...) }
```

### Stability Annotation
`MediaItem`, `MediaFolder`, dan semua domain model menggunakan `@Stable` atau
`@Immutable` agar Compose skip recompose saat parent recompose:
```kotlin
// MediaModels.kt — tambah annotation
@Immutable
@Parcelize
data class MediaItem(...) : Parcelable
```

### Image Loading (Coil)
VideoCardYouTube thumbnail menggunakan `AsyncImage` dengan `crossfade(200)` dan
`placeholder(R.drawable.placeholder_thumbnail)` agar transisi halus:
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(item.thumbnailUri ?: item.uri)
        .crossfade(200)
        .size(Size.ORIGINAL)
        .build(),
    contentDescription = item.displayName,
    contentScale = ContentScale.Crop,
    placeholder = painterResource(R.drawable.placeholder_thumbnail),
    error = painterResource(R.drawable.placeholder_thumbnail),
    modifier = Modifier.fillMaxSize(),
)
```

### Animasi BottomNavBar
Saat navigasi antar tab, konten body menggunakan `AnimatedContent` untuk
transisi fade + slide yang halus:
```kotlin
AnimatedContent(
    targetState = currentDestination,
    transitionSpec = {
        fadeIn(tween(200)) + slideInVertically { it / 20 } togetherWith
        fadeOut(tween(150))
    },
) { destination ->
    when (destination) {
        "home"    -> HomeScreen(...)
        "folders" -> FolderScreen(...)
        // dst
    }
}
```

---

## 18. Playlist Detail Screen

### Situasi Sekarang
`Screen.PlaylistDetail` tidak punya composable sendiri — di-redirect ke
`HomeScreen` via `PlaylistDetail` route yang belum diimplementasi.

### Target v1.3.0
Buat `PlaylistDetailScreen.kt` yang tampil saat user tap playlist dari LibraryScreen.

```kotlin
// presentation/playlist/PlaylistDetailScreen.kt — BARU (skeleton)

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onMediaClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    // Header: nama playlist, jumlah video, total durasi, thumbnail
    // Body: LazyColumn VideoCardList (bukan YouTube-style, karena ini queue)
    // FAB: Putar Semua
}
```

---

## 19. Checklist Testing

Sebelum tag v1.3.001, pastikan semua item ini ✓:

### Navigasi
- [ ] Bottom nav: tap Home → tampil home feed
- [ ] Bottom nav: tap Folder → tampil folder grid
- [ ] Bottom nav: tap Library → tampil playlist + stream
- [ ] Bottom nav: tap You → tampil stats + settings links
- [ ] Back dari FolderDetail → kembali ke Folder tab
- [ ] Back dari PlaylistDetail → kembali ke Library tab
- [ ] Back dari Settings → kembali ke YouScreen (bukan keluar app)
- [ ] Back dari About → kembali ke YouScreen
- [ ] State tab dipertahankan saat navigasi bolak-balik (restoreState=true)

### Home Feed
- [ ] Video muncul di section "Recently Added"
- [ ] Video dengan `lastPlayedPosition > 0` muncul di section "Lanjutkan"
- [ ] Progress bar muncul di thumbnail card yang `isInProgress = true`
- [ ] Filter chip "Favorit" hanya tampilkan video `isFavorite = true`
- [ ] Search masih berfungsi (real-time debounce)
- [ ] Pull-to-refresh masih berfungsi
- [ ] Click video → buka PlayerActivity

### Player
- [ ] Player masih berfungsi normal (tidak ada regresi)
- [ ] Media notification hilang saat swipe dari recents (fix dari patch sebelumnya)
- [ ] Back dari player → kembali ke HomeScreen tab terakhir

### About Screen
- [ ] Versi tampil dengan benar (VERSION_FULL)
- [ ] Build number, commit hash, build date tampil
- [ ] Link GitHub buka browser
- [ ] Link "Laporkan Bug" buka browser ke GitHub Issues
- [ ] Tap info row → copy ke clipboard

### Stabilitas
- [ ] Tidak ada crash saat rotasi layar di HomeScreen
- [ ] Tidak ada crash saat rotate saat FolderDetail terbuka
- [ ] Memory tidak naik saat scroll panjang (LazyList key terpasang)
- [ ] Dark mode: semua screen tampil benar
- [ ] Light mode: semua screen tampil benar

---

## 20. Ringkasan File yang Dibuat / Dimodifikasi

### BARU (9 file)
```
app/src/main/kotlin/com/ryoustream/player/
├── presentation/
│   ├── about/
│   │   └── AboutScreen.kt
│   ├── folder/
│   │   ├── FolderScreen.kt
│   │   └── FolderViewModel.kt
│   ├── playlist/
│   │   ├── PlaylistDetailScreen.kt
│   │   └── PlaylistDetailViewModel.kt
│   ├── you/
│   │   ├── YouScreen.kt
│   │   └── YouViewModel.kt
│   └── components/
│       ├── BottomNavBar.kt
│       ├── TopSearchBar.kt
│       └── FilterChipRow.kt
```

### DIMODIFIKASI (8 file)
```
├── presentation/
│   ├── navigation/Navigation.kt      ← route baru, BottomNav scaffold
│   ├── home/HomeScreen.kt            ← rombak total (hapus TabRow)
│   ├── home/HomeViewModel.kt         ← hapus HomeTab, tambah HomeFilter
│   ├── library/LibraryScreen.kt      ← gabung Playlist + Stream
│   └── settings/SettingsScreen.kt    ← hapus update check (pindah ke YouScreen)
├── domain/model/MediaModels.kt       ← tambah watchProgress, isInProgress
└── app/build.gradle.kts              ← versionMajor=3, versionNameMinor=1
```

### TIDAK DISENTUH (stabil)
```
player/PlayerActivity.kt
player/PlayerScreen.kt
player/PlayerViewModel.kt
service/RyouPlaybackService.kt
util/AppUpdateChecker.kt
data/ (semua repository impl)
domain/ (semua usecase)
di/ (semua Hilt module)
```

---

*PLAN_v1.3.0.md — dibuat: 2025 — ryoustream/RyouPlayer*
