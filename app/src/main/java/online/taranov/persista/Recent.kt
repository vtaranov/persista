package online.taranov.persista

import android.app.Notification
import android.service.notification.StatusBarNotification

data class Seen(val pkg: String, val title: String, val time: Long)

// Titles of other apps' notifications stay in process memory only: never logged or saved.
object Recent {
    private const val LIMIT = 30
    private val items = ArrayDeque<Seen>()

    fun add(pkg: String, title: String, time: Long = System.currentTimeMillis()) {
        items.removeAll { it.pkg == pkg && it.title == title }
        items.addFirst(Seen(pkg, title, time))
        while (items.size > LIMIT) items.removeLast()
    }

    // After a process restart the buffer is empty: seed it from what is in the shade now.
    fun list(): List<Seen> {
        if (items.isEmpty()) NotifyListener.instance?.let { l ->
            runCatching { l.activeNotifications }.getOrNull().orEmpty()
                .filter { accept(it, l.packageName) }
                .sortedBy { it.postTime }
                .forEach { add(it.packageName, titleOf(it.notification), it.postTime) }
        }
        return items.toList()
    }

    fun accept(sbn: StatusBarNotification, ownPkg: String): Boolean =
        sbn.packageName != ownPkg && sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

    fun titleOf(n: Notification): String =
        n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
}
