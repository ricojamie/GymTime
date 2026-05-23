package com.example.gymtime.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class WearWorkoutNotificationService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != WearContract.DATA_ACTIVE_SESSION) continue

            val session = WearSession.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
            if (session.active) {
                showWorkoutNotification(session)
            } else {
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            }
        }
    }

    private fun showWorkoutNotification(session: WearSession) {
        if (!canPostNotifications()) return

        createChannel()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (session.exerciseName.isBlank()) {
            "Open logger on phone"
        } else {
            "Set ${session.setNumber} - ${session.exerciseName}"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("IronLog workout active")
            .setContentText(text)
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active workout",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shortcut back to the active IronLog workout"
            setSound(null, null)
            enableVibration(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CHANNEL_ID = "ironlog_active_workout"
        private const val NOTIFICATION_ID = 3001
    }
}
