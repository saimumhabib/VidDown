package com.viddown.app.data

// ──────────────────────────────────────────────
// Video Info Models
// ──────────────────────────────────────────────

data class VideoInfo(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String,
    val duration: String,
    val uploader: String,
    val viewCount: Long,
    val description: String,
    val platform: Platform,
    val videoFormats: List<VideoFormat>,
    val audioFormats: List<AudioFormat>,
    val subtitles: List<SubtitleInfo>,
    val isPlaylist: Boolean = false,
    val playlistCount: Int = 0,
    val playlistEntries: List<PlaylistEntry> = emptyList()
)

data class PlaylistEntry(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String,
    val height: Int,
    val fps: Int?,
    val filesize: Long?,
    val vcodec: String,
    val acodec: String,
    val tbr: Double
) {
    val displayLabel: String get() = when {
        height >= 2160 -> "4K (2160p)"
        height >= 1440 -> "2K (1440p)"
        height >= 1080 -> "Full HD (1080p)"
        height >= 720  -> "HD (720p)"
        height >= 480  -> "SD (480p)"
        height >= 360  -> "360p"
        height >= 240  -> "240p"
        else           -> "${height}p"
    }
    val filesizeDisplay: String get() = filesize?.let { formatFileSize(it) } ?: "?"
}

data class AudioFormat(
    val formatId: String,
    val ext: String,
    val abr: Double,
    val filesize: Long?
) {
    val displayLabel: String get() = if (abr > 0) "MP3 (${abr.toInt()} kbps)" else "MP3 (Best Quality)"
    val filesizeDisplay: String get() = filesize?.let { formatFileSize(it) } ?: "?"
}

data class SubtitleInfo(
    val lang: String,
    val name: String
)

// ──────────────────────────────────────────────
// Download Item Models
// ──────────────────────────────────────────────

data class ActiveDownload(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String,
    val format: String,
    val quality: String,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val filePath: String = "",
    val fileSize: Long = 0L
)

enum class DownloadStatus {
    QUEUED, ANALYZING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

enum class Platform {
    YOUTUBE, INSTAGRAM, TIKTOK, FACEBOOK, TWITTER, SOUNDCLOUD, TWITCH, OTHER;

    val displayName: String get() = when (this) {
        YOUTUBE    -> "YouTube"
        INSTAGRAM  -> "Instagram"
        TIKTOK     -> "TikTok"
        FACEBOOK   -> "Facebook"
        TWITTER    -> "Twitter/X"
        SOUNDCLOUD -> "SoundCloud"
        TWITCH     -> "Twitch"
        OTHER      -> "Video"
    }

    companion object {
        fun fromUrl(url: String): Platform = when {
            "youtube.com" in url || "youtu.be" in url -> YOUTUBE
            "instagram.com" in url                    -> INSTAGRAM
            "tiktok.com" in url                       -> TIKTOK
            "facebook.com" in url || "fb.watch" in url -> FACEBOOK
            "twitter.com" in url || "x.com" in url   -> TWITTER
            "soundcloud.com" in url                   -> SOUNDCLOUD
            "twitch.tv" in url                        -> TWITCH
            else                                      -> OTHER
        }
    }
}

// ──────────────────────────────────────────────
// Helper Functions
// ──────────────────────────────────────────────

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024         -> String.format("%.0f KB", bytes / 1_024.0)
        else                   -> "$bytes B"
    }
}

fun isVideoUrl(text: String): Boolean {
    val patterns = listOf(
        "youtube.com/watch", "youtu.be/", "youtube.com/shorts",
        "instagram.com/p/", "instagram.com/reel/", "instagram.com/tv/",
        "tiktok.com/", "facebook.com/watch", "fb.watch/",
        "twitter.com/", "x.com/", "soundcloud.com/",
        "twitch.tv/", "vimeo.com/", "dailymotion.com/"
    )
    return patterns.any { it in text }
}
