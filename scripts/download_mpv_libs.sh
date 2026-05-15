#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────────────────
# download_mpv_libs.sh
#
# Downloads libmpv.so + dependencies from the official mpv-android arm64 APK
# and places them in app/src/main/jniLibs/arm64-v8a/
#
# The JNI bridge expects:  System.loadLibrary("mpv")  →  libmpv.so
# All other .so (ffmpeg, ass, freetype …) are bundled as dependencies.
#
# Usage (run once before building):
#   chmod +x scripts/download_mpv_libs.sh
#   ./scripts/download_mpv_libs.sh
#
# Requires: curl, unzip
# ────────────────────────────────────────────────────────────────────────────

set -euo pipefail

MPV_RELEASE="2026-04-25"
APK_URL="https://github.com/mpv-android/mpv-android/releases/download/${MPV_RELEASE}/app-default-arm64-v8a-release.apk"
JNI_OUT="app/src/main/jniLibs/arm64-v8a"
TMP_DIR="$(mktemp -d)"

echo "▶  Downloading mpv-android ${MPV_RELEASE} (arm64-v8a)…"
curl -L --fail --progress-bar "${APK_URL}" -o "${TMP_DIR}/mpv.apk"

echo "▶  Extracting arm64-v8a native libs…"
mkdir -p "${JNI_OUT}"
unzip -jo "${TMP_DIR}/mpv.apk" "lib/arm64-v8a/*.so" -d "${JNI_OUT}"

echo "▶  Verifying libmpv.so…"
if [ ! -f "${JNI_OUT}/libmpv.so" ]; then
    echo "❌  libmpv.so not found after extraction!"
    echo "    Contents of ${JNI_OUT}:"
    ls "${JNI_OUT}/"
    exit 1
fi

echo "▶  Cleaning up…"
rm -rf "${TMP_DIR}"

echo ""
echo "✅  Done. Extracted .so files to ${JNI_OUT}:"
ls -lh "${JNI_OUT}/"*.so
echo ""
echo "Now rebuild: ./gradlew :app:assembleDebug"
