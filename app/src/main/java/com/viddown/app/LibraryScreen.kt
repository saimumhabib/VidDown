@file:OptIn(ExperimentalMaterial3Api::class)

package com.viddown.app

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

data class LibraryItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val addedSec: Long,
)

/** Files this app saved in Downloads/VidDown, newest first. */
private fun loadLibrary(ctx: Context): List<LibraryItem> {
    val out = ArrayList<LibraryItem>()
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Downloads._ID,
        MediaStore.Downloads.DISPLAY_NAME,
        MediaStore.Downloads.SIZE,
        MediaStore.Downloads.MIME_TYPE,
        MediaStore.Downloads.DATE_ADDED,
    )
    val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
    val args = arrayOf(Environment.DIRECTORY_DOWNLOADS + "/VidDown%")
    ctx.contentResolver.query(
        collection, projection, selection, args, "${MediaStore.Downloads.DATE_ADDED} DESC"
    )?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
        val iName = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
        val iSize = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
        val iMime = c.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
        val iDate = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
        while (c.moveToNext()) {
            out += LibraryItem(
                uri = ContentUris.withAppendedId(collection, c.getLong(iId)),
                name = c.getString(iName) ?: "file",
                size = c.getLong(iSize),
                mime = c.getString(iMime) ?: "*/*",
                addedSec = c.getLong(iDate),
            )
        }
    }
    return out
}

private fun openFile(ctx: Context, item: LibraryItem) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LibraryScreen(controller: DownloadController) {
    val ctx = LocalContext.current
    val dl by controller.dl.collectAsState()
    var files by remember { mutableStateOf<List<LibraryItem>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<LibraryItem?>(null) }

    // Reload when the tab opens, on refresh, and whenever a download finishes.
    val doneKey = (dl as? DownloadState.Done)?.uri
    LaunchedEffect(tick, doneKey) {
        files = withContext(Dispatchers.IO) {
            runCatching { loadLibrary(ctx) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { tick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        val list = files
        when {
            list == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No downloads yet.\nVideos you download will show up here.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.uri.toString() }) { item ->
                    LibraryRow(item, onOpen = { openFile(ctx, item) }, onDelete = { toDelete = item })
                }
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete file?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(onClick = {
                    val ok = runCatching { ctx.contentResolver.delete(item.uri, null, null) > 0 }
                        .getOrDefault(false)
                    if (!ok) Toast.makeText(ctx, "Couldn't delete this file", Toast.LENGTH_SHORT).show()
                    toDelete = null
                    tick++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val date = remember(item.addedSec) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.addedSec * 1000))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${formatSize(item.size)} · $date", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
