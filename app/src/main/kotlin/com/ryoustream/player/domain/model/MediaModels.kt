package com.ryoustream.player.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * Core domain model representing a playable media item.
 */
@Parcelize
data class MediaItem(
    val id: Long = 0L,
    val uri: @RawValue Uri,
    val displayName: String,
    val title: String = displayName,
    val duration: Long = 0L,         // milliseconds
    val size: Long = 0L,             // bytes
    val mimeType: String = "",
    val path: String = "",
    val folderName: String = "",
    val folderId: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailUri: @RawValue Uri? = null,
    val isFavorite: Boolean = false,
    val lastPlayedPosition: Long = 0L,
    val lastPlayedTime: Long = 0L,
    val playCount: Int = 0,
    val mediaType: MediaType = MediaType.VIDEO,
    val source: MediaSource = MediaSource.LOCAL,
    val resolution: VideoResolution = VideoResolution.UNKNOWN,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val frameRate: Float = 0f,
    val bitrate: Long = 0L,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
) : Parcelable {

    val durationFormatted: String
        get() = formatDuration(duration)

    val sizeFormatted: String
        get() = formatSize(size)

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 16f / 9f

    val hasSubtitles: Boolean
        get() = subtitleTracks.isNotEmpty()

    val hasMultipleAudioTracks: Boolean
        get() = audioTracks.size > 1

    // Progress playback (0.0 – 1.0)
    val watchProgress: Float
        get() = if (duration > 0L) lastPlayedPosition.toFloat() / duration.toFloat() else 0f

    // True jika video sudah mulai tapi belum selesai (5% – 95%)
    val isInProgress: Boolean
        get() = watchProgress > 0.05f && watchProgress < 0.95f

    // Alias deskriptif untuk sizeFormatted
    val fileSizeFormatted: String
        get() = sizeFormatted

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

        fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
                bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
                else -> "$bytes B"
            }
        }
    }
}

enum class MediaType { VIDEO, AUDIO }

enum class MediaSource { LOCAL, NETWORK, SMB, FTP, SFTP, CLOUD }

enum class VideoResolution(val label: String, val maxHeight: Int) {
    UNKNOWN("Unknown", 0),
    SD("SD", 480),
    HD("HD 720p", 720),
    FHD("FHD 1080p", 1080),
    QHD("QHD 1440p", 1440),
    UHD("4K UHD", 2160),
    UHD8K("8K", 4320);

    companion object {
        fun fromHeight(height: Int): VideoResolution = when {
            height <= 0 -> UNKNOWN
            height <= 480 -> SD
            height <= 720 -> HD
            height <= 1080 -> FHD
            height <= 1440 -> QHD
            height <= 2160 -> UHD
            else -> UHD8K
        }
    }
}

/**
 * Subtitle track model
 */
@Parcelize
data class SubtitleTrack(
    val id: Int,
    val language: String = "",
    val label: String = "",
    val mimeType: String = "",
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val externalUri: @RawValue Uri? = null,
    val encoding: String = "UTF-8",
    val delay: Long = 0L,             // milliseconds offset
) : Parcelable

/**
 * Audio track model
 */
@Parcelize
data class AudioTrack(
    val id: Int,
    val language: String = "",
    val label: String = "",
    val channels: Int = 2,
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val codec: String = "",
    val isDefault: Boolean = false,
) : Parcelable

/**
 * Folder model for library browsing
 */
@Parcelize
data class MediaFolder(
    val id: Long,
    val name: String,
    val path: String,
    val mediaCount: Int = 0,
    val totalSize: Long = 0L,
    val thumbnailUri: @RawValue Uri? = null,
    val lastModified: Long = 0L,
) : Parcelable

/**
 * Playlist model
 */
@Parcelize
data class Playlist(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val items: List<MediaItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailUri: @RawValue Uri? = null,
) : Parcelable {
    val itemCount: Int get() = items.size
    val totalDuration: Long get() = items.sumOf { it.duration }
}

/**
 * Network stream model
 */
@Parcelize
data class NetworkStream(
    val id: Long = 0L,
    val name: String,
    val url: String,
    val protocol: StreamProtocol = StreamProtocol.HTTP,
    val username: String = "",
    val password: String = "",
    val lastUsed: Long = 0L,
    val isFavorite: Boolean = false,
) : Parcelable

enum class StreamProtocol(val scheme: String) {
    HTTP("http"),
    HTTPS("https"),
    HLS("https"),      // .m3u8
    DASH("https"),     // .mpd
    RTSP("rtsp"),
    RTP("rtp"),
    SMB("smb"),
    FTP("ftp"),
    SFTP("sftp"),
    UDP("udp"),
    TCP("tcp"),
}

/**
 * Playback state model
 */
data class PlaybackState(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferingPercent: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val volume: Float = 1.0f,
    val currentSubtitleTrack: SubtitleTrack? = null,
    val currentAudioTrack: AudioTrack? = null,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val hdrEnabled: Boolean = false,
    val isLocked: Boolean = false,
    val error: String? = null,
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
}

enum class RepeatMode { NONE, ONE, ALL }

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    CROP("Crop"),
    STRETCH("Stretch"),
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9"),
    RATIO_21_9("21:9"),
}

/**
 * Sort and filter options
 */
enum class MediaSortOrder(val label: String) {
    NAME_ASC("Name ↑"),
    NAME_DESC("Name ↓"),
    DATE_ADDED_DESC("Newest"),
    DATE_ADDED_ASC("Oldest"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest"),
    DURATION_DESC("Longest"),
    DURATION_ASC("Shortest"),
    LAST_PLAYED("Last Played"),
}

enum class ViewMode { GRID, LIST }
