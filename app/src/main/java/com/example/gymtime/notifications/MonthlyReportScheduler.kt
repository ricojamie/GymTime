package com.example.gymtime.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class MonthlyReportScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences? by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Enqueues a one-shot worker for the next 1st-of-month at [FIRE_HOUR]:00 local.
     * Uses [ExistingWorkPolicy.REPLACE] when called outside an active worker run
     * (e.g. toggling the setting in Settings) so we converge to the right fire time.
     */
    fun scheduleNext() {
        enqueue(ExistingWorkPolicy.REPLACE, System.currentTimeMillis(), afterWorkerRun = false)
    }

    /**
     * Chains the next month's worker from inside an actively running [MonthlyReportWorker].
     * Uses [ExistingWorkPolicy.APPEND_OR_REPLACE] so the currently running worker is never
     * cancelled mid-completion.
     */
    fun scheduleSuccessor(nowMs: Long = System.currentTimeMillis()) {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE, nowMs, afterWorkerRun = true)
    }

    /**
     * Cancels any pending monthly-report work. Called when the user disables
     * the setting.
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        prefs?.edit()?.remove(KEY_SCHEDULED_TARGET_MS)?.apply()
    }

    /**
     * Ensures the worker is enqueued without clobbering an up-to-date schedule,
     * while refreshing stale schedules caused by NTP clock sync or timezone shifts.
     * Safe to call on every app start.
     */
    fun ensureScheduled() {
        val nowMs = System.currentTimeMillis()
        val lastHandled = getLastHandledPeriod()
        val desiredTargetMs = computeTargetTriggerMs(nowMs, lastHandled)
        val storedTargetMs = getScheduledTargetMs()
        val policy = if (shouldRefreshSchedule(storedTargetMs, desiredTargetMs, nowMs, lastHandled)) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }
        val delayMs = (desiredTargetMs - nowMs).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<MonthlyReportWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
        if (policy == ExistingWorkPolicy.REPLACE || storedTargetMs == null || storedTargetMs <= 0L) {
            saveScheduledTargetMs(desiredTargetMs)
        }
    }

    fun hasHandledPeriod(period: YearMonth): Boolean {
        val lastHandled = getLastHandledPeriod() ?: return false
        return lastHandled >= period
    }

    fun markPeriodHandled(period: YearMonth) {
        val current = getLastHandledPeriod()
        if (current == null || period > current) {
            prefs?.edit()?.putString(KEY_LAST_HANDLED_PERIOD, period.toString())?.apply()
        }
    }

    private fun enqueue(policy: ExistingWorkPolicy, nowMs: Long, afterWorkerRun: Boolean) {
        val targetMs = computeTargetTriggerMs(
            nowMs = nowMs,
            lastHandledPeriod = getLastHandledPeriod(),
            afterWorkerRun = afterWorkerRun
        )
        val delayMs = (targetMs - nowMs).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<MonthlyReportWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
        saveScheduledTargetMs(targetMs)
    }

    private fun getLastHandledPeriod(): YearMonth? {
        val raw = prefs?.getString(KEY_LAST_HANDLED_PERIOD, null) ?: return null
        return runCatching { YearMonth.parse(raw) }.getOrNull()
    }

    private fun getScheduledTargetMs(): Long? {
        val stored = prefs?.getLong(KEY_SCHEDULED_TARGET_MS, -1L) ?: return null
        return stored.takeIf { it > 0L }
    }

    private fun saveScheduledTargetMs(targetMs: Long) {
        prefs?.edit()?.putLong(KEY_SCHEDULED_TARGET_MS, targetMs)?.apply()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "monthly_report"
        private const val FIRE_HOUR = 9
        private const val PREFS_NAME = "monthly_report_scheduler"
        private const val KEY_LAST_HANDLED_PERIOD = "last_handled_period"
        private const val KEY_SCHEDULED_TARGET_MS = "scheduled_target_ms"
        private val SCHEDULE_DRIFT_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(15)

        fun computeInitialDelayMs(nowMs: Long): Long =
            computeInitialDelayMs(nowMs, lastHandledPeriod = null, zoneId = ZoneId.systemDefault())

        internal fun computeInitialDelayMs(
            nowMs: Long,
            lastHandledPeriod: YearMonth?,
            zoneId: ZoneId = ZoneId.systemDefault(),
            afterWorkerRun: Boolean = false
        ): Long {
            val targetMs = computeTargetTriggerMs(nowMs, lastHandledPeriod, zoneId, afterWorkerRun)
            return (targetMs - nowMs).coerceAtLeast(0L)
        }

        internal fun reportPeriodFor(
            nowMs: Long,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): YearMonth {
            val localDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            return YearMonth.from(localDate).minusMonths(1)
        }

        internal fun computeTargetTriggerMs(
            nowMs: Long,
            lastHandledPeriod: YearMonth? = null,
            zoneId: ZoneId = ZoneId.systemDefault(),
            afterWorkerRun: Boolean = false
        ): Long {
            val localDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            var targetMonth = YearMonth.from(localDate)
            val thisMonthTriggerMs = targetMonth
                .atDay(1)
                .atTime(FIRE_HOUR, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

            if (afterWorkerRun || thisMonthTriggerMs <= nowMs) {
                targetMonth = targetMonth.plusMonths(1)
            }

            if (lastHandledPeriod != null && targetMonth.minusMonths(1) <= lastHandledPeriod) {
                targetMonth = lastHandledPeriod.plusMonths(2)
            }

            return targetMonth
                .atDay(1)
                .atTime(FIRE_HOUR, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }

        internal fun shouldRefreshSchedule(
            storedTargetMs: Long?,
            desiredTargetMs: Long,
            nowMs: Long,
            lastHandledPeriod: YearMonth? = null,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Boolean {
            if (storedTargetMs == null || storedTargetMs <= 0L) return false
            if (storedTargetMs <= nowMs) {
                val storedPeriod = reportPeriodFor(storedTargetMs, zoneId)
                val currentPeriod = reportPeriodFor(nowMs, zoneId)
                return (lastHandledPeriod != null && storedPeriod <= lastHandledPeriod) ||
                    storedPeriod < currentPeriod
            }
            return abs(storedTargetMs - desiredTargetMs) > SCHEDULE_DRIFT_TOLERANCE_MS
        }
    }
}
