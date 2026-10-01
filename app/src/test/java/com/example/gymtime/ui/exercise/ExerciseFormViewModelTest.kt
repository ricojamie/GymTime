package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import io.mockk.every

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseFormViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val exerciseDao: ExerciseDao = mockk(relaxed = true)
    private val muscleGroupDao: MuscleGroupDao = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private lateinit var viewModel: ExerciseFormViewModel

    @Before
    fun setup() {
        every { userPreferencesRepository.newUiEnabled } returns flowOf(false)
        every { muscleGroupDao.getAllMuscleGroups() } returns flowOf(listOf(MuscleGroup(name = "Chest")))
        viewModel = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    @Test
    fun `blank rep target is valid for rep based exercise`() = runTest {
        val job = launch { viewModel.isSaveEnabled.collect {} }
        viewModel.updateExerciseName("Bench Press")
        viewModel.updateTargetMuscle("Chest")
        viewModel.updateLogType(LogType.WEIGHT_REPS)
        viewModel.updateDefaultRestSeconds("90")
        viewModel.updateRepTarget("")
        advanceUntilIdle()

        assertTrue(viewModel.isSaveEnabled.value)
        job.cancel()
    }

    @Test
    fun `invalid rep target disables save`() = runTest {
        val job = launch { viewModel.isSaveEnabled.collect {} }
        viewModel.updateExerciseName("Bench Press")
        viewModel.updateTargetMuscle("Chest")
        viewModel.updateLogType(LogType.WEIGHT_REPS)
        viewModel.updateDefaultRestSeconds("90")
        viewModel.updateRepTarget("0")
        advanceUntilIdle()

        assertFalse(viewModel.isSaveEnabled.value)
        job.cancel()
    }

    @Test
    fun `new exercise name is prefilled from navigation`() {
        val prefilled = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("initialName" to "Incline Dumbbell Press")),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao,
            userPreferencesRepository = userPreferencesRepository
        )

        assertEquals("Incline Dumbbell Press", prefilled.exerciseName.value)
    }

    @Test
    fun `new form becomes dirty and returns clean when reverted`() = runTest {
        val job = launch { viewModel.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)

        viewModel.updateExerciseName("Bench Press")
        advanceUntilIdle()
        assertTrue(viewModel.hasUnsavedChanges.value)

        viewModel.updateExerciseName("")
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        job.cancel()
    }

    @Test
    fun `loaded edit form is clean until a persisted field changes`() = runTest {
        val exercise = Exercise(
            id = 7L,
            name = "Bench Press",
            targetMuscle = "Chest",
            logType = LogType.WEIGHT_REPS,
            isCustom = true,
            notes = "Pause",
            defaultRestSeconds = 120
        )
        every { exerciseDao.getExerciseById(7L) } returns flowOf(exercise)
        val editing = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to "7")),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao,
            userPreferencesRepository = userPreferencesRepository
        )
        val job = launch { editing.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(editing.hasUnsavedChanges.value)

        editing.updateNotes("Long pause")
        advanceUntilIdle()
        assertTrue(editing.hasUnsavedChanges.value)

        editing.updateNotes("Pause")
        advanceUntilIdle()
        assertFalse(editing.hasUnsavedChanges.value)
        job.cancel()
    }
}
