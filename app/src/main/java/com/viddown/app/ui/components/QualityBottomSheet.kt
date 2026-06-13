package com.viddown.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viddown.app.data.*
import com.viddown.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityBottomSheet(
    videoInfo: VideoInfo,
    onDismiss: () -> Unit,
    onVideoDownload: (VideoFormat) -> Unit,
    onAudioDownload: (AudioFormat) -> Unit,
    onSubtitleDownload: (SubtitleInfo) -> Unit,
    onDownloadAllPlaylist: (VideoInfo) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Video", "Audio", "Subtitle")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(40.dp).height(4.dp)
                        .clip(CircleShape)
                        .background(Surface3)
                )
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {

            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        videoInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        color = OnBg
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${videoInfo.platform.displayName} • ${videoInfo.duration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = OnSurface2)
                }
            }

            // Playlist: download all
            if (videoInfo.isPlaylist && videoInfo.playlistCount > 1) {
                Button(
                    onClick = { onDownloadAllPlaylist(videoInfo) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download All (${videoInfo.playlistCount} videos)")
                }
                Spacer(Modifier.height(4.dp))
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Surface1,
                contentColor = RedPrimary,
                divider = { HorizontalDivider(color = Divider) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tab Content
            when (selectedTab) {
                0 -> VideoTab(videoInfo.videoFormats, onVideoDownload)
                1 -> AudioTab(videoInfo.audioFormats, onAudioDownload)
                2 -> SubtitleTab(videoInfo.subtitles, onSubtitleDownload)
            }
        }
    }
}

// ── Video Tab ────────────────────────────────────

@Composable
private fun VideoTab(formats: List<VideoFormat>, onSelect: (VideoFormat) -> Unit) {
    if (formats.isEmpty()) {
        EmptyState("No video formats found")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(formats) { fmt ->
            FormatCard(
                icon = Icons.Rounded.VideoFile,
                label = fmt.displayLabel,
                sublabel = buildString {
                    fmt.fps?.let { append("${it}fps • ") }
                    append(fmt.ext.uppercase())
                    append(" • ${fmt.filesizeDisplay}")
                },
                badgeText = if (fmt.height >= 1080) "HD" else null,
                onClick = { onSelect(fmt) }
            )
        }
    }
}

// ── Audio Tab ────────────────────────────────────

@Composable
private fun AudioTab(formats: List<AudioFormat>, onDownload: (AudioFormat) -> Unit) {
    if (formats.isEmpty()) {
        EmptyState("No audio formats found")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(formats) { index, fmt ->
            FormatCard(
                icon = Icons.Rounded.AudioFile,
                label = fmt.displayLabel,
                sublabel = "${fmt.ext.uppercase()} • ${fmt.filesizeDisplay}",
                badgeText = if (index == 0) "Best" else null,
                onClick = { onDownload(fmt) }
            )
        }
    }
}

// ── Subtitle Tab ─────────────────────────────────

@Composable
private fun SubtitleTab(subtitles: List<SubtitleInfo>, onSelect: (SubtitleInfo) -> Unit) {
    if (subtitles.isEmpty()) {
        EmptyState("No subtitles available")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subtitles) { sub ->
            FormatCard(
                icon = Icons.Rounded.ClosedCaption,
                label = sub.name,
                sublabel = "SRT format • ${sub.lang}",
                onClick = { onSelect(sub) }
            )
        }
    }
}

// ── Format Card ──────────────────────────────────

@Composable
private fun FormatCard(
    icon: ImageVector,
    label: String,
    sublabel: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(RedPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = OnBg)
                    if (badgeText != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(RedPrimary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(badgeText, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }

            Icon(
                Icons.Default.FileDownload,
                null,
                tint = RedPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = OnSurface2, style = MaterialTheme.typography.bodyMedium)
    }
}
