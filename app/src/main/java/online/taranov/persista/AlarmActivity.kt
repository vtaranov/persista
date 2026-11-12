package online.taranov.persista

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

// Always dark, whatever the system theme: the alarm should look the same every time.
class AlarmActivity : Activity() {
    private val closer = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getStringExtra("token") == alarmToken) finish()
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private var alarmToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Ringer.ringing) { finish(); return }
        registerReceiver(closer, IntentFilter(ACTION_CLOSE),
            if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0)
        Ui.setupWindow(this, BG, lightBars = false)
        renderAlarm()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renderAlarm()
    }

    private fun renderAlarm() {
        handler.removeCallbacksAndMessages(null)
        alarmToken = intent.getStringExtra("token")
        if (!Ringer.ringing || alarmToken != Ringer.token) { finish(); return }
        val currentToken = alarmToken!!
        fun dp(v: Int) = Ui.dp(this, v)
        clock = Ui.text(this, "", 76f, WHITE).apply { gravity = Gravity.CENTER }
        val error = Ui.text(this, "", 14f, GREY).apply { gravity = Gravity.CENTER }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(20))
            Ui.applyInsets(this)
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(clock)
                    addView(Ui.text(context, Ringer.source, 17f, GREY).apply { gravity = Gravity.CENTER })
                    addView(Ui.text(context, Ringer.title, 28f, WHITE, bold = true).apply {
                        gravity = Gravity.CENTER; setPadding(0, dp(28), 0, 0)
                    })
                    addView(Ui.text(context, Ringer.text, 18f, GREY).apply {
                        gravity = Gravity.CENTER
                        maxLines = 4
                        setPadding(0, dp(10), 0, dp(24))
                    })
                    addView(error)
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(Ui.pill(context, getString(R.string.snooze_five_minutes), GREEN, WHITE, 0x33FFFFFF) {
                if (Ringer.snooze(currentToken)) finish()
                else error.text = getString(if (Snooze.canSchedule(context)) R.string.snooze_error else R.string.snooze_no_access)
            }.apply {
                textSize = 20f
                minHeight = dp(68)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12); bottomMargin = dp(12) })
            addView(Ui.pill(context, getString(R.string.stop), RED, WHITE, 0x33FFFFFF) { Ringer.stop(currentToken); finish() }.apply {
                textSize = 22f
                minHeight = dp(68)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        tick()
    }

    override fun onResume() {
        super.onResume()
        if (!Ringer.ringing || alarmToken != Ringer.token || !::clock.isInitialized) { finish(); return }
        handler.removeCallbacksAndMessages(null)
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
        private const val GREEN = 0xFF237A42.toInt()
    }
}
