package com.viddown.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Library("Library", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Filled.Settings),
}

/** Three pages (Home / Library / Settings) with a bottom bar; Cookie import opens full-screen from Settings. */
@Composable
fun AppRoot(controller: DownloadController) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var cookieScreen by rememberSaveable { mutableStateOf(false) }
    val tab = Tab.entries[tabIndex]
    val stateHolder = rememberSaveableStateHolder()

    if (cookieScreen) {
        CookieImportScreen(onClose = { cookieScreen = false })
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = i == tabIndex,
                            onClick = { tabIndex = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                // Keeps each page's saved state (e.g. the pasted link on Home) while you switch tabs.
                stateHolder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.Home -> MainScreen(controller)
                        Tab.Library -> LibraryScreen(controller)
                        Tab.Settings -> SettingsScreen(onOpenCookieImport = { cookieScreen = true })
                    }
                }
            }
        }
    }
}
