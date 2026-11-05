package online.taranov.persista

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

// Always dark, whatever the system theme: the alarm should look the same every time.
class AlarmActivity : Activity() {
    private val closer = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Ringer.ringing) { finish(); return }
        registerReceiver(closer, IntentFilter(ACTION_CLOSE), RECEIVER_NOT_EXPORTED)
        Ui.setupWindow(this, BG, lightBars = false)

        fun dp(v: Int) = Ui.dp(this, v)
        fun weight() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        clock = Ui.text(this, "", 76f, WHITE).apply { gravity = Gravity.CENTER }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(48), dp(28), dp(32))
            Ui.applyInsets(this)
            addView(clock)
            addView(Ui.text(context, Ringer.source, 17f, GREY).apply { gravity = Gravity.CENTER })
            addView(Space(context), weight())
            addView(Ui.text(context, Ringer.title, 28f, WHITE, bold = true).apply { gravity = Gravity.CENTER })
            addView(Ui.text(context, Ringer.text, 18f, GREY).apply {
                gravity = Gravity.CENTER
                maxLines = 4
                setPadding(0, dp(10), 0, 0)
            })
            addView(Space(context), weight())
            addView(Ui.pill(context, getString(R.string.stop), RED, WHITE, 0x33FFFFFF) { Ringer.stop(); finish() }.apply {
                textSize = 22f
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(68)))
        })
    }

    override fun onResume() {
        super.onResume()
        tick()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        clock.text = DateFormat.format("HH:mm", now)
        handler.postDelayed({ tick() }, 60_000 - now % 60_000 + 50)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closer) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE = "online.taranov.persista.CLOSE"
        private const val BG = 0xFF0B0B0D.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val GREY = 0xFFA6A6AE.toInt()
        private const val RED = 0xFFE5484D.toInt()
    }
}
