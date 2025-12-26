package com.example.gymtime.domain.analytics

import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.MuscleFreshness
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceUseCaseTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val workoutDao: WorkoutDao = mockk()
    private lateinit var balanceUseCase: BalanceUseCase

    @Before
    fun setup() {
        balanceUseCase = BalanceUseCase(workoutDao)
    }

    @Test
    fun `getMuscleFreshness identifies Fatigued muscles (within 24 hours)`() = runTest {
        val now = System.currentTimeMillis()
        val rawData = listOf(
            MuscleFreshness("Chest", now - TimeUnit.HOURS.toMillis(12))
        )

        coEvery { workoutDao.getMuscleLastTrainedDates() } returns rawData

        val result = balanceUseCase.getMuscleFreshness()
        val chestStatus = result.find { it.muscle == "Chest" }

        assertEquals(RecoveryStatus.FATIGUED, chestStatus?.status)
    }

    @Test
    fun `getMuscleFreshness identifies Recovering muscles (1-3 days)`() = runTest {
        val now = System.currentTimeMillis()
        val rawData = listOf(
            MuscleFreshness("Back", now - TimeUnit.DAYS.toMillis(2))
        )

        coEvery { workoutDao.getMuscleLastTrainedDates() } returns rawData

        val result = balanceUseCase.getMuscleFreshness()
        val backStatus = result.find { it.muscle == "Back" }

        assertEquals(RecoveryStatus.RECOVERING, backStatus?.status)
    }

    @Test
    fun `getMuscleFreshness identifies Fresh muscles (over 3 days)`() = runTest {
        val now = System.currentTimeMillis()
        val rawData = listOf(
            MuscleFreshness("Legs", now - TimeUnit.DAYS.toMillis(5))
        )

        coEvery { workoutDao.getMuscleLastTrainedDates() } returns rawData

        val result = balanceUseCase.getMuscleFreshness()
        val legsStatus = result.find { it.muscle == "Legs" }

        assertEquals(RecoveryStatus.FRESH, legsStatus?.status)
    }

    @Test
    fun `getMuscleFreshness treats never trained muscles as Fresh`() = runTest {
        coEvery { workoutDao.getMuscleLastTrainedDates() } returns emptyList()

        val result = balanceUseCase.getMuscleFreshness()
        val chestStatus = result.find { it.muscle == "Chest" }

        assertEquals(RecoveryStatus.FRESH, chestStatus?.status)
        assertEquals(999, chestStatus?.daysSince)
    }
}
