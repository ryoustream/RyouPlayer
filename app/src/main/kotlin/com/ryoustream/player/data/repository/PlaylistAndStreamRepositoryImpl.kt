package com.ryoustream.player.data.repository

import android.net.Uri
import com.ryoustream.player.data.local.NetworkStreamDao
import com.ryoustream.player.data.local.NetworkStreamEntity
import com.ryoustream.player.data.local.PlaylistDao
import com.ryoustream.player.data.local.PlaylistEntity
import com.ryoustream.player.data.local.PlaylistItemEntity
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.NetworkStream
import com.ryoustream.player.domain.model.Playlist
import com.ryoustream.player.domain.model.StreamProtocol
import com.ryoustream.player.domain.repository.PlaylistRepository
import com.ryoustream.player.domain.repository.StreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ─── Playlist Repository ──────────────────────────────────────────────────────

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    thumbnailUri = entity.thumbnailUri?.let { Uri.parse(it) },
                )
            }
        }

    override fun getPlaylistById(id: Long): Flow<Playlist?> =
        combine(
            playlistDao.getPlaylistById(id),
            playlistDao.getPlaylistItems(id),
        ) { entity, itemEntities ->
            entity?.let {
                Playlist(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    thumbnailUri = it.thumbnailUri?.let { u -> Uri.parse(u) },
                    items = itemEntities.map { item ->
                        MediaItem(
                            id = item.mediaId,
                            uri = Uri.parse(item.mediaUri),
                            displayName = item.mediaTitle,
                            title = item.mediaTitle,
                        )
                    }
                )
            }
        }

    override suspend fun createPlaylist(name: String, description: String): Long =
        playlistDao.insertPlaylist(
            PlaylistEntity(name = name, description = description)
        )

    override suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(
            PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                createdAt = playlist.createdAt,
                updatedAt = System.currentTimeMillis(),
                thumbnailUri = playlist.thumbnailUri?.toString(),
            )
        )
    }

    override suspend fun deletePlaylist(id: Long) = playlistDao.deletePlaylist(id)

    override suspend fun addItemToPlaylist(playlistId: Long, mediaItem: MediaItem) {
        val maxPos = playlistDao.getMaxPosition(playlistId) ?: -1
        playlistDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaId = mediaItem.id,
                mediaUri = mediaItem.uri.toString(),
                mediaTitle = mediaItem.displayName,
                position = maxPos + 1,
            )
        )
    }

    override suspend fun removeItemFromPlaylist(playlistId: Long, mediaItemId: Long) =
        playlistDao.removePlaylistItem(playlistId, mediaItemId)

    override suspend fun reorderPlaylistItems(playlistId: Long, fromIndex: Int, toIndex: Int) {
        // TODO: Implement reorder
    }

    override suspend fun importM3u(uri: Uri): Playlist? = null  // TODO

    override suspend fun exportM3u(playlistId: Long): Uri? = null  // TODO
}

// ─── Stream Repository ────────────────────────────────────────────────────────

@Singleton
class StreamRepositoryImpl @Inject constructor(
    private val networkStreamDao: NetworkStreamDao,
) : StreamRepository {

    override fun getAllStreams(): Flow<List<NetworkStream>> =
        networkStreamDao.getAllStreams().map { it.map { e -> e.toDomain() } }

    override fun getFavoriteStreams(): Flow<List<NetworkStream>> =
        networkStreamDao.getFavoriteStreams().map { it.map { e -> e.toDomain() } }

    override suspend fun addStream(stream: NetworkStream): Long =
        networkStreamDao.insert(stream.toEntity())

    override suspend fun updateStream(stream: NetworkStream) =
        networkStreamDao.update(stream.toEntity())

    override suspend fun deleteStream(id: Long) = networkStreamDao.delete(id)

    override suspend fun toggleFavorite(id: Long) = networkStreamDao.toggleFavorite(id)

    override suspend fun updateLastUsed(id: Long) = networkStreamDao.updateLastUsed(id)

    private fun NetworkStreamEntity.toDomain() = NetworkStream(
        id = id, name = name, url = url,
        protocol = StreamProtocol.values().find { it.scheme == protocol } ?: StreamProtocol.HTTP,
        username = username, password = password,
        lastUsed = lastUsed, isFavorite = isFavorite,
    )

    private fun NetworkStream.toEntity() = NetworkStreamEntity(
        id = id, name = name, url = url,
        protocol = protocol.scheme,
        username = username, password = password,
        lastUsed = lastUsed, isFavorite = isFavorite,
    )
}
