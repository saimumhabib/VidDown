package com.viddown.app

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Keeps imported browser cookies in a Netscape-format cookies.txt inside the app's private
 * storage. yt-dlp reads this file, so videos that need a login work after "Cookie import".
 */
object CookieStore {
    data class Entry(val domain: String, val secure: Boolean, val name: String, val value: String)

    private const val HEADER = "# Netscape HTTP Cookie File"

    private fun file(ctx: Context) = File(ctx.filesDir, "cookies.txt")

    private const val PREFS = "viddown_prefs"
    private const val KEY_USE = "use_cookies"

    /** "Use cookies" switch in Settings (default: on). */
    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USE, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE, on).apply()
    }

    /** Path of cookies.txt, or null when nothing has been imported. */
    fun path(ctx: Context): String? = file(ctx).takeIf { it.isFile && it.length() > 0 }?.path

    fun load(ctx: Context): List<Entry> {
        val f = file(ctx)
        if (!f.isFile) return emptyList()
        return f.readLines().mapNotNull { line ->
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val p = line.split('\t')
            if (p.size < 7) null
            else Entry(p[0], p[3].equals("TRUE", ignoreCase = true), p[5], p.drop(6).joinToString("\t"))
        }
    }

    fun count(ctx: Context): Int = load(ctx).size

    fun domains(ctx: Context): List<String> =
        load(ctx).map { it.domain.trimStart('.') }.distinct()

    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    /** Merges [fresh] into the stored cookies (same domain + name is replaced). Returns the total stored. */
    fun save(ctx: Context, fresh: List<Entry>): Int {
        val merged = LinkedHashMap<String, Entry>()
        (load(ctx) + fresh).forEach { merged[it.domain + "\t" + it.name] = it }
        val sb = StringBuilder(HEADER).append('\n')
        merged.values.forEach { e ->
            val sub = if (e.domain.startsWith(".")) "TRUE" else "FALSE"
            sb.append(e.domain).append('\t')
                .append(sub).append('\t')
                .append("/").append('\t')
                .append(if (e.secure) "TRUE" else "FALSE").append('\t')
                .append("0").append('\t') // 0 = session cookie (yt-dlp understands this)
                .append(e.name).append('\t')
                .append(e.value).append('\n')
        }
        val f = file(ctx)
        val tmp = File(f.parentFile, "cookies.txt.tmp")
        tmp.writeText(sb.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        return merged.size
    }

    /**
     * Reads cookies out of the in-app browser. [hosts] maps a visited host to "was it https".
     * Android's CookieManager only hands back "name=value" pairs for a URL (no expiry/path),
     * so the cookies are written as domain-wide session cookies.
     */
    fun collect(hosts: Map<String, Boolean>): List<Entry> {
        val cm = CookieManager.getInstance()
        val out = LinkedHashMap<String, Entry>()
        for ((host, https) in hosts) {
            val base = baseDomain(host)
            val plain = isIp(host) || !host.contains('.')
            val domain = if (plain) host else ".$base"
            val scheme = if (https) "https://" else "http://"
            for (h in listOf(host, base).distinct()) {
                val header = cm.getCookie("$scheme$h/") ?: continue
                for (part in header.split(';')) {
                    val kv = part.trim()
                    val i = kv.indexOf('=')
                    if (i <= 0) continue
                    val name = kv.substring(0, i)
                    val value = kv.substring(i + 1)
                    if (name.any { it == '\t' || it == '\n' || it == '\r' }) continue
                    if (value.any { it == '\t' || it == '\n' || it == '\r' }) continue
                    out[domain + "\t" + name] = Entry(domain, https, name, value)
                }
            }
        }
        return out.values.toList()
    }

    private fun isIp(host: String) = host.all { it.isDigit() || it == '.' } || host.contains(':')

    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac")

    fun baseDomain(host: String): String {
        val h = host.lowercase().trim('.')
        if (isIp(h) || !h.contains('.')) return h
        val p = h.split('.')
        if (p.size <= 2) return h
        val take = if (p.last().length == 2 && p[p.size - 2] in SECOND_LEVEL) 3 else 2
        return p.takeLast(take).joinToString(".")
    }
}
