@file:OptIn(ExperimentalMaterial3Api::class)

package com.viddown.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun VidDownTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF90CAF9))
        else -> lightColorScheme(primary = Color(0xFF1565C0))
    }
    androidx.compose.material3.MaterialTheme(colorScheme = scheme, content = content)
}

private val URL_HINT = Regex("""https?://\S+""")

@Composable
fun MainScreen(controller: DownloadController) {
    val ui by controller.ui.collectAsState()
    val dl by controller.dl.collectAsState()
    val shared by controller.sharedLink.collectAsState()
    var link by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // A link shared from another app ("Share" -> VidDown)
    LaunchedEffect(shared) {
        shared?.let {
            link = it
            controller.fetch(it)
            controller.consumeShared()
        }
    }

    val busy = dl is DownloadState.Running
    val fetching = ui is UiState.Fetching

    Scaffold(
        topBar = { TopAppBar(title = { Text("VidDown", fontWeight = FontWeight.Bold) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = link,
                    onValueChange = { new ->
                        // Pasting a link from the keyboard jumps the length: fetch right away.
                        val pasted = new.length - link.length > 8 && URL_HINT.containsMatchIn(new)
                        link = new
                        if (pasted) controller.fetch(new)
                    },
                    label = { Text("Paste video link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isNotBlank()) {
                                link = text
                                controller.fetch(text)
                            }
                        }) { Text("Paste") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        keyboard?.hide()
                        controller.fetch(link)
                    }),
                )
            }
            item {
                Button(
                    onClick = { keyboard?.hide(); controller.fetch(link) },
                    enabled = link.isNotBlank() && !fetching,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Show qualities") }
            }

            if (dl !is DownloadState.None) {
                item { DownloadPanel(dl, controller) }
            }

            when (val s = ui) {
                UiState.Idle -> Unit
                is UiState.Fetching -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (s.firstRun) "Preparing the engine (first time only, may take a minute)…"
                            else "Reading video info…"
                        )
                    }
                }
                is UiState.Failed -> item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            s.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                is UiState.Ready -> {
                    item { VideoCard(s.info) }
                    item { Text("Choose quality to download", style = MaterialTheme.typography.titleMedium) }
                    items(s.info.options, key = { it.id }) { opt ->
                        QualityRow(opt, enabled = !busy) { controller.startDownload(opt) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(info: VideoInfo) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }
            Column(Modifier.padding(16.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    info.uploader,
                    info.durationSec?.let { formatDuration(it) },
                    info.site,
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(meta, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QualityRow(opt: QualityOption, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(opt.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (opt.detail.isNotBlank()) Text(opt.detail, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Download",
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DownloadPanel(dl: DownloadState, controller: DownloadController) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (dl) {
                DownloadState.None -> Unit
                is DownloadState.Running -> {
                    Text(dl.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val pct = dl.percent
                    if (dl.phase == Phase.Downloading && pct != null) {
                        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val parts = buildList {
                        add(phaseLabel(dl.phase))
                        if (dl.phase == Phase.Downloading && pct != null) add("${pct.toInt()}%")
                        dl.speedBps?.let { add(formatSpeed(it)) }
                        dl.etaSec?.let { add(formatDuration(it) + " left") }
                    }
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { controller.cancelDownload() }) { Text("Cancel") }
                }
                is DownloadState.Done -> {
                    Text("Saved to Downloads/VidDown", style = MaterialTheme.typography.titleSmall)
                    Text(dl.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(Uri.parse(dl.uri), dl.mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                ctx.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Open") }
                        TextButton(onClick = { controller.dismissResult() }) { Text("Dismiss") }
                    }
                }
                is DownloadState.Failed -> {
                    Text("Download failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text(dl.message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { controller.dismissResult() }) { Text("Dismiss") }
                }
            }
        }
    }
}
