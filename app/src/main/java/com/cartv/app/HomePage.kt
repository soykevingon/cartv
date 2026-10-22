package com.cartv.app

import android.content.Context
import android.net.Uri

/** The start page: a grid of big site tiles. Edit the list in the CarTV app on your phone. */
object HomePage {
    fun html(ctx: Context): String {
        val tiles = Prefs.sites(ctx).joinToString("") { (name, url) ->
            val host = Uri.parse(url).host ?: ""
            """<a class="t" href="${esc(url)}"><img src="https://www.google.com/s2/favicons?domain=${esc(host)}&amp;sz=128" alt=""><span>${esc(name)}</span></a>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CarTV</title>
<style>
*{box-sizing:border-box}
body{margin:0;padding:28px;background:#0f0f17;color:#fff;font-family:sans-serif}
h1{font-size:18px;font-weight:600;opacity:.5;margin:0 0 20px;letter-spacing:.08em}
.g{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:18px}
.t{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;height:160px;
   background:#1e1e2e;border-radius:22px;color:#fff;text-decoration:none;font-size:22px}
.t:active{background:#2c2c44}
.t img{width:64px;height:64px;border-radius:14px}
</style></head><body><h1>CARTV</h1><div class="g">$tiles</div></body></html>"""
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
