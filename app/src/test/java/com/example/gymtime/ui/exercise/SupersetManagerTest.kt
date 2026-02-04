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
    fun `exitSupersetMode clears all state including last logged values`() {
        supersetManager.startSuperset(listOf(exercise1, exercise2))
        supersetManager.saveLastLoggedValues(exercise1.id, LastLoggedValues(weight = "100", reps = "5"))
        supersetManager.exitSupersetMode()

        assertFalse(supersetManager.isInSupersetMode.value)
        assertTrue(supersetManager.supersetExercises.value.isEmpty())
        assertNull(supersetManager.supersetGroupId.value)
        assertEquals(0, supersetManager.currentExerciseIndex.value)
        assertNull(supersetManager.getLastLoggedValues(exercise1.id))
    }

    @Test
    fun `addExercise appends to existing superset`() {
        supersetManager.startSuperset(listOf(exercise1, exercise2))
        assertEquals(2, supersetManager.supersetExercises.value.size)

        supersetManager.addExercise(exercise3)
        assertEquals(3, supersetManager.supersetExercises.value.size)
        assertEquals(exercise3, supersetManager.supersetExercises.value[2])
    }

    @Test
    fun `addExercise does not add duplicate`() {
        supersetManager.startSuperset(listOf(exercise1, exercise2))
        supersetManager.addExercise(exercise1)

        assertEquals(2, supersetManager.supersetExercises.value.size)
    }

    @Test
    fun `addExercise does nothing when not in superset mode`() {
        supersetManager.addExercise(exercise1)
        assertTrue(supersetManager.supersetExercises.value.isEmpty())
    }

    @Test
    fun `N-exercise rotation cycles through all exercises`() {
        val exercise4 = Exercise(id = 4, name = "OHP", targetMuscle = "Shoulders", logType = LogType.WEIGHT_REPS, isCustom = false, notes = null, defaultRestSeconds = 90)
        val exercises = listOf(exercise1, exercise2, exercise3, exercise4)
        supersetManager.startSuperset(exercises)

        assertEquals(exercise1.id, supersetManager.getCurrentExerciseId())
        assertEquals(exercise2.id, supersetManager.switchToNextExercise())
        assertEquals(exercise3.id, supersetManager.switchToNextExercise())
        assertEquals(exercise4.id, supersetManager.switchToNextExercise())
        assertEquals(exercise1.id, supersetManager.switchToNextExercise()) // Wraps around
    }

    @Test
    fun `saveLastLoggedValues and getLastLoggedValues work correctly`() {
        supersetManager.startSuperset(listOf(exercise1, exercise2))
        val values = LastLoggedValues(weight = "225", reps = "8", duration = "", distance = "")
        supersetManager.saveLastLoggedValues(exercise1.id, values)

        assertEquals(values, supersetManager.getLastLoggedValues(exercise1.id))
        assertNull(supersetManager.getLastLoggedValues(exercise2.id))
    }
}
