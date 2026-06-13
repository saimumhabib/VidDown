package com.viddown.app.ui.screens

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import com.viddown.app.manager.LogFileManager
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viddown.app.ui.theme.*

@Composable
fun SettingsScreen() {
    var defaultQuality  by remember { mutableStateOf("1080p") }
    var defaultFormat   by remember { mutableStateOf("MP4") }
    val mcCtx = LocalContext.current
    var maxConcurrent   by remember { mutableIntStateOf(com.viddown.app.manager.AppPreferences.getMaxConcurrent(mcCtx)) }
    var notifications   by remember { mutableStateOf(true) }
    var wifiOnly        by remember { mutableStateOf(false) }
    var autoPlaylist    by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(BgDark)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            "Settings",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = OnBg,
            modifier = Modifier.padding(20.dp)
        )

        // Download Settings
        SettingsSection("Download") {
            val ctx = LocalContext.current
            var downloadLabel by remember { mutableStateOf(com.viddown.app.manager.AppPreferences.getDownloadFolderLabel(ctx)) }
            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    com.viddown.app.manager.AppPreferences.setDownloadFolderUri(ctx, uri)
                    downloadLabel = com.viddown.app.manager.AppPreferences.getDownloadFolderLabel(ctx)
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .clickable { folderPicker.launch(null) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Folder, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Download Location", style = MaterialTheme.typography.titleMedium, color = OnBg)
                    Text(downloadLabel, style = MaterialTheme.typography.bodySmall, color = OnSurface2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = OnSurface2)
            }
            SettingsDivider()
            SettingsDropdown(
                icon = Icons.Rounded.HighQuality,
                title = "Default Quality",
                subtitle = "Applied when downloading video",
                selected = defaultQuality,
                options = listOf("4K", "1080p", "720p", "480p", "360p"),
                onSelect = { defaultQuality = it }
            )
            SettingsDivider()
            SettingsDropdown(
                icon = Icons.Rounded.VideoFile,
                title = "Default Format",
                subtitle = "Container format for videos",
                selected = defaultFormat,
                options = listOf("MP4", "MKV", "WEBM"),
                onSelect = { defaultFormat = it }
            )
            SettingsDivider()
            SettingsStepControl(
                icon = Icons.Rounded.DownloadForOffline,
                title = "Max Concurrent Downloads",
                subtitle = "Simultaneously running downloads",
                value = maxConcurrent,
                min = 1, max = 5,
                onChanged = { maxConcurrent = it; com.viddown.app.manager.AppPreferences.setMaxConcurrent(mcCtx, it) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Behavior Settings
        SettingsSection("Behavior") {
            SettingsSwitch(
                icon = Icons.Rounded.Notifications,
                title = "Download Notifications",
                subtitle = "Show progress and completion alerts",
                checked = notifications,
                onChecked = { notifications = it }
            )
            SettingsDivider()
            SettingsSwitch(
                icon = Icons.Rounded.Wifi,
                title = "Wi-Fi Only",
                subtitle = "Download only when connected to Wi-Fi",
                checked = wifiOnly,
                onChecked = { wifiOnly = it }
            )
            SettingsDivider()
            SettingsSwitch(
                icon = Icons.Rounded.PlaylistAdd,
                title = "Auto-detect Playlist",
                subtitle = "Prompt to download full playlist",
                checked = autoPlaylist,
                onChecked = { autoPlaylist = it }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Cookies (for login-required / age-restricted videos)
        SettingsSection("Cookies") {
            val cctx = LocalContext.current
            var importUrl by remember { mutableStateOf("") }
            var browserSite by remember { mutableStateOf<com.viddown.app.manager.AppCookieManager.Site?>(null) }
            var manualSite by remember { mutableStateOf<com.viddown.app.manager.AppCookieManager.Site?>(null) }
            var importMsg by remember { mutableStateOf<String?>(null) }

            Text(
                "Paste a URL below and open it to log in, then tap \"Import Cookies\" — or add cookies manually with +.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            OutlinedTextField(
                value = importUrl,
                onValueChange = { importUrl = it },
                placeholder = { Text("https://www.youtube.com or facebook.com URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            com.viddown.app.manager.AppCookieManager.Site.values().forEach { site ->
                var hasCookies by remember { mutableStateOf(com.viddown.app.manager.AppCookieManager.hasCookies(cctx, site)) }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Cookie, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(site.displayName, style = MaterialTheme.typography.titleMedium, color = OnBg)
                        Text(
                            if (hasCookies) "Cookies saved" else "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasCookies) RedPrimary else OnSurface2
                        )
                    }
                    IconButton(onClick = {
                        browserSite = site
                    }) {
                        Icon(Icons.Rounded.Public, null, tint = OnSurface2)
                    }
                    IconButton(onClick = { manualSite = site }) {
                        Icon(Icons.Rounded.Add, null, tint = OnSurface2)
                    }
                    if (hasCookies) {
                        IconButton(onClick = {
                            com.viddown.app.manager.AppCookieManager.deleteCookies(cctx, site)
                            hasCookies = false
                        }) {
                            Icon(Icons.Rounded.Delete, null, tint = OnSurface2)
                        }
                    }
                }
                if (site != com.viddown.app.manager.AppCookieManager.Site.values().last()) SettingsDivider()

                // Manual paste dialog
                if (manualSite == site) {
                    var text by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { manualSite = null },
                        title = { Text("Add ${site.displayName} cookies", color = OnBg) },
                        text = {
                            Column {
                                Text(
                                    "Paste a cookies.txt export, or the raw Cookie header value (name=value; name2=value2) from your browser's dev tools.",
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface2
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = text, onValueChange = { text = it },
                                    modifier = Modifier.fillMaxWidth().height(140.dp)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (com.viddown.app.manager.AppCookieManager.saveCookies(cctx, site, text)) {
                                    hasCookies = true
                                }
                                manualSite = null
                            }) { Text("Save", color = RedPrimary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { manualSite = null }) { Text("Cancel", color = OnSurface2) }
                        }
                    )
                }
            }

            if (importMsg != null) {
                Text(
                    importMsg!!,
                    color = OnSurface2,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // In-app browser for auto cookie import
            browserSite?.let { site ->
                val url = importUrl.trim().ifBlank { site.loginUrl }
                    .let { if (!it.startsWith("http")) "https://$it" else it }
                com.viddown.app.ui.components.CookieWebViewDialog(
                    site = site,
                    startUrl = url,
                    onDismiss = { browserSite = null },
                    onImported = { ok ->
                        importMsg = if (ok) "${site.displayName} cookies imported ✅" else "Import failed — make sure you're logged in"
                        browserSite = null
                    }
                )
            }
        }

        SettingsSection("About") {
            SettingsInfoRow(icon = Icons.Rounded.Info,         title = "Version",    value = "1.0.0")
            SettingsDivider()
            SettingsInfoRow(icon = Icons.Rounded.Build,        title = "yt-dlp",     value = "Latest")
            SettingsDivider()
            SettingsInfoRow(icon = Icons.Rounded.VideoSettings, title = "FFmpeg-kit", value = "6.0")
            SettingsDivider()
            val ctx2 = LocalContext.current
            Row(
                Modifier.fillMaxWidth()
                    .clickable {
                        ctx2.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/saimum10"))
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Person, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Author", style = MaterialTheme.typography.titleMedium, color = OnBg)
                    Text("SAIMUM HABIB · github.com/saimum10", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = OnSurface2)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Diagnostics / Error Log
        SettingsSection("Diagnostics") {
            val scope = rememberCoroutineScope()
            var updateStatus by remember { mutableStateOf<String?>(null) }
            var updating by remember { mutableStateOf(false) }
            val updateCtx = LocalContext.current
            Button(
                onClick = {
                    updating = true
                    updateStatus = null
                    scope.launch {
                        updateStatus = com.viddown.app.manager.EngineManager.updateYtDlp(updateCtx)
                        updating = false
                    }
                },
                enabled = !updating,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (updating) "Checking for update..." else "Check for yt-dlp Update")
            }
            if (updateStatus != null) {
                Text(
                    updateStatus!!,
                    color = OnSurface2,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            SettingsDivider()

            var ctx = LocalContext.current
            var saveLogEnabled by remember { mutableStateOf(LogFileManager.isSaveEnabled(ctx)) }
            SettingsSwitch(
                icon = Icons.Rounded.BugReport,
                title = "Save Error Log",
                subtitle = "Persist engine errors to app storage",
                checked = saveLogEnabled,
                onChecked = {
                    saveLogEnabled = it
                    LogFileManager.setSaveEnabled(ctx, it)
                }
            )
            SettingsDivider()

            var showDialog by remember { mutableStateOf(false) }
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(Icons.Rounded.Article, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Error Log")
            }

            if (showDialog) {
                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                var logText by remember { mutableStateOf(LogFileManager.readLog(context)) }

                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(logText))
                        }) { Text("Copy") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            LogFileManager.clearLog(context)
                            logText = ""
                        }) { Text("Clear") }
                    },
                    title = { Text("Saved Error Log") },
                    text = {
                        Column {
                            Text(
                                if (logText.isBlank()) "(no logs)" else logText,
                                maxLines = 20,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                // Share as .txt file if present, else share text
                                try {
                                    val f = File(context.filesDir, "error_log.txt")
                                    if (f.exists() && f.length() > 0) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share log"))
                                    } else {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, logText)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share log"))
                                    }
                                } catch (e: Exception) {
                                    // fallback: copy to clipboard
                                    clipboard.setText(AnnotatedString(logText))
                                }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)) {
                                Text("Share as .txt")
                            }
                        }
                    }
                )
            }
        }
    }
}

// ── Section Wrapper ──────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = RedPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface1),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

// ── Settings Items ───────────────────────────────

@Composable
private fun SettingsSwitch(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnBg)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedTrackColor = RedPrimary)
        )
    }
}

@Composable
private fun SettingsDropdown(
    icon: ImageVector, title: String, subtitle: String,
    selected: String, options: List<String>, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnBg)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected, color = RedPrimary, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ArrowDropDown, null, tint = RedPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = if (opt == selected) RedPrimary else OnSurface1) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsStepControl(
    icon: ImageVector, title: String, subtitle: String,
    value: Int, min: Int, max: Int, onChanged: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnBg)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurface2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > min) onChanged(value - 1) },
                modifier = Modifier.size(32.dp)
            ) { Icon(Icons.Rounded.Remove, null, tint = if (value > min) RedPrimary else OnSurface2, modifier = Modifier.size(18.dp)) }
            Text(
                "$value",
                color = OnBg,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            IconButton(
                onClick = { if (value < max) onChanged(value + 1) },
                modifier = Modifier.size(32.dp)
            ) { Icon(Icons.Rounded.Add, null, tint = if (value < max) RedPrimary else OnSurface2, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = OnBg, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface2)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp), color = Divider, thickness = 0.5.dp)
}
