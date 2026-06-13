package com.viddown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.viddown.app.ui.screens.*
import com.viddown.app.ui.theme.*
import com.viddown.app.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VidDownTheme {
                VidDownApp(
                    sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)
                )
            }
        }
    }

    // Handle new intents (share from other apps, re-launch)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

// ── Navigation ───────────────────────────────────

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home      : Screen("home",      "Home",      Icons.Rounded.Home)
    object Downloads : Screen("downloads", "Downloads", Icons.Rounded.FileDownload)
    object History   : Screen("history",   "History",   Icons.Rounded.History)
    object Settings  : Screen("settings",  "Settings",  Icons.Rounded.Settings)
}

@Composable
fun VidDownApp(sharedUrl: String? = null) {
    val navController = rememberNavController()
    val viewModel: DownloadViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(Screen.Home, Screen.Downloads, Screen.History, Screen.Settings)

    // Handle URL shared from other apps
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.analyzeUrl(sharedUrl)
        }
    }

    // Badge count for active downloads
    val downloadsState by viewModel.downloadsState.collectAsState()
    val downloadCount = downloadsState.activeDownloads.size

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            NavigationBar(
                containerColor = Surface1,
                tonalElevation = 0.dp
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (screen is Screen.Downloads && downloadCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = RedPrimary) {
                                        Text("$downloadCount", fontSize = 9.sp)
                                    }
                                }) {
                                    Icon(screen.icon, contentDescription = screen.label)
                                }
                            } else {
                                Icon(screen.icon, contentDescription = screen.label)
                            }
                        },
                        label = {
                            Text(
                                screen.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = RedPrimary,
                            selectedTextColor   = RedPrimary,
                            unselectedIconColor = OnSurface2,
                            unselectedTextColor = OnSurface2,
                            indicatorColor      = RedPrimary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                Screen.Home.route,
                enterTransition  = { fadeIn() },
                exitTransition   = { fadeOut() }
            ) { HomeScreen(viewModel) }

            composable(
                Screen.Downloads.route,
                enterTransition  = { fadeIn() },
                exitTransition   = { fadeOut() }
            ) { DownloadsScreen(viewModel) }

            composable(
                Screen.History.route,
                enterTransition  = { fadeIn() },
                exitTransition   = { fadeOut() }
            ) { HistoryScreen(viewModel) }

            composable(
                Screen.Settings.route,
                enterTransition  = { fadeIn() },
                exitTransition   = { fadeOut() }
            ) { SettingsScreen() }
        }
    }
}
