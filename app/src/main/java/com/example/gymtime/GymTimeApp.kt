package com.example.gymtime

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.notifications.MonthlyReportScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymTimeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var monthlyReportScheduler: MonthlyReportScheduler
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Idempotently arm the monthly-report worker if the user has it enabled.
        CoroutineScope(Dispatchers.Default).launch {
            if (userPreferencesRepository.monthlyReportEnabled.first()) {
                monthlyReportScheduler.ensureScheduled()
            }
        }
    }
}
