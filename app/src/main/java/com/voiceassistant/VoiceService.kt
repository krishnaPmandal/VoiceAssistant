package com.voiceassistant

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Optional foreground service — keeps the app responsive in background.
 * Not required for basic use; primarily for future hotword detection expansion.
 */
class VoiceService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "va_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Voice Assistant",
            NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle(Prefs.getAssistantName(this))
            .setContentText("Ready — long press Home to activate")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()

        startForeground(1, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
