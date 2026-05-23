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
            if (enabled) {
                val report = monthlyReportUseCase()
                // Skip posting if there's literally nothing to report (e.g.,
                // brand-new install with no workouts last month).
                if (report.workoutCount > 0) {
                    notifier.postReport(report)
                } else {
                    Log.d(TAG, "Skipping monthly report — no workouts in ${report.monthLabel}")
                }
            }
            // Always re-arm so we keep firing month after month.
            scheduler.scheduleNext()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Monthly report worker failed", e)
            // Even on failure, re-schedule so a transient error doesn't kill the chain.
            scheduler.scheduleNext()
            Result.success()
        }
    }

    companion object {
        private const val TAG = "MonthlyReportWorker"
    }
}
