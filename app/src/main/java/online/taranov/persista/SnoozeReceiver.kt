package online.taranov.persista

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = intent.data?.pathSegments.orEmpty()
        when (intent.action) {
            Snooze.FIRE -> if (parts.size == 2) Snooze.deliver(context, parts[0], parts[1])
            Snooze.CANCEL -> if (parts.size == 2) Snooze.cancel(context, parts[0], parts[1])
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Snooze.reconcile(context, clockChanged = true)
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Snooze.reconcile(context)
        }
    }
}
