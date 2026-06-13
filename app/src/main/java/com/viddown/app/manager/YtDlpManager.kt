package com.viddown.app.manager

import android.content.Context
import android.util.Log
import com.viddown.app.data.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class YtDlpManager(private val context: Context) {

    private val TAG = "YtDlpManager"

    // Active process IDs — used for cancellation
    private val activeProcessIds = ConcurrentHashMap.newKeySet<String>()

    // Track initialization state
    private val initMutex = Mutex()
    @Volatile
    private var initStarted = false
    @Volatile
    private var isReady = false

    // ─────────────────────────────────────────
    // Init (called from ViewModel & App)
    // ─────────────────────────────────────────

    suspend fun initialize() {
        initMutex.withLock {
            if (isReady) {
                Log.d(TAG, "✅ Already initialized")
                return@withLock
            }
            
            if (initStarted) {
                Log.d(TAG, "⏳ Initialization already in progress...")
                return@withLock
            }
            
            initStarted = true
        }

        Log.d(TAG, "🔄 Starting YtDlpManager initialization...")
        
        val ready = EngineManager.ensureInitialized(context)
        
        if (ready) {
            isReady = true
            Log.d(TAG, "✅ YtDlpManager ready!")
        } else {
            isReady = false
            val error = EngineManager.lastError ?: "Unknown error"
            Log.e(TAG, "❌ YtDlpManager initialization failed:\n$error")
        }
    }

    /**
     * Waits for initialization to complete (blocks if needed)
     * Returns true if ready, false if failed
     */
    private suspend fun waitForInitialization(): Boolean {
        // If not started, kick it off (this also covers the case where
        // a previous attempt failed — initialize() retries via EngineManager).
        if (!initStarted || (!isReady && !initMutex.isLocked)) {
            initialize()
        }

        // Wait for the in-flight initialize() (possibly started by another
        // coroutine) to finish. Bug fix: previously this loop also required
        // `!initStarted`, which is always false here, so the loop never ran
        // and a concurrent caller would immediately get "Engine not ready"
        // even while initialization was still in progress.
        var attempts = 0
        while (!isReady && attempts < 100) {
            kotlinx.coroutines.delay(100)
            attempts++
        }

        return isReady
    }

    /**
     * Returns null if the engine is ready, or a friendly error message if not.
     */
    private suspend fun engineErrorOrNull(): String? {
        val ready = waitForInitialization()
        if (ready) return null
        
        val reason = EngineManager.lastError ?: "Unknown error"
        return "Engine not ready: $reason"
    }

    // ─────────────────────────────────────────
    // Fetch Video Info
    // ─────────────────────────────────────────

    suspend fun getVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        engineErrorOrNull()?.let { 
            Log.e(TAG, "❌ getVideoInfo blocked: $it")
            return@withContext Result.failure(Exception(it)) 
        }
        
        try {
            Log.d(TAG, "📥 Analyzing URL: $url")

            // Playlist detection: if the URL points at a playlist/channel,
            // fetch the flat entry list separately (fast, no per-video
            // analysis), then analyze the FIRST entry for the preview/format
            // list. "Download All" can then queue each entry with format "best".
            var playlistEntries: List<PlaylistEntry> = emptyList()
            var effectiveUrl = url
            if (looksLikePlaylist(url)) {
                playlistEntries = fetchPlaylistEntries(url)
                if (playlistEntries.isNotEmpty()) {
                    effectiveUrl = playlistEntries.first().url
                }
            }

            val request = YoutubeDLRequest(effectiveUrl).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                // "android" alone only returns a limited (often 360p) progressive
                // format set for YouTube. Adding "web" pulls in the full DASH
                // format list (144p up to 4K/2160p) while "android" keeps the
                // initial request fast and provides a fallback if "web" is
                // throttled/blocked.
                addOption("--extractor-args", "youtube:player_client=android,web")
                AppCookieManager.getCookiesFilePathForUrl(context, url)?.let { addOption("--cookies", it) }
                addOption("--socket-timeout", "15")
            }

            val response = YoutubeDL.getInstance().execute(request)

            // --dump-json returns one JSON object per line; take the first valid one
            val jsonLine = response.out
                .lines()
                .firstOrNull { it.trimStart().startsWith("{") }
                ?: return@withContext Result.failure(Exception("No video data received"))

            val info = parseVideoInfo(effectiveUrl, jsonLine).let {
                if (playlistEntries.isNotEmpty())
                    it.copy(isPlaylist = true, playlistCount = playlistEntries.size, playlistEntries = playlistEntries)
                else it
            }
            Log.d(TAG, "✅ Successfully analyzed: ${info.title}")
            Result.success(info)

        } catch (e: Exception) {
            Log.e(TAG, "❌ getVideoInfo error: ${e.message}", e)
            val msg = when {
                e is kotlin.UninitializedPropertyAccessException -> {
                    "Engine not properly initialized: ${EngineManager.lastError ?: e.message}"
                }
                e.message?.contains("instance not initialized", true) == true -> {
                    "Instance not initialized. This usually means yt-dlp/ffmpeg/aria2c binaries failed to extract. Check storage space and permissions."
                }
                else -> friendlyError(e.message)
            }
            Result.failure(Exception(msg))
        }
    }

    // ─────────────────────────────────────────
    // Download Video
    // ─────────────────────────────────────────

    suspend fun downloadVideo(
        url: String,
        formatId: String,
        outputPath: String,
        processId: String = UUID.randomUUID().toString(),
        onProgress: (Float, String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        engineErrorOrNull()?.let { 
            Log.e(TAG, "❌ downloadVideo blocked: $it")
            return@withContext false 
        }
        
        activeProcessIds.add(processId)
        try {
            Log.d(TAG, "⬇️  Starting video download: $url (format: $formatId)")
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-warnings")
                addOption("-f", if (formatId.any { it in "*[]<>" }) "$formatId/best" else "$formatId+bestaudio/best/$formatId")
                addOption("--merge-output-format", "mp4")
                addOption("--extractor-args", "youtube:player_client=android,web")
                AppCookieManager.getCookiesFilePathForUrl(context, url)?.let { addOption("--cookies", it) }
                addOption("--no-playlist")
                addOption("-o", outputPath)
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                onProgress(
                    progress / 100f,
                    "",
                    formatEta(eta)
                )
            }
            Log.d(TAG, "✅ Video download completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ downloadVideo error: ${e.message}", e)
            if (e is kotlin.UninitializedPropertyAccessException || 
                (e.message?.contains("instance not initialized", true) == true)) {
                Log.e(TAG, "⚠️  Engine init issue during download: ${EngineManager.lastError}")
            }
            false
        } finally {
            activeProcessIds.remove(processId)
        }
    }

    // ─────────────────────────────────────────
    // Download Audio (MP3)
    // ─────────────────────────────────────────

    suspend fun downloadAudio(
        url: String,
        formatId: String,
        outputPath: String,
        processId: String = UUID.randomUUID().toString(),
        onProgress: (Float, String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        engineErrorOrNull()?.let { 
            Log.e(TAG, "❌ downloadAudio blocked: $it")
            return@withContext false 
        }
        
        activeProcessIds.add(processId)
        try {
            Log.d(TAG, "🎵 Starting audio download: $url (format: $formatId)")
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-warnings")
                addOption("-f", "$formatId/bestaudio/best")
                addOption("--extract-audio")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
                addOption("--extractor-args", "youtube:player_client=android,web")
                AppCookieManager.getCookiesFilePathForUrl(context, url)?.let { addOption("--cookies", it) }
                addOption("--no-playlist")
                addOption("-o", outputPath)
            }

            YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                onProgress(progress / 100f, "", formatEta(eta))
            }
            Log.d(TAG, "✅ Audio download completed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ downloadAudio error: ${e.message}", e)
            if (e is kotlin.UninitializedPropertyAccessException || 
                (e.message?.contains("instance not initialized", true) == true)) {
                Log.e(TAG, "⚠️  Engine init issue during audio download: ${EngineManager.lastError}")
            }
            false
        } finally {
            activeProcessIds.remove(processId)
        }
    }

    // ─────────────────────────────────────────
    // Download Subtitle (SRT)
    // ─────────────────────────────────────────

    suspend fun downloadSubtitle(url: String, lang: String, outputPath: String): Boolean =
        withContext(Dispatchers.IO) {
            engineErrorOrNull()?.let { 
                Log.e(TAG, "❌ downloadSubtitle blocked: $it")
                return@withContext false 
            }
            
            try {
                Log.d(TAG, "📝 Downloading subtitle: $lang from $url")
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-warnings")
                    addOption("--write-subs")
                    addOption("--write-auto-subs")
                    addOption("--sub-langs", lang)
                    addOption("--sub-format", "srt")
                    addOption("--skip-download")
                    addOption("--convert-subs", "srt")
                    addOption("-o", outputPath)
                }
                YoutubeDL.getInstance().execute(request)
                Log.d(TAG, "✅ Subtitle download completed")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ downloadSubtitle error: ${e.message}", e)
                if (e is kotlin.UninitializedPropertyAccessException || 
                    (e.message?.contains("instance not initialized", true) == true)) {
                    Log.e(TAG, "⚠️  Engine init issue during subtitle download: ${EngineManager.lastError}")
                }
                false
            }
        }

    // ─────────────────────────────────────────
    // Cancel All Active Downloads
    // ─────────────────────────────────────────

    /** Cancel a single in-progress download by its id (used for per-item pause/cancel). */
    fun cancel(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            Log.w(TAG, "Cancel failed for $processId: ${e.message}")
        }
        activeProcessIds.remove(processId)
    }

    /** Cancel every in-progress download (used when the app/service is torn down). */
    fun cancel() {
        Log.d(TAG, "🛑 Cancelling all downloads...")
        activeProcessIds.toList().forEach { processId ->
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (e: Exception) {
                Log.w(TAG, "Cancel failed for $processId: ${e.message}")
            }
        }
        activeProcessIds.clear()
    }

    // ─────────────────────────────────────────
    // JSON Parser
    // ─────────────────────────────────────────

    private fun parseVideoInfo(url: String, json: String): VideoInfo {
        val obj = JSONObject(json)
        val formatsArray = obj.optJSONArray("formats")
        val videoFormats = mutableListOf<VideoFormat>()
        val audioFormats = mutableListOf<AudioFormat>()

        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                val fmt = formatsArray.getJSONObject(i)
                val vcodec  = fmt.optString("vcodec", "none")
                val acodec  = fmt.optString("acodec", "none")
                val height  = fmt.optInt("height", 0)
                val ext     = fmt.optString("ext", "mp4")
                val formatId = fmt.optString("format_id", "")
                val filesize = if (fmt.has("filesize")) fmt.optLong("filesize", 0) else null
                val tbr     = fmt.optDouble("tbr", 0.0)

                when {
                    vcodec == "none" && acodec != "none" -> {
                        audioFormats.add(
                            AudioFormat(formatId, ext, fmt.optDouble("abr", tbr), filesize)
                        )
                    }
                    vcodec != "none" && height > 0 -> {
                        videoFormats.add(
                            VideoFormat(
                                formatId, ext, "${height}p", height,
                                fmt.optInt("fps", 0).takeIf { it > 0 },
                                filesize, vcodec, acodec, tbr
                            )
                        )
                    }
                }
            }
        }

        val dedupedVideo = videoFormats
            .groupBy { it.height }
            .map { (_, fmts) -> fmts.maxByOrNull { it.tbr }!! }
            .sortedByDescending { it.height }
            .filter { it.height >= 144 }

        val bestAudio = audioFormats
            .filter { it.abr > 0 }
            .distinctBy { it.abr.toInt() }
            .sortedByDescending { it.abr }

        val subtitles = mutableListOf<SubtitleInfo>()
        obj.optJSONObject("subtitles")?.keys()?.forEach { lang ->
            subtitles.add(SubtitleInfo(lang, getLangName(lang)))
        }

        val durationSecs = obj.optInt("duration", 0)
        val durationStr = obj.optString("duration_string", "").ifBlank {
            if (durationSecs > 0) formatDuration(durationSecs) else "--:--"
        }

        return VideoInfo(
            id          = obj.optString("id", ""),
            url         = url,
            title       = obj.optString("title", "Unknown Title"),
            thumbnail   = obj.optString("thumbnail", ""),
            duration    = durationStr,
            uploader    = obj.optString("uploader", obj.optString("channel", "")),
            viewCount   = obj.optLong("view_count", 0),
            description = obj.optString("description", "").take(300),
            platform    = Platform.fromUrl(url),
            videoFormats = dedupedVideo,
            audioFormats = bestAudio,
            subtitles   = subtitles
        )
    }

    // ─────────────────────────────────────────
    // Playlist Support
    // ─────────────────────────────────────────

    private fun looksLikePlaylist(url: String): Boolean =
        url.contains("list=", true) || url.contains("/playlist", true) ||
        url.contains("/sets/", true) // SoundCloud sets

    /**
     * Quickly fetches the list of videos in a playlist (id/title/url only,
     * no per-video format analysis) using `--flat-playlist`. Capped at 50
     * entries to keep "Download All" manageable on mobile.
     */
    private fun fetchPlaylistEntries(url: String): List<PlaylistEntry> {
        return try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
                addOption("--no-warnings")
                addOption("--playlist-end", "50")
                AppCookieManager.getCookiesFilePathForUrl(context, url)?.let { addOption("--cookies", it) }
                addOption("--socket-timeout", "15")
            }
            val response = YoutubeDL.getInstance().execute(request)
            response.out.lines()
                .filter { it.trimStart().startsWith("{") }
                .mapNotNull { line ->
                    try {
                        val o = JSONObject(line)
                        val id = o.optString("id", "")
                        if (id.isBlank()) return@mapNotNull null
                        val entryUrl = o.optString("url", "").ifBlank {
                            o.optString("webpage_url", "").ifBlank {
                                "https://www.youtube.com/watch?v=$id"
                            }
                        }
                        PlaylistEntry(
                            id = id,
                            title = o.optString("title", "Untitled"),
                            url = entryUrl,
                            thumbnail = o.optString("thumbnail", "")
                        )
                    } catch (_: Exception) { null }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Playlist entry fetch failed: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────

    private fun formatEta(etaSecs: Long): String {
        if (etaSecs <= 0) return ""
        val h = etaSecs / 3600
        val m = (etaSecs % 3600) / 60
        val s = etaSecs % 60
        return when {
            h > 0  -> "${h}h ${m}m"
            m > 0  -> "${m}m ${s}s"
            else   -> "${s}s"
        }
    }

    private fun formatDuration(secs: Int): String {
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    private fun getLangName(code: String): String = when (code.lowercase()) {
        "en", "en-us", "en-orig" -> "English"
        "bn"  -> "বাংলা";  "hi"  -> "हिन्दी"
        "ar"  -> "Arabic"; "zh"  -> "Chinese"
        "fr"  -> "French"; "de"  -> "German"
        "es"  -> "Spanish";"pt"  -> "Portuguese"
        "ru"  -> "Russian";"ja"  -> "Japanese"
        "ko"  -> "Korean"; "tr"  -> "Turkish"
        "id"  -> "Indonesian"
        else  -> code.uppercase()
    }

    private fun friendlyError(msg: String?): String {
        if (msg == null) return "Unknown error"
        return when {
            "Unable to extract" in msg || "Unsupported URL" in msg ->
                "URL not supported. Try a different link."
            "HTTP Error 403" in msg -> "Access denied (403). Try again later."
            "HTTP Error 429" in msg -> "Rate limited. Please wait a moment."
            "Sign in" in msg || "login" in msg ->
                "This video requires login."
            "Private video" in msg -> "This video is private."
            "not available" in msg -> "Video not available in your region."
            else -> msg.take(120)
        }
    }
}
