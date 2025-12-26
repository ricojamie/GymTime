package com.example.gymtime.ui.exercise

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLoggingViewModelTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val savedStateHandle = SavedStateHandle(mapOf("exerciseId" to 1L))
    private val exerciseRepository: ExerciseRepository = mockk()
    private val workoutRepository: WorkoutRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val volumeOrbRepository: VolumeOrbRepository = mockk(relaxed = true)
    private val supersetManager: SupersetManager = SupersetManager() // Use real one for interaction test
    private val routineRepository: RoutineRepository = mockk()

    private lateinit var viewModel: ExerciseLoggingViewModel

    private val testExercise = Exercise(
        id = 1L,
        name = "Bench Press",
        targetMuscle = "Chest",
        logType = LogType.WEIGHT_REPS,
        isCustom = false,
        notes = null,
        defaultRestSeconds = 90
    )

    private val testWorkout = Workout(
        id = 1L,
        startTime = Date(),
        endTime = null,
        name = null,
        note = null
    )

    @Before
    fun setup() {
        coEvery { exerciseRepository.getExercise(1L) } returns flowOf(testExercise)
        coEvery { exerciseRepository.getPersonalBestsByReps(1L) } returns emptyMap()
        coEvery { exerciseRepository.getHeaviestSet(1L) } returns null
        coEvery { workoutRepository.getCurrentWorkout() } returns testWorkout
        coEvery { workoutRepository.getSetsForWorkout(1L) } returns flowOf(emptyList())
        coEvery { workoutRepository.getLastWorkoutSetsForExercise(1L, any()) } returns emptyList()
        coEvery { routineRepository.getRoutineDayWithExercises(any()) } returns flowOf(null)
        
        every { volumeOrbRepository.orbState } returns MutableStateFlow(mockk(relaxed = true))

        viewModel = ExerciseLoggingViewModel(
            context,
            savedStateHandle,
            exerciseRepository,
            workoutRepository,
            userPreferencesRepository,
            volumeOrbRepository,
            supersetManager,
            routineRepository
        )
    }

    @Test
    fun `viewModel initializes and loads exercise`() = runTest {
        advanceUntilIdle()
        assertEquals(testExercise, viewModel.exercise.value)
        assertEquals(testWorkout, viewModel.currentWorkout.value)
    }

    @Test
    fun `logSet calls repository with correct data`() = runTest {
        advanceUntilIdle()
        viewModel.updateWeight("100")
        viewModel.updateReps("10")
        viewModel.updateRpe("8")
        
        coEvery { workoutRepository.logSet(any()) } just Runs
        
        viewModel.logSet()
        advanceUntilIdle()
        
        coVerify { 
            workoutRepository.logSet(match { 
                it.weight == 100f && it.reps == 10 && it.rpe == 8f && it.exerciseId == 1L
            }) 
        }
    }

    @Test
    fun `logSet clears RPE and note but keeps weight and reps for next set`() = runTest {
        advanceUntilIdle()
        viewModel.updateWeight("100")
        viewModel.updateReps("10")
        viewModel.updateRpe("8")
        viewModel.updateSetNote("Felt heavy")
        
        coEvery { workoutRepository.logSet(any()) } just Runs
        
        viewModel.logSet()
        advanceUntilIdle()
        
        assertEquals("100", viewModel.weight.value)
        assertEquals("10", viewModel.reps.value)
        assertEquals("", viewModel.rpe.value)
        assertEquals("", viewModel.setNote.value)
    }
}
