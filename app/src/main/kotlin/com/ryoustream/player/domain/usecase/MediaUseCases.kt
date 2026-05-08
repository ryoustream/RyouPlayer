package com.ryoustream.player.domain.usecase

import android.net.Uri
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.Playlist
import com.ryoustream.player.domain.repository.MediaRepository
import com.ryoustream.player.domain.repository.PlaylistRepository
import com.ryoustream.player.domain.repository.StreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case: Get all videos with optional sorting
 */
class GetAllVideosUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(sortOrder: MediaSortOrder = MediaSortOrder.DATE_ADDED_DESC): Flow<List<MediaItem>> =
        mediaRepository.getAllVideos(sortOrder)
}

/**
 * Use case: Get recently played media
 */
class GetRecentlyPlayedUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(limit: Int = 20): Flow<List<MediaItem>> =
        mediaRepository.getRecentlyPlayed(limit)
}

/**
 * Use case: Get favorite media
 */
class GetFavoritesUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<List<MediaItem>> =
        mediaRepository.getFavorites()
}

/**
 * Use case: Search media
 */
class SearchMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(query: String): Flow<List<MediaItem>> =
        if (query.isBlank()) mediaRepository.getAllVideos()
        else mediaRepository.searchMedia(query)
}

/**
 * Use case: Toggle favorite
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(id: Long): Boolean =
        mediaRepository.toggleFavorite(id)
}

/**
 * Use case: Update playback position for resume feature
 */
class UpdatePlaybackPositionUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(id: Long, position: Long, duration: Long) =
        mediaRepository.updatePlaybackPosition(id, position, duration)
}

/**
 * Use case: Get media by URI (handles external intent playback)
 */
class GetMediaByUriUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(uri: Uri): MediaItem? =
        mediaRepository.getMediaByUri(uri)
}

/**
 * Use case: Get all playlists
 */
class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<Playlist>> =
        playlistRepository.getAllPlaylists()
}

/**
 * Use case: Create a new playlist
 */
class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(name: String, description: String = ""): Result<Long> =
        runCatching { playlistRepository.createPlaylist(name, description) }
}

/**
 * Use case: Add item to playlist
 */
class AddToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, mediaItem: MediaItem) =
        playlistRepository.addItemToPlaylist(playlistId, mediaItem)
}

/**
 * Use case: Delete playlist
 */
class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long) = playlistRepository.deletePlaylist(id)
}

/**
 * Use case: Rescan media library
 */
class RescanMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke() = mediaRepository.rescanMedia()
}

/**
 * Use case: Get videos grouped by folder with item counts
 */
class GetVideosByFolderUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(folderId: Long, sortOrder: MediaSortOrder = MediaSortOrder.NAME_ASC) =
        mediaRepository.getVideosByFolder(folderId, sortOrder)
}
