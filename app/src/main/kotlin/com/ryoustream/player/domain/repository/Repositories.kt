package com.ryoustream.player.domain.repository

import android.net.Uri
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.NetworkStream
import com.ryoustream.player.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for local media files.
 * Abstracts MediaStore access and local database operations.
 */
interface MediaRepository {
    /** Observe all video files from MediaStore */
    fun getAllVideos(sortOrder: MediaSortOrder = MediaSortOrder.DATE_ADDED_DESC): Flow<List<MediaItem>>

    /** Observe videos in a specific folder */
    fun getVideosByFolder(folderId: Long, sortOrder: MediaSortOrder = MediaSortOrder.NAME_ASC): Flow<List<MediaItem>>

    /** Observe all media folders */
    fun getAllFolders(): Flow<List<MediaFolder>>

    /** Observe recently played items */
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<MediaItem>>

    /** Observe favorite items */
    fun getFavorites(): Flow<List<MediaItem>>

    /** Search media by query */
    fun searchMedia(query: String): Flow<List<MediaItem>>

    /** Get single media item by ID */
    suspend fun getMediaById(id: Long): MediaItem?

    /** Get single media item by URI */
    suspend fun getMediaByUri(uri: Uri): MediaItem?

    /** Update playback position (resume support) */
    suspend fun updatePlaybackPosition(id: Long, position: Long, duration: Long)

    /** Update play count */
    suspend fun incrementPlayCount(id: Long)

    /** Toggle favorite status */
    suspend fun toggleFavorite(id: Long): Boolean

    /** Trigger media rescan */
    suspend fun rescanMedia()

    /** Get total media count */
    suspend fun getMediaCount(): Int
}

/**
 * Repository interface for playlists.
 */
interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(id: Long): Flow<Playlist?>
    suspend fun createPlaylist(name: String, description: String = ""): Long
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: Long)
    suspend fun addItemToPlaylist(playlistId: Long, mediaItem: MediaItem)
    suspend fun removeItemFromPlaylist(playlistId: Long, mediaItemId: Long)
    suspend fun reorderPlaylistItems(playlistId: Long, fromIndex: Int, toIndex: Int)
    suspend fun importM3u(uri: Uri): Playlist?
    suspend fun exportM3u(playlistId: Long): Uri?
}

/**
 * Repository interface for network streams.
 */
interface StreamRepository {
    fun getAllStreams(): Flow<List<NetworkStream>>
    fun getFavoriteStreams(): Flow<List<NetworkStream>>
    suspend fun addStream(stream: NetworkStream): Long
    suspend fun updateStream(stream: NetworkStream)
    suspend fun deleteStream(id: Long)
    suspend fun toggleFavorite(id: Long)
    suspend fun updateLastUsed(id: Long)
}

/**
 * Repository interface for app settings/preferences.
 */
interface SettingsRepository {
    // Playback
    val hardwareDecodingEnabled: Flow<Boolean>
    val subtitleEnabled: Flow<Boolean>
    val subtitleFontSize: Flow<Int>
    val subtitleDelay: Flow<Long>
    val defaultPlaybackSpeed: Flow<Float>
    val rememberPosition: Flow<Boolean>
    val gestureSeekEnabled: Flow<Boolean>
    val gestureBrightnessEnabled: Flow<Boolean>
    val gestureVolumeEnabled: Flow<Boolean>
    val doubleTapSeekSeconds: Flow<Int>
    val pipEnabled: Flow<Boolean>
    val backgroundPlayEnabled: Flow<Boolean>
    val screenOrientationLocked: Flow<Boolean>
    val defaultAspectRatio: Flow<String>

    // UI
    val themeMode: Flow<String>
    val amoledMode: Flow<Boolean>
    val useSystemColor: Flow<Boolean>
    val gridColumns: Flow<Int>
    val viewMode: Flow<String>
    val showVideoThumbnails: Flow<Boolean>
    val animationsEnabled: Flow<Boolean>

    // Display
    val ignoreNotch: Flow<Boolean>

    // Advanced
    val networkBufferSize: Flow<Int>
    val cacheSize: Flow<Int>
    val codecPreference: Flow<String>
    val showHiddenFiles: Flow<Boolean>

    suspend fun setHardwareDecoding(enabled: Boolean)
    suspend fun setSubtitleEnabled(enabled: Boolean)
    suspend fun setSubtitleFontSize(size: Int)
    suspend fun setSubtitleDelay(delay: Long)
    suspend fun setDefaultPlaybackSpeed(speed: Float)
    suspend fun setRememberPosition(enabled: Boolean)
    suspend fun setGestureSeek(enabled: Boolean)
    suspend fun setGestureBrightness(enabled: Boolean)
    suspend fun setGestureVolume(enabled: Boolean)
    suspend fun setDoubleTapSeekSeconds(seconds: Int)
    suspend fun setPipEnabled(enabled: Boolean)
    suspend fun setBackgroundPlay(enabled: Boolean)
    suspend fun setThemeMode(mode: String)
    suspend fun setAmoledMode(enabled: Boolean)
    suspend fun setUseSystemColor(enabled: Boolean)
    suspend fun setGridColumns(columns: Int)
    suspend fun setViewMode(mode: String)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setNetworkBufferSize(size: Int)
    suspend fun setCacheSize(size: Int)
    suspend fun setCodecPreference(codec: String)
    suspend fun setShowHiddenFiles(enabled: Boolean)
    suspend fun setIgnoreNotch(enabled: Boolean)
    suspend fun resetToDefaults()
}
