package online.taranov.persista

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView

// Hand-made components: the stock DeviceDefault widgets on One UI are grey all-caps Material 2015 buttons.
object Ui {
    const val BRAND = 0xFFE5533D.toInt()

    class Palette(
        val bg: Int, val surface: Int, val text: Int, val text2: Int, val line: Int, val press: Int,
        val primaryBg: Int, val primaryFg: Int,
        val okBg: Int, val okFg: Int, val warnBg: Int, val warnFg: Int, val danger: Int,
    )

    private val LIGHT = Palette(
        bg = 0xFFF4F4F6.toInt(), surface = 0xFFFFFFFF.toInt(), text = 0xFF1C1C1E.toInt(), text2 = 0xFF6C6C72.toInt(),
        line = 0xFFE4E4E8.toInt(), press = 0x1F000000,
        primaryBg = 0xFF526779.toInt(), primaryFg = 0xFFFFFFFF.toInt(),
        okBg = 0xFFDDF3E3.toInt(), okFg = 0xFF1B6B33.toInt(), warnBg = 0xFFFFEFCF.toInt(), warnFg = 0xFF7A4A00.toInt(),
        danger = 0xFFD93025.toInt(),
    )
    private val DARK = Palette(
        bg = 0xFF0E0E10.toInt(), surface = 0xFF1E1E21.toInt(), text = 0xFFF2F2F4.toInt(), text2 = 0xFF9E9EA6.toInt(),
        line = 0xFF2E2E32.toInt(), press = 0x2EFFFFFF,
        primaryBg = 0xFF9CB0C1.toInt(), primaryFg = 0xFF15212B.toInt(),
        okBg = 0xFF16331F.toInt(), okFg = 0xFF8FDCA6.toInt(), warnBg = 0xFF3A2C0C.toInt(), warnFg = 0xFFF5C66B.toInt(),
        danger = 0xFFFF6B5E.toInt(),
    )

    fun night(ctx: Context) =
        ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun palette(ctx: Context) = if (night(ctx)) DARK else LIGHT

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(ctx: Context, color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
    }

    fun clickable(view: View, pressColor: Int, radiusDp: Int, bg: Int?, onClick: () -> Unit) {
        val ctx = view.context
        view.background = RippleDrawable(
            ColorStateList.valueOf(pressColor),
            bg?.let { rounded(ctx, it, radiusDp) },
            rounded(ctx, 0xFFFFFFFF.toInt(), radiusDp),
        )
        view.setOnClickListener { onClick() }
    }

    fun text(ctx: Context, s: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, 600, false)
    }

    fun pill(ctx: Context, label: String, bg: Int, fg: Int, press: Int, onClick: () -> Unit) =
        text(ctx, label, 15f, fg, bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(ctx, 44)
            setPadding(dp(ctx, 20), 0, dp(ctx, 20), 0)
            clickable(this, press, 22, bg, onClick)
        }

    fun link(ctx: Context, label: String, color: Int, press: Int, onClick: () -> Unit) =
        text(ctx, label, 15f, color, bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(ctx, 44)
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
            clickable(this, press, 22, null, onClick)
        }

    fun chip(ctx: Context, s: String, p: Palette) = text(ctx, s, 12f, p.text2).apply {
        background = rounded(ctx, p.line, 10)
        setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
    }

    fun card(ctx: Context, color: Int) = LinearLayout(ctx).apply {
        background = rounded(ctx, color, 20)
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
    }

    fun divider(ctx: Context, p: Palette) = View(ctx).apply {
        setBackgroundColor(p.line)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    // targetSdk 35+ always draws edge to edge: keep content clear of the status and navigation bars.
    fun applyInsets(view: View) {
        val l = view.paddingLeft; val t = view.paddingTop; val r = view.paddingRight; val b = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
            insets
        }
    }

    fun setupWindow(activity: Activity, bg: Int, lightBars: Boolean) {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE)
        activity.window.decorView.setBackgroundColor(bg)
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        activity.window.insetsController?.setSystemBarsAppearance(if (lightBars) mask else 0, mask)
    }
}
