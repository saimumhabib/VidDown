package com.viddown.app.manager

import android.content.Context
import android.net.Uri

/**
 * Stores user preferences such as a custom download folder (chosen via the
 * Storage Access Framework folder picker in Settings).
 *
 * If no custom folder is set, downloads continue to go to the public
 * Downloads/VidDown/<Video|Music|Subtitles> folders as before.
 */
object AppPreferences {
    private const val PREFS = "viddown_prefs"
    private const val KEY_MAX_CONCURRENT = "max_concurrent_downloads"
    private const val KEY_DOWNLOAD_URI = "download_folder_uri"

    fun getMaxConcurrent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MAX_CONCURRENT, 2)

    fun setMaxConcurrent(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MAX_CONCURRENT, value).apply()
    }


    fun getDownloadFolderUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOAD_URI, null) ?: return null
        return try { Uri.parse(raw) } catch (_: Exception) { null }
    }

    fun setDownloadFolderUri(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (uri == null) {
            prefs.edit().remove(KEY_DOWNLOAD_URI).apply()
        } else {
            prefs.edit().putString(KEY_DOWNLOAD_URI, uri.toString()).apply()
        }
    }

    /** Human-readable summary of the current download folder, for display in Settings. */
    fun getDownloadFolderLabel(context: Context): String {
        val uri = getDownloadFolderUri(context) ?: return "Downloads/VidDown (default)"
        return try {
            val path = uri.path ?: uri.toString()
            // content://.../tree/primary:Movies/VidDown -> "Movies/VidDown"
            path.substringAfterLast(':').ifBlank { "Custom folder" }
        } catch (_: Exception) {
            "Custom folder"
        }
    }
}
