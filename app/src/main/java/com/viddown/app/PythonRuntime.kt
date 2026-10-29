package com.viddown.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Prepares and launches the bundled CPython 3.14 (arm64-v8a).
 *
 * Layout under filesDir/python:
 *   lib/python3.14/     <- python-lib-3.14-arm64-v8a.zip (stdlib + lib-dynload)
 *   site-packages/      <- ytdlp-site-packages.zip (yt-dlp, requests, certifi, ...)
 *   viddown_runner.py   <- our wrapper around yt-dlp
 */
class PythonRuntime(private val ctx: Context) {

    private val root = File(ctx.filesDir, "python")
    private val stdlib = File(root, "lib/python3.14")
    private val site = File(root, "site-packages")
    private val stamp = File(root, ".stamp")
    private val mutex = Mutex()

    private val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
    private val launcher = File(nativeDir, "libviddown_python.so")
    private val ffmpeg = File(nativeDir, "libffmpeg.so")
    val runner = File(root, "viddown_runner.py")

    private fun currentStamp(): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()

    fun isInstalled(): Boolean =
        stamp.exists() && runCatching { stamp.readText() == currentStamp() }.getOrDefault(false)

    /** Unpacks python + yt-dlp on first run (and again after every app update). */
    suspend fun ensureInstalled() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isInstalled()) return@withLock
            if (!launcher.exists()) {
                throw IOException("Engine files are missing (${launcher.name}). Reinstall the app.")
            }
            root.deleteRecursively()
            stdlib.mkdirs()
            site.mkdirs()
            ctx.assets.open("python-lib-3.14-arm64-v8a.zip").use { unzip(it, stdlib) }
            ctx.assets.open("ytdlp-site-packages.zip").use { unzip(it, site) }
            ctx.assets.open("viddown_runner.py").use { input ->
                runner.outputStream().use { input.copyTo(it) }
            }
            stamp.writeText(currentStamp())
        }
    }

    fun processBuilder(args: List<String>): ProcessBuilder {
        val pb = ProcessBuilder(listOf(launcher.path) + args)
        pb.directory(ctx.filesDir)
        pb.environment().apply {
            put("PYTHONHOME", root.path)
            put("PYTHONPATH", site.path)
            put("LD_LIBRARY_PATH", nativeDir.path) // libpython3.14.so, libssl_python.so, ...
            put("HOME", ctx.filesDir.path)
            put("TMPDIR", ctx.cacheDir.path)
            put("PYTHONUNBUFFERED", "1")
            put("PYTHONUTF8", "1")
            put("PYTHONIOENCODING", "utf-8")
            put("SSL_CERT_FILE", File(site, "certifi/cacert.pem").path)
        }
        return pb
    }

    val ffmpegPath: String get() = ffmpeg.path

    /** cookies.txt written by Settings > Cookie import, or null if nothing was imported or "Use cookies" is off. */
    val cookiesPath: String? get() = if (CookieStore.isEnabled(ctx)) CookieStore.path(ctx) else null

    private fun unzip(input: InputStream, dest: File) {
        val base = dest.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(base)) throw IOException("Bad zip entry: ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
