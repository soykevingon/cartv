package com.cartv.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/**
 * Every 5 s, if a YouTube video is playing, saves its URL with &t=<seconds> so the
 * other side (car or phone) resumes at the same spot. Paused videos aren't saved,
 * so a paused phone never overwrites the car's position and vice versa.
 */
class PositionTracker(
    private val ctx: Context,
    private val writer: String,
    private val webView: () -> WebView?,
) {
    companion object {
        const val PLAYING_TIME_JS =
            "(function(){var v=document.querySelector('video');" +
                "return (v&&!v.paused&&isFinite(v.currentTime))?Math.floor(v.currentTime):-1})()"
        const val ANY_TIME_JS =
            "(function(){var v=document.querySelector('video');" +
                "return (v&&isFinite(v.currentTime))?Math.floor(v.currentTime):-1})()"
        const val PAUSE_JS = "document.querySelectorAll('video,audio').forEach(function(m){m.pause()})"

        fun isWatch(url: String): Boolean {
            val u = Uri.parse(url)
            return (u.host ?: "").endsWith("youtube.com") && u.path == "/watch"
        }

        fun parse(result: String?): Int = result?.toDoubleOrNull()?.toInt() ?: -1

        fun withTime(url: String, seconds: Int): String {
            val u = Uri.parse(url)
            val b = u.buildUpon().clearQuery()
            for (name in u.queryParameterNames) {
                if (name == "t") continue
                for (v in u.getQueryParameters(name)) b.appendQueryParameter(name, v)
            }
            b.appendQueryParameter("t", "${seconds}s")
            return b.build().toString()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { saveNow(); handler.postDelayed(this, 5000) }
    }

    fun start() { handler.removeCallbacks(tick); handler.postDelayed(tick, 5000) }
    fun stop() { handler.removeCallbacks(tick) }

    fun saveNow() {
        val wv = webView() ?: return
        val url = wv.url ?: return
        if (!isWatch(url)) return
        wv.evaluateJavascript(PLAYING_TIME_JS) { r ->
            val t = parse(r)
            if (t > 0 && wv.url == url) Prefs.setLast(ctx, withTime(url, t), writer)
        }
    }
}
