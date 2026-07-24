package com.example.gymtime.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
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
            if (event.dataItem.uri.path != WearContract.DATA_ACTIVE_SESSION) continue

            when (event.type) {
                DataEvent.TYPE_CHANGED -> {
                    val session = runCatching {
                        WearSession.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
                    }.onFailure { error ->
                        Log.w(TAG, "Ignoring malformed Wear session data", error)
                    }.getOrNull() ?: continue

                    if (session.active) {
                        showWorkoutNotification(session)
                    } else {
                        cancelWorkoutNotification()
                    }
                }

                DataEvent.TYPE_DELETED -> cancelWorkoutNotification()
            }
        }
    }

    private fun showWorkoutNotification(session: WearSession) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentKey = listOf(
            session.workoutId,
            session.exerciseId,
            session.setNumber,
            session.exerciseName
        ).joinToString(separator = "|")
        if (contentKey == lastRenderedContentKey) return

        createChannel()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = when {
            session.exerciseName.isBlank() -> "Open logger on phone"
            session.setNumber > 0 -> "Set ${session.setNumber} · ${session.exerciseName}"
            else -> session.exerciseName
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setContentTitle("IronLog workout active")
            .setContentText(text)
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setLocalOnly(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            lastRenderedContentKey = contentKey
        } catch (_: SecurityException) {
            // Permission can still be revoked between the check and notify call.
        }
    }

    private fun cancelWorkoutNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        lastRenderedContentKey = null
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

    companion object {
        private const val TAG = "WearWorkoutNotification"
        private const val CHANNEL_ID = "ironlog_active_workout"
        private const val NOTIFICATION_ID = 3001
        @Volatile private var lastRenderedContentKey: String? = null
    }
}
