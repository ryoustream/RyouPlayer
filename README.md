# 🎬 Ryou Player

<p align="center">
  <img src="app/src/main/res/drawable/ic_splash_logo.xml" width="120" alt="Ryou Player Logo"/>
</p>

<p align="center">
  <strong>Modern Premium Video Player for Android</strong><br/>
  Material You · ExoPlayer · Clean Architecture · Kotlin Compose
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-12%2B-brightgreen?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-blueviolet?logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-2024.12-blue?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/ExoPlayer-Media3_1.5-orange" />
  <img src="https://img.shields.io/github/actions/workflow/status/ryoustream/RyouPlayer/build.yml?label=CI%2FCD&logo=github" />
</p>

---

## ✨ Features

### 🎥 Playback Engine
- **Media3 ExoPlayer** — Google's latest media framework
- Hardware + Software decoder fallback
- HDR / HDR10 / HLG / Dolby Vision detection
- Audio passthrough (DTS, Dolby)
- Auto Frame Rate (AFR)

### 📁 Format Support
| Video | Audio | Subtitle |
|-------|-------|----------|
| MP4, MKV, AVI, MOV | AAC, MP3, FLAC, WAV | SRT, ASS, SSA |
| FLV, WEBM, TS, M2TS | DTS, Dolby, Opus | VTT, TTML, SUB |
| MPEG, OGG, 3GP | Vorbis, PCM | Embedded + External |

### 🌐 Network Streaming
- HLS (m3u8), MPEG-DASH, RTSP, RTP
- HTTP/HTTPS, SMB, FTP, SFTP
- Stream history + reconnect

### 🎮 Player Controls
- Gesture seek / brightness / volume
- Double-tap seek ±10s
- Pinch to zoom
- Aspect ratio toggle (Fit/Fill/Crop/16:9/4:3)
- Playback speed (0.25× – 3×)
- A-B repeat, frame stepping
- Sleep timer, Screen lock
- **Picture-in-Picture** support
- Background playback

### 🎨 UI / UX
- **Material You 3** — Dynamic Color (Android 12+)
- Dark / Light / AMOLED mode
- Smooth animations
- Edge-to-edge display
- Grid & List view toggle
- Resume playback (continue watching)

### 📚 Library
- Videos, Folders, Recent, Favorites
- Playlist create/import/export (M3U)
- Search + Sort + Filter
- Chromecast support

---

## 🏗️ Architecture

```
app/
├── data/
│   ├── local/          # Room DB + MediaStore
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Data models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic use cases
├── presentation/
│   ├── home/           # Home screen + ViewModel
│   ├── player/         # Player screen + ViewModel
│   ├── library/        # Library screen + ViewModel
│   ├── settings/       # Settings screen + ViewModel
│   ├── components/     # Reusable UI components
│   ├── navigation/     # Compose Navigation
│   └── theme/          # Material You 3 theme
├── service/
│   ├── RyouPlaybackService.kt   # Media3 SessionService
│   └── MediaScannerService.kt  # Background scanner
├── di/                 # Hilt DI modules
└── util/               # Helpers
```

**Stack:** MVVM + Clean Architecture + Repository Pattern
**DI:** Hilt
**Async:** Kotlin Coroutines + Flow + StateFlow
**Media:** Media3 ExoPlayer 1.5
**DB:** Room
**Prefs:** DataStore

---

## 🚀 Build with GitHub Actions

APK is automatically built on every push. No Android Studio needed!

### Automatic Builds
| Event | Result |
|-------|--------|
| Push to `main` | Debug + Release APK + Draft GitHub Release |
| Push to `develop` | Debug + Release APK artifact |
| Pull Request | Debug APK |
| Manual trigger | Choose debug / release / both |

### Download APK
1. Go to **Actions** tab → select latest workflow run
2. Under **Artifacts** → download `RyouPlayer-debug-*` or `RyouPlayer-release-*`

### Version Format
```
v1.0.20260508-build42
  │  │         └── GitHub Actions run number
  │  └────────── build date (YYYYMMDD)
  └─────────────── semantic version
```

---

## 🔑 APK Signing Setup

### For local builds
```bash
# 1. Generate keystore
keytool -genkey -v \
  -keystore app/keystore/release.keystore \
  -alias ryoustream \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. Copy template
cp signing.properties.template signing.properties

# 3. Fill in your passwords in signing.properties
# 4. Build
./gradlew assembleRelease
```

### For GitHub Actions (CI/CD)
Add these **GitHub Secrets** (Settings → Secrets → Actions):

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | `base64 -i release.keystore` output |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (`ryoustream`) |
| `KEY_PASSWORD` | Key password |

---

## 🛠️ Local Setup (Optional)

> You can build entirely via GitHub Actions without local tools.
> For local development:

```bash
# Requirements: JDK 17+
git clone https://github.com/ryoustream/RyouPlayer
cd RyouPlayer

# Debug build
./gradlew assembleDebug

# Release build (requires signing.properties)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

---

## 📱 Requirements

- **Android 12** (API 31) minimum
- **Android 15** (API 35) target
- **Android 16** (API 36) preview ready
- ARM64-v8a / x86_64

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'feat: add amazing feature'`
4. Push: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## 📄 License

```
Copyright 2026 Ryou Stream

Licensed under the Apache License, Version 2.0
```

---

<p align="center">Built with ❤️ by <a href="https://github.com/ryoustream">ryoustream</a></p>
