package online.taranov.persista

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import java.util.UUID

object Ringer {
    private const val CHANNEL = "alarm"
    private const val NOTIF_ID = 1
    private const val TIMEOUT_MS = 5 * 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrators: VibratorManager? = null
    private lateinit var app: Context
    private var session: AlarmSession? = null
    private val waiting = ArrayDeque<AlarmSession>()
    var token: String? = null; private set
    var repeatToken: String? = null; private set

    var title = ""; private set
    var text = ""; private set
    var source = ""; private set
    val ringing get() = session != null // Still stoppable if every sound source failed but vibration works.

    fun start(ctx: Context, title: String, text: String, sound: String? = null, source: String = "",
              pkg: String = "", key: String = "") {
        app = ctx.applicationContext
        val incoming = AlarmSession(UUID.randomUUID().toString(), title, text, sound, source, pkg, key)
        // Keep one coherent visible alarm instead of changing the title under an older melody.
        if (ringing) { waiting.addLast(incoming); return }
        play(incoming)
    }

    private fun play(s: AlarmSession, repeat: String? = null): Boolean {
        session = s
        token = UUID.randomUUID().toString()
        repeatToken = repeat
        title = s.title
        text = s.text
        source = s.source
        val currentToken = token!!
        try {
            wakeLock = app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "persista:ring")
                .apply { acquire(TIMEOUT_MS + 10_000) }
            player = createPlayer(s.sound)
            vibrators = app.getSystemService(VibratorManager::class.java).also {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0)
                if (Build.VERSION.SDK_INT >= 33) it.defaultVibrator.vibrate(effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
                else @Suppress("DEPRECATION") it.defaultVibrator.vibrate(effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            handler.postDelayed({ stop(currentToken) }, TIMEOUT_MS)
            postNotification(currentToken)
            return player != null
        } catch (e: Exception) {
            Log.i(NotifyListener.TAG, "alarm start failed: ${e.javaClass.simpleName}")
            stop(currentToken)
            return false
        }
    }

    fun snooze(expected: String): Boolean {
        if (token != expected) return false
        val current = session ?: return false
        if (!Snooze.plan(app, current)) return false
        stop(expected)
        return true
    }

    fun stop(expected: String? = token, advance: Boolean = true) {
        if (!::app.isInitialized) return
        if (expected == null || token != expected) return
        val oldSession = session
        val oldRepeat = repeatToken
        handler.removeCallbacksAndMessages(null)
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrators?.cancel()
        vibrators = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        session = null
        token = null
        repeatToken = null
        if (oldSession != null && oldRepeat != null) Snooze.complete(app, oldSession.id, oldRepeat)
        app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        app.sendBroadcast(Intent(AlarmActivity.ACTION_CLOSE).setPackage(app.packageName).putExtra("token", expected))
        // Finish the old Activity before posting the next full-screen intent.
        if (advance) handler.post { next(app) }
        else SnoozeService.releaseIfIdle()
    }

    fun stopAll(ctx: Context) {
        waiting.clear()
        Snooze.cancelAll(ctx)
        stop(advance = false)
        SnoozeService.releaseIfIdle()
    }

    fun stopRepeat() {
        if (repeatToken != null) stop(advance = false)
    }

    fun next(ctx: Context) {
        app = ctx.applicationContext
        if (ringing) return
        if (!Calls.isOn(ctx)) { waiting.clear(); Snooze.cancelAll(ctx); SnoozeService.releaseIfIdle(); return }
        if (SnoozeService.instance != null) {
            val e = Snooze.takeReady(ctx)
            if (e != null) {
                if (!play(e.session, e.token)) Snooze.fail(ctx, e)
                return
            }
        }
        SnoozeService.releaseIfIdle()
        if (waiting.isNotEmpty()) play(waiting.removeFirst())
    }

    // A missing or unreadable chosen sound must not silence the alarm: fall back to defaults.
    private fun createPlayer(sound: String?): MediaPlayer? {
        val uris = listOfNotNull(
            sound?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        uris.forEachIndexed { i, uri ->
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(app, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                if (i > 0 && sound != null) Log.i(NotifyListener.TAG, "sound fallback to #$i")
                return mp
            } catch (e: Exception) {
                mp.release()
                Log.i(NotifyListener.TAG, "sound #$i failed: ${e.javaClass.simpleName}")
            }
        }
        return null
    }

    private fun postNotification(currentToken: String) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val full = PendingIntent.getActivity(
            app, 0,
            Intent(app, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("persista://alarm/$currentToken")).putExtra("token", currentToken),
            flags
        )
        val stop = PendingIntent.getBroadcast(app, 1, Intent(app, StopReceiver::class.java)
            .setAction(StopReceiver.ACTION_STOP).setData(Uri.parse("persista://stop/$currentToken"))
            .putExtra("token", currentToken), flags)
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(Ui.BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(Notification.Action.Builder(null, app.getString(R.string.stop), stop).build())
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
