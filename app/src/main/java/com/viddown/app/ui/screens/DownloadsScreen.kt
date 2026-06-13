package com.viddown.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.viddown.app.data.*
import com.viddown.app.ui.theme.*
import com.viddown.app.viewmodel.DownloadViewModel

@Composable
fun DownloadsScreen(viewModel: DownloadViewModel) {
    val state by viewModel.downloadsState.collectAsState()
    val downloads = state.activeDownloads

    Column(Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Downloads", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = OnBg)
            Spacer(Modifier.weight(1f))
            if (downloads.isNotEmpty()) {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(RedPrimary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${downloads.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (downloads.isEmpty()) {
            EmptyDownloads()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItemCard(
                        download = download,
                        onCancel = { viewModel.cancelDownload(download.id) },
                        onPause = { viewModel.pauseDownload(download.id) },
                        onResume = { viewModel.resumeDownload(download.id) }
                    )
                }
            }
        }
    }
}

// ── Download Item Card ───────────────────────────

@Composable
fun DownloadItemCard(download: ActiveDownload, onCancel: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Surface2)
                ) {
                    if (download.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = download.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.VideoFile, null,
                            tint = OnSurface2,
                            modifier = Modifier.align(Alignment.Center).size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        download.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(download.status)
                        Text("•", color = OnSurface2, fontSize = 11.sp)
                        Text(download.quality, fontSize = 11.sp, color = OnSurface2)
                    }
                }

                // Pause Button
                if (download.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Pause, null, tint = OnSurface2, modifier = Modifier.size(20.dp))
                    }
                }
                // Resume Button
                if (download.status == DownloadStatus.PAUSED) {
                    IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                // Cancel Button
                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.PAUSED) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Cancel, null, tint = OnSurface2, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress Bar
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    Column {
                        LinearProgressIndicator(
                            progress = { download.progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = RedPrimary,
                            trackColor = Surface3
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(download.progress * 100).toInt()}%", fontSize = 11.sp, color = OnSurface2)
                            if (download.speed.isNotBlank()) {
                                Text("${download.speed} • ETA ${download.eta}", fontSize = 11.sp, color = OnSurface2)
                            }
                        }
                    }
                }
                DownloadStatus.QUEUED, DownloadStatus.ANALYZING -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = RedPrimary.copy(alpha = 0.6f),
                        trackColor = Surface3
                    )
                }
                DownloadStatus.COMPLETED -> {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = GreenSuccess,
                        trackColor = Surface3
                    )
                }
                DownloadStatus.FAILED -> {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = RedError,
                        trackColor = Surface3
                    )
                }
                else -> {}
            }
        }
    }
}

// ── Status Chip ──────────────────────────────────

@Composable
private fun StatusChip(status: DownloadStatus) {
    val (label, color) = when (status) {
        DownloadStatus.QUEUED      -> Pair("Queued", OnSurface2)
        DownloadStatus.ANALYZING   -> Pair("Analyzing", YellowWarning)
        DownloadStatus.DOWNLOADING -> Pair("Downloading", RedPrimary)
        DownloadStatus.PAUSED      -> Pair("Paused", YellowWarning)
        DownloadStatus.COMPLETED   -> Pair("Done", GreenSuccess)
        DownloadStatus.FAILED      -> Pair("Failed", RedError)
        DownloadStatus.CANCELLED   -> Pair("Cancelled", OnSurface2)
    }
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Empty State ──────────────────────────────────

@Composable
private fun EmptyDownloads() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.DownloadDone,
                null,
                tint = Surface3,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("No active downloads", color = OnSurface2, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Paste a video URL to get started", color = OnSurface2.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
