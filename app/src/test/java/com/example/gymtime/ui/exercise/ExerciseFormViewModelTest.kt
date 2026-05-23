package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.LogType
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
    private lateinit var viewModel: ExerciseFormViewModel

    @Before
    fun setup() {
        every { muscleGroupDao.getAllMuscleGroups() } returns flowOf(listOf(MuscleGroup(name = "Chest")))
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
}
