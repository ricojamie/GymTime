package com.example.gymtime.notifications

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.domain.report.MonthlyReport
import com.example.gymtime.domain.report.MonthlyReportUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class MonthlyReportWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)
    private val monthlyReportUseCase: MonthlyReportUseCase = mockk()
    private val notifier: MonthlyReportNotifier = mockk(relaxed = true)
    private val preferences: UserPreferencesRepository = mockk()
    private val scheduler: MonthlyReportScheduler = mockk(relaxed = true)

    @Before
    fun setup() {
        every { params.runAttemptCount } returns 0
        every { preferences.monthlyReportEnabled } returns flowOf(true)
        every { scheduler.hasHandledPeriod(any()) } returns false
        every { notifier.canPostNotifications() } returns true
        every { notifier.postReport(any()) } returns true
    }

    @Test
    fun `disabled preference cancels pending work without re-arming or querying`() = runTest {
        every { preferences.monthlyReportEnabled } returns flowOf(false)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { scheduler.cancel() }
        verify(exactly = 0) { scheduler.scheduleSuccessor(any()) }
        verify(exactly = 0) { scheduler.scheduleNext() }
        coVerify(exactly = 0) { monthlyReportUseCase.invoke(any()) }
    }

    @Test
    fun `already handled period skips heavy report query and schedules successor`() = runTest {
        every { scheduler.hasHandledPeriod(any()) } returns true

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { monthlyReportUseCase.invoke(any()) }
        verify(exactly = 0) { notifier.postReport(any()) }
        verify(exactly = 1) { scheduler.scheduleSuccessor(any()) }
    }

    @Test
    fun `missing notification permission skips report generation and schedules successor`() = runTest {
        every { notifier.canPostNotifications() } returns false

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { monthlyReportUseCase.invoke(any()) }
        verify(exactly = 0) { notifier.postReport(any()) }
        verify(exactly = 1) { scheduler.scheduleSuccessor(any()) }
    }

    @Test
    fun `happy path posts report marks period handled and schedules successor`() = runTest {
        val report = sampleReport(workoutCount = 6)
        coEvery { monthlyReportUseCase.invoke(any()) } returns report

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { notifier.postReport(report) }
        verify(exactly = 1) { scheduler.markPeriodHandled(any()) }
        verify(exactly = 1) { scheduler.scheduleSuccessor(any()) }
    }

    @Test
    fun `cancellation exception is rethrown without retrying or re-arming`() = runTest {
        coEvery { monthlyReportUseCase.invoke(any()) } throws CancellationException("cancelled")

        var caughtCancellation = false
        try {
            buildWorker().doWork()
        } catch (_: CancellationException) {
            caughtCancellation = true
        }

        assertTrue(caughtCancellation)
        verify(exactly = 0) { scheduler.scheduleSuccessor(any()) }
        verify(exactly = 0) { scheduler.scheduleNext() }
    }

    @Test
    fun `transient failure returns retry before max attempts`() = runTest {
        every { params.runAttemptCount } returns 1
        coEvery { monthlyReportUseCase.invoke(any()) } throws IllegalStateException("db busy")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(exactly = 0) { scheduler.scheduleSuccessor(any()) }
    }

    @Test
    fun `failure after max attempts re-arms successor when enabled and returns failure`() = runTest {
        every { params.runAttemptCount } returns 3
        coEvery { monthlyReportUseCase.invoke(any()) } throws IllegalStateException("persistent error")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(exactly = 1) { scheduler.scheduleSuccessor(any()) }
    }

    private fun buildWorker() = MonthlyReportWorker(
        appContext = context,
        params = params,
        monthlyReportUseCase = monthlyReportUseCase,
        notifier = notifier,
        preferences = preferences,
        scheduler = scheduler
    )

    private fun sampleReport(workoutCount: Int) = MonthlyReport(
        periodStart = Date(),
        periodEnd = Date(),
        monthLabel = "April 2026",
        workoutCount = workoutCount,
        totalVolume = 12_000f,
        totalWorkingSets = 40,
        exerciseCount = 8,
        previousWorkoutCount = 5,
        previousTotalVolume = 10_000f,
        previousWorkingSets = 35,
        volumeChangePercent = 20f,
        workingSetChangePercent = 14.3f,
        trainingDays = 6,
        activeWeeks = 3,
        weeksInPeriod = 4,
        endOfMonthStreakDays = 7,
        topMuscles = emptyList(),
        undertrainedMuscles = emptyList(),
        newPRs = emptyList(),
        averageRating = 4f
    )
}
