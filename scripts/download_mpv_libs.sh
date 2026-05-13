#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# download_mpv_libs.sh
#
# Downloads libplayer-lib.so (libmpv + ffmpeg + libass built for Android)
# from the official mpv-android release APK and places it where the app
# expects it: app/src/main/jniLibs/arm64-v8a/
#
# Run once before building:
#   chmod +x scripts/download_mpv_libs.sh
#   ./scripts/download_mpv_libs.sh
#
# Requires: curl, unzip (standard on macOS / Linux / WSL)
# ────────────────────────────────────────────────────────────────────────────

set -euo pipefail

MPV_RELEASE="2026-04-25"
APK_URL="https://github.com/mpv-android/mpv-android/releases/download/${MPV_RELEASE}/app-default-arm64-v8a-release.apk"
JNI_OUT="app/src/main/jniLibs/arm64-v8a"
TMP_DIR="$(mktemp -d)"

echo "▶  Downloading mpv-android ${MPV_RELEASE} (arm64-v8a)…"
curl -L --fail --progress-bar "${APK_URL}" -o "${TMP_DIR}/mpv.apk"

echo "▶  Extracting native libs…"
mkdir -p "${JNI_OUT}"
unzip -jo "${TMP_DIR}/mpv.apk" "lib/arm64-v8a/*.so" -d "${JNI_OUT}"

echo "▶  Cleaning up…"
rm -rf "${TMP_DIR}"

echo ""
echo "✅  Extracted .so files to ${JNI_OUT}:"
ls -lh "${JNI_OUT}"/*.so
echo ""
echo "Now rebuild the project: ./gradlew :app:assembleDebug"
