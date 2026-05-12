#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# download_libass.sh
#
# ⚠️  This script is NO LONGER NEEDED.
#
# libass is now included as a Gradle/Maven Central dependency:
#   io.github.peerless2012:ass-kt:0.3.0
#   https://github.com/peerless2012/libass-android
#
# The native libass.so is bundled inside the AAR and unpacked automatically
# during the Gradle build. No manual .so downloading is required.
#
# This file is kept only for reference. It exits cleanly so CI steps that
# still invoke it don't fail.
# ─────────────────────────────────────────────────────────────────────────────
echo "ℹ️  libass is now a Gradle dependency (io.github.peerless2012:ass-kt)."
echo "   No manual download needed — Gradle handles it via Maven Central."
exit 0
