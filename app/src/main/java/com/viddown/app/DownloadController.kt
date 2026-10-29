package com.viddown.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** App-wide state holder: lives as long as the process, so downloads survive screen rotation. */
class DownloadController(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val runtime = PythonRuntime(app)
    private val engine = YtDlpEngine(runtime)

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _dl = MutableStateFlow<DownloadState>(DownloadState.None)
    val dl: StateFlow<DownloadState> = _dl.asStateFlow()

    private val _shared = MutableStateFlow<String?>(null)
    val sharedLink: StateFlow<String?> = _shared.asStateFlow()

    private var fetchJob: Job? = null
    private var dlJob: Job? = null

    private val workRoot: File get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads")

    init {
        // Leftovers from a previous crash / killed process.
        scope.launch { runCatching { workRoot.deleteRecursively() } }
    }

    fun setSharedLink(text: String) { _shared.value = text }
    fun consumeShared() { _shared.value = null }

    fun fetch(raw: String) {
        val url = URL_RE.find(raw)?.value?.trimEnd(')', ']', '.', ',', ';')
        if (url == null) {
            _ui.value = UiState.Failed("That doesn't look like a link. Paste a video URL starting with http:// or https://")
            return
        }
        fetchJob?.cancel()
        _ui.value = UiState.Fetching(firstRun = !runtime.isInstalled())
        fetchJob = scope.launch {
            try {
                _ui.value = UiState.Ready(engine.fetchInfo(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = UiState.Failed(e.message ?: "Couldn't read this link")
            }
        }
    }

    fun startDownload(option: QualityOption) {
        val info = (_ui.value as? UiState.Ready)?.info ?: return
        if (dlJob?.isActive == true) return
        _dl.value = DownloadState.Running(info.title, Phase.Starting, null, null, null)
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        dlJob = scope.launch {
            val jobDir = File(workRoot, "job-${System.currentTimeMillis()}")
            try {
                val file = engine.download(info.url, option, jobDir) { p ->
                    _dl.value = DownloadState.Running(info.title, p.phase, p.percent, p.speedBps, p.etaSec)
                }
                _dl.value = DownloadState.Running(info.title, Phase.Saving, null, null, null)
                val saved = MediaSaver.saveToDownloads(app, file)
                _dl.value = DownloadState.Done(info.title, saved.name, saved.uri.toString(), saved.mime)
            } catch (e: CancellationException) {
                _dl.value = DownloadState.None
                throw e
            } catch (e: Exception) {
                _dl.value = DownloadState.Failed(e.message ?: "Download failed")
            } finally {
                runCatching { jobDir.deleteRecursively() }
            }
        }
    }

    fun cancelDownload() { dlJob?.cancel() }

    fun dismissResult() {
        if (_dl.value !is DownloadState.Running) _dl.value = DownloadState.None
    }

    companion object {
        private val URL_RE = Regex("""https?://[^\s]+""")
    }
}
