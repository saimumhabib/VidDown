package com.viddown.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.io.File

class EngineException(message: String) : Exception(message)

data class Progress(val phase: Phase, val percent: Float?, val speedBps: Double?, val etaSec: Long?)

/** Talks to viddown_runner.py (yt-dlp) through a child process; one JSON object per stdout line. */
class YtDlpEngine(private val runtime: PythonRuntime) {

    private class ProcResult(val exitCode: Int, val stderrTail: String) {
        fun describe(): String {
            val tail = stderrTail.trim().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            return "The download engine stopped unexpectedly (code $exitCode). $tail".trim()
        }
    }

    suspend fun fetchInfo(url: String): VideoInfo {
        runtime.ensureInstalled()
        val req = JSONObject().put("cmd", "info").put("url", url)
        runtime.cookiesPath?.let { req.put("cookies", it) }
        var info: VideoInfo? = null
        var error: String? = null
        val result = run(req) { ev ->
            when (ev.optString("type")) {
                "info" -> info = parseInfo(url, ev)
                "error" -> error = ev.optString("message")
            }
        }
        return info ?: throw EngineException(error ?: result.describe())
    }

    suspend fun download(
        url: String,
        option: QualityOption,
        outDir: File,
        onProgress: (Progress) -> Unit,
    ): File {
        runtime.ensureInstalled()
        val req = JSONObject()
            .put("cmd", "download")
            .put("url", url)
            .put("format", option.selector)
            .put("outdir", outDir.path)
            .put("ffmpeg", runtime.ffmpegPath)
        runtime.cookiesPath?.let { req.put("cookies", it) }
        var path: String? = null
        var error: String? = null
        val result = run(req) { ev ->
            when (ev.optString("type")) {
                "progress" -> onProgress(
                    Progress(
                        Phase.Downloading,
                        if (ev.has("percent")) ev.getDouble("percent").toFloat() else null,
                        if (ev.has("speed")) ev.getDouble("speed") else null,
                        if (ev.has("eta")) ev.getLong("eta") else null,
                    )
                )
                "merging" -> onProgress(Progress(Phase.Merging, null, null, null))
                "done" -> path = ev.optString("path")
                "error" -> error = ev.optString("message")
            }
        }
        val p = path
        if (p.isNullOrBlank()) throw EngineException(error ?: result.describe())
        return File(p)
    }

    private fun parseInfo(url: String, ev: JSONObject): VideoInfo {
        val arr = ev.getJSONArray("options")
        val options = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QualityOption(
                id = o.getString("id"),
                label = o.getString("label"),
                detail = o.optString("detail", ""),
                selector = o.getString("selector"),
                isAudio = o.optBoolean("audio", false),
            )
        }
        return VideoInfo(
            url = url,
            title = ev.optString("title", "Video"),
            uploader = if (ev.has("uploader")) ev.getString("uploader") else null,
            durationSec = if (ev.has("duration")) ev.getLong("duration") else null,
            thumbnail = if (ev.has("thumbnail")) ev.getString("thumbnail") else null,
            site = if (ev.has("site")) ev.getString("site") else null,
            options = options,
        )
    }

    private suspend fun run(req: JSONObject, onEvent: (JSONObject) -> Unit): ProcResult = coroutineScope {
        val process = runtime.processBuilder(listOf(runtime.runner.path, req.toString())).start()
        process.outputStream.close()
        val reader = async(Dispatchers.IO) {
            val err = StringBuilder()
            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        synchronized(err) { if (err.length < 6000) err.appendLine(line) }
                    }
                }
            }.apply { isDaemon = true; start() }
            process.inputStream.bufferedReader().forEachLine { line ->
                if (line.startsWith("{")) {
                    val ev = runCatching { JSONObject(line) }.getOrNull()
                    if (ev != null) runCatching { onEvent(ev) }
                }
            }
            val code = process.waitFor()
            errThread.join(1500)
            ProcResult(code, synchronized(err) { err.toString() }.takeLast(1500))
        }
        try {
            reader.await()
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
