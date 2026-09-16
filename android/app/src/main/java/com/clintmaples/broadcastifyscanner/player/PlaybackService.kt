package com.clintmaples.broadcastifyscanner.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.clintmaples.broadcastifyscanner.MainActivity
import com.clintmaples.broadcastifyscanner.R

class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val count = intent?.getIntExtra(EXTRA_COUNT, 1)?.coerceAtLeast(1) ?: 1
        ensureChannel()
        val notification = buildNotification(count)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(count: Int): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (count == 1) {
            getString(R.string.notification_listening) + " · 1 feed"
        } else {
            getString(R.string.notification_listening) + " · $count feeds"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scanner)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.clintmaples.broadcastifyscanner.STOP_PLAYBACK"
        const val EXTRA_COUNT = "count"
        private const val CHANNEL_ID = "scanner-playback"
        private const val NOTIFICATION_ID = 17

        fun start(context: Context, playingCount: Int) {
            val intent = Intent(context, PlaybackService::class.java)
                .putExtra(EXTRA_COUNT, playingCount)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
