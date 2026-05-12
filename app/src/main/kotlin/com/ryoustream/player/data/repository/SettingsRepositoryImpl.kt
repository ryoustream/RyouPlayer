package com.ryoustream.player.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ryoustream.player.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ryou_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val dataStore = context.dataStore

    // ─── Keys ─────────────────────────────────────────────────────────────────
    private object Keys {
        // Playback
        val HARDWARE_DECODING = booleanPreferencesKey("hardware_decoding")
        val SUBTITLE_ENABLED = booleanPreferencesKey("subtitle_enabled")
        val SUBTITLE_FONT_SIZE = intPreferencesKey("subtitle_font_size")
        val SUBTITLE_DELAY = longPreferencesKey("subtitle_delay")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val SCREEN_ORIENTATION_LOCKED = booleanPreferencesKey("screen_orientation_locked")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")

        // UI
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
        val USE_SYSTEM_COLOR = booleanPreferencesKey("use_system_color")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")

        // Advanced
        val NETWORK_BUFFER_SIZE = intPreferencesKey("network_buffer_size")
        val CACHE_SIZE = intPreferencesKey("cache_size")
        val CODEC_PREFERENCE = stringPreferencesKey("codec_preference")
        val DEBUG_INFO = booleanPreferencesKey("debug_info")
        // Display
        val IGNORE_NOTCH = booleanPreferencesKey("ignore_notch")
    }

    // ─── Safe read helper ──────────────────────────────────────────────────────
    private fun <T> DataStore<Preferences>.readSafely(
        key: Preferences.Key<T>,
        default: T
    ): Flow<T> = data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[key] ?: default }

    // ─── Flows ────────────────────────────────────────────────────────────────
    override val hardwareDecodingEnabled = dataStore.readSafely(Keys.HARDWARE_DECODING, true)
    override val subtitleEnabled = dataStore.readSafely(Keys.SUBTITLE_ENABLED, true)
    override val subtitleFontSize = dataStore.readSafely(Keys.SUBTITLE_FONT_SIZE, 16)
    override val subtitleDelay = dataStore.readSafely(Keys.SUBTITLE_DELAY, 0L)
    override val defaultPlaybackSpeed = dataStore.readSafely(Keys.DEFAULT_PLAYBACK_SPEED, 1.0f)
    override val rememberPosition = dataStore.readSafely(Keys.REMEMBER_POSITION, true)
    override val gestureSeekEnabled = dataStore.readSafely(Keys.GESTURE_SEEK, true)
    override val gestureBrightnessEnabled = dataStore.readSafely(Keys.GESTURE_BRIGHTNESS, true)
    override val gestureVolumeEnabled = dataStore.readSafely(Keys.GESTURE_VOLUME, true)
    override val doubleTapSeekSeconds = dataStore.readSafely(Keys.DOUBLE_TAP_SEEK_SECONDS, 10)
    override val pipEnabled = dataStore.readSafely(Keys.PIP_ENABLED, true)
    override val backgroundPlayEnabled = dataStore.readSafely(Keys.BACKGROUND_PLAY, false)
    override val screenOrientationLocked = dataStore.readSafely(Keys.SCREEN_ORIENTATION_LOCKED, false)
    override val defaultAspectRatio = dataStore.readSafely(Keys.DEFAULT_ASPECT_RATIO, "FIT")
    override val themeMode = dataStore.readSafely(Keys.THEME_MODE, "SYSTEM")
    override val amoledMode = dataStore.readSafely(Keys.AMOLED_MODE, false)
    override val useSystemColor = dataStore.readSafely(Keys.USE_SYSTEM_COLOR, true)
    override val gridColumns = dataStore.readSafely(Keys.GRID_COLUMNS, 2)
    override val viewMode = dataStore.readSafely(Keys.VIEW_MODE, "GRID")
    override val showVideoThumbnails = dataStore.readSafely(Keys.SHOW_THUMBNAILS, true)
    override val animationsEnabled = dataStore.readSafely(Keys.ANIMATIONS_ENABLED, true)
    override val networkBufferSize = dataStore.readSafely(Keys.NETWORK_BUFFER_SIZE, 32)
    override val cacheSize = dataStore.readSafely(Keys.CACHE_SIZE, 256)
    override val codecPreference = dataStore.readSafely(Keys.CODEC_PREFERENCE, "AUTO")
    override val debugInfoEnabled = dataStore.readSafely(Keys.DEBUG_INFO, false)
    override val ignoreNotch = dataStore.readSafely(Keys.IGNORE_NOTCH, false)

    // ─── Setters ──────────────────────────────────────────────────────────────
    override suspend fun setHardwareDecoding(enabled: Boolean) = dataStore.set(Keys.HARDWARE_DECODING, enabled)
    override suspend fun setSubtitleEnabled(enabled: Boolean) = dataStore.set(Keys.SUBTITLE_ENABLED, enabled)
    override suspend fun setSubtitleFontSize(size: Int) = dataStore.set(Keys.SUBTITLE_FONT_SIZE, size)
    override suspend fun setSubtitleDelay(delay: Long) = dataStore.set(Keys.SUBTITLE_DELAY, delay)
    override suspend fun setDefaultPlaybackSpeed(speed: Float) = dataStore.set(Keys.DEFAULT_PLAYBACK_SPEED, speed)
    override suspend fun setRememberPosition(enabled: Boolean) = dataStore.set(Keys.REMEMBER_POSITION, enabled)
    override suspend fun setGestureSeek(enabled: Boolean) = dataStore.set(Keys.GESTURE_SEEK, enabled)
    override suspend fun setGestureBrightness(enabled: Boolean) = dataStore.set(Keys.GESTURE_BRIGHTNESS, enabled)
    override suspend fun setGestureVolume(enabled: Boolean) = dataStore.set(Keys.GESTURE_VOLUME, enabled)
    override suspend fun setDoubleTapSeekSeconds(seconds: Int) = dataStore.set(Keys.DOUBLE_TAP_SEEK_SECONDS, seconds)
    override suspend fun setPipEnabled(enabled: Boolean) = dataStore.set(Keys.PIP_ENABLED, enabled)
    override suspend fun setBackgroundPlay(enabled: Boolean) = dataStore.set(Keys.BACKGROUND_PLAY, enabled)
    override suspend fun setThemeMode(mode: String) = dataStore.set(Keys.THEME_MODE, mode)
    override suspend fun setAmoledMode(enabled: Boolean) = dataStore.set(Keys.AMOLED_MODE, enabled)
    override suspend fun setUseSystemColor(enabled: Boolean) = dataStore.set(Keys.USE_SYSTEM_COLOR, enabled)
    override suspend fun setGridColumns(columns: Int) = dataStore.set(Keys.GRID_COLUMNS, columns)
    override suspend fun setViewMode(mode: String) = dataStore.set(Keys.VIEW_MODE, mode)
    override suspend fun setAnimationsEnabled(enabled: Boolean) = dataStore.set(Keys.ANIMATIONS_ENABLED, enabled)
    override suspend fun setNetworkBufferSize(size: Int) = dataStore.set(Keys.NETWORK_BUFFER_SIZE, size)
    override suspend fun setCacheSize(size: Int) = dataStore.set(Keys.CACHE_SIZE, size)
    override suspend fun setCodecPreference(codec: String) = dataStore.set(Keys.CODEC_PREFERENCE, codec)
    override suspend fun setDebugInfo(enabled: Boolean) = dataStore.set(Keys.DEBUG_INFO, enabled)
    override suspend fun setIgnoreNotch(enabled: Boolean) = dataStore.set(Keys.IGNORE_NOTCH, enabled)

    override suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    private suspend fun <T> DataStore<Preferences>.set(key: Preferences.Key<T>, value: T) {
        edit { prefs -> prefs[key] = value }
    }
}
