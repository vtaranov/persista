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
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log

object Ringer {
    private const val CHANNEL = "alarm"
    private const val NOTIF_ID = 1
    private const val TIMEOUT_MS = 5 * 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrators: VibratorManager? = null
    private lateinit var app: Context

    var title = ""; private set
    var text = ""; private set
    var source = ""; private set
    val ringing get() = player != null

    fun start(ctx: Context, title: String, text: String, sound: String? = null, source: String = "") {
        app = ctx.applicationContext
        this.title = title
        this.text = text
        this.source = source
        if (player == null) {
            wakeLock = app.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "persista:ring")
                .apply { acquire(TIMEOUT_MS + 10_000) }
            player = createPlayer(sound)
            vibrators = app.getSystemService(VibratorManager::class.java).also {
                it.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                )
            }
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stop() }, TIMEOUT_MS)
        postNotification()
    }

    fun stop() {
        if (!::app.isInitialized) return
        handler.removeCallbacksAndMessages(null)
        player?.run { stop(); release() }
        player = null
        vibrators?.cancel()
        vibrators = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        app.sendBroadcast(Intent(AlarmActivity.ACTION_CLOSE).setPackage(app.packageName))
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

    private fun postNotification() {
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
            Intent(app, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val stop = PendingIntent.getBroadcast(app, 1, Intent(app, StopReceiver::class.java), flags)
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setColor(Ui.BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(Notification.Action.Builder(null, app.getString(R.string.stop), stop).build())
            .build()
        nm.notify(NOTIF_ID, n)
    }
}
