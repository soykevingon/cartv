package com.cartv.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Phone-side screen: grant location (for the parked lock) and set the home page. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        layout.addView(TextView(this).apply { text = "CarTV"; textSize = 28f })
        status = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad) }
        layout.addView(status)

        layout.addView(Button(this).apply {
            text = "Allow location"
            setOnClickListener { requestLocation() }
        })

        layout.addView(TextView(this).apply { text = "\nHome page"; textSize = 16f })
        val home = EditText(this).apply { setText(Prefs.home(this@MainActivity)); setSingleLine() }
        layout.addView(home)
        layout.addView(Button(this).apply {
            text = "Save home page"
            setOnClickListener {
                Prefs.setHome(this@MainActivity, Prefs.resolve(home.text.toString()))
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        })

        layout.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
            text = "Video is blocked while the car is moving (GPS speed). " +
                "Choose \"Allow all the time\" so it works while Android Auto is on the car screen.\n\n" +
                "Netflix won't play here (it requires DRM a browser view can't provide). YouTube works."
        })
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        status.text = when {
            !has(Manifest.permission.ACCESS_FINE_LOCATION) -> "Location: not allowed (video stays blocked)"
            Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ->
                "Location: only while open. Tap again and pick \"Allow all the time\"."
            else -> "Location: allowed. Ready."
        }
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
