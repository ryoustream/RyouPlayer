#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# download_libass.sh
#
# Downloads prebuilt libass.so for Android from a GitHub release.
# Run once before building, or add to your CI pipeline.
#
# Usage:
#   chmod +x scripts/download_libass.sh
#   ./scripts/download_libass.sh
#
# If the download fails the build continues with the stub renderer.
# AssJniRenderer.isAvailable will return false until real libass.so is present.
#
# Source: https://github.com/rmnscnce/libass-android (prebuilt AAR/zip)
# Alternatively, build libass yourself from:
#   https://github.com/libass/libass  (NDK cross-compile required)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DEST="app/src/main/jniLibs"
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

# ── Pick release URL ──────────────────────────────────────────────────────────
# Attempt: rmnscnce/libass-android prebuilt zip (contains individual .so files)
# Update this URL to the latest release tag as needed.
BASE_URL="https://github.com/rmnscnce/libass-android/releases/latest/download"

echo "📦 Downloading prebuilt libass.so for Android..."

python3 -c "
import os, sys, urllib.request, zipfile, shutil, tempfile

base_url = '${BASE_URL}'
dest     = '${DEST}'
abis     = ['arm64-v8a', 'armeabi-v7a', 'x86_64']
filename = 'libass-android.zip'
url      = f'{base_url}/{filename}'

print(f'  Fetching {url}')
try:
    tmp = tempfile.mktemp(suffix='.zip')
    urllib.request.urlretrieve(url, tmp)
    with zipfile.ZipFile(tmp, 'r') as z:
        names = z.namelist()
        print(f'  ZIP contents: {names[:10]}')
        for abi in abis:
            # look for libass.so anywhere in the zip under this abi directory
            candidates = [n for n in names if abi in n and n.endswith('libass.so')]
            if not candidates:
                print(f'  ⚠️  libass.so not found for {abi} in zip')
                continue
            src_path = candidates[0]
            dst_dir  = os.path.join(dest, abi)
            os.makedirs(dst_dir, exist_ok=True)
            dst_path = os.path.join(dst_dir, 'libass.so')
            with z.open(src_path) as src, open(dst_path, 'wb') as dst:
                shutil.copyfileobj(src, dst)
            sz = os.path.getsize(dst_path)
            print(f'  ✅ {abi}/libass.so  ({sz:,} bytes)')
    os.unlink(tmp)
    print('Done.')
except Exception as e:
    print(f'  ❌ Download failed: {e}')
    print('  Build will continue with the Kotlin stub renderer.')
    sys.exit(0)  # non-fatal — CMake falls back to stub
"

echo ""
echo "libass.so locations (if present):"
for abi in "${ABIS[@]}"; do
    f="${DEST}/${abi}/libass.so"
    if [ -f "$f" ]; then
        size=$(stat -c%s "$f" 2>/dev/null || stat -f%z "$f")
        echo "  ✅  ${f}  (${size} bytes)"
    else
        echo "  ⬜  ${f}  — not present (stub renderer will be used)"
    fi
done
