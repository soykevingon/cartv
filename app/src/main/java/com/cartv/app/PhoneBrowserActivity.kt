package com.cartv.app

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/** Same browser as the car, on the phone: sign in to your accounts, pick a video, send it to the car. */
class PhoneBrowserActivity : Activity() {

    private lateinit var wv: WebView
    private lateinit var urlBox: EditText

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
        bar.addView(btn("←") { if (wv.canGoBack()) wv.goBack() })
        bar.addView(btn("⌂") { wv.loadUrl(Web.HOME_URL) })
        bar.addView(urlBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("Send to car") { sendToCar() })

        wv = WebView(this)
        Web.setup(wv, this)
        wv.webViewClient = object : CarTvClient(this) {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!urlBox.hasFocus()) urlBox.setText(if (url.startsWith(Web.HOME_URL)) "" else url)
            }
        }
        wv.webChromeClient = CarTvChrome(this)

        root.addView(bar)
        root.addView(wv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        wv.loadUrl(Prefs.last(this))
    }

    private fun go() {
        val t = urlBox.text.toString()
        if (t.isBlank()) return
        wv.loadUrl(Prefs.resolve(t))
        urlBox.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(urlBox.windowToken, 0)
    }

    private fun sendToCar() {
        val url = wv.url ?: return
        Prefs.setLast(this, url)
        CookieManager.getInstance().flush()
        val car = BrowserRenderer.current
        if (car != null) {
            car.load(url)
            Toast.makeText(this, "Sent to car", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Saved. It opens next time you start CarTV in the car.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() { super.onResume(); wv.onResume() }
    override fun onPause() { CookieManager.getInstance().flush(); wv.onPause(); super.onPause() }
    override fun onDestroy() { wv.destroy(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (wv.canGoBack()) wv.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
