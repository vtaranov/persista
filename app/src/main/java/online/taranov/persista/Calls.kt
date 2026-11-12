package online.taranov.persista

import android.app.AlertDialog
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Calls are paused only until a moment in time, never indefinitely: a forgotten pause must not cost a meeting.
object Calls {
    private const val PREFS = "state"
    private const val KEY = "pausedUntil"
    private const val CHANNEL = "state"
    private const val NOTIF_ID = 2
    private const val HOUR = 3_600_000L
    private const val MORNING_HOUR = 8
    const val ACTION_RESUME = "online.taranov.persista.RESUME"
    const val ACTION_EXTEND = "online.taranov.persista.EXTEND"

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pausedUntil(ctx: Context): Long = prefs(ctx).getLong(KEY, 0)

    fun isOn(ctx: Context) = System.currentTimeMillis() >= pausedUntil(ctx)

    fun pause(ctx: Context, until: Long) {
        save(ctx, until)
        Ringer.stopAll(ctx)
        refresh(ctx)
    }

    fun resume(ctx: Context) {
        save(ctx, 0)
        refresh(ctx)
    }

    fun extend(ctx: Context) = pause(ctx, maxOf(pausedUntil(ctx), System.currentTimeMillis()) + HOUR)

    fun morning(): Long {
        val now = ZonedDateTime.now()
        val today = now.truncatedTo(ChronoUnit.DAYS).withHour(MORNING_HOUR)
        return (if (now.isBefore(today)) today else today.plusDays(1)).toInstant().toEpochMilli()
    }

    fun time(until: Long): String = DateFormat.format("HH:mm", until).toString()

    fun chooser(ctx: Context, done: () -> Unit): Dialog {
        val now = System.currentTimeMillis()
        val options = listOf(
            ctx.getString(R.string.pause_1h) to now + HOUR,
            ctx.getString(R.string.pause_3h) to now + 3 * HOUR,
            ctx.getString(R.string.pause_morning, "%02d:00".format(MORNING_HOUR)) to morning(),
        )
        return AlertDialog.Builder(ctx)
            .setTitle(R.string.pause_title)
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> pause(ctx, options[i].second); done() }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    // Syncs the "paused" notification and the tile with the stored state.
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        val until = pausedUntil(app)
        val left = until - System.currentTimeMillis()
        if (left <= 0) {
            nm.cancel(NOTIF_ID)
        } else {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.channel_paused), NotificationManager.IMPORTANCE_LOW))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            fun action(a: String, code: Int) =
                PendingIntent.getBroadcast(app, code, Intent(app, StopReceiver::class.java).setAction(a), flags)
            val open = PendingIntent.getActivity(app, 3, Intent(app, MainActivity::class.java), flags)
            nm.notify(NOTIF_ID, Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setColor(Ui.BRAND)
                .setContentTitle(app.getString(R.string.paused_notif_title, time(until)))
                .setContentText(app.getString(R.string.paused_text))
                .setOngoing(true)
                .setTimeoutAfter(left)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, app.getString(R.string.resume), action(ACTION_RESUME, 4)).build())
                .addAction(Notification.Action.Builder(null, app.getString(R.string.extend), action(ACTION_EXTEND, 5)).build())
                .build())
        }
        TileService.requestListeningState(app, ComponentName(app, CallsTile::class.java))
    }

    private fun save(ctx: Context, until: Long) = prefs(ctx).edit().putLong(KEY, until).apply()
}
