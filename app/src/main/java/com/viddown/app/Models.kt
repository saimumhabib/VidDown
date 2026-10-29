package com.viddown.app

data class QualityOption(
    val id: String,
    val label: String,
    val detail: String,
    val selector: String,
    val isAudio: Boolean,
)

data class VideoInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long?,
    val thumbnail: String?,
    val site: String?,
    val options: List<QualityOption>,
)

/** State of the "paste link -> show qualities" part of the screen. */
sealed interface UiState {
    data object Idle : UiState
    data class Fetching(val firstRun: Boolean) : UiState
    data class Ready(val info: VideoInfo) : UiState
    data class Failed(val message: String) : UiState
}

enum class Phase { Starting, Downloading, Merging, Saving }

/** State of the current download. */
sealed interface DownloadState {
    data object None : DownloadState
    data class Running(
        val title: String,
        val phase: Phase,
        val percent: Float?,
        val speedBps: Double?,
        val etaSec: Long?,
    ) : DownloadState
    data class Done(val title: String, val fileName: String, val uri: String, val mime: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}
