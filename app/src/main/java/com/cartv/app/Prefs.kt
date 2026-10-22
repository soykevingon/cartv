package com.cartv.app

import android.content.Context
import android.net.Uri
import java.net.URLEncoder

object Prefs {
    private const val FILE = "cartv"
    const val DEFAULT_SITES = "YouTube | https://m.youtube.com"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Tiles on the home page, one "Name | URL" per line. */
    fun sitesRaw(ctx: Context): String = p(ctx).getString("sites", DEFAULT_SITES) ?: DEFAULT_SITES
    fun setSitesRaw(ctx: Context, raw: String) = p(ctx).edit().putString("sites", raw).apply()
    fun sites(ctx: Context): List<Pair<String, String>> =
        sitesRaw(ctx).lines().mapNotNull { line ->
            val parts = line.split('|').map { it.trim() }
            when {
                parts.size >= 2 && parts[1].isNotEmpty() -> parts[0].ifEmpty { parts[1] } to resolve(parts[1])
                parts.size == 1 && parts[0].isNotEmpty() -> {
                    val url = resolve(parts[0]); (Uri.parse(url).host ?: url) to url
                }
                else -> null
            }
        }

    fun last(ctx: Context): String = p(ctx).getString("last", null) ?: Web.HOME_URL
    fun setLast(ctx: Context, url: String) = p(ctx).edit().putString("last", url).apply()

    fun adblock(ctx: Context): Boolean = p(ctx).getBoolean("adblock", true)
    fun setAdblock(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("adblock", on).apply()

    /** Floating menu button position as fractions of the car screen (0..1). */
    fun fab(ctx: Context): Pair<Float, Float> =
        p(ctx).getFloat("fabX", 0.98f) to p(ctx).getFloat("fabY", 0.45f)
    fun setFab(ctx: Context, x: Float, y: Float) =
        p(ctx).edit().putFloat("fabX", x).putFloat("fabY", y).apply()

    /** Turns typed text into a URL: domains load directly, anything else becomes a Google search. */
    fun resolve(text: String): String {
        val t = text.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains('.') && !t.contains(' ') -> "https://$t"
            else -> "https://www.google.com/search?q=" + enc(t)
        }
    }

    /** Car keyboard search: YouTube by default, Google when you're on another site. */
    fun searchUrl(text: String, currentUrl: String?): String {
        val t = text.trim()
        val cur = currentUrl ?: ""
        return when {
            t.contains('.') && !t.contains(' ') -> resolve(t)
            cur.contains("youtube.com") || cur.startsWith(Web.HOME_URL) || cur.isEmpty() ->
                "https://m.youtube.com/results?search_query=" + enc(t)
            else -> "https://www.google.com/search?q=" + enc(t)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
