package com.cartv.app

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class CarTvService : CarAppService() {
    // Sideloaded personal app: accept any Android Auto host.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onCreateSession(): Session = CarTvSession()
}

class CarTvSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val renderer = BrowserRenderer(carContext)
        lifecycle.addObserver(renderer)
        return BrowserScreen(carContext, renderer)
    }
}
