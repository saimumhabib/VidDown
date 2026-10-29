@file:OptIn(ExperimentalMaterial3Api::class)

package com.viddown.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/** Settings -> Cookie import: a URL bar + in-app browser. "Import" copies the browser's cookies into the app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieImportScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var input by rememberSaveable { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val visited = remember { LinkedHashMap<String, Boolean>() } // host -> https?

    fun note(url: String?) {
        val u = url?.let { Uri.parse(it) } ?: return
        val host = u.host ?: return
        if (u.scheme == "http" || u.scheme == "https") visited[host] = (u.scheme == "https")
    }

    val webView = remember {
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Some sites (Google sign-in) reject the default embedded-WebView user agent.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)
        }
    }
    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loading = true
                note(url)
                if (url != null) { currentUrl = url; input = url }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loading = false
                note(url)
                if (url != null) currentUrl = url
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme
                return scheme != "http" && scheme != "https" // don't hand off intent:// etc.
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
        }
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    fun go(raw: String) {
        var t = raw.trim()
        if (t.isEmpty()) return
        if (SCHEME_RE.containsMatchIn(t)) {
            if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) return
        } else {
            t = if (t.contains(' ') || !t.contains('.')) {
                "https://www.google.com/search?q=" + Uri.encode(t)
            } else {
                "https://$t"
            }
        }
        keyboard?.hide()
        webView.loadUrl(t)
    }

    fun doImport() {
        CookieManager.getInstance().flush()
        val hosts = LinkedHashMap(visited)
        Uri.parse(currentUrl).let { u ->
            val h = u.host
            if (h != null && h !in hosts) hosts[h] = (u.scheme == "https")
        }
        val entries = CookieStore.collect(hosts)
        if (entries.isEmpty()) {
            Toast.makeText(ctx, "No cookies found. Open a site and sign in first.", Toast.LENGTH_LONG).show()
            return
        }
        CookieStore.save(ctx, entries)
        val sites = entries.map { it.domain.trimStart('.') }.distinct().size
        Toast.makeText(ctx, "Imported ${entries.size} cookies from $sites site(s)", Toast.LENGTH_LONG).show()
        onClose()
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cookie import", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { doImport() }) { Text("Import", fontWeight = FontWeight.Bold) }
                },
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Enter a link (e.g. youtube.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                trailingIcon = { TextButton(onClick = { go(input) }) { Text("Go") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(input) }),
            )
            if (loading && progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
