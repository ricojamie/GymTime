package com.example.gymtime.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.domain.report.MonthlyReportUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class MonthlyReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val monthlyReportUseCase: MonthlyReportUseCase,
    private val notifier: MonthlyReportNotifier,
    private val preferences: UserPreferencesRepository,
    private val scheduler: MonthlyReportScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val enabled = preferences.monthlyReportEnabled.first()
            if (!enabled) {
                scheduler.cancel()
                return Result.success()
            }

            val nowMs = System.currentTimeMillis()
            val period = MonthlyReportScheduler.reportPeriodFor(nowMs)

            if (scheduler.hasHandledPeriod(period)) {
                Log.d(TAG, "Skipping duplicate monthly report for $period")
                scheduler.scheduleSuccessor(nowMs)
                return Result.success()
            }

            if (!notifier.canPostNotifications()) {
                Log.d(TAG, "Skipping monthly report — notifications disabled or permission missing")
                scheduler.scheduleSuccessor(nowMs)
                return Result.success()
            }

            val report = monthlyReportUseCase()
            // Skip posting if there's literally nothing to report (e.g.,
            // brand-new install with no workouts last month).
            if (report.workoutCount > 0) {
                if (notifier.postReport(report)) {
                    scheduler.markPeriodHandled(period)
                }
            } else {
                Log.d(TAG, "Skipping monthly report — no workouts in ${report.monthLabel}")
                scheduler.markPeriodHandled(period)
            }

            scheduler.scheduleSuccessor(nowMs)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Monthly report worker failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                val stillEnabled = runCatching { preferences.monthlyReportEnabled.first() }
                    .getOrDefault(false)
                if (stillEnabled) {
                    scheduler.scheduleSuccessor(System.currentTimeMillis())
                }
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "MonthlyReportWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
