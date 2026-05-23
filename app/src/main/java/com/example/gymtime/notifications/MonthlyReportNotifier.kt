package com.example.gymtime.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.gymtime.MainActivity
import com.example.gymtime.R
import com.example.gymtime.domain.report.MonthlyReport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthlyReportNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun postReport(report: MonthlyReport) {
        ensureChannel()
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        val title = "Your ${report.monthLabel} report is ready"
        val body = buildString {
            append(report.workoutCount).append(" workouts • ")
            append(numberFormat.format(report.totalVolume.toLong())).append(" lbs")
            if (report.newPRs.isNotEmpty()) append(" • ${report.newPRs.size} new PR")
            if (report.newPRs.size > 1) append("s")
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION, DESTINATION_MONTHLY_REPORT)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monthly Report",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "A monthly summary of your training"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "monthly_report_channel"
        const val NOTIFICATION_ID = 2
        const val REQUEST_CODE = 1001
        const val EXTRA_DESTINATION = "extra_destination"
        const val DESTINATION_MONTHLY_REPORT = "monthly_report"
    }
}
