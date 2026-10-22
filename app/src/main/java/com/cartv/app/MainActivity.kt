package com.cartv.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Phone-side setup: permissions, sign-in browser, ad blocker, home page tiles. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun label(t: String, size: Float = 16f) = TextView(this).apply {
            text = t; textSize = size; setPadding(0, pad, 0, pad / 3)
        }
        fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }

        layout.addView(TextView(this).apply { text = "CarTV"; textSize = 28f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, pad / 2, 0, pad / 2) }
        layout.addView(status)
        layout.addView(btn("Allow location") { requestLocation() })
        layout.addView(btn("Allow microphone (voice search)") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
        })

        layout.addView(label("Browser"))
        layout.addView(btn("Open browser — sign in, pick a video, send it to the car") {
            startActivity(Intent(this, PhoneBrowserActivity::class.java))
        })
        layout.addView(CheckBox(this).apply {
            text = "Ad blocker"
            isChecked = Prefs.adblock(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setAdblock(this@MainActivity, on)
                Toast.makeText(this@MainActivity, "Applies next time CarTV opens", Toast.LENGTH_SHORT).show()
            }
        })

        layout.addView(label("Home page tiles — one per line:  Name | website"))
        val sites = EditText(this).apply {
            setText(Prefs.sitesRaw(this@MainActivity))
            minLines = 3
            gravity = Gravity.TOP
        }
        layout.addView(sites)
        layout.addView(btn("Save tiles") {
            Prefs.setSitesRaw(this@MainActivity, sites.text.toString())
            Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
        })

        layout.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
            text = "Video is blocked while the car is moving (GPS speed). " +
                "Choose \"Allow all the time\" for location so it works while Android Auto is running.\n\n" +
                "Netflix won't play: it blocks Android browsers, and its protected video can't be drawn on the car screen."
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() {
        super.onResume()
        val loc = when {
            !has(Manifest.permission.ACCESS_FINE_LOCATION) -> "Location: not allowed (video stays blocked)"
            Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
                "Location: only while open. Tap again and pick \"Allow all the time\"."
            else -> "Location: allowed"
        }
        val mic = if (has(Manifest.permission.RECORD_AUDIO)) "Microphone: allowed" else "Microphone: not allowed"
        status.text = "$loc\n$mic"
    }

    private fun requestLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1
            )
        } else if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        onResume()
    }

    private fun has(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}
