package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.util.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseFormViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val exerciseDao: ExerciseDao = mockk(relaxed = true)
    private val muscleGroupDao: MuscleGroupDao = mockk()
    private lateinit var viewModel: ExerciseFormViewModel

    @Before
    fun setup() {
        every { muscleGroupDao.getAllMuscleGroups() } returns flowOf(listOf(MuscleGroup(name = "Chest")))
        every { exerciseDao.getAllExercises() } returns flowOf(emptyList())
        viewModel = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao
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
            muscleGroupDao = muscleGroupDao
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
            muscleGroupDao = muscleGroupDao
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

    @Test
    fun `loadExercise surfaces loadError cleanly when exercise is missing`() = runTest {
        every { exerciseDao.getExerciseById(99L) } returns flowOf(null)
        val missing = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to "99")),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao
        )
        advanceUntilIdle()

        assertFalse(missing.isLoading.value)
        assertNotNull(missing.loadError.value)
    }

    @Test
    fun `saveExercise trims notes before persisting`() = runTest {
        var inserted: Exercise? = null
        coEvery { exerciseDao.insertExercise(any()) } coAnswers {
            inserted = firstArg()
            42L
        }
        viewModel.updateExerciseName("  Bench Press ")
        viewModel.updateTargetMuscle("Chest")
        viewModel.updateLogType(LogType.WEIGHT_REPS)
        viewModel.updateNotes("  Pause on chest  ")

        viewModel.saveExercise()
        advanceUntilIdle()

        coVerify(exactly = 1) { exerciseDao.insertExercise(any()) }
        assertEquals("Bench Press", inserted?.name)
        assertEquals("Pause on chest", inserted?.notes)

        val blankNotesVm = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao
        )
        blankNotesVm.updateExerciseName("Bench Press")
        blankNotesVm.updateTargetMuscle("Chest")
        blankNotesVm.updateLogType(LogType.WEIGHT_REPS)
        blankNotesVm.updateNotes("   ")
        blankNotesVm.saveExercise()
        advanceUntilIdle()
        assertNull(inserted?.notes)
    }

    @Test
    fun `similarExercises matches queries during creation and stays empty when editing`() = runTest {
        val bench = Exercise(1L, "Bench Press", "Chest", LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90)
        every { exerciseDao.getAllExercises() } returns flowOf(listOf(bench))

        viewModel.updateExerciseName("bench")
        val matches = viewModel.similarExercises.first()
        assertEquals(listOf(bench), matches)

        every { exerciseDao.getExerciseById(1L) } returns flowOf(bench)
        val editing = ExerciseFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to "1")),
            exerciseDao = exerciseDao,
            muscleGroupDao = muscleGroupDao
        )
        assertTrue(editing.similarExercises.first().isEmpty())
    }
}
