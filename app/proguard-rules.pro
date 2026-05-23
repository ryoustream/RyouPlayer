# ──────────────────────────────────────────────────────────────────────────────
# Ryou Player ProGuard Rules
# ──────────────────────────────────────────────────────────────────────────────

# ── General Android ──────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# ── Kotlin Serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ── Hilt / Dagger ────────────────────────────────────────────────────────────
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.internal.Factory
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ── Room Database ─────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.**

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowobfuscation,allowshrinking class <3>

# ── Coil ─────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Google Cast ───────────────────────────────────────────────────────────────
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ── Gson ─────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── App specific ─────────────────────────────────────────────────────────────
-keep class com.ryoustream.player.** { *; }

# ── mpv-android JNI bridge ───────────────────────────────────────────────────
# libplayer.so resolves these by exact name via GetStaticMethodID at runtime.
# R8 MUST NOT rename or remove any method in is.xyz.mpv.MPVLib.
#
# Critical callbacks (descriptor confirmed from libplayer.so binary):
#   eventProperty(String)             (Ljava/lang/String;)V
#   eventProperty(String, boolean)    (Ljava/lang/String;Z)V
#   eventProperty(String, long)       (Ljava/lang/String;J)V
#   eventProperty(String, double)     (Ljava/lang/String;D)V    ← was missing, caused crash
#   eventProperty(String, String)     (Ljava/lang/String;Ljava/lang/String;)V
#   event(int)                        (I)V
#   logMessage(String, int, String)   (Ljava/lang/String;ILjava/lang/String;)V ← was missing
#
# Native JNI functions (Java_is_xyz_mpv_MPVLib_*) are resolved by ELF symbol
# name when libplayer.so is loaded — keeping the class name is sufficient.
-keep class is.xyz.mpv.MPVLib {
    *;
}
-keep class is.xyz.mpv.MPVLib$* {
    *;
}
# MPVView is a SurfaceView wired to the JNI surface lifecycle
-keep class is.xyz.mpv.MPVView { *; }
