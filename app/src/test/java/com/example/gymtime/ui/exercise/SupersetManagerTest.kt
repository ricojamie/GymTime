package com.example.gymtime.ui.exercise

import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.util.TestDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SupersetManagerTest {

    @get:Rule
    val testDispatcherRule = TestDispatcherRule()

    private lateinit var supersetManager: SupersetManager

    private val exercise1 = Exercise(id = 1, name = "Bench Press", targetMuscle = "Chest", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90)
    private val exercise2 = Exercise(id = 2, name = "Rows", targetMuscle = "Back", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90)
    private val exercise3 = Exercise(id = 3, name = "Curls", targetMuscle = "Arms", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90)

    @Before
    fun setup() {
        supersetManager = SupersetManager()
    }

    @Test
    fun `startSuperset initializes state correctly`() = runTest {
        val exercises = listOf(exercise1, exercise2)
        supersetManager.startSuperset(exercises)

        assertTrue(supersetManager.isInSupersetMode.value)
        assertEquals(exercises, supersetManager.supersetExercises.value)
        assertEquals(0, supersetManager.currentExerciseIndex.value)
        assertNotNull(supersetManager.supersetGroupId.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startSuperset throws exception if less than 2 exercises`() {
        supersetManager.startSuperset(listOf(exercise1))
    }

    @Test
    fun `switchToNextExercise rotates through exercises`() {
        val exercises = listOf(exercise1, exercise2, exercise3)
        supersetManager.startSuperset(exercises)

        val next1 = supersetManager.switchToNextExercise()
        assertEquals(exercise2.id, next1)
        assertEquals(1, supersetManager.currentExerciseIndex.value)

        val next2 = supersetManager.switchToNextExercise()
        assertEquals(exercise3.id, next2)
        assertEquals(2, supersetManager.currentExerciseIndex.value)

        val next3 = supersetManager.switchToNextExercise()
        assertEquals(exercise1.id, next3)
        assertEquals(0, supersetManager.currentExerciseIndex.value)
    }

    @Test
    fun `getCurrentExerciseId returns correct id`() {
        val exercises = listOf(exercise1, exercise2)
        supersetManager.startSuperset(exercises)

        assertEquals(exercise1.id, supersetManager.getCurrentExerciseId())

        supersetManager.switchToNextExercise()
        assertEquals(exercise2.id, supersetManager.getCurrentExerciseId())
    }

    @Test
    fun `getOrderIndex returns correct index`() {
        val exercises = listOf(exercise1, exercise2)
        supersetManager.startSuperset(exercises)

        assertEquals(0, supersetManager.getOrderIndex(exercise1.id))
        assertEquals(1, supersetManager.getOrderIndex(exercise2.id))
        assertEquals(-1, supersetManager.getOrderIndex(99L))
    }

    @Test
    fun `exitSupersetMode clears all state`() {
        supersetManager.startSuperset(listOf(exercise1, exercise2))
        supersetManager.exitSupersetMode()

        assertFalse(supersetManager.isInSupersetMode.value)
        assertTrue(supersetManager.supersetExercises.value.isEmpty())
        assertNull(supersetManager.supersetGroupId.value)
        assertEquals(0, supersetManager.currentExerciseIndex.value)
    }
}
