package com.ryoustream.player.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.MediaSource
import com.ryoustream.player.domain.model.MediaType
import com.ryoustream.player.domain.model.VideoResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStore data source.
 * Queries the Android MediaStore to discover local video/audio files.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Get all video files from MediaStore
     */
    suspend fun getAllVideos(sortOrder: MediaSortOrder = MediaSortOrder.DATE_ADDED_DESC): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<MediaItem>()

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.BITRATE,
                MediaStore.Video.Media.RESOLUTION,
            )

            val orderByClause = when (sortOrder) {
                MediaSortOrder.NAME_ASC -> "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
                MediaSortOrder.NAME_DESC -> "${MediaStore.Video.Media.DISPLAY_NAME} DESC"
                MediaSortOrder.DATE_ADDED_DESC -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
                MediaSortOrder.DATE_ADDED_ASC -> "${MediaStore.Video.Media.DATE_ADDED} ASC"
                MediaSortOrder.SIZE_DESC -> "${MediaStore.Video.Media.SIZE} DESC"
                MediaSortOrder.SIZE_ASC -> "${MediaStore.Video.Media.SIZE} ASC"
                MediaSortOrder.DURATION_DESC -> "${MediaStore.Video.Media.DURATION} DESC"
                MediaSortOrder.DURATION_ASC -> "${MediaStore.Video.Media.DURATION} ASC"
                else -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            contentResolver.query(
                collection,
                projection,
                null,
                null,
                orderByClause
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val bitrateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BITRATE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val height = cursor.getInt(heightCol)
                    val width = cursor.getInt(widthCol)

                    val item = MediaItem(
                        id = id,
                        uri = contentUri,
                        displayName = cursor.getString(nameCol) ?: "",
                        title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "",
                        duration = cursor.getLong(durationCol),
                        size = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        path = cursor.getString(dataCol) ?: "",
                        folderName = cursor.getString(bucketNameCol) ?: "",
                        folderId = cursor.getLong(bucketIdCol),
                        dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                        dateModified = cursor.getLong(dateModifiedCol) * 1000L,
                        width = width,
                        height = height,
                        bitrate = cursor.getLong(bitrateCol),
                        mediaType = MediaType.VIDEO,
                        source = MediaSource.LOCAL,
                        resolution = VideoResolution.fromHeight(height),
                        thumbnailUri = getThumbnailUri(id),
                    )
                    items.add(item)
                }
            }
            items
        }

    /**
     * Get all unique folders containing videos
     */
    suspend fun getAllFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<Long, MediaFolder>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val mediaId = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                val dateMod = cursor.getLong(dateModCol) * 1000L
                val path = cursor.getString(dataCol) ?: ""
                val folderPath = path.substringBeforeLast("/")

                val existing = folders[bucketId]
                if (existing == null) {
                    folders[bucketId] = MediaFolder(
                        id = bucketId,
                        name = bucketName,
                        path = folderPath,
                        mediaCount = 1,
                        totalSize = size,
                        thumbnailUri = getThumbnailUri(mediaId),
                        lastModified = dateMod,
                    )
                } else {
                    folders[bucketId] = existing.copy(
                        mediaCount = existing.mediaCount + 1,
                        totalSize = existing.totalSize + size,
                        lastModified = maxOf(existing.lastModified, dateMod),
                    )
                }
            }
        }
        folders.values.sortedByDescending { it.mediaCount }
    }

    /**
     * Search media by display name
     */
    suspend fun searchVideos(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )

        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val height = cursor.getInt(heightCol)
                items.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = cursor.getString(nameCol) ?: "",
                        title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "",
                        duration = cursor.getLong(durationCol),
                        size = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        path = cursor.getString(dataCol) ?: "",
                        folderName = cursor.getString(bucketNameCol) ?: "",
                        folderId = cursor.getLong(bucketIdCol),
                        dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                        dateModified = cursor.getLong(dateModifiedCol) * 1000L,
                        width = cursor.getInt(widthCol),
                        height = height,
                        resolution = VideoResolution.fromHeight(height),
                        thumbnailUri = getThumbnailUri(id),
                    )
                )
            }
        }
        items
    }

    /**
     * Get a video thumbnail URI (Android 10+)
     */
    private fun getThumbnailUri(mediaId: Long): Uri {
        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
    }
}
