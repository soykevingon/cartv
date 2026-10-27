package com.cartv.app

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
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
 * VirtualDisplay on it and show a Presentation holding the WebView plus our own
 * overlays (floating menu, keyboard). Taps from the car screen are forwarded in.
 *
 * When the surface goes away briefly (reverse camera), the VirtualDisplay is kept and
 * re-attached, so the page and video keep their place instead of reloading.
 */
class BrowserRenderer(private val carContext: CarContext) : SurfaceCallback, DefaultLifecycleObserver {

    companion object {
        /** The live car browser, so the phone can send pages to it. */
        var current: BrowserRenderer? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var surfW = 0
    private var surfH = 0
    private var surfDpi = 0
    private var area: Rect? = null

    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var chrome: CarTvChrome? = null
    private var blocker: TextView? = null
    private var fab: TextView? = null
    private var menuLayer: FrameLayout? = null
    private var menuPanel: LinearLayout? = null
    private var keyboard: OnScreenKeyboard? = null
    private var banner: TextView? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var moveMode = false
    private var fabSize = 0
    private var blockState = ParkedMonitor.State.PARKED

    private val parked = ParkedMonitor(carContext) { s -> handler.post { applyBlock(s) } }
    private val tracker = PositionTracker(carContext, "car") { webView }

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
        val vd = virtualDisplay
        if (vd != null && presentation != null &&
            container.width == surfW && container.height == surfH && container.dpi == surfDpi
        ) {
            vd.surface = surface // back from reverse camera etc.: same page, same spot
            current = this
            applyBlock(blockState)
            pokeFab()
            return
        }
        release()
        surfW = container.width; surfH = container.height; surfDpi = container.dpi
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val newVd = dm.createVirtualDisplay("CarTV", surfW, surfH, surfDpi, surface, 0)
        virtualDisplay = newVd
        val p = Presentation(carContext, newVd.display)
        p.setContentView(buildViews(p.context))
        p.show()
        presentation = p
        current = this
        tracker.start()
        applyArea()
        applyBlock(blockState)
        pokeFab()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        virtualDisplay?.surface = null // keep everything alive until the surface returns
    }

    /** The part of the screen Android Auto's own buttons never cover. Keep the page inside it. */
    override fun onStableAreaChanged(stableArea: Rect) {
        area = Rect(stableArea)
        applyArea()
    }

    private fun applyArea() {
        val r = root ?: return
        val a = area ?: return
        if (a.width() <= 0 || a.height() <= 0) return
        r.setPadding(
            a.left.coerceAtLeast(0), a.top.coerceAtLeast(0),
            (surfW - a.right).coerceAtLeast(0), (surfH - a.bottom).coerceAtLeast(0)
        )
    }

    private fun release() {
        if (current === this) current = null
        tracker.stop()
        handler.removeCallbacks(hideFab)
        chrome?.closePopup()
        webView?.destroy()
        webView = null; chrome = null
        customView = null
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        root = null; blocker = null; fab = null; menuLayer = null; menuPanel = null; keyboard = null; banner = null
    }

    private fun buildViews(ctx: Context): View {
        val d = ctx.resources.displayMetrics.density
        fun px(dp: Int) = (dp * d).toInt()
        val frame = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }

        val wv = WebView(ctx)
        Web.setup(wv, carContext)
        wv.webViewClient = CarTvClient(carContext, "car")
        val ch = object : CarTvChrome(carContext, "car", { p ->
            frame.addView(p, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }) {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView?.let { frame.removeView(it) }
                customView = view
                customCallback = callback
                frame.addView(view, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
            override fun onHideCustomView() = hideCustomView()
        }
        wv.webChromeClient = ch
        frame.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Floating menu button. Fades out after a few seconds; any tap brings it back.
        fabSize = px(58)
        val fabView = TextView(ctx).apply {
            text = "☰"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC1E1E2E"))
                setStroke(px(2), Color.parseColor("#66FFFFFF"))
            }
            setOnClickListener { toggleMenu() }
        }
        frame.addView(fabView, FrameLayout.LayoutParams(fabSize, fabSize))

        // Menu: full-screen catcher (tap outside closes) holding the panel.
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6), px(6), px(6), px(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E1E2E")); cornerRadius = 18 * d
            }
        }
        listOf<Pair<String, () -> Unit>>(
            "Home" to { load(Web.HOME_URL) },
            "Search" to { showKeyboard() },
            "Back" to { goBack() },
            "Reload" to { reload() },
            "Move button" to { startMove() },
        ).forEach { (label, action) ->
            panel.addView(TextView(ctx).apply {
                text = label
                textSize = 20f
                setTextColor(Color.WHITE)
                minWidth = px(190)
                setPadding(px(18), px(14), px(18), px(14))
                setOnClickListener { hideMenu(); action() }
            })
        }
        val layer = FrameLayout(ctx).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hideMenu() }
        }
        layer.addView(panel, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        frame.addView(layer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val kb = OnScreenKeyboard(ctx,
            onSubmit = { q -> hideKeyboard(); load(Prefs.searchUrl(q, webView?.url)) },
            onClose = { hideKeyboard() })
        frame.addView(kb.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val ban = TextView(ctx).apply {
            text = "Tap where you want the menu button"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(px(18), px(10), px(18), px(10))
            background = GradientDrawable().apply { setColor(Color.parseColor("#EE000000")); cornerRadius = 12 * d }
            visibility = View.GONE
        }
        frame.addView(ban, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = px(12)
        })

        val block = TextView(ctx).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true // swallow taps while blocked
        }
        frame.addView(block, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeFab() }

        root = frame; webView = wv; chrome = ch; blocker = block; fab = fabView
        menuLayer = layer; menuPanel = panel; keyboard = kb; banner = ban
        wv.loadUrl(Prefs.last(carContext))
        return frame
    }

    private fun hideCustomView() {
        customView?.let { root?.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    // ---- Floating menu ----

    private val hideFab = Runnable {
        if (menuLayer?.visibility != View.VISIBLE && !moveMode) {
            fab?.animate()?.alpha(0f)?.setDuration(400)?.withEndAction { fab?.visibility = View.INVISIBLE }
        }
    }

    private fun pokeFab() {
        val f = fab ?: return
        f.animate().cancel()
        f.visibility = View.VISIBLE
        f.alpha = 1f
        handler.removeCallbacks(hideFab)
        handler.postDelayed(hideFab, 4000)
    }

    private fun placeFab() {
        val r = root ?: return
        val f = fab ?: return
        val (fx, fy) = Prefs.fab(carContext)
        val iw = r.width - r.paddingLeft - r.paddingRight
        val ih = r.height - r.paddingTop - r.paddingBottom
        f.x = r.paddingLeft + fx * (iw - fabSize).coerceAtLeast(0)
        f.y = r.paddingTop + fy * (ih - fabSize).coerceAtLeast(0)
    }

    fun toggleMenu() {
        if (blocked) return
        if (menuLayer?.visibility == View.VISIBLE) hideMenu() else showMenu()
    }

    private fun showMenu() {
        val r = root ?: return
        val f = fab ?: return
        val p = menuPanel ?: return
        val layer = menuLayer ?: return
        pokeFab()
        keyboard?.hide()
        p.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val pw = p.measuredWidth
        val ph = p.measuredHeight
        val minX = r.paddingLeft.toFloat()
        val minY = r.paddingTop.toFloat()
        val maxX = (r.width - r.paddingRight - pw).toFloat().coerceAtLeast(minX)
        val maxY = (r.height - r.paddingBottom - ph).toFloat().coerceAtLeast(minY)
        val gap = fabSize / 6f
        val x = if (f.x + fabSize / 2f > r.width / 2f) f.x - pw - gap else f.x + fabSize + gap
        val y = f.y + fabSize / 2f - ph / 2f
        // Panel coordinates are relative to the menu layer, which starts at the root's padding.
        p.x = x.coerceIn(minX, maxX) - layer.left
        p.y = y.coerceIn(minY, maxY) - layer.top
        layer.visibility = View.VISIBLE
    }

    private fun hideMenu() {
        menuLayer?.visibility = View.GONE
        pokeFab()
    }

    private fun startMove() {
        moveMode = true
        banner?.visibility = View.VISIBLE
        pokeFab()
    }

    private fun placeAt(x: Float, y: Float) {
        val r = root ?: return
        val iw = r.width - r.paddingLeft - r.paddingRight
        val ih = r.height - r.paddingTop - r.paddingBottom
        val fx = ((x - r.paddingLeft - fabSize / 2f) / (iw - fabSize).coerceAtLeast(1)).coerceIn(0f, 1f)
        val fy = ((y - r.paddingTop - fabSize / 2f) / (ih - fabSize).coerceAtLeast(1)).coerceIn(0f, 1f)
        Prefs.setFab(carContext, fx, fy)
        placeFab()
        moveMode = false
        banner?.visibility = View.GONE
        pokeFab()
    }

    // ---- Keyboard ----

    private fun showKeyboard() { hideMenu(); keyboard?.show() }
    private fun hideKeyboard() { keyboard?.hide() }

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
                menuLayer?.visibility = View.GONE
                hideKeyboard()
                b.visibility = View.VISIBLE
                b.bringToFront()
                webView?.evaluateJavascript(PositionTracker.PAUSE_JS, null)
                webView?.onPause()
            }
        }
    }

    private val blocked get() = blockState != ParkedMonitor.State.PARKED
    private val overlayOpen get() = menuLayer?.visibility == View.VISIBLE || keyboard?.isShowing == true

    // ---- Input from the car screen ----

    override fun onClick(x: Float, y: Float) {
        if (blocked) return
        if (moveMode) { placeAt(x, y); return }
        val target = root ?: return
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(down)
        down.recycle()
        pokeFab() // after DOWN, so a tap on the hidden button's spot still goes to the page
        handler.postDelayed({
            val u = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            u.source = InputDevice.SOURCE_TOUCHSCREEN
            root?.dispatchTouchEvent(u)
            u.recycle()
        }, 60)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (blocked || overlayOpen || customView != null) return
        webView?.evaluateJavascript("window.scrollBy($distanceX,$distanceY)", null)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (blocked || overlayOpen || customView != null) return
        webView?.evaluateJavascript(
            "window.scrollBy({left:${-velocityX * 0.3f},top:${-velocityY * 0.3f},behavior:'smooth'})", null
        )
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (blocked || overlayOpen) return
        webView?.zoomBy(scaleFactor.coerceIn(0.5f, 2f))
    }

    fun scrollPage(direction: Int) {
        if (blocked || overlayOpen) return
        webView?.evaluateJavascript(
            "window.scrollBy({top:${direction}*window.innerHeight*0.8,behavior:'smooth'})", null
        )
    }

    fun load(url: String) {
        chrome?.closePopup()
        hideCustomView()
        webView?.loadUrl(url)
    }

    fun reload() { webView?.reload() }

    fun goBack() {
        when {
            chrome?.closePopup() == true -> {}
            customView != null -> hideCustomView()
            webView?.canGoBack() == true -> webView?.goBack()
        }
    }
}
