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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaPlaybackStateDao: MediaPlaybackStateDao,
) : MediaRepository {

    override fun getAllVideos(sortOrder: MediaSortOrder): Flow<List<MediaItem>> = flow {
        val videos = mediaStoreDataSource.getAllVideos(sortOrder)
        val enriched = videos.map { item ->
            val state = mediaPlaybackStateDao.getById(item.id)
            item.copy(
                isFavorite = state?.isFavorite ?: false,
                lastPlayedPosition = state?.positionMs ?: 0L,
                lastPlayedTime = state?.lastPlayedTime ?: 0L,
                playCount = state?.playCount ?: 0,
            )
        }
        emit(enriched)
    }.flowOn(Dispatchers.IO)

    override fun getVideosByFolder(folderId: Long, sortOrder: MediaSortOrder): Flow<List<MediaItem>> = flow {
        val all = mediaStoreDataSource.getAllVideos(sortOrder)
        emit(all.filter { it.folderId == folderId })
    }.flowOn(Dispatchers.IO)

    override fun getAllFolders(): Flow<List<MediaFolder>> = flow {
        emit(mediaStoreDataSource.getAllFolders())
    }.flowOn(Dispatchers.IO)

    override fun getRecentlyPlayed(limit: Int): Flow<List<MediaItem>> =
        mediaPlaybackStateDao.getRecentlyPlayed(limit).map { states ->
            val allVideos = mediaStoreDataSource.getAllVideos()
            val videoMap = allVideos.associateBy { it.id }
            states.mapNotNull { state ->
                videoMap[state.mediaId]?.copy(
                    isFavorite = state.isFavorite,
                    lastPlayedPosition = state.positionMs,
                    lastPlayedTime = state.lastPlayedTime,
                    playCount = state.playCount,
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun getFavorites(): Flow<List<MediaItem>> =
        mediaPlaybackStateDao.getFavorites().map { states ->
            val allVideos = mediaStoreDataSource.getAllVideos()
            val videoMap = allVideos.associateBy { it.id }
            states.mapNotNull { state ->
                videoMap[state.mediaId]?.copy(
                    isFavorite = true,
                    lastPlayedPosition = state.positionMs,
                    lastPlayedTime = state.lastPlayedTime,
                    playCount = state.playCount,
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun searchMedia(query: String): Flow<List<MediaItem>> = flow {
        emit(mediaStoreDataSource.searchVideos(query))
    }.flowOn(Dispatchers.IO)

    override suspend fun getMediaById(id: Long): MediaItem? = withContext(Dispatchers.IO) {
        mediaStoreDataSource.getAllVideos().find { it.id == id }
    }

    override suspend fun getMediaByUri(uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
        // Try to find by URI in MediaStore
        val all = mediaStoreDataSource.getAllVideos()
        all.find { it.uri == uri } ?: run {
            // Create a minimal MediaItem for URIs not in MediaStore (e.g. network, SAF)
            MediaItem(
                id = System.currentTimeMillis(),
                uri = uri,
                displayName = uri.lastPathSegment ?: "Unknown",
                title = uri.lastPathSegment ?: "Unknown",
            )
        }
    }

    override suspend fun updatePlaybackPosition(id: Long, position: Long, duration: Long) =
        withContext(Dispatchers.IO) {
            val existing = mediaPlaybackStateDao.getById(id)
            if (existing == null) {
                mediaPlaybackStateDao.insert(
                    MediaPlaybackStateEntity(
                        mediaId = id,
                        positionMs = position,
                        durationMs = duration,
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
                    mediaId = id,
                    playCount = 1,
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
            mediaPlaybackStateDao.insert(
                MediaPlaybackStateEntity(mediaId = id, isFavorite = true)
            )
            true
        } else {
            mediaPlaybackStateDao.toggleFavorite(id)
            !existing.isFavorite
        }
    }

    override suspend fun rescanMedia() = withContext(Dispatchers.IO) {
        // Trigger MediaStore rescan via broadcast
        context.sendBroadcast(
            android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        )
    }

    override suspend fun getMediaCount(): Int = withContext(Dispatchers.IO) {
        mediaStoreDataSource.getAllVideos().size
    }
}
