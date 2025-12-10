package com.example.gymtime.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseSelectionViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val supersetManager: SupersetManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedMuscles = MutableStateFlow<Set<String>>(emptySet())
    val selectedMuscles: StateFlow<Set<String>> = _selectedMuscles

    // Superset selection mode state
    private val _isSupersetModeEnabled = MutableStateFlow(false)
    val isSupersetModeEnabled: StateFlow<Boolean> = _isSupersetModeEnabled

    private val _selectedForSuperset = MutableStateFlow<List<Exercise>>(emptyList())
    val selectedForSuperset: StateFlow<List<Exercise>> = _selectedForSuperset

    // Maximum exercises allowed in a superset (2 for free, 3 for premium later)
    val maxSupersetExercises = 2

    private val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    // Get muscle groups from database
    val availableMuscles: Flow<List<String>> = muscleGroupDao.getAllMuscleGroups().map { groups ->
        val dbMuscles = groups.map { it.name }
        val defaultMuscles = listOf("Back", "Biceps", "Chest", "Core", "Legs", "Shoulders", "Triceps", "Cardio")
        (dbMuscles + defaultMuscles).distinct().sorted()
    }

    // Filtered exercises based on search query and selected muscles, sorted alphabetically
    val filteredExercises: Flow<List<Exercise>> = combine(
        allExercises,
        _searchQuery,
        _selectedMuscles
    ) { exercises, query, selectedMuscles ->
        exercises.filter { exercise ->
            val matchesSearch = exercise.name.contains(query, ignoreCase = true)
            val matchesMuscle = selectedMuscles.isEmpty() || exercise.targetMuscle in selectedMuscles
            matchesSearch && matchesMuscle
        }.sortedBy { it.name.lowercase() }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleMuscleFilter(muscle: String) {
        val current = _selectedMuscles.value.toMutableSet()
        if (muscle in current) {
            current.remove(muscle)
        } else {
            current.add(muscle)
        }
        _selectedMuscles.value = current
    }

    fun clearMuscleFilters() {
        _selectedMuscles.value = emptySet()
    }

    fun deleteExercise(exerciseId: Long) {
        viewModelScope.launch {
            exerciseDao.deleteExerciseById(exerciseId)
        }
    }

    // Superset mode functions

    /**
     * Toggle superset selection mode on/off.
     * Clears selection when turning off.
     */
    fun toggleSupersetMode() {
        _isSupersetModeEnabled.value = !_isSupersetModeEnabled.value
        if (!_isSupersetModeEnabled.value) {
            clearSupersetSelection()
        }
    }

    /**
     * Toggle selection of an exercise for superset.
     * If already selected, removes it. If not, adds it (up to max).
     */
    fun toggleExerciseSelection(exercise: Exercise) {
        val current = _selectedForSuperset.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == exercise.id }

        if (existingIndex >= 0) {
            // Already selected, remove it
            current.removeAt(existingIndex)
        } else if (current.size < maxSupersetExercises) {
            // Not selected and under limit, add it
            current.add(exercise)
        }
        // If at max, ignore the tap (don't add)

        _selectedForSuperset.value = current
    }

    /**
     * Check if an exercise is selected for superset.
     */
    fun isExerciseSelected(exerciseId: Long): Boolean {
        return _selectedForSuperset.value.any { it.id == exerciseId }
    }

    /**
     * Get the selection order number (1-based) for an exercise.
     * Returns null if not selected.
     */
    fun getSelectionOrder(exerciseId: Long): Int? {
        val index = _selectedForSuperset.value.indexOfFirst { it.id == exerciseId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Check if we have enough exercises selected to start a superset.
     */
    fun canStartSuperset(): Boolean {
        return _selectedForSuperset.value.size == maxSupersetExercises
    }

    /**
     * Start the superset with selected exercises.
     * This initializes the SupersetManager and returns the first exercise ID.
     */
    fun startSuperset(): Long {
        val exercises = _selectedForSuperset.value
        require(exercises.size >= 2) { "Need at least 2 exercises for superset" }

        supersetManager.startSuperset(exercises)

        // Clear local selection state (superset is now active in manager)
        _isSupersetModeEnabled.value = false
        _selectedForSuperset.value = emptyList()

        return exercises.first().id
    }

    /**
     * Clear superset selection without starting.
     */
    fun clearSupersetSelection() {
        _selectedForSuperset.value = emptyList()
    }
}
