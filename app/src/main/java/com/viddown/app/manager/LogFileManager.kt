package com.viddown.app.manager

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogFileManager {
    private const val PREFS = "viddown_prefs"
    private const val KEY_SAVE_LOG = "save_error_log"
    private const val LOG_NAME = "error_log.txt"

    private fun logFile(context: Context): File = File(context.filesDir, LOG_NAME)

    fun appendLog(context: Context, text: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_SAVE_LOG, false)) return

            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val entry = "[${df.format(Date())}] $text\n"
            logFile(context).appendText(entry)
        } catch (_: Throwable) { /* best-effort only */ }
    }

    fun readLog(context: Context): String {
        return try {
            val f = logFile(context)
            if (!f.exists()) "" else f.readText()
        } catch (e: Throwable) {
            "Unable to read log: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            val f = logFile(context)
            if (f.exists()) f.writeText("")
        } catch (_: Throwable) { }
    }

    fun setSaveEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SAVE_LOG, enabled).apply()
    }

    fun isSaveEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SAVE_LOG, false)
    }
}
