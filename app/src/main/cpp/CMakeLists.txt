cmake_minimum_required(VERSION 3.22.1)
project("ryouass")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Detect whether prebuilt libass.so is present ──────────────────────────────
#
# Place prebuilt libass.so for each ABI in:
#   app/src/main/jniLibs/<abi>/libass.so
#
# Download prebuilt (arm64-v8a / armeabi-v7a / x86_64) from:
#   https://github.com/rmnscnce/libass-android/releases/latest
# or run:
#   ./scripts/download_libass.sh
#
# If libass.so is absent, a stub is compiled and AssJniRenderer.isAvailable
# returns false at runtime — the pure-Kotlin renderer is used as fallback.

set(LIBASS_PATH "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libass.so")

if(EXISTS "${LIBASS_PATH}")
    message(STATUS "[ryouass] libass found for ${ANDROID_ABI}")
    add_library(ass SHARED IMPORTED)
    set_target_properties(ass PROPERTIES IMPORTED_LOCATION "${LIBASS_PATH}")
    set(LIBASS_AVAILABLE TRUE)
else()
    message(WARNING "[ryouass] libass NOT found for ${ANDROID_ABI} — compiling stub")
    set(LIBASS_AVAILABLE FALSE)
endif()

# ── JNI bridge ────────────────────────────────────────────────────────────────
add_library(ryouass SHARED ass_jni.cpp)

target_include_directories(ryouass PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

if(LIBASS_AVAILABLE)
    target_compile_definitions(ryouass PRIVATE LIBASS_PRESENT=1)
    target_link_libraries(ryouass ass)
else()
    target_compile_definitions(ryouass PRIVATE LIBASS_PRESENT=0)
endif()

find_library(log-lib     log)
find_library(android-lib android)
find_library(jni-gfx     jnigraphics)

target_link_libraries(ryouass ${log-lib} ${android-lib} ${jni-gfx})
