package com.viddown.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the process alive (and shows progress) while a download runs in the background. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as VidDownApp).controller
        if (intent?.action == ACTION_CANCEL) controller.cancelDownload()

        startForeground(
            Notifier.ID_PROGRESS,
            Notifier.progress(this, controller.dl.value as? DownloadState.Running),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (watcher == null) {
            watcher = scope.launch {
                controller.dl.collect { st ->
                    when (st) {
                        is DownloadState.Running -> Notifier.update(this@DownloadService, st)
                        is DownloadState.Done -> { Notifier.done(this@DownloadService, st); finish() }
                        is DownloadState.Failed -> { Notifier.failed(this@DownloadService, st); finish() }
                        DownloadState.None -> finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.viddown.app.CANCEL"
    }
}
