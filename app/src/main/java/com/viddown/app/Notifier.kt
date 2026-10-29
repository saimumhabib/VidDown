package com.viddown.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CH_PROGRESS = "progress"
    private const val CH_RESULT = "result"
    const val ID_PROGRESS = 1
    const val ID_RESULT = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(ctx: Context, st: DownloadState.Running?): Notification {
        val cancel = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(st?.title ?: "VidDown")
            .setContentText(if (st != null) phaseLabel(st.phase) else "Starting…")
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", cancel)
        val pct = st?.percent
        if (st?.phase == Phase.Downloading && pct != null) b.setProgress(100, pct.toInt(), false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    fun done(ctx: Context, st: DownloadState.Done) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(st.uri), st.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pi = PendingIntent.getActivity(
            ctx, 2, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(st.fileName)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_RESULT, n)
    }

    fun failed(ctx: Context, st: DownloadState.Failed) {
        val n = NotificationCompat.Builder(ctx, CH_RESULT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(st.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(st.message))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_RESULT, n)
    }

    fun update(ctx: Context, st: DownloadState.Running) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID_PROGRESS, progress(ctx, st))
    }
}
