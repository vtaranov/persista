package online.taranov.persista

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotifyListener : NotificationListenerService() {
    // Calendar re-posts the same reminder on updates; ring once per key.
    private val recent = HashMap<String, Long>()

    override fun onListenerConnected() {
        instance = this
        Calls.refresh(this)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Recent.accept(sbn, packageName)) return
        val n = sbn.notification
        val title = Recent.titleOf(n)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        Recent.add(sbn.packageName, title)
        if (!Calls.isOn(this)) {
            Log.i(TAG, "paused, skip ${sbn.packageName}")
            return
        }
        val rules = Rules.load(this)
        val idx = rules.indexOfFirst { it.matches(sbn.packageName, title, text) }
        if (idx < 0) return
        val rule = rules[idx]

        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > DEDUP_MS }
        if (recent.containsKey(sbn.key)) return
        recent[sbn.key] = now

        Log.i(TAG, "match ${sbn.packageName} rule=#$idx '${rule.keyword}' sound=${rule.sound != null}")
        val app = AppPicker.label(this, sbn.packageName)
        Ringer.start(this, title.ifBlank { app }, text, rule.sound, app)
    }

    companion object {
        const val TAG = "Persista"
        private const val DEDUP_MS = 30 * 60_000L
        var instance: NotifyListener? = null
            private set
    }
}
