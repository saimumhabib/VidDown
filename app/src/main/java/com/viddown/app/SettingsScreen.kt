@file:OptIn(ExperimentalMaterial3Api::class)

package com.viddown.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onOpenCookieImport: () -> Unit) {
    val ctx = LocalContext.current
    var count by remember { mutableStateOf(CookieStore.count(ctx)) }
    var sites by remember { mutableStateOf(CookieStore.domains(ctx)) }
    var confirmClear by remember { mutableStateOf(false) }
    var useCookies by remember { mutableStateOf(CookieStore.isEnabled(ctx)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(onClick = onOpenCookieImport, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Cookie import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (count > 0) {
                                    val shown = sites.take(3).joinToString(", ") + if (sites.size > 3) " +${sites.size - 3}" else ""
                                    "$count cookies · $shown"
                                } else {
                                    "Sign in to a site inside the app and import its cookies for videos that need a login."
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineMedium)
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Use cookies", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (useCookies) "On: imported cookies are used for link info and downloads"
                                else "Off: cookies are ignored",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = useCookies,
                            onCheckedChange = {
                                useCookies = it
                                CookieStore.setEnabled(ctx, it)
                            },
                        )
                    }
                }
            }


            if (count > 0) {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear imported cookies")
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear imported cookies?") },
            text = { Text("Downloads will no longer use your imported logins.") },
            confirmButton = {
                TextButton(onClick = {
                    CookieStore.clear(ctx)
                    count = 0
                    sites = emptyList()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
