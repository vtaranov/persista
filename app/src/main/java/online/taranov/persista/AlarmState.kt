package online.taranov.persista

// Framework-free state: private notification data never travels in PendingIntent extras.
data class AlarmSession(
    val id: String,
    val title: String,
    val text: String,
    val sound: String?,
    val source: String,
    val pkg: String = "",
    val key: String = "",
)

enum class SnoozePhase { WAITING, READY, PLAYING }

data class SnoozeEntry(
    val session: AlarmSession,
    val token: String,
    val dueElapsed: Long,
    val boot: Int,
    val phase: SnoozePhase = SnoozePhase.WAITING,
) {
    fun matches(id: String?, generation: String?) = session.id == id && token == generation
    fun wallTime(nowWall: Long, nowElapsed: Long) = nowWall + (dueElapsed - nowElapsed).coerceAtLeast(0)
}

data class SnoozeLedger(val entries: List<SnoozeEntry> = emptyList()) {
    fun put(entry: SnoozeEntry) = copy(entries = entries.filterNot { it.session.id == entry.session.id } + entry)
    fun find(id: String?, token: String?) = entries.firstOrNull { it.matches(id, token) }
    fun remove(id: String?, token: String?) = copy(entries = entries.filterNot { it.matches(id, token) })
    fun deliver(id: String?, token: String?, nowElapsed: Long): SnoozeLedger {
        val e = find(id, token) ?: return this
        if (e.phase != SnoozePhase.WAITING || nowElapsed < e.dueElapsed) return this
        return put(e.copy(phase = SnoozePhase.READY))
    }
    fun nextReady() = entries.filter { it.phase == SnoozePhase.READY }.minByOrNull { it.dueElapsed }
    fun claim(id: String, token: String): SnoozeLedger {
        val e = find(id, token) ?: return this
        return if (e.phase == SnoozePhase.READY) put(e.copy(phase = SnoozePhase.PLAYING)) else this
    }
}

object SnoozeTransaction {
    // The current playback is stopped by the caller only after this returns true.
    fun plan(before: SnoozeLedger, e: SnoozeEntry, write: (SnoozeLedger) -> Boolean,
             arm: (SnoozeEntry) -> Unit, disarm: (SnoozeEntry) -> Unit): Boolean {
        if (!write(before.put(e))) { write(before); return false }
        try { arm(e) }
        catch (_: Exception) {
            runCatching { disarm(e) }
            write(before)
            return false
        }
        before.entries.filter { it.session.id == e.session.id }.forEach { runCatching { disarm(it) } }
        return true
    }
}
