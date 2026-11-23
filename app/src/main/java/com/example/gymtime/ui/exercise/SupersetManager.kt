package com.example.gymtime.ui.exercise

import com.example.gymtime.data.db.entity.DistanceUnit
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
    val calories: String = "",
    val reps: String = "",
    val duration: String = "",
    val distance: String = "",
    val distanceUnit: DistanceUnit? = null
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

    // Tracks plan-backed supersets and explicitly exited plan groups across VM recreations.
    private var isPlanBackedSuperset: Boolean = false
    private var planGroupWorkoutId: Long? = null
    private var planMemberIds: List<Long> = emptyList()
    private val exitedPlanGroups = mutableSetOf<Pair<Long, String>>()

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
     * @param exercises List of exercises (must have at least 2 distinct exercises)
     * @param explicitGroupId Optional specific group ID to use (e.g. from a routine)
     */
    @Synchronized
    fun startSuperset(exercises: List<Exercise>, explicitGroupId: String? = null) {
        val distinctExercises = exercises.distinctBy { it.id }
        require(distinctExercises.size >= 2) { "Superset requires at least 2 distinct exercises" }
        _lastLoggedValues.clear()
        isPlanBackedSuperset = false
        planGroupWorkoutId = null
        planMemberIds = emptyList()
        _supersetExercises.value = distinctExercises
        _supersetGroupId.value = explicitGroupId ?: UUID.randomUUID().toString()
        _currentExerciseIndex.value = 0
        _isInSupersetMode.value = true
    }

    /**
     * Keeps a plan-backed superset synced with workout plan changes (e.g. overview swaps)
     * while preserving any extra exercises added dynamically during logging.
     */
    @Synchronized
    fun syncPlanSuperset(
        workoutId: Long,
        groupId: String,
        exercises: List<Exercise>,
        activeExerciseId: Long
    ) {
        if ((workoutId to groupId) in exitedPlanGroups) return
        val distinctPlanExercises = exercises.distinctBy { it.id }
        if (distinctPlanExercises.size < 2) return

        val alreadyInSamePlanGroup =
            _isInSupersetMode.value &&
                isPlanBackedSuperset &&
                planGroupWorkoutId == workoutId &&
                _supersetGroupId.value == groupId

        if (!alreadyInSamePlanGroup) {
            _lastLoggedValues.clear()
            isPlanBackedSuperset = true
            planGroupWorkoutId = workoutId
            planMemberIds = distinctPlanExercises.map { it.id }
            _supersetExercises.value = distinctPlanExercises
            _supersetGroupId.value = groupId
            _isInSupersetMode.value = true
            val activeIndex = distinctPlanExercises.indexOfFirst { it.id == activeExerciseId }
            _currentExerciseIndex.value = if (activeIndex >= 0) activeIndex else 0
            return
        }

        val newPlanIds = distinctPlanExercises.map { it.id }
        if (planMemberIds == newPlanIds) {
            val activeIndex = _supersetExercises.value.indexOfFirst { it.id == activeExerciseId }
            if (activeIndex >= 0) {
                _currentExerciseIndex.value = activeIndex
            }
            return
        }

        val oldPlanIdSet = planMemberIds.toSet()
        val newPlanIdSet = newPlanIds.toSet()
        val adHocExtras = _supersetExercises.value.filter {
            it.id !in oldPlanIdSet && it.id !in newPlanIdSet
        }
        val merged = distinctPlanExercises + adHocExtras
        val mergedIds = merged.mapTo(mutableSetOf()) { it.id }
        _lastLoggedValues.keys.retainAll(mergedIds)
        planMemberIds = newPlanIds
        _supersetExercises.value = merged
        val activeIndex = merged.indexOfFirst { it.id == activeExerciseId }
        if (activeIndex >= 0) {
            _currentExerciseIndex.value = activeIndex
        } else {
            _currentExerciseIndex.value = _currentExerciseIndex.value.coerceIn(0, merged.lastIndex)
        }
    }

    @Synchronized
    fun isPlanSupersetSynced(workoutId: Long, groupId: String, expectedPlanIds: List<Long>): Boolean {
        return _isInSupersetMode.value &&
            isPlanBackedSuperset &&
            planGroupWorkoutId == workoutId &&
            _supersetGroupId.value == groupId &&
            planMemberIds == expectedPlanIds
    }

    @Synchronized
    fun isPlanGroupExited(workoutId: Long, groupId: String): Boolean =
        (workoutId to groupId) in exitedPlanGroups

    @Synchronized
    fun dissolvePlanSupersetIfMissing(workoutId: Long, activePlanGroupIds: Set<String>) {
        if (!_isInSupersetMode.value || !isPlanBackedSuperset || planGroupWorkoutId != workoutId) return
        val currentGroupId = _supersetGroupId.value ?: return
        if (currentGroupId !in activePlanGroupIds) {
            exitSupersetMode()
        }
    }

    @Synchronized
    fun exitPlanSuperset(workoutId: Long?, fallbackGroupId: String? = null) {
        val groupId = if (isPlanBackedSuperset) _supersetGroupId.value else fallbackGroupId
        if (workoutId != null && groupId != null) {
            exitedPlanGroups.add(workoutId to groupId)
        }
        exitSupersetMode()
    }

    @Synchronized
    fun clearWorkoutExitedPlanGroups(workoutId: Long?) {
        if (workoutId != null) {
            exitedPlanGroups.removeAll { it.first == workoutId }
        }
    }

    /**
     * Switch to the next exercise in the rotation.
     * @return The exerciseId of the next exercise
     */
    @Synchronized
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
    @Synchronized
    fun getCurrentExerciseId(): Long? {
        val exercises = _supersetExercises.value
        if (exercises.isEmpty()) return null
        return exercises.getOrNull(_currentExerciseIndex.value)?.id
    }

    /**
     * Get the order index for a specific exercise in the superset.
     * @return The index (0-based) or -1 if not found
     */
    @Synchronized
    fun getOrderIndex(exerciseId: Long): Int {
        return _supersetExercises.value.indexOfFirst { it.id == exerciseId }
    }

    /**
     * Check if a specific exercise is part of the current superset.
     */
    @Synchronized
    fun isInCurrentSuperset(exerciseId: Long): Boolean {
        return _supersetExercises.value.any { it.id == exerciseId }
    }

    /**
     * Manually set the current exercise index (for manual switching).
     */
    @Synchronized
    fun setCurrentExerciseIndex(index: Int) {
        if (index >= 0 && index < _supersetExercises.value.size) {
            _currentExerciseIndex.value = index
        }
    }

    /** Selects a specific exercise in the active superset. */
    @Synchronized
    fun switchToExercise(exerciseId: Long): Boolean {
        val index = getOrderIndex(exerciseId)
        if (!_isInSupersetMode.value || index == -1) return false
        _currentExerciseIndex.value = index
        return true
    }

    /**
     * Add an exercise to an existing active superset.
     * @param exercise The exercise to add
     */
    @Synchronized
    fun addExercise(exercise: Exercise) {
        if (!_isInSupersetMode.value) return
        // Don't add duplicates
        if (_supersetExercises.value.any { it.id == exercise.id }) return
        _supersetExercises.value = _supersetExercises.value + exercise
    }

    /** Refreshes metadata (e.g. renamed title) for an exercise already in the active superset. */
    @Synchronized
    fun updateExercise(exercise: Exercise) {
        if (!_isInSupersetMode.value) return
        val current = _supersetExercises.value
        val index = current.indexOfFirst { it.id == exercise.id }
        if (index >= 0 && current[index] != exercise) {
            _supersetExercises.value = current.toMutableList().apply { set(index, exercise) }
        }
    }

    /**
     * Save the last-logged form values for an exercise.
     */
    @Synchronized
    fun saveLastLoggedValues(exerciseId: Long, values: LastLoggedValues) {
        _lastLoggedValues[exerciseId] = values
    }

    /**
     * Get the last-logged form values for an exercise, if any.
     */
    @Synchronized
    fun getLastLoggedValues(exerciseId: Long): LastLoggedValues? {
        return _lastLoggedValues[exerciseId]
    }

    /**
     * Exit superset mode and clear all state.
     */
    @Synchronized
    fun exitSupersetMode() {
        _isInSupersetMode.value = false
        _supersetExercises.value = emptyList()
        _supersetGroupId.value = null
        _currentExerciseIndex.value = 0
        _lastLoggedValues.clear()
        isPlanBackedSuperset = false
        planGroupWorkoutId = null
        planMemberIds = emptyList()
    }
}
