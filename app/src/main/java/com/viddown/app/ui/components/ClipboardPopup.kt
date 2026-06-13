package com.viddown.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viddown.app.ui.theme.*

@Composable
fun ClipboardPopup(
    url: String,
    visible: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit  = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Icon
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(RedPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        tint = RedPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Text
                Column(Modifier.weight(1f)) {
                    Text(
                        "Video link detected!",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBg
                    )
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Dismiss
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = OnSurface2, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Download Button
            Row(
                Modifier.fillMaxWidth().padding(top = 44.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Ignore", color = OnSurface2)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download")
                }
            }
        }
    }
}
