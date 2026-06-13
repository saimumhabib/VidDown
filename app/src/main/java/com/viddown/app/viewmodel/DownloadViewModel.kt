package com.viddown.app.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.viddown.app.data.*
import com.viddown.app.manager.YtDlpManager
import com.viddown.app.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// ──────────────────────────────────────────────
// UI State
// ──────────────────────────────────────────────

data class HomeUiState(
    val urlInput: String = "",
    val isAnalyzing: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val error: String? = null,
    val showQualitySheet: Boolean = false,
    val clipboardUrl: String? = null,
    val showClipboardPopup: Boolean = false
)

data class DownloadsUiState(
    val activeDownloads: List<ActiveDownload> = emptyList()
)

// ──────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val ytDlp = YtDlpManager(ctx)
    private val db = AppDatabase.getInstance(ctx)
    private val dao = db.downloadHistoryDao()

    // ── States ──
    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _downloadsState = MutableStateFlow(DownloadsUiState())
    val downloadsState: StateFlow<DownloadsUiState> = _downloadsState.asStateFlow()

    val history: StateFlow<List<DownloadHistoryEntity>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Init ──
    init {
        viewModelScope.launch(Dispatchers.IO) {
            ytDlp.initialize()
        }
    }

    // ──────────────────────────────────────────
    // URL Input
    // ──────────────────────────────────────────

    fun onUrlChanged(url: String) {
        _homeState.update { it.copy(urlInput = url, error = null, videoInfo = null) }
    }

    fun analyzeUrl(url: String = _homeState.value.urlInput) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _homeState.update { it.copy(isAnalyzing = true, error = null, videoInfo = null, urlInput = url) }
            val result = ytDlp.getVideoInfo(url)
            result.fold(
                onSuccess = { info ->
                    _homeState.update {
                        it.copy(isAnalyzing = false, videoInfo = info, showQualitySheet = true)
                    }
                },
                onFailure = { err ->
                    _homeState.update {
                        it.copy(isAnalyzing = false, error = err.message ?: "Analysis failed")
                    }
                }
            )
        }
    }

    // ──────────────────────────────────────────
    // Clipboard
    // ──────────────────────────────────────────

    fun checkClipboard(text: String?) {
        if (text != null && isVideoUrl(text) && text != _homeState.value.urlInput) {
            _homeState.update { it.copy(clipboardUrl = text, showClipboardPopup = true) }
        }
    }

    fun acceptClipboard() {
        val url = _homeState.value.clipboardUrl ?: return
        _homeState.update { it.copy(showClipboardPopup = false, clipboardUrl = null) }
        analyzeUrl(url)
    }

    fun dismissClipboard() {
        _homeState.update { it.copy(showClipboardPopup = false, clipboardUrl = null) }
    }

    // ──────────────────────────────────────────
    // Quality Sheet
    // ──────────────────────────────────────────

    fun showQualitySheet()  { _homeState.update { it.copy(showQualitySheet = true) } }
    fun dismissQualitySheet() { _homeState.update { it.copy(showQualitySheet = false) } }

    fun clearVideoInfo() {
        _homeState.update { it.copy(videoInfo = null, urlInput = "", showQualitySheet = false, error = null) }
    }

    // ──────────────────────────────────────────
    // Start Download
    // ──────────────────────────────────────────

    /**
     * Queues every video in a playlist for download at "best" quality
     * (<=1080p mp4). Skips a full per-video analysis for speed — yt-dlp
     * resolves "bv*[height<=1080]+ba/best" per item at download time.
     */
    fun startPlaylistDownload(info: VideoInfo) {
        dismissQualitySheet()
        info.playlistEntries.forEach { entry ->
            val downloadId = UUID.randomUUID().toString()
            val fileName = sanitizeFileName(entry.title) + ".mp4"
            val outputPath = getDownloadDir("Video") + "/" + fileName

            enqueueDownload(
                id = downloadId,
                url = entry.url,
                title = entry.title,
                thumbnail = entry.thumbnail,
                quality = "Best (≤1080p)",
                format = "MP4",
                outputPath = outputPath,
                type = DownloadType.VIDEO,
                formatId = "bv*[height<=1080]+ba",
                platform = info.platform.displayName
            )
        }
    }

    fun startVideoDownload(info: VideoInfo, format: VideoFormat) {
        val downloadId = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(info.title) + "_${format.height}p.mp4"
        val outputPath = getDownloadDir("Video") + "/" + fileName

        enqueueDownload(
            id = downloadId,
            url = info.url,
            title = info.title,
            thumbnail = info.thumbnail,
            quality = format.displayLabel,
            format = "MP4",
            outputPath = outputPath,
            type = DownloadType.VIDEO,
            formatId = format.formatId,
            platform = info.platform.displayName
        )
    }

    fun startAudioDownload(info: VideoInfo, audio: AudioFormat) {
        val downloadId = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(info.title) + ".mp3"
        val outputPath = getDownloadDir("Music") + "/" + fileName

        enqueueDownload(
            id = downloadId,
            url = info.url,
            title = info.title,
            thumbnail = info.thumbnail,
            quality = audio.displayLabel,
            format = "MP3",
            outputPath = outputPath,
            type = DownloadType.AUDIO,
            formatId = audio.formatId.ifBlank { "bestaudio" },
            platform = info.platform.displayName
        )
    }

    fun startSubtitleDownload(info: VideoInfo, subtitle: SubtitleInfo) {
        val downloadId = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(info.title) + ".${subtitle.lang}.srt"
        val outputPath = getDownloadDir("Subtitles") + "/" + fileName

        enqueueDownload(
            id = downloadId,
            url = info.url,
            title = info.title + " [${subtitle.name}]",
            thumbnail = info.thumbnail,
            quality = subtitle.name,
            format = "SRT",
            outputPath = outputPath,
            type = DownloadType.SUBTITLE,
            formatId = subtitle.lang,
            platform = info.platform.displayName
        )
    }

    // ──────────────────────────────────────────
    // Internal: Enqueue & Execute Download
    // ──────────────────────────────────────────

    private enum class DownloadType { VIDEO, AUDIO, SUBTITLE }

    private data class DownloadParams(
        val url: String, val title: String, val thumbnail: String,
        val quality: String, val format: String, val outputPath: String,
        val type: DownloadType, val formatId: String, val platform: String
    )

    // Remembers each download's request so it can be resumed after a pause.
    private val downloadParams = java.util.concurrent.ConcurrentHashMap<String, DownloadParams>()

    private fun enqueueDownload(
        id: String, url: String, title: String, thumbnail: String,
        quality: String, format: String, outputPath: String,
        type: DownloadType, formatId: String, platform: String
    ) {
        val download = ActiveDownload(
            id = id, url = url, title = title, thumbnail = thumbnail,
            quality = quality, format = format, filePath = outputPath,
            status = DownloadStatus.QUEUED
        )
        downloadParams[id] = DownloadParams(url, title, thumbnail, quality, format, outputPath, type, formatId, platform)

        _downloadsState.update { it.copy(activeDownloads = it.activeDownloads + download) }
        dismissQualitySheet()

        // Start foreground service
        val intent = Intent(ctx, com.viddown.app.service.DownloadService::class.java).apply {
            putExtra("download_id", id)
            putExtra("url", url)
            putExtra("title", title)
            putExtra("format_id", formatId)
            putExtra("output_path", outputPath)
            putExtra("type", type.name)
        }
        ctx.startForegroundService(intent)

        viewModelScope.launch {
            waitForFreeSlot(id)
            val p = downloadParams[id] ?: return@launch
            executeDownload(id, p)
        }
    }

    /**
     * Simple cooperative queue: waits until fewer than
     * Settings → "Max Concurrent Downloads" items are actively downloading,
     * or until this item is cancelled while waiting.
     */
    private suspend fun waitForFreeSlot(id: String) {
        while (true) {
            val state = _downloadsState.value.activeDownloads.find { it.id == id }
                ?: return // removed/cancelled while queued
            if (state.status == DownloadStatus.CANCELLED) return

            val limit = com.viddown.app.manager.AppPreferences.getMaxConcurrent(ctx)
            val running = _downloadsState.value.activeDownloads.count { it.status == DownloadStatus.DOWNLOADING }
            if (running < limit) return
            kotlinx.coroutines.delay(500)
        }
    }

    private suspend fun executeDownload(id: String, p: DownloadParams) {
        val current = _downloadsState.value.activeDownloads.find { it.id == id }
        if (current?.status == DownloadStatus.CANCELLED) return

        updateDownload(id) { it.copy(status = DownloadStatus.DOWNLOADING) }

        val onProgress = { progress: Float, speed: String, eta: String ->
            updateDownload(id) {
                it.copy(progress = progress, speed = speed, eta = eta)
            }
        }

        val success = when (p.type) {
            // processId = download id, so pauseDownload() can cancel just this one.
            DownloadType.VIDEO    -> ytDlp.downloadVideo(p.url, p.formatId, p.outputPath, id, onProgress)
            DownloadType.AUDIO    -> ytDlp.downloadAudio(p.url, p.formatId, p.outputPath, id, onProgress)
            DownloadType.SUBTITLE -> ytDlp.downloadSubtitle(p.url, p.formatId, p.outputPath)
        }

        // If the user paused (rather than a real failure), leave it as PAUSED
        // so the Resume button keeps working — yt-dlp resumes via the
        // partial (.part) file it already wrote to outputPath.
        val wasPaused = _downloadsState.value.activeDownloads.find { it.id == id }?.status == DownloadStatus.PAUSED
        if (!success && wasPaused) return

        val finalStatus = if (success) DownloadStatus.COMPLETED else DownloadStatus.FAILED
        var finalPath = p.outputPath
        if (success) {
            finalPath = moveToCustomFolderIfConfigured(p.outputPath)
        }
        updateDownload(id) { it.copy(status = finalStatus, filePath = finalPath, progress = if (success) 1f else it.progress) }

        if (success) {
            downloadParams.remove(id)
            val file = File(finalPath)
            dao.insert(
                DownloadHistoryEntity(
                    id = id,
                    url = p.url,
                    title = p.title,
                    thumbnail = p.thumbnail,
                    quality = p.quality,
                    format = p.format,
                    filePath = finalPath,
                    fileSize = file.length(),
                    platform = p.platform,
                    status = "COMPLETED"
                )
            )
            // Remove from active after delay
            kotlinx.coroutines.delay(2000)
            removeDownload(id)
        }
    }

    /** Pause an in-progress download. The partial file is kept so Resume can continue it. */
    fun pauseDownload(id: String) {
        ytDlp.cancel(id)
        updateDownload(id) { it.copy(status = DownloadStatus.PAUSED, speed = "", eta = "") }
    }

    /** Resume a paused download — yt-dlp continues from the existing partial file. */
    fun resumeDownload(id: String) {
        val p = downloadParams[id] ?: return
        updateDownload(id) { it.copy(status = DownloadStatus.QUEUED) }
        viewModelScope.launch {
            waitForFreeSlot(id)
            executeDownload(id, p)
        }
    }

    fun cancelDownload(id: String) {
        ytDlp.cancel(id)
        downloadParams.remove(id)
        updateDownload(id) { it.copy(status = DownloadStatus.CANCELLED) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            removeDownload(id)
        }
    }

    // ──────────────────────────────────────────
    // History
    // ──────────────────────────────────────────

    fun deleteHistoryItem(item: DownloadHistoryEntity) {
        viewModelScope.launch {
            dao.delete(item)
            // Optionally delete file
            File(item.filePath).delete()
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.deleteAll() }
    }

    // ──────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────

    private fun updateDownload(id: String, update: (ActiveDownload) -> ActiveDownload) {
        _downloadsState.update { state ->
            state.copy(activeDownloads = state.activeDownloads.map {
                if (it.id == id) update(it) else it
            })
        }
    }

    private fun removeDownload(id: String) {
        _downloadsState.update { it.copy(activeDownloads = it.activeDownloads.filter { d -> d.id != id }) }
    }

    /**
     * If the user picked a custom download folder (Settings → Download
     * Location), copy the just-downloaded file there via SAF and delete the
     * temp copy under the default Downloads/VidDown path. Returns the new
     * path on success, or the original path if no custom folder is set or
     * the copy fails for any reason.
     */
    private fun moveToCustomFolderIfConfigured(outputPath: String): String {
        val treeUri = com.viddown.app.manager.AppPreferences.getDownloadFolderUri(ctx) ?: return outputPath
        return try {
            val srcFile = File(outputPath)
            if (!srcFile.exists()) return outputPath

            val treeDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, treeUri)
                ?: return outputPath
            val mime = when (srcFile.extension.lowercase()) {
                "mp4"  -> "video/mp4"
                "mp3"  -> "audio/mpeg"
                "srt"  -> "application/x-subrip"
                else   -> "application/octet-stream"
            }
            // Remove any pre-existing file with the same name to avoid " (1)" duplicates
            treeDoc.findFile(srcFile.name)?.delete()
            val destDoc = treeDoc.createFile(mime, srcFile.name) ?: return outputPath

            ctx.contentResolver.openOutputStream(destDoc.uri)?.use { out ->
                srcFile.inputStream().use { it.copyTo(out) }
            } ?: return outputPath

            srcFile.delete()
            destDoc.uri.toString()
        } catch (e: Exception) {
            outputPath
        }
    }

    private fun getDownloadDir(subfolder: String): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "VidDown/$subfolder"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
    }
}
