package com.ryoustream.player.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import android.content.Context
import kotlinx.coroutines.flow.Flow

// ─── ENTITIES ─────────────────────────────────────────────────────────────────

@Entity(tableName = "media_playback_state")
data class MediaPlaybackStateEntity(
    @PrimaryKey val mediaId: Long,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0L,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played") val lastPlayedTime: Long = 0L,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_subtitle_track") val lastSubtitleTrack: Int = -1,
    @ColumnInfo(name = "last_audio_track") val lastAudioTrack: Int = 0,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "thumbnail_uri") val thumbnailUri: String? = null,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlist_id")]
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "media_title") val mediaTitle: String,
    @ColumnInfo(name = "position") val position: Int = 0,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "network_streams")
data class NetworkStreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "protocol") val protocol: String = "http",
    @ColumnInfo(name = "username") val username: String = "",
    @ColumnInfo(name = "password") val password: String = "",
    @ColumnInfo(name = "last_used") val lastUsed: Long = 0L,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
)

// ─── DAOs ──────────────────────────────────────────────────────────────────────

@Dao
interface MediaPlaybackStateDao {
    @Query("SELECT * FROM media_playback_state WHERE mediaId = :id")
    suspend fun getById(id: Long): MediaPlaybackStateEntity?

    @Query("SELECT * FROM media_playback_state ORDER BY last_played DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<MediaPlaybackStateEntity>>

    @Query("SELECT * FROM media_playback_state WHERE is_favorite = 1 ORDER BY last_played DESC")
    fun getFavorites(): Flow<List<MediaPlaybackStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaPlaybackStateEntity)

    @Update
    suspend fun update(entity: MediaPlaybackStateEntity)

    @Query("UPDATE media_playback_state SET position_ms = :position, duration_ms = :duration, last_played = :time WHERE mediaId = :id")
    suspend fun updatePosition(id: Long, position: Long, duration: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE media_playback_state SET play_count = play_count + 1, last_played = :time WHERE mediaId = :id")
    suspend fun incrementPlayCount(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE media_playback_state SET is_favorite = NOT is_favorite WHERE mediaId = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT is_favorite FROM media_playback_state WHERE mediaId = :id")
    suspend fun isFavorite(id: Long): Boolean?

    @Query("DELETE FROM media_playback_state WHERE last_played < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updated_at DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistById(id: Long): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items WHERE playlist_id = :playlistId ORDER BY position ASC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND media_id = :mediaId")
    suspend fun removePlaylistItem(playlistId: Long, mediaId: Long)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?
}

@Dao
interface NetworkStreamDao {
    @Query("SELECT * FROM network_streams ORDER BY last_used DESC")
    fun getAllStreams(): Flow<List<NetworkStreamEntity>>

    @Query("SELECT * FROM network_streams WHERE is_favorite = 1 ORDER BY name ASC")
    fun getFavoriteStreams(): Flow<List<NetworkStreamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stream: NetworkStreamEntity): Long

    @Update
    suspend fun update(stream: NetworkStreamEntity)

    @Query("DELETE FROM network_streams WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE network_streams SET is_favorite = NOT is_favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("UPDATE network_streams SET last_used = :time WHERE id = :id")
    suspend fun updateLastUsed(id: Long, time: Long = System.currentTimeMillis())
}

// ─── DATABASE ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        MediaPlaybackStateEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        NetworkStreamEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class RyouDatabase : RoomDatabase() {
    abstract fun mediaPlaybackStateDao(): MediaPlaybackStateDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun networkStreamDao(): NetworkStreamDao

    companion object {
        const val DATABASE_NAME = "ryou_player.db"

        fun create(context: Context): RyouDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                RyouDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
