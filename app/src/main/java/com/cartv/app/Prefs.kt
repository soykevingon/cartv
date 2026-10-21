package com.cartv.app

import android.content.Context
import java.net.URLEncoder

object Prefs {
    private const val FILE = "cartv"
    const val DEFAULT_HOME = "https://m.youtube.com"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun home(ctx: Context): String = p(ctx).getString("home", DEFAULT_HOME) ?: DEFAULT_HOME
    fun setHome(ctx: Context, url: String) = p(ctx).edit().putString("home", url).apply()

    fun last(ctx: Context): String = p(ctx).getString("last", null) ?: home(ctx)
    fun setLast(ctx: Context, url: String) = p(ctx).edit().putString("last", url).apply()

    /** Turns typed text into a URL: domains load directly, anything else becomes a Google search. */
    fun resolve(text: String): String {
        val t = text.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains('.') && !t.contains(' ') -> "https://$t"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
    }
}
