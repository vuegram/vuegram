package com.vuegram.data.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.vuegram.R

/**
 * Keeps VueGram's process (and so TdStreamServer) alive while the external player has the screen: on a TV low on
 * memory, Android would otherwise kill the backgrounded app in the middle of an episode.
 */
class StreamKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    // Becoming a foreground service in onCreate, not only in onStartCommand: if the service is stopped right after
    // it was started (the player failed to open), Android would otherwise end the app for not calling startForeground.
    override fun onCreate() {
        super.onCreate()
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.stream_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.stream_notification))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "stream"
        private const val ID = 1

        fun start(context: Context) {
            try {
                val intent = Intent(context, StreamKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (e: Exception) {
                // Streaming still works without it; only the protection against being killed is lost.
                Log.w("TdStream", "keep-alive service not started", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamKeepAliveService::class.java))
        }
    }
}
