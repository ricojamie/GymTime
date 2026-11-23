package com.example.gymtime.ui.workout

import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.entity.Set as LoggedSet
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.ui.exercise.SupersetManager
import com.example.gymtime.util.TestDispatcherRule
import com.example.gymtime.wear.ActiveWearSessionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutResumeViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val workoutDao: WorkoutDao = mockk(relaxed = true)
    private val setDao: SetDao = mockk(relaxed = true)
    private val workoutRepository: WorkoutRepository = mockk(relaxed = true)
    private val activeWearSessionRepository: ActiveWearSessionRepository = mockk(relaxed = true)
    private val supersetManager: SupersetManager = mockk(relaxed = true)

    private val createdViewModels = mutableListOf<WorkoutResumeViewModel>()

    @After
    fun tearDown() = runTest {
        val jobs = createdViewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        jobs.joinAll()
        createdViewModels.clear()
    }

    private fun createViewModel(): WorkoutResumeViewModel {
        return WorkoutResumeViewModel(
            workoutDao = workoutDao,
            setDao = setDao,
            workoutRepository = workoutRepository,
            activeWearSessionRepository = activeWearSessionRepository,
            supersetManager = supersetManager
        ).also { createdViewModels += it }
    }

    @Test
    fun `same workout re-emission does not flash loading or empty exercises and groups sets per exercise`() = runTest {
        val workout1 = Workout(id = 10L, startTime = Date(), endTime = null, name = "Push Day", note = null)
        val ongoingFlow = MutableStateFlow<Workout?>(workout1)
        val setsFlow = MutableStateFlow(
            listOf(
                LoggedSet(
                    id = 1L, workoutId = 10L, exerciseId = 100L, weight = 185f, reps = 5,
                    rpe = null, durationSeconds = null, distanceMeters = null,
                    isWarmup = false, isComplete = true, timestamp = Date(1000L)
                ),
                LoggedSet(
                    id = 2L, workoutId = 10L, exerciseId = 100L, weight = 135f, reps = 8,
                    rpe = null, durationSeconds = null, distanceMeters = null,
                    isWarmup = true, isComplete = true, timestamp = Date(900L)
                ),
                LoggedSet(
                    id = 3L, workoutId = 10L, exerciseId = 200L, weight = 60f, reps = 10,
                    rpe = null, durationSeconds = null, distanceMeters = null,
                    isWarmup = false, isComplete = true, timestamp = Date(1100L)
                )
            )
        )
        val planFlow = MutableStateFlow(
            listOf(
                WorkoutPlanSummary(
                    instanceId = 1L, exerciseId = 100L, exerciseName = "Bench Press", targetMuscle = "Chest",
                    orderIndex = 0, supersetGroupId = null, supersetOrderIndex = 0,
                    plannedSets = 3, repMin = 5, repMax = 8, restSeconds = 120,
                    isSkipped = false, addedDuringWorkout = false, setCount = 1, anySetCount = 2, bestWeight = 185f,
                    totalVolume = 925f, notes = null
                ),
                WorkoutPlanSummary(
                    instanceId = 2L, exerciseId = 200L, exerciseName = "Dumbbell Fly", targetMuscle = "Chest",
                    orderIndex = 1, supersetGroupId = null, supersetOrderIndex = 0,
                    plannedSets = 3, repMin = 10, repMax = 12, restSeconds = 90,
                    isSkipped = false, addedDuringWorkout = false, setCount = 1, anySetCount = 1, bestWeight = 60f,
                    totalVolume = 600f, notes = null
                )
            )
        )

        every { workoutDao.getOngoingWorkout() } returns ongoingFlow
        coEvery { workoutRepository.hasWorkoutPlan(10L) } returns true
        every { workoutRepository.getWorkoutPlanSummaries(10L) } returns planFlow
        every { setDao.getSetsForWorkout(10L) } returns setsFlow

        val vm = createViewModel()
        val observedLoading = mutableListOf<Boolean>()
        val observedCounts = mutableListOf<Int>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.previewState.collect { state ->
                observedLoading += state.isLoading
                observedCounts += state.exercises.size
            }
        }

        val initialLoaded = vm.previewState.first { !it.isLoading && it.exercises.size == 2 }
        assertEquals(185f, initialLoaded.exercises[0].bestWeight)
        assertEquals(1, initialLoaded.exercises[0].setCount)
        assertEquals(2, initialLoaded.exercises[0].anySetCount)
        assertEquals(60f, initialLoaded.exercises[1].bestWeight)

        observedLoading.clear()
        observedCounts.clear()

        // Updating the same workout row (e.g., name edit) must NOT flash isLoading=true or emptyList().
        ongoingFlow.value = workout1.copy(name = "Push Day Renamed")
        val renamed = vm.previewState.first { it.name == "Push Day Renamed" }
        assertFalse(renamed.isLoading)
        assertTrue(observedLoading.none { it })
        assertTrue(observedCounts.all { it == 2 })

        // Switching to a different workout ID loads that new session cleanly.
        val workout2 = Workout(id = 11L, startTime = Date(), endTime = null, name = "Pull Day", note = null)
        coEvery { workoutRepository.hasWorkoutPlan(11L) } returns false
        every { setDao.getWorkoutExerciseSummaries(11L) } returns flowOf(
            listOf(
                WorkoutExerciseSummary(
                    exerciseId = 300L, exerciseName = "Barbell Row", targetMuscle = "Back",
                    setCount = 1, bestWeight = 155f, totalVolume = 1240f, firstSetTimestamp = 2000L,
                    supersetGroupId = null
                )
            )
        )
        every { setDao.getSetsForWorkout(11L) } returns flowOf(
            listOf(
                LoggedSet(
                    id = 4L, workoutId = 11L, exerciseId = 300L, weight = 155f, reps = 8,
                    rpe = null, durationSeconds = null, distanceMeters = null,
                    isWarmup = false, isComplete = true, timestamp = Date(2000L)
                )
            )
        )
        ongoingFlow.value = workout2
        val switched = vm.previewState.first { !it.isLoading && it.workoutId == 11L && it.exercises.size == 1 }
        assertEquals("Barbell Row", switched.exercises.single().exerciseName)
        collector.cancel()
    }

    @Test
    fun `clearFinishError resets stale dialog error after finish failure`() = runTest {
        val workout = Workout(id = 10L, startTime = Date(), endTime = null, name = "Push Day", note = null)
        every { workoutDao.getOngoingWorkout() } returns flowOf(workout)
        coEvery { workoutRepository.hasWorkoutPlan(10L) } returns false
        every { setDao.getWorkoutExerciseSummaries(10L) } returns flowOf(emptyList())
        every { setDao.getSetsForWorkout(10L) } returns flowOf(emptyList())
        coEvery { workoutRepository.finishWorkout(10L) } throws IllegalStateException("Disk full")

        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.previewState.collect {}
        }
        vm.previewState.first { !it.isLoading }

        vm.finishWorkout()
        val errored = vm.previewState.first { it.finishError != null }
        assertNotNull(errored.finishError)

        vm.clearFinishError()
        val cleared = vm.previewState.first { it.finishError == null }
        assertNull(cleared.finishError)
        collector.cancel()
    }
}
