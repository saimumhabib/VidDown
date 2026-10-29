package com.viddown.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

object MediaSaver {
    data class Saved(val uri: Uri, val name: String, val mime: String)

    /** Copies [file] into Downloads/VidDown using MediaStore (no storage permission needed). */
    fun saveToDownloads(ctx: Context, file: File): Saved {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VidDown")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create a file in Downloads")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open the output file")
            out.use { o -> file.inputStream().use { it.copyTo(o, 1 shl 16) } }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(uri, file.name, mime)
    }
}
