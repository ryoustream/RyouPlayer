package com.ryoustream.player.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ryoustream.player.domain.model.MediaFolder
import com.ryoustream.player.domain.model.MediaItem
import com.ryoustream.player.domain.model.MediaSortOrder
import com.ryoustream.player.domain.model.MediaSource
import com.ryoustream.player.domain.model.MediaType
import com.ryoustream.player.domain.model.VideoResolution
import com.ryoustream.player.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStore data source.
 * Queries the Android MediaStore to discover local video/audio files.
 *
 * Hidden-file / .nomedia strategy
 * ────────────────────────────────
 * Android MediaStore enforces .nomedia exclusion at the OS level — there is no
 * public API to bypass it in a regular query.  When MANAGE_EXTERNAL_STORAGE
 * ("All files access") is granted, we supplement the MediaStore results with a
 * direct filesystem walk via [scanFilesystemExtra].  Files found in .nomedia
 * directories or hidden (dot-prefixed) directories that are already in
 * MediaStore are de-duplicated by absolute path in MediaRepositoryImpl.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        // Video file extensions recognised during filesystem scan.
        // Matches the intent-filters declared in AndroidManifest.xml.
        private val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "avi", "mov", "flv", "webm",
            "m2ts", "ts", "wmv", "3gp", "m4v", "mpg", "mpeg",
        )
    }

    // ── MediaStore query ──────────────────────────────────────────────────────

    /**
     * Get all video files from MediaStore.
     *
     * Note: files in .nomedia folders are absent from the MediaStore index
     * regardless of query parameters — Android enforces this at the OS level.
     * Use [scanFilesystemExtra] (requires MANAGE_EXTERNAL_STORAGE) to supplement
     * with those files.
     */
    suspend fun getAllVideos(
        sortOrder: MediaSortOrder = MediaSortOrder.DATE_ADDED_DESC,
        @Suppress("UNUSED_PARAMETER") ignoreNomedia: Boolean = true,
    ): List<MediaItem> =
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
                MediaSortOrder.NAME_ASC         -> "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
                MediaSortOrder.NAME_DESC        -> "${MediaStore.Video.Media.DISPLAY_NAME} DESC"
                MediaSortOrder.DATE_ADDED_DESC  -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
                MediaSortOrder.DATE_ADDED_ASC   -> "${MediaStore.Video.Media.DATE_ADDED} ASC"
                MediaSortOrder.SIZE_DESC        -> "${MediaStore.Video.Media.SIZE} DESC"
                MediaSortOrder.SIZE_ASC         -> "${MediaStore.Video.Media.SIZE} ASC"
                MediaSortOrder.DURATION_DESC    -> "${MediaStore.Video.Media.DURATION} DESC"
                MediaSortOrder.DURATION_ASC     -> "${MediaStore.Video.Media.DURATION} ASC"
                else                            -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val cursor = contentResolver.query(collection, projection, null, null, orderByClause)

            cursor?.use { c ->
                val idCol          = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol        = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol       = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol    = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol        = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol        = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dataCol        = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val bucketNameCol  = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val bucketIdCol    = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val dateAddedCol   = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModifiedCol= c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val widthCol       = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol      = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val bitrateCol     = c.getColumnIndexOrThrow(MediaStore.Video.Media.BITRATE)

                while (c.moveToNext()) {
                    val id         = c.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val height = c.getInt(heightCol)
                    val width  = c.getInt(widthCol)

                    items.add(MediaItem(
                        id           = id,
                        uri          = contentUri,
                        displayName  = c.getString(nameCol) ?: "",
                        title        = c.getString(titleCol) ?: c.getString(nameCol) ?: "",
                        duration     = c.getLong(durationCol),
                        size         = c.getLong(sizeCol),
                        mimeType     = c.getString(mimeCol) ?: "video/*",
                        path         = c.getString(dataCol) ?: "",
                        folderName   = c.getString(bucketNameCol) ?: "",
                        folderId     = c.getLong(bucketIdCol),
                        dateAdded    = c.getLong(dateAddedCol) * 1000L,
                        dateModified = c.getLong(dateModifiedCol) * 1000L,
                        width        = width,
                        height       = height,
                        bitrate      = c.getLong(bitrateCol),
                        mediaType    = MediaType.VIDEO,
                        source       = MediaSource.LOCAL,
                        resolution   = VideoResolution.fromHeight(height),
                        thumbnailUri = getThumbnailUri(id),
                    ))
                }
            }
            items
        }

    /** Get all unique folders containing videos (MediaStore only). */
    suspend fun getAllFolders(
        @Suppress("UNUSED_PARAMETER") ignoreNomedia: Boolean = true,
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
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

        val folderCursor = contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC"
        )

        folderCursor?.use { cursor ->
            val idCol        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val bucketIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol= cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dataCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateModCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val bucketId   = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val mediaId    = cursor.getLong(idCol)
                val size       = cursor.getLong(sizeCol)
                val dateMod    = cursor.getLong(dateModCol) * 1000L
                val path       = cursor.getString(dataCol) ?: ""
                val folderPath = path.substringBeforeLast("/")

                val existing = folders[bucketId]
                if (existing == null) {
                    folders[bucketId] = MediaFolder(
                        id           = bucketId,
                        name         = bucketName,
                        path         = folderPath,
                        mediaCount   = 1,
                        totalSize    = size,
                        thumbnailUri = getThumbnailUri(mediaId),
                        lastModified = dateMod,
                    )
                } else {
                    folders[bucketId] = existing.copy(
                        mediaCount   = existing.mediaCount + 1,
                        totalSize    = existing.totalSize + size,
                        lastModified = maxOf(existing.lastModified, dateMod),
                    )
                }
            }
        }
        folders.values.sortedByDescending { it.mediaCount }
    }

    // ── Filesystem scan (MANAGE_EXTERNAL_STORAGE required) ────────────────────

    /**
     * Walks the external storage filesystem to find video files that MediaStore
     * deliberately excludes:
     *  - Files in directories that contain a `.nomedia` marker (when [ignoreNomedia] = true)
     *  - Files in dot-prefixed (hidden) directories (when [showHiddenFiles] = true)
     *
     * Requires MANAGE_EXTERNAL_STORAGE ("All files access") to be granted;
     * returns an empty list silently if not.
     *
     * The caller is responsible for de-duplicating these results against
     * MediaStore results (by [MediaItem.path]).
     *
     * Performance note: [MediaMetadataRetriever] is opened once per file.
     * For very large libraries (>500 hidden files) this can be slow; an
     * in-process cache keyed by (path, lastModified) is a future optimisation.
     */
    suspend fun scanFilesystemExtra(
        showHiddenFiles: Boolean,
        ignoreNomedia: Boolean,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasAllFilesAccess()) return@withContext emptyList()
        if (!showHiddenFiles && !ignoreNomedia) return@withContext emptyList()

        val items   = mutableListOf<MediaItem>()
        val rootDir = Environment.getExternalStorageDirectory()

        rootDir.walkTopDown()
            .onEnter { dir ->
                // Never descend into Android system cache / data dirs — no useful
                // media there and it would be very slow.
                val name = dir.name
                when {
                    dir == rootDir                                     -> true
                    name.equals("Android", ignoreCase = true)         -> false
                    name.startsWith(".") && !showHiddenFiles           -> false
                    else                                               -> true
                }
            }
            .filter { it.isFile }
            .filter { file ->
                if (file.extension.lowercase() !in VIDEO_EXTENSIONS) return@filter false
                val parent = file.parentFile ?: return@filter false
                val isHiddenFile = file.name.startsWith(".")
                val isInHiddenDir = parent.name.startsWith(".")
                val hasNomedia = File(parent, ".nomedia").exists()

                // Only include files that MediaStore would normally skip:
                // - In a .nomedia folder (and user wants to see them)
                // - In a hidden dir (and user wants to see them)
                // Hidden files themselves (dot-prefixed filenames) follow the same rule.
                val wantNomedia = hasNomedia && ignoreNomedia
                val wantHidden  = (isInHiddenDir || isHiddenFile) && showHiddenFiles
                wantNomedia || wantHidden
            }
            .forEach { file ->
                val item = buildMediaItemFromFile(file)
                if (item != null) items.add(item)
            }

        items
    }

    /**
     * Derives the list of extra [MediaFolder] objects that correspond to the
     * extra [MediaItem]s returned by [scanFilesystemExtra].
     *
     * Groups items by parent directory and creates a synthetic [MediaFolder]
     * for each unique parent not already present in the MediaStore folder list.
     */
    fun deriveFoldersFromExtras(
        extraItems: List<MediaItem>,
        existingFolderPaths: Set<String>,
    ): List<MediaFolder> {
        if (extraItems.isEmpty()) return emptyList()

        return extraItems
            .groupBy { File(it.path).parent ?: "" }
            .filterKeys { it.isNotEmpty() && it !in existingFolderPaths }
            .map { (folderPath, folderItems) ->
                val folderDir = File(folderPath)
                val firstItem = folderItems.first()
                MediaFolder(
                    // Synthetic IDs: use a stable hash of the path.
                    // These are negative to avoid colliding with positive MediaStore bucket IDs.
                    id           = -(folderPath.hashCode().toLong().and(0x7FFF_FFFFL) + 1L),
                    name         = folderDir.name,
                    path         = folderPath,
                    mediaCount   = folderItems.size,
                    totalSize    = folderItems.sumOf { it.size },
                    thumbnailUri = firstItem.uri,
                    lastModified = folderItems.maxOf { it.dateModified },
                )
            }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts metadata from [file] using [MediaMetadataRetriever] and builds
     * a [MediaItem].  Returns null if the file cannot be opened.
     *
     * Synthetic IDs are derived from the file path hash so that the same file
     * always gets the same ID across app restarts.
     */
    private fun buildMediaItemFromFile(file: File): MediaItem? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mimeType = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: "video/*"
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull() ?: 0L

            // Synthetic MediaItem ID: stable hash of absolute path, offset to
            // avoid collisions with real MediaStore IDs (which start from ~1).
            val syntheticId = file.absolutePath.hashCode().toLong().and(0x7FFF_FFFFL) + 0x1_0000_0000L

            MediaItem(
                id           = syntheticId,
                uri          = Uri.fromFile(file),
                displayName  = file.name,
                title        = file.nameWithoutExtension,
                duration     = duration,
                size         = file.length(),
                mimeType     = mimeType,
                path         = file.absolutePath,
                folderName   = file.parentFile?.name ?: "",
                folderId     = -(file.parent.hashCode().toLong().and(0x7FFF_FFFFL) + 1L),
                dateAdded    = file.lastModified(),
                dateModified = file.lastModified(),
                width        = width,
                height       = height,
                bitrate      = bitrate,
                mediaType    = MediaType.VIDEO,
                source       = MediaSource.LOCAL,
                resolution   = VideoResolution.fromHeight(height),
                thumbnailUri = Uri.fromFile(file),
            )
        } catch (_: Exception) {
            null   // unreadable file — skip silently
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Search media by display name (MediaStore only). */
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
            val idCol          = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeCol        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dataCol        = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val bucketNameCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val bucketIdCol    = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val dateAddedCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedCol= cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val widthCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol      = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id     = cursor.getLong(idCol)
                val height = cursor.getInt(heightCol)
                items.add(MediaItem(
                    id          = id,
                    uri         = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    displayName = cursor.getString(nameCol) ?: "",
                    title       = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "",
                    duration    = cursor.getLong(durationCol),
                    size        = cursor.getLong(sizeCol),
                    mimeType    = cursor.getString(mimeCol) ?: "video/*",
                    path        = cursor.getString(dataCol) ?: "",
                    folderName  = cursor.getString(bucketNameCol) ?: "",
                    folderId    = cursor.getLong(bucketIdCol),
                    dateAdded   = cursor.getLong(dateAddedCol) * 1000L,
                    dateModified= cursor.getLong(dateModifiedCol) * 1000L,
                    width       = cursor.getInt(widthCol),
                    height      = height,
                    resolution  = VideoResolution.fromHeight(height),
                    thumbnailUri = getThumbnailUri(id),
                ))
            }
        }
        items
    }

    private fun getThumbnailUri(mediaId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
}
