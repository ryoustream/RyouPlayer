import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

// ─── Version Helpers ──────────────────────────────────────────────────────────
val buildDate: String = SimpleDateFormat("yyyyMMdd").format(Date())
val buildTime: String = SimpleDateFormat("HHmm").format(Date())
val META_VERSION = 1   // always 1 — identifies RyouPlayer
val versionMajor = 4   // major release (maps to "4" in v1.4.x)

// BUILD_NUMBER from CI (GitHub Actions run_number).
// This is the "patch / build" counter used in versionCode.
val buildNumber: Int = (System.getenv("BUILD_NUMBER") ?: "73").toIntOrNull() ?: 73
val commitHash: String = (System.getenv("COMMIT_HASH") ?: "local").take(7)

// versionNameMinor — bump manually on every feature/fix release.
// Rule: reset to 1 on versionMajor bump, then increment by 1 per release.
// History (v1.4.x):
//   001 = Home UI revamp (YouTube-style TopBar, InProgressCard, folder avatar),
//         Player pill controls (ControlPill groups, liquid-glass style),
//         Settings navigable sub-pages, media name fix, minSdk → API 29
//   002 = Player controls revamp: hapus pill kiri/kanan, semua opsi
//         dikelompokkan di PlayerSettingsSheet (gear icon). Fix header
//         tab beranda/folder/pustaka, hapus tab Anda + avatar duplikat.
val versionNameMinor: Int = 2

// versionCode format: META×1_000_000 + MAJOR×10_000 + BUILD
// Encoding: 01_02_0066 → 1*1_000_000 + 2*10_000 + 66 = 1_020_066
// Guarantees: v1.2.x always > v1.1.x (1_020_xxx > 1_010_999) ✓
// CRITICAL: versionCode must always INCREASE between releases.
//           Never reuse a BUILD_NUMBER; the keystore must stay the same
//           keystore for all releases (changing keystore = users must uninstall).
val calculatedVersionCode: Int =
    META_VERSION * 1_000_000 + versionMajor * 10_000 + buildNumber

// versionName: {META}.{MAJOR}.{MINOR:03d}
// Example: 1.2.004
val calculatedVersionName: String =
    "$META_VERSION.$versionMajor.${versionNameMinor.toString().padStart(3, '0')}"

// ─── Signing Config ───────────────────────────────────────────────────────────
val signingPropertiesFile = rootProject.file("signing.properties")
val keystorePropertiesFile = rootProject.file("keystore.properties")

fun loadSigningProps(): Properties? {
    val file = when {
        signingPropertiesFile.exists() -> signingPropertiesFile
        keystorePropertiesFile.exists() -> keystorePropertiesFile
        else -> null
    } ?: return null
    return Properties().also { it.load(FileInputStream(file)) }
}

val signingProps = loadSigningProps()

android {
    namespace = "com.ryoustream.player"
    compileSdk = 35

    // Pin NDK version so llvm-strip toolchain stays consistent across machines and CI.
    // r27c (27.2.12479018) is the default bundled with AGP 8.7.x and the version used
    // to build mpv-android / libass pre-built .so files we depend on.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.ryoustream.player"
        minSdk = 29
        targetSdk = 35
        versionCode = calculatedVersionCode
        versionName = calculatedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Inject build info into BuildConfig
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("String", "COMMIT_HASH", "\"$commitHash\"")
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        buildConfigField("String", "VERSION_FULL", "\"$calculatedVersionName\"")
        buildConfigField("String", "APPLICATION_ID", "\"com.ryoustream.player\"")

        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }

        // Limit to modern 64-bit ARM only: armv8 (arm64-v8a) covers both ARMv8 and ARMv9 cores
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

    }

    // ─── Signing ──────────────────────────────────────────────────────────────
    signingConfigs {
        create("release") {
            if (signingProps != null) {
                // From signing.properties file (CI or local)
                storeFile = file(signingProps.getProperty("storeFile", "keystore/release.keystore"))
                storePassword = signingProps.getProperty("storePassword", "")
                keyAlias = signingProps.getProperty("keyAlias", "ryoualias123")
                // PKCS12: keyPassword must equal storePassword
                keyPassword = signingProps.getProperty("storePassword", "")
                // storeType: auto-detected by workflow (JKS or PKCS12)
                val ksType = signingProps.getProperty("storeType", "")
                if (ksType.isNotEmpty()) storeType = ksType
            } else {
                // From environment variables (GitHub Actions)
                val keystoreB64 = System.getenv("KEYSTORE_BASE64")
                if (!keystoreB64.isNullOrEmpty()) {
                    val keystoreBytes = Base64.getDecoder().decode(keystoreB64)
                    val keystoreFile = file("${layout.buildDirectory.get()}/keystore/release.keystore")
                    keystoreFile.parentFile.mkdirs()
                    keystoreFile.writeBytes(keystoreBytes)
                    storeFile = keystoreFile
                }
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "ryoualias123"
                // PKCS12: keyPassword must equal storePassword
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("Boolean", "IS_DEBUG_BUILD", "true")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "IS_DEBUG_BUILD", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                // Suppress Media3 UnstableApi opt-in requirement globally — we acknowledge usage
                "-opt-in=androidx.media3.common.util.UnstableApi",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
        jniLibs {
            // ── keepDebugSymbols explanation ──────────────────────────────────
            // AGP's strip step runs llvm-strip from the pinned NDK to remove debug
            // symbols from .so files before packaging.  When a .so was built with a
            // different toolchain (or is already fully stripped), llvm-strip emits:
            //   "Unable to strip … packaging them as they are"
            //
            // Adding a pattern here tells AGP: "skip strip for this lib, include as-is."
            // The library IS in the APK either way — this just silences the warning
            // and documents intent.
            //
            // libass.so / libasskt.so  — pre-built by peerless2012/libass-android AAR;
            //   compiled with a pinned older NDK toolchain, already stripped.
            // libc++_shared.so         — NDK C++ shared runtime; pre-built by Google,
            //   ships without a strippable debug section.
            keepDebugSymbols += listOf(
                // mpv + ffmpeg pre-built libs from mpv-android — already stripped at source
                "**/libmpv.so",
                "**/libavcodec.so",
                "**/libavdevice.so",
                "**/libavfilter.so",
                "**/libavformat.so",
                "**/libavutil.so",
                "**/libswresample.so",
                "**/libswscale.so",
                // libass / libc++ pre-built
                "**/libass.so",
                "**/libasskt.so",
                "**/libc++_shared.so",
                // Jetpack pre-stripped
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
            )
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.splashscreen)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Media3 — only media-common + session kept for MediaSessionCompat in RyouPlaybackService.
    // ExoPlayer, HLS, DASH, RTSP, UI, datasource removed — mpv handles all playback.
    implementation(libs.media3.common)
    implementation(libs.media3.session)

    // NOTE: libass-kt / libass-media removed.
    // mpv renders subtitles (SRT, ASS, SSA, PGS, VTT) internally via its
    // bundled libass. No separate Android libass wrapper needed.

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil — image loading + video thumbnail extraction
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Accompanist — permissions helper only; systemuicontroller unused → removed
    implementation(libs.accompanist.permissions)

    // Cast
    implementation(libs.google.cast)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// ─── Version helper task ───────────────────────────────────────────────────────
// Used by CI to read the canonical version name so the APK filename, artifact
// name, and GitHub release tag all match what's shown inside the app.
//   Usage: ./gradlew -q :app:printVersionName
tasks.register("printVersionName") {
    group       = "versioning"
    description = "Print the canonical versionName to stdout (used by CI)."
    doLast { println(calculatedVersionName) }
}
