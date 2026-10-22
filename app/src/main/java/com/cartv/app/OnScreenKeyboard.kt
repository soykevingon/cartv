package com.cartv.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** A keyboard drawn on the car screen (Android Auto gives this app no keyboard of its own). */
class OnScreenKeyboard(
    ctx: Context,
    private val onSubmit: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val d = ctx.resources.displayMetrics.density
    private var text = ""
    private val display: TextView
    val view: LinearLayout

    init {
        view = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F00F0F17"))
            setPadding(px(10), px(10), px(10), px(10))
            isClickable = true // swallow taps that miss a key
            visibility = View.GONE
        }
        display = TextView(ctx).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16), 0, px(16), 0)
            background = rounded("#2A2A3A")
        }
        view.addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.1f).apply {
            setMargins(px(3), px(3), px(3), px(6))
        })

        for (row in listOf("1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm.-")) {
            val r = row(ctx)
            row.forEach { c -> r.addView(key(ctx, c.toString(), "#33334A") { type(c.toString()) }, keyLp(1f)) }
            view.addView(r, rowLp())
        }
        val last = row(ctx)
        last.addView(key(ctx, "⌫", "#33334A") { text = text.dropLast(1); refresh() }, keyLp(1.5f))
        last.addView(key(ctx, "space", "#33334A") { type(" ") }, keyLp(4f))
        last.addView(key(ctx, "Search", "#3D6BE0") { if (text.isNotBlank()) onSubmit(text) }, keyLp(2.5f))
        last.addView(key(ctx, "✕", "#5A2A2A") { onClose() }, keyLp(1.5f))
        view.addView(last, rowLp())
    }

    fun show() { text = ""; refresh(); view.visibility = View.VISIBLE }
    fun hide() { view.visibility = View.GONE }
    val isShowing get() = view.visibility == View.VISIBLE

    private fun type(s: String) { text += s; refresh() }
    private fun refresh() { display.text = if (text.isEmpty()) "Search YouTube or type a website" else "$text▏" ;
        display.alpha = if (text.isEmpty()) 0.5f else 1f }

    private fun row(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private fun rowLp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    private fun keyLp(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w).apply {
        setMargins(px(3), px(3), px(3), px(3))
    }

    private fun key(ctx: Context, label: String, color: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        textSize = 20f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = rounded(color)
        setOnClickListener { onClick() }
    }

    private fun rounded(color: String) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = 10 * d
    }

    private fun px(dp: Int) = (dp * d).toInt()
}
