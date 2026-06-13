package com.viddown.app.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.viddown.app.manager.AppCookieManager
import com.viddown.app.ui.theme.BgDark
import com.viddown.app.ui.theme.OnBg
import com.viddown.app.ui.theme.RedPrimary

/**
 * Full-screen in-app browser. The user logs into [site] normally; tapping
 * "Import Cookies" reads the session cookies the WebView just received and
 * saves them via [AppCookieManager] for use with yt-dlp.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieWebViewDialog(
    site: AppCookieManager.Site,
    startUrl: String,
    onDismiss: () -> Unit,
    onImported: (Boolean) -> Unit
) {
    var currentUrl by remember { mutableStateOf(startUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(BgDark)) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = OnBg)
                }
                Text(
                    "Login to ${site.displayName} to import cookies",
                    color = OnBg,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val wv = webViewRef
                    val ok = if (wv != null) AppCookieManager.saveFromWebView(wv.context, site) else false
                    onImported(ok)
                }) {
                    Text("Import Cookies", color = RedPrimary, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                currentUrl,
                color = OnBg.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (url != null) currentUrl = url
                                android.webkit.CookieManager.getInstance().flush()
                            }
                        }
                        loadUrl(startUrl)
                        webViewRef = this
                    }
                }
            )
        }
    }
}
