package com.viddown.app

import java.util.Locale

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSpeed(bytesPerSec: Double): String {
    val kb = bytesPerSec / 1024.0
    return if (kb < 1024) String.format(Locale.US, "%.0f KB/s", kb)
    else String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
}

fun phaseLabel(phase: Phase): String = when (phase) {
    Phase.Starting -> "Starting…"
    Phase.Downloading -> "Downloading"
    Phase.Merging -> "Merging video & audio…"
    Phase.Saving -> "Saving to Downloads…"
}

fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
