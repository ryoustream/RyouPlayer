# PLAN v1.4.0 (1.4.001)

Lima pilar utama: Home UI modern, Settings navigable, Player YouTube 2025 style, Controls pill-grouped, Media name fix.

---

## RISET — YouTube Android v20.42+ (Oktober 2025)

Redesign terbesar YouTube dalam bertahun-tahun. Ringkasan perubahan yang relevan untuk RyouPlayer:

### Player Landscape (yang paling relevan)
```
┌─────────────────────────────────────────────────────────────┐
│ [←]  Title                               [PiP] [MoreVert]  │ ← TopBar minimalis
│                                                             │
│                   ▒▒▒▒ VIDEO ▒▒▒▒                           │
│                                                             │
│              [⏮]  [↩10]  [▶/⏸]  [10↪]  [⏭]               │ ← Center transport
│                                                             │
│  ╔══════════════════╗              ╔════════════╗           │
│  ║ [🔒][🔁][⏭][spd] ║              ║ [CC][🎵][ℹ]║           │ ← PILL kiri & kanan
│  ╚══════════════════╝              ╚════════════╝           │
│                                                             │
│  ━━━━━╸━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │ ← Seeker
│  00:14                                         -44:46      │ ← Timestamps
└─────────────────────────────────────────────────────────────┘
```

### YouTube 2025 Key Changes
1. **Liquid Glass / Semi-transparent** — overlay controls translucent, bukan solid hitam
2. **Pill-shaped groups** — tombol terkait dikelompokkan dalam kontainer pill bersama
3. **Landscape bottom-left pill** — action controls (repeat, speed, subtitle, dll) dalam satu pill
4. **No darkening when paused** — background tidak gelap saat pause
5. **Double-tap feedback** — "+10" / "-10" text animasi, bukan segitiga lama
6. **Bolder rounded icons** — ikon lebih tebal, rounded outline
7. **Controls obscure less** — overlay floating lebih tipis, gradient lebih halus

---

## PETA FILE YANG DIMODIFIKASI

```
DIMODIFIKASI
  app/build.gradle.kts                  ← version bump → 1.4.001
  data/local/MediaStoreDataSource.kt    ← FIX media name (strip ext, prefer title)
  presentation/home/HomeScreen.kt       ← revamp total UI
  presentation/home/HomeViewModel.kt    ← tambah banner/featured logic
  presentation/player/PlayerScreen.kt  ← revamp controls (pill groups, liquid glass)
  presentation/settings/SettingsScreen.kt  ← navigable sections
  presentation/components/VideoCards.kt   ← update VideoCardYouTube, InProgressCard
  presentation/components/TopSearchBar.kt ← polish search bar style

DIBUAT BARU
  presentation/settings/SettingsNavGraph.kt     ← sub-page navigation
  presentation/settings/sections/PlaybackSettingsPage.kt
  presentation/settings/sections/SubtitleSettingsPage.kt
  presentation/settings/sections/GestureSettingsPage.kt
  presentation/settings/sections/AppearanceSettingsPage.kt
  presentation/settings/sections/FileSettingsPage.kt
  presentation/settings/sections/AdvancedSettingsPage.kt

TIDAK DISENTUH
  PlayerActivity.kt        ← window/immersive flags sudah OK
  FolderScreen.kt          ← sudah di-update di v1.3.102
  FolderViewModel.kt       ← sudah di-update di v1.3.102
  AppUpdateChecker.kt      ← sudah di-update di v1.3.102
```

---

## BUG FIX — Media Name (Section 1)

### Root Cause
`MediaStore.Video.Media.DISPLAY_NAME` mengembalikan **filename raw** termasuk ekstensi, misalnya:
- `"1704067200000.mp4"` → ditampilkan sebagai angka
- `"VID20240101_123456.mp4"` → bukan nama manusiawi
- `"content://media/external/video/media/1234"` → ID mentah

`TITLE` dari MediaStore mengembalikan metadata embedded (ID3/MP4 atom), bisa lebih bermakna.

### Solusi di `MediaStoreDataSource.kt`
```kotlin
// Fungsi helper baru
private fun computeDisplayName(rawDisplayName: String?, title: String?): String {
    // 1. Ambil displayName, strip ekstensi
    val nameNoExt = rawDisplayName
        ?.substringBeforeLast('.')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: ""

    // 2. Cek apakah hasilnya "angka saja" (timestamp/ID)
    val isPureNumeric = nameNoExt.all { it.isDigit() }
    val looksLikeId  = nameNoExt.length > 10 && isPureNumeric

    // 3. Jika angka murni & ada title embedded yang berbeda → pakai title
    val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() && it != nameNoExt }

    return when {
        looksLikeId && cleanTitle != null -> cleanTitle
        isPureNumeric && cleanTitle != null -> cleanTitle
        nameNoExt.isNotBlank() -> nameNoExt
        cleanTitle != null -> cleanTitle
        else -> rawDisplayName ?: "Video"
    }
}
```

Pemakaian di `queryAllVideos()`:
```kotlin
val rawName  = c.getString(nameCol)
val rawTitle = c.getString(titleCol)

MediaItem(
    displayName = computeDisplayName(rawName, rawTitle),
    title       = rawTitle?.takeIf { it.isNotBlank() } ?: computeDisplayName(rawName, rawTitle),
    // ...
)
```

### Juga update `VideoCardYouTube`:
- Tampilkan `item.displayName` (sudah clean setelah fix di atas) — tidak perlu perubahan di composable
- Tapi pastikan `item.title` dipakai di PlayerScreen topbar sebagai fallback

---

## HOME UI REVAMP (Section 2–5)

### Section 2 — Masalah Saat Ini

```
SEBELUM (v1.3.x)
┌─────────────────────────────────────────────────┐
│ [🔍 Cari] [avatar]                              │ ← Search bar biasa
│ [Semua][Baru][Favorit][Berlangsung]              │ ← FilterChip row
│                                                 │
│  ┌── Lanjutkan Menonton ──────────────────────┐ │
│  │ [card][card][card]  ←scroll→               │ │ ← LazyRow pendek
│  └────────────────────────────────────────────┘ │
│                                                 │
│  ── Semua Video ──                              │
│  [thumb 16:9]                                   │
│  [folder-icon] Title · folder · 1080p · size   │ ← info row basic
│  ─────────────────────────────────────────────  │
│  [thumb 16:9]                                   │
│  ...                                            │
└─────────────────────────────────────────────────┘
```

### Section 3 — Setelah (v1.4.0)

```
SESUDAH (v1.4.0) — YouTube-modern style
┌─────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────┐ │
│ │ RyouPlayer      [🔍]  [cast?]  [you-avatar] │ │ ← TopBar + avatar user (settings)
│ └─────────────────────────────────────────────┘ │
│                                                 │
│ ┌ ─ ─ ─ ─ ─ ─ Chips ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─┐ │
│  [Semua] [Baru] [Favorit] [Sedang Ditonton]    │ ← Chips di dalam Surface tipis
│ └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
│                                                 │
│  ── Lanjutkan Menonton ──  [Lihat semua →]      │ ← Section header dengan action
│ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│ │ [thumb]  │ │ [thumb]  │ │ [thumb]  │ ←scroll │ ← InProgressCard REVAMP
│ │ ▓▓░░░░░░ │ │ ▓▓▓░░░░░ │ │ ░░░░░░░░ │         │   (lebih tinggi, overlay progress)
│ │ Title    │ │ Title    │ │ Title    │         │
│ └──────────┘ └──────────┘ └──────────┘         │
│                                                 │
│  ── Semua Video ──                              │
│ ┌─────────────────────────────────────────────┐ │
│ │ [thumb 16:9 — rounded 12dp]                 │ │ ← thumbnail full width, rounded
│ │ ┌──┐  Title multi-line (2 baris)            │ │ ← folder initial avatar (huruf)
│ │ │F │  FolderName • 1080p • 2.3GB • 3h ago   │ │   bukan icon Folder generic
│ │ └──┘                                   [⋮]  │ │
│ └─────────────────────────────────────────────┘ │
│                                                 │
│ ┌─────────────────────────────────────────────┐ │
│ │ [thumb]                                     │ │
│ │ ...                                         │ │
└─────────────────────────────────────────────────┘
```

### Section 4 — Perubahan Spesifik

#### TopBar Baru
```kotlin
// Bukan TopAppBar Material3 biasa — custom Row
Row {
    Text("RyouPlayer", style = titleLarge, fontWeight = Bold)   // kiri: branding
    Spacer(weight 1f)
    IconButton(search)          // search icon
    IconButton(cast?)           // cast icon (opsional, bisa hide jika tidak ada)
    // Avatar lingkaran → onClick buka Settings
    Box(size=34, shape=Circle, background=primaryContainer) {
        Text(initial_huruf_pertama, style=labelLarge)
    }
}
```

#### FilterChipRow — Lebih Modern
- Chip sekarang pakai `FilterChip` M3 dengan `selectedBorder` dan warna fill
- Tidak ada padding vertikal berlebih
- Smooth scroll, tidak ada clipping kiri

#### InProgressCard — Revamp
```
Sebelum: width=160dp, thumbnail + progress bar bawah + text kecil
Sesudah: width=180dp, thumbnail LEBIH TINGGI (1:1 ratio jika portrait, 16:9 jika landscape),
         progress bar 4dp dengan rounded, overlay gradient bawah,
         sisa waktu badge di atas thumbnail kanan atas,
         nama file 2 baris di bawah
```

#### VideoCardYouTube — Penyempurnaan
- **Folder avatar** → ganti dari `Icon(Folder)` menjadi **huruf pertama folder** (seperti channel YouTube)
- Font avatar: `titleMedium`, warna dari `primaryContainer`
- Thumbnail: sudah `16:9`, tambah corner radius `12dp` (sekarang `0dp`)
- Meta row: `FolderName • Resolution • Size • "X waktu lalu"`
- Hapus gradient overlay tipis pada thumbnail (lebih bersih)

### Section 5 — HomeViewModel Additions
```kotlin
// State tambahan
data class HomeUiState(
    // ... existing fields ...
    val featuredVideo: MediaItem? = null,  // video terbaru/terpopuler untuk banner
)

// Tidak ada breaking change pada existing state
// featuredVideo = inProgressVideos.firstOrNull() ?: videos.firstOrNull()
```

---

## SETTINGS REVAMP — Navigable Sections (Section 6–8)

### Section 6 — Masalah Saat Ini

Settings saat ini adalah **satu LazyColumn panjang** dengan semua item tercampur.
"Nyangkut UI lama" artinya tampilan masih flat list tanpa hierarki visual.

### Section 7 — Arsitektur Baru: Settings as Navigation

```
SettingsScreen (root) ← index/menu utama
├── PlaybackSettingsPage    ← Pemutaran, Kecepatan, Ingat Posisi, PiP, dll
├── SubtitleSettingsPage    ← Subtitle toggle, font size, style
├── GestureSettingsPage     ← Seek, Brightness, Volume, Double-tap
├── AppearanceSettingsPage  ← Tema, AMOLED, Dynamic Color, Animasi, Notch
├── FileSettingsPage        ← Hidden files, .nomedia, scan
└── AdvancedSettingsPage    ← Codec, Hardware decode, Network buffer, Reset, Update
```

### Section 8 — SettingsScreen Root (Index)

```
SESUDAH — SettingsScreen sebagai menu utama bergaya modern
┌─────────────────────────────────────────────────┐
│ [←]  Pengaturan                                 │
│ ─────────────────────────────────────────────── │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ 🎬  Pemutaran                      [›]  │   │ ← NavigationCard
│  │     Kecepatan, ingat posisi, PiP...     │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ CC  Subtitle                       [›]  │   │
│  │     Font, ukuran, tampilkan...          │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ 👆  Gerakan                        [›]  │   │
│  │     Seek, kecerahan, volume...          │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ 🎨  Tampilan                       [›]  │   │
│  │     Tema, AMOLED, animasi...            │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ 📁  File & Media                   [›]  │   │
│  │     File tersembunyi, .nomedia...       │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │ ⚙️  Lanjutan                        [›]  │   │
│  │     Codec, hardware, pembaruan...       │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ── Tentang ──                                  │
│  RyouPlayer v1.4.001  · Lihat perubahan         │
│                                                 │
└─────────────────────────────────────────────────┘
```

Setiap `NavigationCard`:
```kotlin
@Composable
fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,         // ringkasan isi sub-page
    badge: String? = null,    // opsional: "Baru" chip, versi, dll
    onClick: () -> Unit,
)
```

### Implementasi Navigation

Dua opsi — **pilih Opsi B** (lebih simpel, tidak perlu NavGraph baru):

**Opsi A** (NavGraph): Tambah route `settings/playback`, `settings/subtitle`, dll di `Navigation.kt`.
Lebih clean tapi perlu update Navigation + Hilt setup.

**Opsi B** (in-screen state): Satu `SettingsScreen` dengan `currentPage: SettingsPage?` state.
`null` = halaman index. Jika non-null, tampilkan sub-page dengan `AnimatedContent`.
Back handler di-intercept untuk kembali ke index.

**Pilihan: Opsi B** — lebih simpel, tidak perlu ubah Navigation.kt.

```kotlin
enum class SettingsPage { PLAYBACK, SUBTITLE, GESTURE, APPEARANCE, FILE, ADVANCED }

@Composable
fun SettingsScreen(onBack: () -> Unit, ...) {
    var currentPage by remember { mutableStateOf<SettingsPage?>(null) }

    BackHandler(enabled = currentPage != null) { currentPage = null }

    AnimatedContent(targetState = currentPage, ...) { page ->
        when (page) {
            null              -> SettingsIndexPage(onNavigate = { currentPage = it }, onBack = onBack)
            SettingsPage.PLAYBACK    -> PlaybackSettingsPage(onBack = { currentPage = null }, vm)
            SettingsPage.SUBTITLE    -> SubtitleSettingsPage(onBack = { currentPage = null }, vm)
            SettingsPage.GESTURE     -> GestureSettingsPage(onBack = { currentPage = null }, vm)
            SettingsPage.APPEARANCE  -> AppearanceSettingsPage(onBack = { currentPage = null }, vm)
            SettingsPage.FILE        -> FileSettingsPage(onBack = { currentPage = null }, vm)
            SettingsPage.ADVANCED    -> AdvancedSettingsPage(onBack = { currentPage = null }, vm)
        }
    }
}
```

Transisi: `slideInHorizontally(from right) + fadeIn` saat masuk sub-page,
`slideOutHorizontally(to right) + fadeOut` saat kembali.

---

## PLAYER UI REVAMP (Section 9–15)

### Section 9 — Diagram Before vs After

```
SEBELUM (v1.3.102)
┌────────────────────────────────────────────────────────┐
│ [←]  Title                                  [MoreVert] │
│ ██████████████████ BLACK GRADIENT ████████████████████  │
│                                                        │
│            [⏮] [↩10] [▶/⏸] [10↪] [⏭]                │
│                                                        │
│ ████████████████████████████████████████████████████   │
│ ━━━━━━╸━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━    │
│ 00:14                                        -44:46    │
│ [🔒][🔁][⏭]                    [CC][🎵][ℹ]             │ ← Row 1: 6 icons berserakan
│ [spd][queue]               [ratio][orient]            │ ← Row 2: 4 items berserakan
└────────────────────────────────────────────────────────┘
  Masalah:
  - Gradient keras gelap memblokir konten video
  - 10 item kontrol di 2 baris tanpa pengelompokan jelas
  - Tidak ada visual grouping / pill
  - Tombol kontrol terasa "berantakan"

SESUDAH (v1.4.0) — YouTube 2025 Liquid Glass style
┌────────────────────────────────────────────────────────┐
│ [←]  Title                           [PiP?] [MoreVert] │ ← TopBar
│                                                        │
│                  VIDEO CONTENT                         │ ← Overlay LEBIH TIPIS
│                                                        │
│            [⏮] [↩10] [▶/⏸] [10↪] [⏭]                │ ← Center transport
│                                                        │
│  ╔═══════════════════════╗  ╔══════════════════════╗  │
│  ║ [🔒] [🔁] [⏭] [1.0×] ║  ║ [CC] [🎵] [ℹ] [⋮]   ║  │ ← PILL KIRI & PILL KANAN
│  ╚═══════════════════════╝  ╚══════════════════════╝  │   (glass/frosted background)
│                                                        │
│  ━━━━━╸━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │ ← Seeker
│  00:14                                         -44:46 │ ← Timestamps
└────────────────────────────────────────────────────────┘
```

### Section 10 — Gradient & Background

**Sebelum**: `Color.Black.copy(0.85f)` top, `Color.Black.copy(0.92f)` bottom — terlalu keras.

**Sesudah**: Lebih halus, mirip YouTube 2025:
```kotlin
// Top gradient — lebih soft
Brush.verticalGradient(
    listOf(Color.Black.copy(0.65f), Color.Transparent),
    endY = 100f
)

// Bottom gradient — lebih soft, cukup untuk readability
Brush.verticalGradient(
    listOf(Color.Transparent, Color.Black.copy(0.75f)),
    startY = 0f
)

// TIDAK ada dimming saat paused (hapus perubahan alpha saat paused)
```

### Section 11 — TopBar Baru

Lebih clean dari sebelumnya, tambah PiP button:
```
[← ArrowBack]  [Title ellipsis — weight=1]  [PiP button]  [MoreVert]
```

PiP button: `Icons.Default.PictureInPictureAlt` — hanya visible jika `Build.VERSION >= O`.
Pindahkan PiP logic dari MoreVert menu ke tombol langsung di TopBar.

MoreVert tetap ada berisi: Aspect Ratio, Orientation, Lock, Video Info.

### Section 12 — Center Transport (tidak banyak berubah)

Transport center tetap sama strukturnya (sudah bagus di v1.3.x), tapi:
- Play/Pause FAB: ganti dari `Color.White.copy(0.15f)` ke `Color.White.copy(0.20f)` + tambah `blur` atau `border` untuk liquid glass feel
- Seek overlay feedback: ganti triangle lama → tampilkan teks `"+10"` / `"-10"` lebih besar dan simpel (YouTube 2025 style)

```kotlin
// Baru: seekFeedback composable (ganti SeekPreview lama)
@Composable
fun SeekFeedbackOverlay(seekDelta: Long) {
    val sign = if (seekDelta >= 0) "+" else ""
    val seconds = seekDelta / 1000
    Text(
        "$sign${seconds}s",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
}
```

### Section 13 — Pill Controls (INTI UTAMA)

**Ini adalah perubahan terbesar di Player.** Ganti 2 baris flat menjadi 2 pill groups.

#### Pill Component
```kotlin
@Composable
fun ControlPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),       // fully rounded
        color = Color.Black.copy(alpha = 0.45f), // frosted glass feel
        border = BorderStroke(0.5.dp, Color.White.copy(0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}
```

#### Layout 2 Pill Groups (dalam Row di bawah Center Transport)
```kotlin
Row(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    // ── PILL KIRI: Playback controls ──────────────
    ControlPill {
        PillIconButton(icon=Lock/LockOpen, onClick=toggleLock, active=isLocked)
        PillDivider()
        PillIconButton(icon=Repeat/RepeatOne, onClick=toggleRepeat, active=repeatMode!=NONE)
        PillIconButton(icon=SkipNext, onClick=toggleAutoNext, active=autoNext,
                       label="Auto")
        PillDivider()
        // Speed badge — teks "1.0×" bukan icon
        PillTextButton(text="${speed}×", onClick=showSpeedMenu)
    }

    // ── PILL KANAN: Media controls ────────────────
    ControlPill {
        PillIconButton(icon=Subtitles, onClick=showSubtitlePanel, active=subtitleEnabled)
        PillIconButton(icon=Audiotrack, onClick=showAudioPanel)
        PillIconButton(icon=Info, onClick=toggleVideoInfo)
        PillDivider()
        PillIconButton(icon=Queue/PlaylistPlay, onClick=showQueuePanel)
    }
}
```

#### PillIconButton Spec
```kotlin
@Composable
fun PillIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
    label: String? = null,       // opsional label kecil di bawah icon
    size: Int = 20,              // icon size dp
    modifier: Modifier = Modifier,
) {
    // touch target 40dp, icon 20dp
    // active → tint = primary color
    // inactive → tint = White.copy(0.80f)
    // jika label ada → Column(icon, label kecil 9sp)
}
```

#### PillDivider
```kotlin
Box(
    modifier = Modifier
        .width(0.5.dp)
        .height(20.dp)
        .background(Color.White.copy(0.25f))
)
```

### Section 14 — Seeker & Timestamps

Seeker tetap sama, hanya perbaikan visual minor:
- Track height: sudah `4dp` (OK)
- Thumb: sudah `16dp` bulat putih (OK)
- Timestamps: pastikan konsisten — `00:14` kiri, `-44:46` kanan (sudah OK di v1.3.x)
- Tambah padding bawah dari pill atas (ada gap 8dp antara pill dan seeker)

```kotlin
// Urutan dari bawah ke atas:
// [timestamps row]
// [seeker slider]  
// [8dp spacer]
// [pill row]        ← sekarang di atas seeker, bukan di bawah
// [center transport]
```

**Perubahan urutan**: Pill controls NAIK, ditempatkan **di antara center transport dan seeker**,
bukan di bawah seeker. Ini lebih mirip YouTube 2025 di mana action controls
ada di bawah tengah tapi di atas progress bar.

Atau pertahankan di bawah seeker untuk konsistensi aksesibilitas. **Pilihan: tetap di bawah seeker**
(lebih mudah dijangkau ibu jari, konsisten dengan v1.3.x).

### Section 15 — Orientation & Aspect Ratio

Pindahkan Orientation dan Aspect Ratio dari MoreVert ke bawah — tapi karena pill sudah penuh,
tempatkan keduanya sebagai **row kecil di bawah pill** (hanya 2 tombol):

```kotlin
// Row paling bawah setelah timestamps
Row(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    // Kiri: Orientation toggle (icon kecil)
    SmallIconButton(icon=ScreenRotation, onClick=cycleOrientation,
                    label=orientationMode.shortLabel)

    // Kanan: Aspect Ratio label
    SmallIconButton(icon=AspectRatio, onClick=cycleAspectRatio,
                    label=aspectRatioMode.label)
}
```

Ini memindahkan Orientation & Ratio **keluar dari MoreVert** dan menjadi accessible langsung.
MoreVert hanya berisi Lock, Video Info, PiP (kalau tidak muat di TopBar), Reset Brightness.

---

## VIDEO CARDS (Section 16)

### VideoCardYouTube — Folder Avatar

```kotlin
// Sebelum
Icon(Icons.Default.Folder, ..., tint = onPrimaryContainer)

// Sesudah — initial letter, lebih personal
val initial = item.folderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
val bgColor = colorFromString(item.folderName) // hash → MaterialTheme color

Box(
    modifier = Modifier.size(36.dp).clip(CircleShape).background(bgColor),
    contentAlignment = Alignment.Center,
) {
    Text(initial, style = titleSmall, color = Color.White, fontWeight = Bold)
}
```

`colorFromString()` → hash `folderName.hashCode()` ke salah satu dari 8 warna Material3 token
(misal `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, dll).
Folder yang sama selalu dapat warna yang sama.

### VideoCardYouTube — Thumbnail Corner Radius

```kotlin
// Sebelum: tidak ada clip di thumbnail
AsyncImage(modifier = Modifier.fillMaxSize())

// Sesudah: clip semua sudut (YouTube style)
AsyncImage(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
```

### InProgressCard — Revamp

```kotlin
// Sebelum: width=160dp, 16:9, progress bar 3dp, text labelSmall
// Sesudah:
Card(
    modifier = Modifier.width(200.dp),
    shape = RoundedCornerShape(12.dp),
) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
        AsyncImage(...)
        // Gradient overlay
        // Sisa waktu badge kanan atas: "44:46 tersisa" pada Surface hitam
        // Progress bar 4dp rounded di bawah
    }
    Column(Modifier.padding(8.dp)) {
        Text(item.displayName, bodySmall, maxLines=2)
        Text(item.folderName, labelSmall, onSurfaceVariant)
    }
}
```

---

## KOMPONEN PENDUKUNG (Section 17)

### SettingsNavCard (baru)
```kotlin
@Composable
fun SettingsNavCard(
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    subtitle: String,
    badge: String? = null,  // misal "Baru" untuk fitur baru
    onClick: () -> Unit,
)
```
- Shape: `RoundedCornerShape(16.dp)`
- Background: `MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)`
- Elevation: `0.dp` (flat, seperti YouTube cards)
- Padding: `16.dp` semua sisi
- Icon: `size=48.dp` container bulat dengan `containerColor=primaryContainer`, icon `24.dp`

### PillIconButton & ControlPill
Lihat spec di Section 13.

### SmallIconButton (baru — untuk Orientation/Ratio row)
```kotlin
@Composable
fun SmallIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
)
// Surface frosted 0.35f, shape RoundedCornerShape(8dp)
// Row: Icon(16dp) + Text(label, 11sp) + horizontal padding 10dp
```

---

## VERSION BUMP (Section 18)

```kotlin
// build.gradle.kts
val versionNameMajor: Int = 1
val versionNameMinor: Int = 4    // 3 → 4
val versionNamePatch: Int = 0    // baru
val versionNameBuild: Int = 001  // BARU (ganti dari single versionNameMinor)
```

Ubah skema versi: `1.4.001` lebih ekspresif (major.minor.build).
Update `BuildConfig.VERSION_FULL` sesuai.

---

## URUTAN IMPLEMENTASI

```
Pass 1 — Bug Fix (paling kritikal, quick win)
  [P1-A] MediaStoreDataSource: computeDisplayName() fix media name angka

Pass 2 — Player Controls Revamp
  [P2-A] PlayerScreen: ControlPill + PillIconButton + PillDivider components
  [P2-B] PlayerScreen: ganti 2 flat rows dengan 2 ControlPill groups
  [P2-C] PlayerScreen: gradient lebih soft, hapus dimming saat pause
  [P2-D] PlayerScreen: SeekFeedbackOverlay ("+10s" text) ganti SeekPreview lama
  [P2-E] PlayerScreen: TopBar tambah PiP button langsung
  [P2-F] PlayerScreen: Orientation + Ratio row di bawah timestamps

Pass 3 — Home UI Revamp
  [P3-A] VideoCards: folder initial-letter avatar + thumbnail corner radius
  [P3-B] VideoCards: InProgressCard revamp
  [P3-C] HomeScreen: TopBar baru (branding + avatar → settings)
  [P3-D] HomeScreen: FilterChipRow polish
  [P3-E] HomeScreen: section header dengan action "Lihat semua"

Pass 4 — Settings Navigable
  [P4-A] SettingsScreen: SettingsNavCard component
  [P4-B] SettingsScreen: IndexPage (root menu)
  [P4-C] SettingsScreen: 6 sub-pages dengan AnimatedContent
  [P4-D] SettingsScreen: BackHandler untuk sub-page

Pass 5 — Polish & Version Bump
  [P5-A] build.gradle.kts: version → 1.4.001
  [P5-B] Review semua perubahan, test edge cases
```

---

## NOTES & CONSTRAINTS

### Yang TIDAK berubah di v1.4.0
- Logic MediaRepository, FolderViewModel, AppUpdateChecker — sudah OK di v1.3.102
- Navigation.kt routing — tidak ada route baru (Settings sub-page pakai in-screen state)
- PlayerViewModel — tidak ada breaking change, hanya PlayerScreen.kt yang diubah
- Database, DataStore schema — tidak berubah

### Liquid Glass Implementation
Android tidak punya native `blur()` yang efisien di Compose (perlu RenderEffect yang API 31+).
Untuk kompatibilitas, pakai **semi-transparent `Surface`** dengan `color = Color.Black.copy(0.45f)`
dan `border` tipis. Efek "glass" dicapai dari translucency + border, tanpa actual blur.

Untuk API 31+, bisa ditambahkan blur sebagai progressive enhancement:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Modifier.blur(radius = 8.dp)  // Compose experimental
}
```
Tapi ini opsional, tidak wajib untuk v1.4.0.

### Settings ViewModel
SettingsViewModel tetap satu kelas. Sub-pages semua consume ViewModel yang sama (diteruskan sebagai
parameter). Tidak perlu SettingsViewModel terpisah per sub-page.

### BackHandler Priority
BackHandler di Settings sub-page harus `enabled = currentPage != null`.
Jika `currentPage == null`, Back diteruskan ke `onBack: () -> Unit` yang navigasi keluar settings.


---

## SECTION 19 — MINIMUM API 29 (Android 10) + ARM64-ONLY

### Konteks

| | Sebelum | Sesudah |
|---|---|---|
| `minSdk` | 31 (Android 12) | **29 (Android 10)** |
| `targetSdk` | 35 | 35 (tidak berubah) |
| `compileSdk` | 35 | 35 (tidak berubah) |
| ABI | arm64-v8a (sudah) | arm64-v8a ✓ (konfirmasi) |

**Alasan turun ke API 29:**
Android 10 (rillis 2019) masih dipakai oleh jutaan perangkat low-mid range.
API 31 terlalu tinggi — memotong ~15–20% pangsa pasar Android aktif global.
Android 10+ sudah mendukung semua fitur inti RyouPlayer (scoped storage, MPV, Compose).

**ARM64-only sudah benar:** `jniLibs/` hanya berisi `arm64-v8a/` (MPV native libs).
Tidak ada armeabi-v7a, x86, x86_64 — sudah tepat untuk modern device 64-bit.

---

### Audit API — Yang Sudah Aman (tidak perlu ubah)

Semua penggunaan API tinggi sudah dilindungi `Build.VERSION.SDK_INT >= ...` check:

| Fitur | API Yang Dipakai | Guard Saat Ini | Status |
|---|---|---|---|
| Dynamic Color (Material You) | API 31 (S) | `>= S` ✓ | Aman, degradasi graceful ke tema solid |
| `registerReceiver` + flags | API 33 (TIRAMISU) | `>= TIRAMISU` ✓ | Aman |
| `MANAGE_EXTERNAL_STORAGE` | API 30 (R) | `>= R` ✓ | Aman |
| `READ_MEDIA_VIDEO` permission | API 33 (TIRAMISU) | `>= TIRAMISU` ✓ | Aman |
| `MediaStore.VOLUME_EXTERNAL` | API 29 (Q) | `>= Q` ✓ | Aman — API 29 = target kita |
| PiP `PictureInPictureParams` | API 26 (O) | `>= O` ✓ | Aman |
| `ACTION_MANAGE_UNKNOWN_APP_SOURCES` | API 26 (O) | `>= O` ✓ | Aman |
| `WindowCompat.setDecorFitsSystemWindows` | API 21 (compat) | — | Aman |
| `WindowInsetsControllerCompat` | API 21 (compat) | — | Aman |
| `enableEdgeToEdge()` | API 21 (compat) | — | Aman |

---

### Apa Yang Berubah Jika minSdk = 29

#### Perlu Ditangani: Permission Flow Android 10

Di API 29, storage permission masih menggunakan **`READ_EXTERNAL_STORAGE`** (bukan `READ_MEDIA_VIDEO` yang baru di API 33).
`PermissionHelper.kt` sudah punya guard `>= TIRAMISU` — tapi perlu pastikan fallback ke `READ_EXTERNAL_STORAGE` valid untuk API 29–32.

```kotlin
// PermissionHelper — audit & pastikan lengkap untuk semua tier:
fun requiredPermissions(): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        listOf(Manifest.permission.READ_MEDIA_VIDEO)         // API 33+ → READ_MEDIA_VIDEO
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)    // API 30-32 → READ_EXTERNAL_STORAGE
    else ->
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)    // API 29 → READ_EXTERNAL_STORAGE
}
```

#### Perlu Ditangani: WRITE_EXTERNAL_STORAGE

`WRITE_EXTERNAL_STORAGE` di API 29 masih diperlukan untuk fitur download APK (DownloadManager ke public Downloads).
Di API 29, `DownloadManager` ke `DIRECTORY_DOWNLOADS` **tidak butuh** WRITE permission di API 29+
(sudah scoped storage). Cek AndroidManifest:

```xml
<!-- Tambah maxSdkVersion agar tidak muncul di API 29+ -->
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

#### Perlu Ditangani: SplashScreen

`androidx.core:core-splashscreen` bekerja di API 21+ via compat — **tidak ada masalah**.
Tapi di API 29-30, splash tidak ada native splash system, digantikan compat library. Sudah OK.

#### Tidak Ada Masalah: Compose & Material3

Compose BOM `2024.12.01` menetapkan `minSdk = 21`. PullToRefreshBox, ModalBottomSheet,
semua M3 components — kompatibel dengan API 29. ✓

---

### File Yang Diubah

#### 1. `gradle/libs.versions.toml`
```toml
# Sebelum
minSdk = "31"

# Sesudah
minSdk = "29"
```

#### 2. `app/build.gradle.kts`
```kotlin
defaultConfig {
    // Sebelum
    minSdk = 31
    
    // Sesudah
    minSdk = 29
}
```

#### 3. `app/src/main/AndroidManifest.xml`
```xml
<!-- Tambah — WRITE_EXTERNAL_STORAGE hanya untuk API 28 ke bawah -->
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

#### 4. `util/PermissionHelper.kt` — audit fallback API 29
Pastikan `requiredPermissions()` mengembalikan `READ_EXTERNAL_STORAGE` untuk API 29–32.
Cek `hasStoragePermission()` cover tier API 29.

#### 5. `presentation/permission/PermissionScreen.kt` — audit UI
Pastikan teks dan flow permission screen benar untuk API 29 (tidak ada `MANAGE_ALL_FILES` di API 29).

---

### Konfirmasi ARM64-only

`app/src/main/jniLibs/` sudah **hanya berisi `arm64-v8a/`**.
`build.gradle.kts` sudah:
```kotlin
ndk {
    abiFilters += listOf("arm64-v8a")  // ← sudah benar ✓
}
```

**Tidak ada perubahan ABI yang diperlukan.**

Keuntungan arm64-only:
- APK lebih kecil (tidak perlu lib 32-bit)
- Performa optimal di semua Android 10+ device (semua Android 10+ flagship & mid-range adalah arm64)
- MPV native libs sudah dikompilasi untuk arm64 saja

---

### Testing Matrix (Target v1.4.0)

| API Level | Android | Target | Priority |
|---|---|---|---|
| 29 | Android 10 | Minimum ← **baru** | Wajib test |
| 30 | Android 11 | — | Test storage & permission |
| 31–32 | Android 12–12L | — | Test Dynamic Color fallback |
| 33 | Android 13 | — | Test `READ_MEDIA_VIDEO` |
| 34–35 | Android 14–15 | Saat ini minimum | Sudah teruji |

---

### Risiko & Mitigasi

| Risiko | Kemungkinan | Mitigasi |
|---|---|---|
| Storage permission tidak jalan di API 29 | Medium | Audit `PermissionHelper` + test device API 29 |
| MPV crash di old kernel API 29 | Low | arm64-v8a libs sudah diuji untuk API 24+ |
| Compose performance di weak API 29 devices | Low | arm64-only sudah filter device lama yang biasanya weak |
| DownloadManager path issue API 29 | Low | Sudah pakai `DIRECTORY_DOWNLOADS` public (API 29+) |
| Cast SDK tidak jalan API 29 | Very Low | Google Cast 21.5.0 requires API 21+ ✓ |

---

### Urutan di Pass Implementasi

Tambahkan setelah Pass 1 (Bug Fix):

```
Pass 1b — API 29 + ARM64
  [P1b-A] libs.versions.toml: minSdk "31" → "29"
  [P1b-B] app/build.gradle.kts: minSdk 31 → 29
  [P1b-C] AndroidManifest.xml: tambah WRITE_EXTERNAL_STORAGE maxSdkVersion=28
  [P1b-D] PermissionHelper.kt: audit & fix requiredPermissions() untuk API 29-32
  [P1b-E] PermissionScreen.kt: audit teks & flow untuk API 29
  [P1b-F] Build & verify tidak ada @RequiresApi yang unguarded di minSdk 29 context
```

