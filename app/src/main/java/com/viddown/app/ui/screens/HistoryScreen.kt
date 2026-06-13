package com.viddown.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.viddown.app.data.DownloadHistoryEntity
import com.viddown.app.data.formatFileSize
import com.viddown.app.ui.theme.*
import com.viddown.app.viewmodel.DownloadViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: DownloadViewModel) {
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = OnBg)
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = OnSurface2)
                }
            }
        }

        if (history.isEmpty()) {
            EmptyHistory()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onDelete = { viewModel.deleteHistoryItem(item) }
                    )
                }
            }
        }
    }

    // Clear All Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("Clear History", color = OnBg) },
            text = { Text("Delete all download history? Files on storage will also be removed.", color = OnSurface1) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = RedError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = OnSurface2)
                }
            }
        )
    }
}

// ── History Item Card ────────────────────────────

@Composable
private fun HistoryItemCard(item: DownloadHistoryEntity, onDelete: () -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(Surface2)
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Format badge
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(topStart = 6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(item.format, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(12.dp))

            // Info
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.quality, fontSize = 11.sp, color = RedPrimary, fontWeight = FontWeight.Medium)
                    if (item.fileSize > 0) {
                        Text("•", fontSize = 11.sp, color = OnSurface2)
                        Text(formatFileSize(item.fileSize), fontSize = 11.sp, color = OnSurface2)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formatDate(item.timestamp),
                    fontSize = 10.sp,
                    color = OnSurface2.copy(alpha = 0.7f)
                )
            }

            // Menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.MoreVert, null, tint = OnSurface2, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Open file
                    DropdownMenuItem(
                        text = { Text("Open", color = OnSurface1) },
                        leadingIcon = { Icon(Icons.Rounded.OpenInNew, null, tint = OnSurface2) },
                        onClick = {
                            showMenu = false
                            openFile(context, item.filePath)
                        }
                    )
                    // Share
                    DropdownMenuItem(
                        text = { Text("Share", color = OnSurface1) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null, tint = OnSurface2) },
                        onClick = {
                            showMenu = false
                            shareFile(context, item.filePath)
                        }
                    )
                    HorizontalDivider(color = Divider)
                    // Delete
                    DropdownMenuItem(
                        text = { Text("Delete", color = RedError) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = RedError) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────

private fun formatDate(ts: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(ts))
}

private fun openFile(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = when (file.extension.lowercase()) {
        "mp4", "mkv", "webm" -> "video/*"
        "mp3", "m4a"         -> "audio/*"
        "srt"                -> "text/plain"
        else                  -> "*/*"
    }
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun shareFile(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share via").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

// ── Empty State ──────────────────────────────────

@Composable
private fun EmptyHistory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.History, null, tint = Surface3, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("No downloads yet", color = OnSurface2, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Completed downloads appear here", color = OnSurface2.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
