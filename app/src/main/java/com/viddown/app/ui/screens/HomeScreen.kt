package com.viddown.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.viddown.app.data.*
import com.viddown.app.ui.components.*
import com.viddown.app.ui.theme.*
import com.viddown.app.viewmodel.DownloadViewModel

@Composable
fun HomeScreen(viewModel: DownloadViewModel) {
    val state by viewModel.homeState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check clipboard when app comes to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val text = clipboard.getText()?.text
                viewModel.checkClipboard(text)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Column(Modifier.fillMaxSize()) {

            // Top App Bar
            TopBar()

            // Clipboard Popup (top of screen)
            ClipboardPopup(
                url = state.clipboardUrl ?: "",
                visible = state.showClipboardPopup,
                onAccept = { viewModel.acceptClipboard() },
                onDismiss = { viewModel.dismissClipboard() }
            )

            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // URL Input Card
                UrlInputCard(
                    url = state.urlInput,
                    isLoading = state.isAnalyzing,
                    onUrlChanged = { viewModel.onUrlChanged(it) },
                    onAnalyze = { viewModel.analyzeUrl() },
                    onPaste = {
                        clipboard.getText()?.text?.let { txt ->
                            viewModel.onUrlChanged(txt)
                        }
                    },
                    onClear = { viewModel.clearVideoInfo() }
                )

                Spacer(Modifier.height(16.dp))

                // Error Message
                AnimatedVisibility(state.error != null) {
                    ErrorCard(message = state.error ?: "")
                }

                // Analyzing Indicator
                AnimatedVisibility(state.isAnalyzing) {
                    AnalyzingCard()
                }

                // Video Preview Card
                AnimatedVisibility(
                    visible = state.videoInfo != null && !state.isAnalyzing,
                    enter = fadeIn() + expandVertically()
                ) {
                    state.videoInfo?.let { info ->
                        Spacer(Modifier.height(8.dp))
                        VideoPreviewCard(
                            info = info,
                            onDownloadClick = { viewModel.showQualitySheet() }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Supported Platforms
                if (state.videoInfo == null && !state.isAnalyzing) {
                    SupportedPlatforms()
                }
            }
        }

        // Quality Bottom Sheet
        if (state.showQualitySheet && state.videoInfo != null) {
            QualityBottomSheet(
                videoInfo = state.videoInfo!!,
                onDismiss = { viewModel.dismissQualitySheet() },
                onVideoDownload = { fmt -> viewModel.startVideoDownload(state.videoInfo!!, fmt) },
                onAudioDownload = { fmt -> viewModel.startAudioDownload(state.videoInfo!!, fmt) },
                onSubtitleDownload = { sub -> viewModel.startSubtitleDownload(state.videoInfo!!, sub) },
                onDownloadAllPlaylist = { info -> viewModel.startPlaylistDownload(info) }
            )
        }
    }
}

// ── Top Bar ──────────────────────────────────────

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(RedPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Download, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VidDown", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = OnBg)
            Spacer(Modifier.width(8.dp))
            Text("(SAIMUM HABIB)", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = RedPrimary)
        }
    }
}

// ── URL Input Card ───────────────────────────────

@Composable
private fun UrlInputCard(
    url: String,
    isLoading: Boolean,
    onUrlChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Enter Video URL", style = MaterialTheme.typography.labelMedium, color = RedPrimary)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://youtube.com/watch?v=...", color = OnSurface2) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = RedPrimary,
                    unfocusedBorderColor = Divider,
                    focusedTextColor     = OnBg,
                    unfocusedTextColor   = OnBg,
                    cursorColor          = RedPrimary,
                    focusedContainerColor   = Surface2,
                    unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Rounded.Clear, null, tint = OnSurface2)
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Paste Button
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface1),
                    border = BorderStroke(1.dp, Divider)
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste")
                }

                // Analyze Button
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.weight(2f),
                    enabled = url.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isLoading) "Analyzing..." else "Analyze")
                }
            }
        }
    }
}

// ── Video Preview Card ───────────────────────────

@Composable
private fun VideoPreviewCard(info: VideoInfo, onDownloadClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Thumbnail
            if (info.thumbnail.isNotBlank()) {
                Box(
                    Modifier.fillMaxWidth().height(200.dp).clip(
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                ) {
                    AsyncImage(
                        model = info.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Duration badge
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(info.duration, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Platform badge
                    Box(
                        Modifier.align(Alignment.TopStart).padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RedPrimary)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(info.platform.displayName, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(Modifier.padding(14.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    info.uploader.ifBlank { info.platform.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface2
                )

                Spacer(Modifier.height(8.dp))

                // Stats Row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip(Icons.Rounded.VideoFile, "${info.videoFormats.size} qualities")
                    StatChip(Icons.Rounded.AudioFile, "MP3")
                    if (info.subtitles.isNotEmpty()) {
                        StatChip(Icons.Rounded.ClosedCaption, "${info.subtitles.size} subs")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Analyzing Card ───────────────────────────────

@Composable
private fun AnalyzingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface1),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Column {
                Text("Analyzing video...", style = MaterialTheme.typography.titleMedium, color = OnBg)
                Text("Fetching available formats", style = MaterialTheme.typography.bodySmall, color = OnSurface2)
            }
        }
    }
}

// ── Error Card ───────────────────────────────────

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Error, null, tint = RedError, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = RedError)
        }
    }
}

// ── Supported Platforms ──────────────────────────

@Composable
private fun SupportedPlatforms() {
    val platforms = listOf("YouTube", "Instagram", "TikTok", "Facebook", "Twitter/X", "SoundCloud", "Twitch")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Supported Platforms", style = MaterialTheme.typography.labelMedium, color = OnSurface2)
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(platforms.size) { i ->
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Surface1)
                        .border(1.dp, Divider, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(platforms[i], fontSize = 12.sp, color = OnSurface1)
                }
            }
        }
    }
}

// ── Stat Chip ────────────────────────────────────

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Surface2).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = OnSurface1)
    }
}
