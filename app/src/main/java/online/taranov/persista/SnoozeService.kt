package online.taranov.persista

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

// Exists only while a delivered repeat is ringing or waiting behind the active alarm.
// The exact alarm delivery grants the background FGS start exemption.
class SnoozeService : Service() {
    private var lastStartId = 0
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        instance = this
        try {
            startForeground(Snooze.SERVICE_ID, Snooze.serviceNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (_: Exception) {
            Snooze.failReady(this)
            instance = null
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!Calls.isOn(this)) Snooze.cancelAll(this)
        Ringer.next(this)
        releaseIfIdle()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            Ringer.stopRepeat()
        }
        super.onDestroy()
    }

    companion object {
        var instance: SnoozeService? = null
            private set

        fun releaseIfIdle() {
            val service = instance ?: return
            if (Ringer.repeatToken == null && !Snooze.hasReady(service)) {
                // A newer delivery may already be queued by Android, before onStartCommand runs.
                if (service.stopSelfResult(service.lastStartId)) {
                    instance = null
                    service.stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }
}
