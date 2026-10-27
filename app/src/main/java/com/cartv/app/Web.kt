package com.cartv.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Message
import android.view.ViewGroup
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
        // Pop-up windows, needed for "Sign in with Google" on sites like Patreon.
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
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

/** @param track save pages as "last page" (off for sign-in pop-ups). */
open class CarTvClient(
    private val ctx: Context,
    private val writer: String,
    private val track: Boolean = true,
) : WebViewClient() {
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

    // YouTube changes videos without a full page load; this catches those too.
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (track && url != null && !isReload) Prefs.setLast(ctx, url, writer)
    }

    override fun onPageFinished(view: WebView, url: String) {
        CookieManager.getInstance().flush()
    }
}

/** @param addPopup where to show a pop-up window (sign-in screens). */
open class CarTvChrome(
    private val ctx: Context,
    private val writer: String,
    private val addPopup: (WebView) -> Unit,
) : WebChromeClient() {

    var popup: WebView? = null
        private set

    override fun onPermissionRequest(request: PermissionRequest) {
        val micOk = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val allowed = request.resources.filter {
            it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ||
                (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && micOk)
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        closePopup()
        val w = WebView(view.context)
        Web.setup(w, ctx)
        w.webViewClient = CarTvClient(ctx, writer, track = false)
        w.webChromeClient = this
        addPopup(w)
        popup = w
        (resultMsg.obj as WebView.WebViewTransport).webView = w
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) {
        if (window === popup) closePopup()
    }

    /** @return true if a pop-up was open. */
    fun closePopup(): Boolean {
        val w = popup ?: return false
        popup = null
        (w.parent as? ViewGroup)?.removeView(w)
        w.destroy()
        return true
    }
}
