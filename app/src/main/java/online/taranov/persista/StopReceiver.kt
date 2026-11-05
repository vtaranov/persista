package online.taranov.persista

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = when (intent.action) {
        Calls.ACTION_RESUME -> Calls.resume(context)
        Calls.ACTION_EXTEND -> Calls.extend(context)
        else -> Ringer.stop()
    }
}
