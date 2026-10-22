package com.cartv.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Shared browser setup for the car screen and the phone browser (same cookies = same sign-ins). */
object Web {
    const val HOME_URL = "https://cartv.local/"

    @SuppressLint("SetJavaScriptEnabled")
    fun setup(wv: WebView, ctx: Context) {
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        // Look like regular Chrome so Google lets you sign in.
        s.userAgentString = s.userAgentString
            .replace("; wv", "")
            .replace(Regex("Version/[\\d.]+ "), "")

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(wv, true)

        if (Prefs.adblock(ctx) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(wv, AdBlock.SCRIPT, setOf("*"))
        }
    }
}

open class CarTvClient(private val ctx: Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Stay inside CarTV; block intent:// links that try to open other apps.
        val scheme = request.url.scheme ?: return true
        return scheme != "http" && scheme != "https"
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host == "cartv.local") {
            return WebResourceResponse("text/html", "utf-8", HomePage.html(ctx).byteInputStream())
        }
        if (Prefs.adblock(ctx) && AdBlock.blocks(url)) return AdBlock.empty()
        return null
    }

    override fun onPageFinished(view: WebView, url: String) {
        Prefs.setLast(ctx, url)
        CookieManager.getInstance().flush()
    }
}

open class CarTvChrome(private val ctx: Context) : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        val micOk = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val allowed = request.resources.filter {
            it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ||
                (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && micOk)
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }
}
