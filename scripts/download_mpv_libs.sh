#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# download_mpv_libs.sh
#
# Downloads libmpv.so (+ ffmpeg + libass built for Android) from the
# official mpv-android release APK and places it where the app expects it:
#   app/src/main/jniLibs/arm64-v8a/
#
# The APK ships the library as libmpv.so, but this app loads it under
# the name "player-lib" (System.loadLibrary("player-lib")), so we rename
# libmpv.so → libplayer-lib.so after extraction.
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

# The APK ships the JNI bridge as libmpv.so, but MPVLib.kt calls
# System.loadLibrary("player-lib") which resolves to libplayer-lib.so.
echo "▶  Renaming libmpv.so → libplayer-lib.so…"
if [ -f "${JNI_OUT}/libmpv.so" ]; then
    mv "${JNI_OUT}/libmpv.so" "${JNI_OUT}/libplayer-lib.so"
    echo "   ✓ renamed"
else
    echo "   ⚠️  libmpv.so not found — listing extracted files:"
    ls -lh "${JNI_OUT}"/*.so || true
    echo "   Check the APK contents and update this script accordingly."
    exit 1
fi

echo "▶  Cleaning up…"
rm -rf "${TMP_DIR}"

echo ""
echo "✅  Native libs ready in ${JNI_OUT}:"
ls -lh "${JNI_OUT}"/*.so
echo ""
echo "Now rebuild the project: ./gradlew :app:assembleDebug"
