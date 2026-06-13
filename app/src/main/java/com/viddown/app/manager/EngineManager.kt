package com.viddown.app.manager

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles initialization of YoutubeDL / FFmpeg / Aria2c.
 *
 * init() does file extraction (python, ffmpeg, aria2c binaries from assets),
 * which can fail. We must NOT silently swallow that failure — otherwise every
 * later call to YoutubeDL.getInstance().execute(...) throws
 * "instance not initialized" with no useful context.
 */
object EngineManager {

    private const val TAG = "EngineManager"

    @Volatile
    private var initialized = false

    @Volatile
    var lastError: String? = null
        private set

    private val mutex = Mutex()

    /**
     * Ensures the engine is initialized. Safe to call repeatedly —
     * if a previous attempt failed, it will retry.
     * Returns true if ready to use, false otherwise (check [lastError]).
     */
    suspend fun ensureInitialized(context: Context): Boolean {
        if (initialized) return true

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (initialized) return@withLock true

                val appContext = context.applicationContext

                try {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                    Aria2c.getInstance().init(appContext)

                    initialized = true
                    lastError = null
                    Log.d(TAG, "Engine initialized successfully ✅")
                    true
                } catch (e: Throwable) {
                    // Capture the REAL reason (could be: corrupt binaries,
                    // unsupported ABI, low storage, permission issues, etc.)
                    val sb = StringBuilder()
                    sb.append("${e.javaClass.simpleName}: ${e.message}\n")
                    try {
                        val abis = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
                        sb.append("Supported ABIs: $abis\n")
                    } catch (_: Throwable) { /* ignore */ }
                    try {
                        val files = appContext.filesDir?.listFiles()?.map { it.name } ?: emptyList()
                        sb.append("App files: ${files.joinToString(", ")}\n")
                    } catch (_: Throwable) { /* ignore */ }
                    sb.append("Possible causes: corrupt/unsupported native binaries, low storage, or permission issues.\n")
                    sb.append("Check logcat for full stacktrace and ensure you run on a matching ABI (arm64-v8a / armeabi-v7a) or a compatible emulator.\n")

                    val message = sb.toString().trimEnd()
                    lastError = message
                    try {
                        // Best-effort: persist the error if user enabled saving
                        LogFileManager.appendLog(appContext, message)
                    } catch (_: Throwable) { }
                    Log.e(TAG, "Engine init failed: $message", e)
                    false
                }
            }
        }
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Checks for and downloads a newer yt-dlp release (YouTube frequently
     * breaks the old extractor, so this lets users self-update without an
     * app update). Returns a human-readable status string.
     */
    suspend fun updateYtDlp(context: Context): String = withContext(Dispatchers.IO) {
        try {
            if (!initialized) ensureInitialized(context)
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
            when (status) {
                com.yausername.youtubedl_android.YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated successfully ✅"
                com.yausername.youtubedl_android.YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date ✅"
                else -> "Update finished: $status"
            }
        } catch (e: Exception) {
            "Update failed: ${e.message}"
        }
    }
}
