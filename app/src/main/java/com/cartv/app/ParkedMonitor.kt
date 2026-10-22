package com.cartv.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock

/**
 * Uses the phone's GPS speed to decide if the car is parked.
 * Moving = over ~11 km/h. Parked again = under ~3.6 km/h for 5 seconds.
 */
class ParkedMonitor(
    private val ctx: Context,
    private val onChange: (State) -> Unit,
) : LocationListener {

    enum class State { PARKED, MOVING, NO_PERMISSION }

    private val lm = ctx.getSystemService(LocationManager::class.java)
    private var state: State? = null
    private var slowSince = 0L

    fun start() {
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) { emit(State.NO_PERMISSION); return }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            emit(State.PARKED)
        } catch (e: SecurityException) {
            emit(State.NO_PERMISSION)
        }
    }

    fun stop() {
        try { lm.removeUpdates(this) } catch (_: Exception) {}
    }

    override fun onLocationChanged(loc: Location) {
        val speed = if (loc.hasSpeed()) loc.speed else 0f // m/s
        val now = SystemClock.elapsedRealtime()
        when {
            speed >= 3f -> { slowSince = 0L; emit(State.MOVING) }
            speed < 1f -> {
                if (slowSince == 0L) slowSince = now
                if (now - slowSince >= 5000L) emit(State.PARKED)
            }
            else -> slowSince = 0L
        }
    }

    private fun emit(s: State) {
        if (s != state) { state = s; onChange(s) }
    }

    // Older Android versions call these; keep them to avoid crashes.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
