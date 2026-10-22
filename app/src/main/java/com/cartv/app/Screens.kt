package com.cartv.app

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat

/**
 * The browser fills the car display. Android Auto requires one fixed button in the
 * corner, so it's a small menu icon; the main controls are in the floating menu.
 */
class BrowserScreen(carContext: CarContext, private val renderer: BrowserRenderer) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(iconButton(R.drawable.ic_menu) { renderer.toggleMenu() })
            .build()

        val mapActions = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(iconButton(R.drawable.ic_up) { renderer.scrollPage(-1) })
            .addAction(iconButton(R.drawable.ic_down) { renderer.scrollPage(1) })
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            .setMapActionStrip(mapActions)
            .setPanModeListener { }
            .build()
    }

    private fun iconButton(res: Int, onClick: () -> Unit) =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick() }
            .build()
}
