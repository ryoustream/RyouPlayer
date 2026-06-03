# PLAN v1.3.1 (1.3.101)

Tiga pilar utama: Player UI YouTube-style, Settings revamp, Bug fixes.

---

## ARSITEKTUR

### Section 1 — Diagram Before vs After (Player Controls)

```
SEBELUM (v1.3.001)
┌─────────────────────────────────────────────┐
│ [←] Title               [ratio][spd][rot][🔒]│  ← TopBar (1 row)
│                                             │
│        [⏮] [↩10] [▶/⏸] [10↪] [⏭]          │  ← Center row
│                                             │
│  00:00          ━━━━━━━━━━━━━━━━     45:00  │  ← Slider
│  [🔁][⏭][⏹]               [ℹ][CC][🎵]      │  ← BottomRow
└─────────────────────────────────────────────┘
  Panel: Dialog floating bottom-end
  - SubtitlePanel: Dialog + Box(BottomEnd)
  - AudioPanel:    Dialog + Box(BottomEnd)
  - VideoInfo:     Dialog + Box(BottomEnd)

SESUDAH (v1.3.1) — YouTube Premium / v21.20+ style
┌─────────────────────────────────────────────┐
│ [←] Title                       [⋮ more]   │  ← TopBar lebih minimalis
│                                             │
│ ▒▒▒▒▒▒▒▒▒▒▒ VIDEO ▒▒▒▒▒▒▒▒▒▒▒▒▒▒           │
│                                             │
│   [⏮]  [↩10]  [▶/⏸]  [10↪]  [⏭]           │  ← Center (ukuran lebih besar)
│                                             │
│  ━━━╸━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━      │  ← Seeker tebal M3 style
│  00:14                            45:00    │  ← Timestamp di bawah seeker
│                                             │
│  [🔒] [🔁] [auto]  ──────── [spd] [CC] [🎵] │  ← Bottom action row baru
│  [ratio]                     [rot] [ℹ] [⋮] │    (2 row opsi, compact)
└─────────────────────────────────────────────┘
  Panel: ModalBottomSheet M3 (bukan Dialog)
  - SubtitlePanel → ModalBottomSheet
  - AudioPanel    → ModalBottomSheet  
  - VideoInfo     → ModalBottomSheet
  - QueuePanel    → ModalBottomSheet BARU (daftar file folder)
```

### Section 2 — Peta File

```
DIMODIFIKASI
  PlayerScreen.kt          ← revamp total controls UI
  PlayerViewModel.kt       ← tambah showQueuePanel, queue list state
  SubtitleStyleSheet.kt    ← minor: wrap ke ModalBottomSheet

TIDAK DISENTUH
  PlayerActivity.kt        ← immersive/window flags sudah oke
  SettingsScreen.kt → direvamp terpisah (Section 12–14)
```

---

## PLAYER UI (Section 3–11)

### Section 3 — TopBar Baru

Lebih minimalis dari sebelumnya. Tiga elemen saja:

```
[← ArrowBack]  [Title — maxLines 1, ellipsis]  [MoreVert]
```

`MoreVert` membuka `DropdownMenu` berisi:
- Aspect Ratio (cycle / submenu)
- Orientation (submenu)
- Lock Controls
- Video Info

Hapus dari TopBar: TextButton ratio, TextButton speed, IconButton orientation, IconButton lock.
Semua pindah ke BottomActionRows atau MoreVert menu.

### Section 4 — Center Transport

Ukuran tombol diperbesar, spacing lebih longgar. Tambah efek ripple transparan:

| Tombol | Ukuran icon | Modifier |
|--------|------------|---------|
| SkipPrev | 32 dp | size(48.dp), alpha 0.5 jika disabled |
| Replay10 | 42 dp | size(56.dp) |
| Play/Pause | 48 dp | size(72.dp), FAB bulat putih transparan |
| Forward10 | 42 dp | size(56.dp) |
| SkipNext | 32 dp | size(48.dp), alpha 0.5 jika disabled |

Chapter markers: jika `chapterMarks.isNotEmpty()`, tampilkan titik-titik kecil di atas seeker (overlay).

### Section 5 — Seeker + Timestamp YouTube Style

```
Sebelum:
  [00:14]  ━━━━━━━━━━━━━━━━━━━━━━━━  [45:00]
  (timestamp kiri & kanan, Slider di tengah)

Sesudah:
            ━━━━━╸━━━━━━━━━━━━━━━━━━      ← Slider tebal (trackHeight 4dp, thumb 16dp)
  00:14                        -44:46     ← timestamp kiri = current, kanan = sisa (minus)
```

Implementasi:
- `Slider` dengan custom `SliderDefaults.Thumb` bulat 16dp putih
- `trackHeight` 4dp via `SliderDefaults.Track`
- timestamp kanan: tampilkan `-{sisa}` bukan `{total}` (YouTube style)
- `chapter tick marks`: Box overlay di atas track, setiap chapter = vertical line 6dp putih

### Section 6 — Bottom Action Rows (2 baris)

Ganti 1 baris campuran menjadi 2 baris yang rapi:

**Baris 1 — kiri: transport options, kanan: track options**
```
[🔒 Lock] [🔁 Repeat] [⏭ AutoNext]    ──    [CC Subtitle] [🎵 Audio] [ℹ Info]
```

**Baris 2 — kiri: playback options, kanan: display options**
```
[1.0× Speed]  [Queue]                  ──    [Ratio]  [🔄 Orientation]
```

- Speed: `TextButton` "1.0×", tap buka DropdownMenu speeds
- Queue: `IconButton` buka QueuePanel (ModalBottomSheet baru)
- Ratio: `TextButton` dengan label singkat (FIT / FILL / CROP / 4:3 / 16:9 / 21:9)
- Orientation: icon saja

### Section 7 — Gradient Overlay Update

```
Sebelum: top 110dp + bottom 180dp

Sesudah:
  Top:    120dp, dari Black.copy(0.85f) → Transparent (lebih gelap, judul lebih terbaca)
  Bottom: 220dp, dari Transparent → Black.copy(0.92f) (lebih tinggi, cover 2 baris action)
```

### Section 8 — Panel: SubtitlePanel → ModalBottomSheet

Ganti `Dialog + Box(BottomEnd)` menjadi `ModalBottomSheet` M3:

```kotlin
// Sebelum
Dialog(usePlatformDefaultWidth = false) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Surface(Modifier.width(280.dp).padding(bottom = 72.dp, end = 8.dp)) { ... }
    }
}

// Sesudah
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    // Full-width sheet, max height 70% layar
    Column(Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.70f)) { ... }
}
```

Konten tetap sama (delay strip, track list, load file). Layout menjadi full-width.

### Section 9 — Panel: AudioPanel → ModalBottomSheet

Sama seperti Section 8. Full-width sheet. Delay strip di atas, track list di bawah.

### Section 10 — Panel: VideoInfo → ModalBottomSheet

Sama. Tabel key-value di dalam `LazyColumn` (bukan `verticalScroll`).

### Section 11 — Panel: QueuePanel (BARU)

`ModalBottomSheet` berisi daftar file dalam folder yang sedang diputar:

State baru di `PlayerUiState`:
```kotlin
val showQueuePanel: Boolean = false
val queueItems: List<Uri>   = emptyList()   // = _folderFiles
val currentQueueIndex: Int  = 0             // = _folderIndex
```

UI:
```
┌── Queue (5 file) ──────────────────────┐
│  1  nama_file_1.mkv          34:12     │
│ ▶2  nama_file_2.mkv  [playing]  45:00  │  ← highlighted, auto-scroll
│  3  nama_file_3.mkv          12:34     │
└────────────────────────────────────────┘
```

- `LazyColumn`, `key = { it.toString() }`
- Item aktif: background `primary.copy(0.12f)`, icon `PlayArrow` kecil
- Tap item: `viewModel.jumpToQueue(index)` → panggil `playAtIndex(idx)` (expose ke public)
- ViewModel: `fun showQueuePanel()`, `fun hideQueuePanel()`, `fun jumpToQueue(idx: Int)`

---

## SETTINGS REVAMP (Section 12–14)

### Section 12 — Audit SettingsScreen Saat Ini

Yang sudah bagus (pertahankan):
- `SwitchSetting`, `ClickableSetting`, `SectionHeader` composables — sudah M3
- Update check dialog — sudah bagus
- LazyColumn structure

Yang perlu diupdate/diperbaiki:
- Semua label masih **full English** — ganti ke **Indonesia** (konsisten dengan LibraryScreen)
- Section "About" di Settings → **hapus**, sudah ada AboutScreen terpisah; ganti dengan link ke AboutScreen
- Speed default setting: belum ada UI untuk mengubah default speed (ada di repo tapi tidak ditampilkan)
- Double-tap seconds: belum ada UI (ada di repo: `doubleTapSeekSeconds`)
- Subtitle font size: ada di state tapi tidak ditampilkan
- Codec preference: ada di state tapi tidak ditampilkan

### Section 13 — Settings UI Baru: Grup & Konten

Struktur baru (urutan dan label):

```
── PEMUTARAN ──────────────────────────────
  ⚙  Hardware Decoding      [switch]  ← "Use GPU to decode video"
  ▶  Ingat Posisi           [switch]  ← "Lanjutkan dari posisi terakhir"
  ⏩  Kecepatan Default       [value row → dialog]  ← 0.25–3.0×
  ⏪  Detik Double-tap        [value row → dialog]  ← 5 / 10 / 15 / 20 / 30 dtk
  🎵  Background Playback    [switch]
  📺  Picture-in-Picture     [switch]

── SUBTITLE ────────────────────────────────
  CC  Tampilkan Subtitle     [switch]
  Aa  Ukuran Font Subtitle   [value row → slider dialog]  ← 10–36sp

── GERAKAN ─────────────────────────────────
  👆  Seek (geser horizontal) [switch]
  ☀  Kecerahan (geser kiri)  [switch]
  🔊  Volume (geser kanan)    [switch]

── TAMPILAN ────────────────────────────────
  🌙  Tema                   [value row → dialog: Sistem / Terang / Gelap]
  ⚫  AMOLED / Pure Black    [switch, disabled jika tema Terang]
  🎨  Dynamic Color           [switch, Android 12+]
  ✨  Animasi UI              [switch]
  📱  Abaikan Notch           [switch]

── FILE & MEDIA ────────────────────────────
  📁  Tampilkan File Tersembunyi  [switch]
  🚫  Abaikan .nomedia           [switch]

── LANJUTAN ─────────────────────────────────
  🎞  Preferensi Codec        [value row → dialog: Auto / Software / Hardware]
  🔄  Reset ke Default        [clickable, merah]

── TENTANG ──────────────────────────────────
  ℹ  Tentang Aplikasi        [clickable → AboutScreen]
  🔄  Periksa Pembaruan       [clickable + inline result]
```

### Section 14 — Value Row Component Baru

Komponen baru untuk setting yang punya pilihan/nilai (bukan switch):

```kotlin
@Composable
fun ValueSetting(
    icon: ImageVector,
    title: String,
    value: String,           // teks nilai saat ini
    subtitle: String = "",
    onClick: () -> Unit,
    enabled: Boolean = true,
)
```

Tampilan: mirip `ClickableSetting` tapi trailing = `Text(value)` + `ChevronRight`.

Dialog pemilih kecepatan default:
```
● 0.25×
● 0.5×
● 0.75×
● 1.0×  ← selected
● 1.25×
...
```

Dialog double-tap seconds: pilihan radio 5 / 10 / 15 / 20 / 30.

Dialog codec: Auto / Software / Hardware.

---

## BUG FIXES (Section 15)

### Section 15 — Daftar Bug yang Diperbaiki

| # | Bug | File | Fix |
|---|-----|------|-----|
| B1 | `OrientationMode.values()` — deprecated di Kotlin 1.9+ | PlayerScreen.kt | Ganti ke `OrientationMode.entries` |
| B2 | `AspectRatioMode.values()` — deprecated | PlayerViewModel.kt | Ganti ke `AspectRatioMode.entries` |
| B3 | Subtitle delay reset hanya bersih di UI, tidak di mpv | PlayerViewModel.kt | Pastikan `setSubtitleDelay(0L)` kirim `MPVLib.setPropertyLong("sub-delay", 0)` |
| B4 | `QueuePanel` tidak auto-scroll ke item aktif | PlayerScreen.kt (QueuePanel baru) | `LazyListState.animateScrollToItem(currentQueueIndex)` di `LaunchedEffect` |
| B5 | `SettingsScreen` — section About duplikat dengan AboutScreen | SettingsScreen.kt | Hapus ListItem versi, ganti dengan link ke AboutScreen via callback |
| B6 | `SubtitleStyleSheet` masih pakai `Dialog` bukan `ModalBottomSheet` | SubtitleStyleSheet.kt | Wrap ke `ModalBottomSheet` |
| B7 | Double-tap seek di edge kiri/kanan tidak konsisten dengan zone check | PlayerScreen.kt | Perbaiki threshold: gunakan `size.width * 0.20f` (20%) bukan fixed `56dp` |
| B8 | Timestamp kanan menampilkan total durasi (MX style), bukan sisa (YouTube style) | PlayerScreen.kt | Tampilkan `-{formatDuration(duration - currentPosition)}` |
| B9 | Speed TextButton di TopBar tidak ada hint visual "ini bisa diklik" | PlayerScreen.kt | Pindah ke BottomActionRow sebagai `TextButton` dengan border tipis |
| B10 | `hasPrev/hasNext` tidak update setelah jump ke queue item | PlayerViewModel.kt | Update state di `jumpToQueue()` |

---

## EKSEKUSI (Section 16)

### Section 16 — Urutan Langkah

```
Step 1  PlayerViewModel.kt
        - Tambah showQueuePanel, queueItems, currentQueueIndex ke PlayerUiState
        - Tambah fun showQueuePanel(), hideQueuePanel(), jumpToQueue(idx)
        - Expose _folderFiles sebagai queueItems di state
        - Fix B3, B10

Step 2  PlayerScreen.kt
        - TopBar baru (minimalis + MoreVert menu)
        - Center transport (ukuran lebih besar)
        - Seeker YouTube style (timestamp sisa, chapter marks)
        - BottomActionRows (2 baris)
        - Gradient overlay update
        - SubtitlePanel → ModalBottomSheet
        - AudioPanel → ModalBottomSheet
        - VideoInfoSheet → ModalBottomSheet
        - QueuePanel (ModalBottomSheet baru)
        - Fix B1, B7, B8, B9

Step 3  SubtitleStyleSheet.kt
        - Wrap ke ModalBottomSheet
        - Fix B6

Step 4  SettingsScreen.kt
        - Semua label → Bahasa Indonesia
        - Tambah ValueSetting component
        - Tambah UI: kecepatan default, double-tap seconds, subtitle font size, codec
        - Section About → link ke AboutScreen (tambah onAboutClick callback)
        - Fix B5

Step 5  Navigation.kt
        - Update SettingsScreen composable: tambah onAboutClick lambda
          yang navigate ke Screen.About.route

Step 6  build.gradle.kts
        - Bump versionMajor = 3, versionNameMinor = 2 (→ v1.3.002 … wait)

        KOREKSI versi:
        v1.3.001 = minor 1
        v1.3.101 = format berbeda — lihat build.gradle
        META=1, MAJOR=3, MINOR=101 tidak fit format 3-digit
        → Gunakan versionMajor=3, versionNameMinor=101
          tapi format "1.3.101" ← perlu update padStart ke 3 digit,
          101 sudah 3 digit, jadi "1.3.101" ✓
        → Set versionNameMinor = 101

Step 7  Commit + Push
```

### Section 17 — Checklist Testing

| # | Test | Expected |
|---|------|---------|
| T1 | Buka player, controls muncul | TopBar minimalis + 2 baris bottom |
| T2 | Tap MoreVert | Menu: Aspect Ratio, Orientation, Lock, Video Info |
| T3 | Tap Video Info di menu | ModalBottomSheet muncul dari bawah |
| T4 | Swipe down / tap outside sheet | Sheet dismiss |
| T5 | Tap CC icon | SubtitlePanel ModalBottomSheet muncul |
| T6 | Tap Audio icon | AudioPanel ModalBottomSheet muncul |
| T7 | Tap Queue icon | QueuePanel muncul, item aktif highlighted |
| T8 | Tap item lain di QueuePanel | Video berganti, panel dismiss |
| T9 | Timestamp kanan = sisa waktu negatif | "-44:46" bukan "45:00" |
| T10 | Video dengan chapter marks | Titik kecil di atas seeker |
| T11 | Double-tap di zona 20% edge | Tidak seek |
| T12 | Double-tap di zona tengah kiri | Seek -10s |
| T13 | Double-tap di zona tengah kanan | Seek +10s |
| T14 | Settings terbuka | Label Bahasa Indonesia |
| T15 | Settings → Kecepatan Default | Dialog radio, pilihan tersimpan |
| T16 | Settings → Double-tap Seconds | Dialog radio |
| T17 | Settings → Tentang Aplikasi | Navigate ke AboutScreen |
| T18 | Settings → Periksa Pembaruan | Dialog update seperti sebelumnya |
| T19 | Lock mode | Hanya unlock button muncul, gesture nonaktif |
| T20 | Repeat button | Ikon berubah NONE → ONE → ALL, warna primary jika aktif |

### Section 18 — Ringkasan File

```
DIMODIFIKASI (5 file)
  PlayerScreen.kt        ← revamp total
  PlayerViewModel.kt     ← state baru queue + bug fixes
  SubtitleStyleSheet.kt  ← Dialog → ModalBottomSheet
  SettingsScreen.kt      ← Indonesian labels + ValueSetting + onAboutClick
  Navigation.kt          ← tambah onAboutClick di SettingsScreen composable

TIDAK DISENTUH
  PlayerActivity.kt, HomeScreen.kt, LibraryScreen.kt, dll.
```

### Section 19 — Versi

`v1.3.001` → **`v1.3.101`**

```kotlin
// build.gradle.kts
val versionMajor = 3
val versionNameMinor: Int = 101
// → calculatedVersionName = "1.3.101"
```

---

*Plan v1.3.1 — selesai. 19 section. Siap eksekusi.*
