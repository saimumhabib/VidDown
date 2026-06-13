package com.viddown.app.manager

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Stores per-platform cookies (Netscape cookies.txt format, which is what
 * yt-dlp's `--cookies` option expects) so logged-in/age-restricted/private
 * videos on YouTube, Facebook, etc. can be downloaded.
 *
 * Two ways to fill this in (both end up calling [saveNetscapeCookies] or
 * [saveFromWebViewCookies]):
 *  1. Manual: user pastes a cookies.txt export (from a browser extension) or
 *     a raw "name=value; name2=value2" Cookie header.
 *  2. Auto: user logs in inside an in-app WebView, and we read the cookies
 *     for that domain via [android.webkit.CookieManager] and convert them.
 */
object AppCookieManager {

    enum class Site(val displayName: String, val domain: String, val loginUrl: String) {
        YOUTUBE("YouTube", "youtube.com", "https://accounts.google.com/ServiceLogin?service=youtube"),
        FACEBOOK("Facebook", "facebook.com", "https://www.facebook.com/login")
    }

    private fun cookieFile(context: Context, site: Site): File =
        File(context.filesDir, "cookies_${site.name.lowercase()}.txt")

    fun hasCookies(context: Context, site: Site): Boolean = cookieFile(context, site).exists()

    fun deleteCookies(context: Context, site: Site) {
        cookieFile(context, site).delete()
    }

    /** Absolute path to pass via `--cookies <path>`, or null if none saved. */
    fun getCookiesFilePath(context: Context, site: Site): String? {
        val f = cookieFile(context, site)
        return if (f.exists()) f.absolutePath else null
    }

    /** Picks the right cookie file (if any) for a given video URL. */
    fun getCookiesFilePathForUrl(context: Context, url: String): String? {
        val site = Site.values().firstOrNull { url.contains(it.domain, ignoreCase = true) }
            ?: return null
        return getCookiesFilePath(context, site)
    }

    /**
     * Accepts either a full Netscape cookies.txt export, or a simple
     * "key=value; key2=value2" Cookie-header string (as copied from a
     * browser's dev tools), and writes a valid Netscape file for [site].
     */
    fun saveCookies(context: Context, site: Site, rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return false
        val content = if (trimmed.startsWith("# Netscape") || trimmed.contains("\t")) {
            // Looks like an already-exported Netscape file — use as-is.
            trimmed
        } else {
            // Treat as "name=value; name2=value2" header string.
            netscapeFromHeaderString(site.domain, trimmed)
        }
        return try {
            cookieFile(context, site).writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reads cookies set in the system WebView's CookieManager for [site]'s
     * domain (after the user logs in inside the in-app browser) and converts
     * them to a Netscape cookies.txt file.
     */
    fun saveFromWebView(context: Context, site: Site): Boolean {
        val cm = CookieManager.getInstance()
        val cookieString = cm.getCookie("https://www.${site.domain}/")
            ?: cm.getCookie("https://${site.domain}/")
            ?: return false
        if (cookieString.isBlank()) return false
        return saveCookies(context, site, netscapeFromHeaderString(site.domain, cookieString))
    }

    private fun netscapeFromHeaderString(domain: String, header: String): String {
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        header.split(";").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) return@forEach
            // domain  includeSubdomains  path  secure  expiry  name  value
            sb.append(".$domain\tTRUE\t/\tTRUE\t2147483647\t$name\t$value\n")
        }
        return sb.toString()
    }
}
