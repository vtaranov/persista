package online.taranov.persista

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// All entry points run on the main thread (activities, receivers, listener and service).
object Snooze {
    const val FIRE = "online.taranov.persista.SNOOZE_FIRE"
    const val CANCEL = "online.taranov.persista.SNOOZE_CANCEL"
    const val DELAY = 5 * 60_000L
    const val SERVICE_ID = 3
    private const val FAILURE_ID = 4
    private const val ENTRY_ID = 10 // Each entry has a distinct notification tag.
    private const val CHANNEL = "snooze"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private var serviceRequestedAt = 0L
    private val cancelled = HashSet<String>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("snooze", Context.MODE_PRIVATE)
    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
    private fun boot(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun canSchedule(ctx: Context) = alarms(ctx).canScheduleExactAlarms()

    private fun load(ctx: Context): SnoozeLedger {
        val raw = prefs(ctx).getString("entries", "[]")
        return runCatching {
            val a = JSONArray(raw)
            SnoozeLedger((0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SnoozeEntry(AlarmSession(o.getString("id"), o.getString("title"), o.getString("text"),
                    o.optString("sound").ifEmpty { null }, o.getString("source"), o.optString("pkg"), o.optString("key")),
                    o.getString("token"), o.getLong("due"), o.getInt("boot"), SnoozePhase.valueOf(o.getString("phase")))
            }.filterNot { it.token in cancelled })
        }.getOrElse {
            reportFailure(ctx)
            SnoozeLedger()
        }
    }

    private fun save(ctx: Context, state: SnoozeLedger): Boolean {
        val a = JSONArray()
        state.entries.forEach { e ->
            val s = e.session
            a.put(JSONObject().put("id", s.id).put("title", s.title).put("text", s.text)
                .put("sound", s.sound.orEmpty()).put("source", s.source).put("pkg", s.pkg).put("key", s.key)
                .put("token", e.token).put("due", e.dueElapsed).put("boot", e.boot).put("phase", e.phase.name))
        }
        return prefs(ctx).edit().putString("entries", a.toString()).commit()
    }

    private fun intent(ctx: Context, e: SnoozeEntry, action: String) =
        Intent(ctx, SnoozeReceiver::class.java).setAction(action)
            .setData(Uri.Builder().scheme("persista").authority("snooze")
                .appendPath(e.session.id).appendPath(e.token).build())

    private fun operation(ctx: Context, e: SnoozeEntry) = PendingIntent.getBroadcast(ctx, 0, intent(ctx, e, FIRE), FLAGS)
    private fun hasOperation(ctx: Context, e: SnoozeEntry) = PendingIntent.getBroadcast(ctx, 0, intent(ctx, e, FIRE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), FLAGS)
    private fun schedule(ctx: Context, e: SnoozeEntry) {
        val at = e.wallTime(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        alarms(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, open(ctx)), operation(ctx, e))
    }

    // A failed write or AlarmManager call must leave the current alarm playing.
    fun plan(ctx: Context, session: AlarmSession): Boolean {
        if (!canSchedule(ctx) || !Calls.isOn(ctx) || boot(ctx) < 0) return false
        val before = load(ctx)
        val e = SnoozeEntry(session, UUID.randomUUID().toString(), SystemClock.elapsedRealtime() + DELAY, boot(ctx))
        val planned = SnoozeTransaction.plan(before, e, { save(ctx, it) }, {
            schedule(ctx, it)
            showEntry(ctx, it)
        }, { alarms(ctx).cancel(operation(ctx, it)) })
        if (!planned) nm(ctx).cancel(session.id, ENTRY_ID)
        return planned
    }

    fun deliver(ctx: Context, id: String?, token: String?) {
        reconcile(ctx)
        if (!Calls.isOn(ctx)) { cancelAll(ctx); return }
        val before = load(ctx)
        val e = before.find(id, token) ?: return
        if (e.phase != SnoozePhase.WAITING) return
        if (SystemClock.elapsedRealtime() < e.dueElapsed) {
            // RTC may move before TIME_SET is processed. Never ring before five elapsed minutes.
            try { schedule(ctx, e) } catch (_: Exception) { fail(ctx, e) }
            return
        }
        if (!save(ctx, before.deliver(id, token, SystemClock.elapsedRealtime()))) { fail(ctx, e); return }
        try {
            showEntry(ctx, e.copy(phase = SnoozePhase.READY))
            serviceRequestedAt = SystemClock.elapsedRealtime()
            ctx.startForegroundService(Intent(ctx, SnoozeService::class.java))
        } catch (_: Exception) { serviceRequestedAt = 0; fail(ctx, e) }
    }

    fun takeReady(ctx: Context): SnoozeEntry? {
        if (!Calls.isOn(ctx)) return null
        val before = load(ctx)
        val e = before.nextReady() ?: return null
        if (!save(ctx, before.claim(e.session.id, e.token))) { fail(ctx, e); return null }
        nm(ctx).cancel(e.session.id, ENTRY_ID)
        return e
    }

    fun hasReady(ctx: Context) = load(ctx).nextReady() != null
    fun failReady(ctx: Context) {
        load(ctx).entries.filter { it.phase == SnoozePhase.READY }.forEach { fail(ctx, it) }
    }
    fun containsSource(ctx: Context, pkg: String, key: String) = load(ctx).entries.any {
        it.session.pkg == pkg && it.session.key == key
    }

    fun complete(ctx: Context, id: String, token: String) {
        val before = load(ctx)
        if (before.find(id, token) == null) return // A new snooze generation is already stored.
        if (!save(ctx, before.remove(id, token))) reportFailure(ctx)
        nm(ctx).cancel(id, ENTRY_ID)
    }

    fun cancel(ctx: Context, id: String?, token: String?) {
        val before = load(ctx)
        val e = before.find(id, token) ?: return
        // Cancel the OS alarm even if storage fails; a tombstone blocks delayed intents in memory too.
        alarms(ctx).cancel(operation(ctx, e))
        cancelled.add(e.token)
        if (!save(ctx, before.remove(id, token))) reportFailure(ctx)
        nm(ctx).cancel(e.session.id, ENTRY_ID)
        SnoozeService.releaseIfIdle()
    }

    fun cancelAll(ctx: Context) {
        val before = load(ctx)
        before.entries.forEach { e ->
            alarms(ctx).cancel(operation(ctx, e))
            cancelled.add(e.token)
            nm(ctx).cancel(e.session.id, ENTRY_ID)
        }
        if (!save(ctx, SnoozeLedger())) reportFailure(ctx)
    }

    fun fail(ctx: Context, e: SnoozeEntry) {
        cancel(ctx, e.session.id, e.token)
        reportFailure(ctx)
    }

    // No replay after reboot, force-stop, lost permission or an interrupted playback.
    // Ordinary process death while WAITING keeps its system PendingIntent and private snapshot.
    fun reconcile(ctx: Context, clockChanged: Boolean = false) {
        val before = load(ctx)
        val access = canSchedule(ctx)
        var lost = false
        val keep = before.entries.filter { e ->
            val valid = access && Calls.isOn(ctx) && e.boot == boot(ctx) &&
                when (e.phase) {
                    SnoozePhase.WAITING -> hasOperation(ctx, e) && SystemClock.elapsedRealtime() <= e.dueElapsed + DELAY
                    SnoozePhase.READY -> SnoozeService.instance != null ||
                        (serviceRequestedAt > 0 && SystemClock.elapsedRealtime() - serviceRequestedAt < 10_000)
                    SnoozePhase.PLAYING -> Ringer.repeatToken == e.token
                }
            if (!valid) {
                lost = true
                alarms(ctx).cancel(operation(ctx, e))
                nm(ctx).cancel(e.session.id, ENTRY_ID)
            }
            valid
        }
        if (keep.size != before.entries.size && !save(ctx, SnoozeLedger(keep))) reportFailure(ctx)
        if (lost) reportFailure(ctx)
        keep.forEach { e ->
            if (clockChanged && e.phase == SnoozePhase.WAITING) {
                try { schedule(ctx, e) } catch (_: Exception) { fail(ctx, e); return@forEach }
            }
            if (e.phase != SnoozePhase.PLAYING) showEntry(ctx, e)
        }
    }

    private fun channel(ctx: Context) {
        nm(ctx).createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.channel_snooze),
            NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
    }

    private fun showEntry(ctx: Context, e: SnoozeEntry) {
        channel(ctx)
        val at = e.wallTime(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val title = if (e.phase == SnoozePhase.READY) ctx.getString(R.string.snooze_waiting)
            else ctx.getString(R.string.snooze_until, Calls.time(at))
        val cancel = PendingIntent.getBroadcast(ctx, 0, intent(ctx, e, CANCEL), FLAGS)
        nm(ctx).notify(e.session.id, ENTRY_ID, Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm).setContentTitle(title).setContentText(e.session.source)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open(ctx))
            .addAction(Notification.Action.Builder(null, ctx.getString(R.string.cancel), cancel).build()).build())
    }

    fun serviceNotification(ctx: Context): Notification {
        channel(ctx)
        return Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(ctx.getString(R.string.snooze_service_title))
            .setContentText(ctx.getString(R.string.snooze_service_text))
            .setContentIntent(open(ctx)).setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private fun reportFailure(ctx: Context) {
        channel(ctx)
        nm(ctx).notify(FAILURE_ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(ctx.getString(R.string.snooze_failed_title))
            .setContentText(ctx.getString(R.string.snooze_failed_text))
            .setContentIntent(open(ctx)).setAutoCancel(true).build())
    }
}
