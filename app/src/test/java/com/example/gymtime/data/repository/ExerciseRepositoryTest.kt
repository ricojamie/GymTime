package com.example.gymtime.data.repository

import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.PBWithTimestamp
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseRepositoryTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val exerciseDao: ExerciseDao = mockk()
    private val setDao: SetDao = mockk()
    private val muscleGroupDao: MuscleGroupDao = mockk()

    private lateinit var repository: ExerciseRepository

    @Before
    fun setup() {
        repository = ExerciseRepository(exerciseDao, setDao, muscleGroupDao)
    }

    @Test
    fun `filterDominatedPBs removes records that are strictly worse than others`() = runTest {
        // We need to use reflection or a test-only method to test the private filterDominatedPBs
        // Or we can test it through getPersonalBestsByReps
        
        val exerciseId = 1L
        val rawPBs = listOf(
            PBWithTimestamp(reps = 1, maxWeight = 100f, firstAchievedAt = 1000L),
            PBWithTimestamp(reps = 5, maxWeight = 100f, firstAchievedAt = 1100L), // Dominates (1, 100)
            PBWithTimestamp(reps = 10, maxWeight = 80f, firstAchievedAt = 1200L),
            PBWithTimestamp(reps = 12, maxWeight = 80f, firstAchievedAt = 1300L), // Dominates (10, 80)
            PBWithTimestamp(reps = 3, maxWeight = 110f, firstAchievedAt = 1400L)  // Keep
        )

        coEvery { setDao.getPersonalBestsWithTimestamps(exerciseId) } returns rawPBs

        val result = repository.getPersonalBestsByReps(exerciseId)

        // Expected: (5, 100), (12, 80), (3, 110)
        assertEquals(3, result.size)
        assertTrue(result.containsKey(5))
        assertTrue(result.containsKey(12))
        assertTrue(result.containsKey(3))
        assertEquals(100f, result[5]?.maxWeight)
        assertEquals(110f, result[3]?.maxWeight)
    }

    @Test
    fun `getPersonalRecords calculates E1RM correctly`() = runTest {
        val exerciseId = 1L
        val sets = listOf(
            Set(exerciseId = exerciseId, weight = 100f, reps = 10, workoutId = 1L, timestamp = Date(), rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true), // E1RM approx 133
            Set(exerciseId = exerciseId, weight = 120f, reps = 1, workoutId = 1L, timestamp = Date(), rpe = null, durationSeconds = null, distanceMeters = null, isWarmup = false, isComplete = true)   // E1RM approx 124
        )

        coEvery { setDao.getPersonalBest(exerciseId) } returns sets[1]
        coEvery { setDao.getWorkingSetsForE1RMCalculation(exerciseId) } returns sets

        val result = repository.getPersonalRecords(exerciseId)

        assertEquals(sets[1], result.heaviestWeight)
        assertEquals(sets[0], result.bestE1RM?.first)
    }
}
