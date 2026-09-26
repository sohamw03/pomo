package com.pomo.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pomo.app.MainActivity
import com.pomo.app.R
import com.pomo.app.model.TimerMode

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "pomo_alerts_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showCompletionNotification(completedMode: TimerMode, enabled: Boolean) {
        if (!enabled) return
        // The user can revoke POST_NOTIFICATIONS from system settings after
        // granting it, which puts this app in the "don't notify again" state
        // where notify() silently drops the post.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val title = if (completedMode == TimerMode.WORK) {
            context.getString(R.string.focus_complete_title)
        } else {
            context.getString(R.string.break_complete_title)
        }

        val body = if (completedMode == TimerMode.WORK) {
            context.getString(R.string.focus_complete_body)
        } else {
            context.getString(R.string.break_complete_body)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
