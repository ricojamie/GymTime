package com.example.gymtime

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.notifications.MonthlyReportScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymTimeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var monthlyReportScheduler: MonthlyReportScheduler
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Idempotently arm the monthly-report worker if the user has it enabled.
        applicationScope.launch {
            try {
                if (userPreferencesRepository.monthlyReportEnabled.first()) {
                    monthlyReportScheduler.ensureScheduled()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GymTimeApp", "Failed to verify monthly report schedule", e)
            }
        }
    }
}
