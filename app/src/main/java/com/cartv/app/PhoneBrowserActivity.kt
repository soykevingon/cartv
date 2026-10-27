package com.cartv.app

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/** Same browser as the car, on the phone: sign in, pick a video, send it to the car. */
class PhoneBrowserActivity : Activity() {

    private lateinit var wv: WebView
    private lateinit var urlBox: EditText
    private lateinit var chrome: CarTvChrome
    private var firstResume = true
    private val tracker = PositionTracker(this, "phone") { if (::wv.isInitialized) wv else null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { onClick() }
        }

        urlBox = EditText(this).apply {
            setSingleLine()
            hint = "Website or search"
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(); true }
        }
        bar.addView(btn("←") { back() })
        bar.addView(btn("⌂") { wv.loadUrl(Web.HOME_URL) })
        bar.addView(urlBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("Send to car") { sendToCar() })

        val container = FrameLayout(this)
        wv = WebView(this)
        Web.setup(wv, this)
        wv.webViewClient = object : CarTvClient(this, "phone") {
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (url != null && !urlBox.hasFocus()) urlBox.setText(if (url.startsWith(Web.HOME_URL)) "" else url)
            }
        }
        chrome = CarTvChrome(this, "phone") { p ->
            container.addView(p, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        wv.webChromeClient = chrome
        container.addView(wv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        root.addView(bar)
        root.addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        wv.onResume()
        // First open, or the car played something since: continue from the car's spot.
        if (firstResume || Prefs.lastWriter(this) == "car") {
            firstResume = false
            val url = Prefs.last(this)
            wv.loadUrl(url)
            Prefs.setLast(this, url, "phone")
        }
        tracker.start()
    }

    override fun onPause() {
        tracker.saveNow()
        tracker.stop()
        CookieManager.getInstance().flush()
        wv.onPause()
        super.onPause()
    }

    override fun onDestroy() { chrome.closePopup(); wv.destroy(); super.onDestroy() }

    private fun go() {
        val t = urlBox.text.toString()
        if (t.isBlank()) return
        chrome.closePopup()
        wv.loadUrl(Prefs.resolve(t))
        urlBox.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(urlBox.windowToken, 0)
    }

    private fun back() {
        if (chrome.closePopup()) return
        if (wv.canGoBack()) wv.goBack()
    }

    private fun sendToCar() {
        val base = wv.url ?: return
        wv.evaluateJavascript(PositionTracker.ANY_TIME_JS) { r ->
            val t = PositionTracker.parse(r)
            val url = if (t > 0 && PositionTracker.isWatch(base)) PositionTracker.withTime(base, t) else base
            wv.evaluateJavascript(PositionTracker.PAUSE_JS, null) // don't play on both screens
            Prefs.setLast(this, url, "car")
            CookieManager.getInstance().flush()
            val car = BrowserRenderer.current
            if (car != null) {
                car.load(url)
                Toast.makeText(this, "Sent to car", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Saved. It opens next time you start CarTV in the car.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            chrome.closePopup() -> {}
            wv.canGoBack() -> wv.goBack()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }
}
