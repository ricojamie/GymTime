package com.example.gymtime.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthlyReportScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Enqueues a one-shot worker for the next 1st-of-month at [FIRE_HOUR]:00 local.
     * Uses [ExistingWorkPolicy.REPLACE] so we always converge to the correct
     * fire time even if the user toggles the setting on and off.
     */
    fun scheduleNext() {
        val delayMs = computeInitialDelayMs(System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<MonthlyReportWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Cancels any pending monthly-report work. Called when the user disables
     * the setting.
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * Ensures the worker is enqueued without clobbering an existing schedule.
     * Safe to call on every app start.
     */
    fun ensureScheduled() {
        val delayMs = computeInitialDelayMs(System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<MonthlyReportWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "monthly_report"
        private const val FIRE_HOUR = 9

        fun computeInitialDelayMs(nowMs: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, FIRE_HOUR)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            // If we've already passed this month's fire time, jump to next month.
            if (cal.timeInMillis <= nowMs) {
                cal.add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis - nowMs
        }
    }
}
