package com.ryoustream.player.data.repository

import android.content.Context
import android.net.Uri
import com.ryoustream.player.data.local.MediaPlaybackStateDao
import com.ryoustream.player.data.local.MediaPlaybackStateEntity
import com.ryoustream.player.data.local.MediaStoreDataSource
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.NetworkStream
import com.ryoustream.player.domain.model.Playlist
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.repository.SettingsRepository
import com.ryoustream.player.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaPlaybackStateDao: MediaPlaybackStateDao,
    private val settingsRepository: SettingsRepository,
) : MediaRepository {

    // ── Settings helpers ──────────────────────────────────────────────────────

    private suspend fun ignoreNomedia(): Boolean =
        runCatching { settingsRepository.ignoreNomedia.first() }.getOrDefault(true)

    private suspend fun showHiddenFiles(): Boolean =
        runCatching { settingsRepository.showHiddenFiles.first() }.getOrDefault(false)

    // ── Filesystem-scan helpers ───────────────────────────────────────────────

    /**
     * Returns extra MediaItems from the filesystem (hidden dirs + .nomedia folders)
     * when MANAGE_EXTERNAL_STORAGE is granted and the relevant setting is enabled.
     * Returns an empty list otherwise (fast path — no I/O).
     */
    private suspend fun extraFsItems(
        ignoreNomedia: Boolean,
        showHiddenFiles: Boolean,
    ): List<MediaItem> {
        if (!PermissionHelper.hasAllFilesAccess()) return emptyList()
        if (!ignoreNomedia && !showHiddenFiles) return emptyList()
        return mediaStoreDataSource.scanFilesystemExtra(
            showHiddenFiles = showHiddenFiles,
            ignoreNomedia   = ignoreNomedia,
        )
    }

    /** Merge MediaStore + filesystem items, deduplicate by path, apply sort. */
    private fun mergeAndSort(
        mediaStoreItems: List<MediaItem>,
        fsItems: List<MediaItem>,
        sortOrder: MediaSortOrder,
    ): List<MediaItem> {
        if (fsItems.isEmpty()) return mediaStoreItems
        val existingPaths = mediaStoreItems.mapTo(HashSet()) { it.path }
        val newItems = fsItems.filter { it.path.isNotEmpty() && it.path !in existingPaths }
        if (newItems.isEmpty()) return mediaStoreItems
        return (mediaStoreItems + newItems).sortedWith(sortOrder.comparator())
    }

    private fun MediaSortOrder.comparator(): Comparator<MediaItem> = when (this) {
        MediaSortOrder.NAME_ASC        -> compareBy { it.displayName.lowercase() }
        MediaSortOrder.NAME_DESC       -> compareByDescending { it.displayName.lowercase() }
        MediaSortOrder.DATE_ADDED_DESC -> compareByDescending { it.dateAdded }
        MediaSortOrder.DATE_ADDED_ASC  -> compareBy { it.dateAdded }
        MediaSortOrder.SIZE_DESC       -> compareByDescending { it.size }
        MediaSortOrder.SIZE_ASC        -> compareBy { it.size }
        MediaSortOrder.DURATION_DESC   -> compareByDescending { it.duration }
        MediaSortOrder.DURATION_ASC    -> compareBy { it.duration }
        MediaSortOrder.LAST_PLAYED     -> compareByDescending { it.lastPlayedTime }
        else                           -> compareByDescending { it.dateAdded }
    }

    // ── Repository implementations ────────────────────────────────────────────

    override fun getAllVideos(sortOrder: MediaSortOrder): Flow<List<MediaItem>> = flow {
        val ignoreNomedia   = ignoreNomedia()
        val showHiddenFiles = showHiddenFiles()

        val mediaStoreItems = mediaStoreDataSource.getAllVideos(sortOrder, ignoreNomedia)
        val fsItems         = extraFsItems(ignoreNomedia, showHiddenFiles)
        val merged          = mergeAndSort(mediaStoreItems, fsItems, sortOrder)

        val enriched = merged.map { item ->
            val state = mediaPlaybackStateDao.getById(item.id)
            item.copy(
                isFavorite          = state?.isFavorite ?: false,
                lastPlayedPosition  = state?.positionMs ?: 0L,
                lastPlayedTime      = state?.lastPlayedTime ?: 0L,
                playCount           = state?.playCount ?: 0,
            )
        }
        emit(enriched)
    }.flowOn(Dispatchers.IO)

    override fun getVideosByFolder(folderId: Long, sortOrder: MediaSortOrder): Flow<List<MediaItem>> = flow {
        val ignoreNomedia   = ignoreNomedia()
        val showHiddenFiles = showHiddenFiles()

        val allMs    = mediaStoreDataSource.getAllVideos(sortOrder, ignoreNomedia)
        val allFs    = extraFsItems(ignoreNomedia, showHiddenFiles)
        val all      = mergeAndSort(allMs, allFs, sortOrder)
        emit(all.filter { it.folderId == folderId })
    }.flowOn(Dispatchers.IO)

    override fun getAllFolders(): Flow<List<MediaFolder>> = flow {
        val ignoreNomedia   = ignoreNomedia()
        val showHiddenFiles = showHiddenFiles()

        val mediaStoreFolders = mediaStoreDataSource.getAllFolders(ignoreNomedia).toMutableList()

        // Supplement with folders only reachable via the filesystem scan.
        val fsItems = extraFsItems(ignoreNomedia, showHiddenFiles)
        if (fsItems.isNotEmpty()) {
            val existingPaths = mediaStoreFolders.mapTo(HashSet()) { it.path }
            val extraFolders  = mediaStoreDataSource.deriveFoldersFromExtras(fsItems, existingPaths)
            mediaStoreFolders.addAll(extraFolders)
        }

        emit(mediaStoreFolders.sortedByDescending { it.mediaCount })
    }.flowOn(Dispatchers.IO)

    override fun getRecentlyPlayed(limit: Int): Flow<List<MediaItem>> =
        mediaPlaybackStateDao.getRecentlyPlayed(limit).map { states ->
            val ignoreNomedia   = ignoreNomedia()
            val showHiddenFiles = showHiddenFiles()
            val msVideos = mediaStoreDataSource.getAllVideos(ignoreNomedia = ignoreNomedia)
            val fsVideos = extraFsItems(ignoreNomedia, showHiddenFiles)
            val videoMap = (msVideos + fsVideos).associateBy { it.id }
            states.mapNotNull { state ->
                videoMap[state.mediaId]?.copy(
                    isFavorite         = state.isFavorite,
                    lastPlayedPosition = state.positionMs,
                    lastPlayedTime     = state.lastPlayedTime,
                    playCount          = state.playCount,
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun getFavorites(): Flow<List<MediaItem>> =
        mediaPlaybackStateDao.getFavorites().map { states ->
            val ignoreNomedia   = ignoreNomedia()
            val showHiddenFiles = showHiddenFiles()
            val msVideos = mediaStoreDataSource.getAllVideos(ignoreNomedia = ignoreNomedia)
            val fsVideos = extraFsItems(ignoreNomedia, showHiddenFiles)
            val videoMap = (msVideos + fsVideos).associateBy { it.id }
            states.mapNotNull { state ->
                videoMap[state.mediaId]?.copy(
                    isFavorite         = true,
                    lastPlayedPosition = state.positionMs,
                    lastPlayedTime     = state.lastPlayedTime,
                    playCount          = state.playCount,
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun searchMedia(query: String): Flow<List<MediaItem>> = flow {
        emit(mediaStoreDataSource.searchVideos(query))
    }.flowOn(Dispatchers.IO)

    override suspend fun getMediaById(id: Long): MediaItem? = withContext(Dispatchers.IO) {
        val ignoreNomedia   = ignoreNomedia()
        val showHiddenFiles = showHiddenFiles()
        val ms = mediaStoreDataSource.getAllVideos(ignoreNomedia = ignoreNomedia)
        ms.find { it.id == id }
            ?: extraFsItems(ignoreNomedia, showHiddenFiles).find { it.id == id }
    }

    override suspend fun getMediaByUri(uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
        val ignoreNomedia   = ignoreNomedia()
        val showHiddenFiles = showHiddenFiles()
        val ms = mediaStoreDataSource.getAllVideos(ignoreNomedia = ignoreNomedia)
        ms.find { it.uri == uri }
            ?: extraFsItems(ignoreNomedia, showHiddenFiles).find { it.uri == uri }
            ?: MediaItem(
                id          = System.currentTimeMillis(),
                uri         = uri,
                displayName = uri.lastPathSegment ?: "Unknown",
                title       = uri.lastPathSegment ?: "Unknown",
            )
    }

    override suspend fun updatePlaybackPosition(id: Long, position: Long, duration: Long) =
        withContext(Dispatchers.IO) {
            val existing = mediaPlaybackStateDao.getById(id)
            if (existing == null) {
                mediaPlaybackStateDao.insert(
                    MediaPlaybackStateEntity(
                        mediaId       = id,
                        positionMs    = position,
                        durationMs    = duration,
                        lastPlayedTime = System.currentTimeMillis(),
                    )
                )
            } else {
                mediaPlaybackStateDao.updatePosition(id, position, duration)
            }
        }

    override suspend fun incrementPlayCount(id: Long) = withContext(Dispatchers.IO) {
        val existing = mediaPlaybackStateDao.getById(id)
        if (existing == null) {
            mediaPlaybackStateDao.insert(
                MediaPlaybackStateEntity(
                    mediaId        = id,
                    playCount      = 1,
                    lastPlayedTime = System.currentTimeMillis(),
                )
            )
        } else {
            mediaPlaybackStateDao.incrementPlayCount(id)
        }
    }

    override suspend fun toggleFavorite(id: Long): Boolean = withContext(Dispatchers.IO) {
        val existing = mediaPlaybackStateDao.getById(id)
        if (existing == null) {
            mediaPlaybackStateDao.insert(MediaPlaybackStateEntity(mediaId = id, isFavorite = true))
            true
        } else {
            mediaPlaybackStateDao.toggleFavorite(id)
            !existing.isFavorite
        }
    }

    override suspend fun rescanMedia() = withContext(Dispatchers.IO) {
        // Trigger MediaStore to re-index the external storage root.
        // Note: MediaStore will still skip .nomedia folders — that gap is covered
        // by scanFilesystemExtra() on devices where MANAGE_EXTERNAL_STORAGE is granted.
        val externalRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(externalRoot),
            null,
            null,
        )
    }

    override suspend fun getMediaCount(): Int = withContext(Dispatchers.IO) {
        mediaStoreDataSource.getAllVideos(ignoreNomedia = ignoreNomedia()).size
    }
}
