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
val versionMajor = 1
val versionMinor = 0
val versionPatch = 0

// Auto-increment from BUILD_NUMBER env (GitHub Actions) or fallback to 1
val buildNumber: Int = (System.getenv("BUILD_NUMBER") ?: "1").toIntOrNull() ?: 1
val commitHash: String = (System.getenv("COMMIT_HASH") ?: "local").take(7)

val calculatedVersionCode: Int = versionMajor * 10000 + versionMinor * 100 + buildNumber
val calculatedVersionName: String = "v$versionMajor.$versionMinor.$versionPatch.$buildDate-build$buildNumber"

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
        minSdk = 31
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
                // From signing.properties file (local build)
                storeFile = file(signingProps.getProperty("storeFile", "keystore/release.keystore"))
                storePassword = signingProps.getProperty("storePassword", "")
                keyAlias = signingProps.getProperty("keyAlias", "ryoustream")
                keyPassword = signingProps.getProperty("keyPassword", "")
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
                keyAlias = System.getenv("KEY_ALIAS") ?: "ryoustream"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
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
                "**/libmpv.so",          // prebuilt from mpv-android, already stripped
                "**/libass.so",
                "**/libasskt.so",
                "**/libc++_shared.so",
                // Existing pre-stripped Jetpack libs
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

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)

    // ASS/SSA subtitle rendering — libass via JNI (Maven Central)
    // https://github.com/peerless2012/libass-android
    implementation(libs.libass.kt)
    implementation(libs.libass.media)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Accompanist
    implementation(libs.accompanist.systemuicontroller)
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
