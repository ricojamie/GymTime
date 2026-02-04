package com.example.gymtime.ui.exercise

import com.example.gymtime.data.db.entity.Exercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Values from the last logged set for an exercise, persisted across navigation within a superset.
 */
data class LastLoggedValues(
    val weight: String = "",
    val reps: String = "",
    val duration: String = "",
    val distance: String = ""
)

/**
 * Singleton manager for superset state that persists across navigation.
 * Coordinates superset mode between ExerciseSelection and ExerciseLogging screens.
 */
@Singleton
class SupersetManager @Inject constructor() {

    // Whether we're currently in an active superset logging session
    private val _isInSupersetMode = MutableStateFlow(false)
    val isInSupersetMode: StateFlow<Boolean> = _isInSupersetMode

    // Last-logged form values per exercise (persists across navigation)
    private val _lastLoggedValues = mutableMapOf<Long, LastLoggedValues>()

    // The exercises in the current superset (ordered)
    private val _supersetExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val supersetExercises: StateFlow<List<Exercise>> = _supersetExercises

    // UUID for grouping all sets in this superset session
    private val _supersetGroupId = MutableStateFlow<String?>(null)
    val supersetGroupId: StateFlow<String?> = _supersetGroupId

    // Current index in the exercise rotation (0, 1, etc.)
    private val _currentExerciseIndex = MutableStateFlow(0)
    val currentExerciseIndex: StateFlow<Int> = _currentExerciseIndex

    /**
     * Start a new superset with the given exercises.
     * @param exercises List of exercises (must have at least 2)
     * @param explicitGroupId Optional specific group ID to use (e.g. from a routine)
     */
    fun startSuperset(exercises: List<Exercise>, explicitGroupId: String? = null) {
        require(exercises.size >= 2) { "Superset requires at least 2 exercises" }
        _supersetExercises.value = exercises
        _supersetGroupId.value = explicitGroupId ?: UUID.randomUUID().toString()
        _currentExerciseIndex.value = 0
        _isInSupersetMode.value = true
    }

    /**
     * Switch to the next exercise in the rotation.
     * @return The exerciseId of the next exercise
     */
    fun switchToNextExercise(): Long {
        val exercises = _supersetExercises.value
        if (exercises.isEmpty()) return -1

        val nextIndex = (_currentExerciseIndex.value + 1) % exercises.size
        _currentExerciseIndex.value = nextIndex
        return exercises[nextIndex].id
    }

    /**
     * Get the current exercise ID.
     */
    fun getCurrentExerciseId(): Long? {
        val exercises = _supersetExercises.value
        if (exercises.isEmpty()) return null
        return exercises.getOrNull(_currentExerciseIndex.value)?.id
    }

    /**
     * Get the order index for a specific exercise in the superset.
     * @return The index (0-based) or -1 if not found
     */
    fun getOrderIndex(exerciseId: Long): Int {
        return _supersetExercises.value.indexOfFirst { it.id == exerciseId }
    }

    /**
     * Check if a specific exercise is part of the current superset.
     */
    fun isInCurrentSuperset(exerciseId: Long): Boolean {
        return _supersetExercises.value.any { it.id == exerciseId }
    }

    /**
     * Manually set the current exercise index (for manual switching).
     */
    fun setCurrentExerciseIndex(index: Int) {
        if (index >= 0 && index < _supersetExercises.value.size) {
            _currentExerciseIndex.value = index
        }
    }

    /**
     * Add an exercise to an existing active superset.
     * @param exercise The exercise to add
     */
    fun addExercise(exercise: Exercise) {
        if (!_isInSupersetMode.value) return
        // Don't add duplicates
        if (_supersetExercises.value.any { it.id == exercise.id }) return
        _supersetExercises.value = _supersetExercises.value + exercise
    }

    /**
     * Save the last-logged form values for an exercise.
     */
    fun saveLastLoggedValues(exerciseId: Long, values: LastLoggedValues) {
        _lastLoggedValues[exerciseId] = values
    }

    /**
     * Get the last-logged form values for an exercise, if any.
     */
    fun getLastLoggedValues(exerciseId: Long): LastLoggedValues? {
        return _lastLoggedValues[exerciseId]
    }

    /**
     * Exit superset mode and clear all state.
     */
    fun exitSupersetMode() {
        _isInSupersetMode.value = false
        _supersetExercises.value = emptyList()
        _supersetGroupId.value = null
        _currentExerciseIndex.value = 0
        _lastLoggedValues.clear()
    }
}
