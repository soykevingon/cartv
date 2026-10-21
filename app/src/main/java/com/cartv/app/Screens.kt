package com.cartv.app

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat

/** Main screen: the browser fills the car display; buttons sit on the side. */
class BrowserScreen(carContext: CarContext, private val renderer: BrowserRenderer) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(button("Go") { screenManager.push(UrlScreen(carContext, renderer)) })
            .addAction(button("Back") { renderer.goBack() })
            .addAction(button("Home") { renderer.goHome() })
            .addAction(button("Reload") { renderer.reload() })
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

    private fun button(title: String, onClick: () -> Unit) =
        Action.Builder().setTitle(title).setOnClickListener { onClick() }.build()

    private fun iconButton(res: Int, onClick: () -> Unit) =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { onClick() }
            .build()
}

/** Type a URL or search, or pick a shortcut. */
class UrlScreen(carContext: CarContext, private val renderer: BrowserRenderer) : Screen(carContext) {

    private val shortcuts = listOf(
        "YouTube" to "https://m.youtube.com",
        "Google" to "https://www.google.com",
        "Twitch" to "https://m.twitch.tv",
        "Last page" to null,
    )

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        for ((name, url) in shortcuts) {
            list.addItem(
                Row.Builder().setTitle(name).setOnClickListener {
                    renderer.load(url ?: Prefs.last(carContext))
                    screenManager.pop()
                }.build()
            )
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isNotBlank()) renderer.load(Prefs.resolve(searchText))
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Type a website or search")
            .setShowKeyboardByDefault(false)
            .setItemList(list.build())
            .build()
    }
}
