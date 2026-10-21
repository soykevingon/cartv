package com.cartv.app

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Draws a real WebView onto the Android Auto screen.
 * Android Auto hands us a Surface (the "map" area of a navigation app); we put a
 * VirtualDisplay on it and show a Presentation holding the WebView. Taps/scrolls
 * from the car screen are forwarded to the WebView.
 */
class BrowserRenderer(private val carContext: CarContext) : SurfaceCallback, DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var blocker: TextView? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var blockState = ParkedMonitor.State.PARKED

    private val parked = ParkedMonitor(carContext) { s -> handler.post { applyBlock(s) } }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        parked.start()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        parked.stop()
        release()
    }

    // ---- Surface ----

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        release()
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "CarTV", container.width, container.height, container.dpi, surface, 0
        )
        virtualDisplay = vd
        val p = Presentation(carContext, vd.display)
        p.setContentView(buildViews(p.context))
        p.show()
        presentation = p
        applyBlock(blockState)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) = release()

    private fun release() {
        webView?.let { webView?.url?.let { u -> Prefs.setLast(carContext, u) }; it.destroy() }
        webView = null
        customView = null
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        root = null
        blocker = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildViews(ctx: android.content.Context): View {
        val frame = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }

        val wv = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Stay inside the browser; block intent:// links that try to open other apps.
                    val scheme = request.url.scheme ?: return true
                    return scheme != "http" && scheme != "https"
                }
                override fun onPageFinished(view: WebView, url: String) {
                    Prefs.setLast(carContext, url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customView?.let { frame.removeView(it) }
                    customView = view
                    customCallback = callback
                    frame.addView(view, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
                override fun onHideCustomView() = hideCustomView()
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        frame.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val block = TextView(ctx).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true // swallow taps while blocked
        }
        frame.addView(block, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root = frame
        webView = wv
        blocker = block
        wv.loadUrl(Prefs.last(carContext))
        return frame
    }

    private fun hideCustomView() {
        customView?.let { root?.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    // ---- Parked lock ----

    private fun applyBlock(s: ParkedMonitor.State) {
        blockState = s
        val b = blocker ?: return
        when (s) {
            ParkedMonitor.State.PARKED -> {
                b.visibility = View.GONE
                webView?.onResume()
            }
            ParkedMonitor.State.MOVING, ParkedMonitor.State.NO_PERMISSION -> {
                b.text = if (s == ParkedMonitor.State.MOVING)
                    "Paused while driving.\nIt resumes when the car is parked."
                else
                    "Open CarTV on your phone and allow location\n(used only to detect when you're parked)."
                b.visibility = View.VISIBLE
                b.bringToFront()
                webView?.evaluateJavascript(
                    "document.querySelectorAll('video,audio').forEach(function(m){m.pause()})", null
                )
                webView?.onPause()
            }
        }
    }

    private val blocked get() = blockState != ParkedMonitor.State.PARKED

    // ---- Controls from the car screen ----

    override fun onClick(x: Float, y: Float) {
        if (blocked) return
        val target = root ?: return
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val u = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            u.source = InputDevice.SOURCE_TOUCHSCREEN
            root?.dispatchTouchEvent(u)
            u.recycle()
        }, 60)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (blocked || customView != null) return
        webView?.evaluateJavascript("window.scrollBy($distanceX,$distanceY)", null)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (blocked || customView != null) return
        webView?.evaluateJavascript(
            "window.scrollBy({left:${-velocityX * 0.3f},top:${-velocityY * 0.3f},behavior:'smooth'})", null
        )
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (blocked) return
        webView?.zoomBy(scaleFactor.coerceIn(0.5f, 2f))
    }

    fun scrollPage(direction: Int) {
        if (blocked) return
        webView?.evaluateJavascript(
            "window.scrollBy({top:${direction}*window.innerHeight*0.8,behavior:'smooth'})", null
        )
    }

    fun load(url: String) { hideCustomView(); webView?.loadUrl(url) }
    fun goHome() = load(Prefs.home(carContext))
    fun reload() { webView?.reload() }
    fun goBack() {
        when {
            customView != null -> hideCustomView()
            webView?.canGoBack() == true -> webView?.goBack()
        }
    }
}
