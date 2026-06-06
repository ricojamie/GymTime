package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class ExerciseSortMode(val label: String) {
    ALPHABETICAL("A-Z"),
    ALL_TIME_SETS("All-time sets"),
    RECENT_SETS("90d sets"),
    RECENTLY_USED("Recently used")
}

@HiltViewModel
class ExerciseSelectionViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val supersetManager: SupersetManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutMode: Boolean = savedStateHandle["workoutMode"] ?: false
    private val supersetMode: Boolean = savedStateHandle["supersetMode"] ?: false
    private val adHocParentId: Long? = savedStateHandle["adHocParentId"]
    private val addToSuperset: Boolean = savedStateHandle["addToSuperset"] ?: false

    // Track if we're in workout mode (passed from navigation, not DB query)
    val isWorkoutMode: StateFlow<Boolean> = MutableStateFlow(workoutMode)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedMuscles = MutableStateFlow<Set<String>>(emptySet())
    val selectedMuscles: StateFlow<Set<String>> = _selectedMuscles

    private val _sortMode = MutableStateFlow(ExerciseSortMode.ALPHABETICAL)
    val sortMode: StateFlow<ExerciseSortMode> = _sortMode

    // Superset selection mode state
    private val _isSupersetModeEnabled = MutableStateFlow(supersetMode)
    val isSupersetModeEnabled: StateFlow<Boolean> = _isSupersetModeEnabled

    private val _selectedForSuperset = MutableStateFlow<List<Exercise>>(emptyList())
    val selectedForSuperset: StateFlow<List<Exercise>> = _selectedForSuperset

    private val _supersetStarted = MutableSharedFlow<Long>()
    val supersetStarted = _supersetStarted.asSharedFlow()

    // Event when exercise is added to existing superset (pop back)
    private val _exerciseAddedToSuperset = MutableSharedFlow<Long>()
    val exerciseAddedToSuperset = _exerciseAddedToSuperset.asSharedFlow()

    // Expose add-to-superset mode for UI
    val isAddToSupersetMode: Boolean = addToSuperset

    // Maximum exercises allowed in a superset
    val maxSupersetExercises = 10

    private val recentUsageStartMs: Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -90)
    }.timeInMillis

    private val allExercises: Flow<List<ExerciseUsageRow>> =
        exerciseRepository.getExercisesWithUsageStats(recentUsageStartMs)

    // Get muscle groups from database
    val availableMuscles: Flow<List<String>> = exerciseRepository.getAllMuscleGroups().map { groups ->
        groups.map { it.name }.sorted()
    }

    // Filtered exercises based on search query and selected muscles.
    val filteredExercises: Flow<List<ExerciseUsageRow>> = combine(
        allExercises,
        _searchQuery,
        _selectedMuscles,
        _sortMode
    ) { rows, query, selectedMuscles, sortMode ->
        val filtered = rows.filter { row ->
            val exercise = row.exercise
            val matchesSearch = exercise.name.contains(query, ignoreCase = true)
            val matchesMuscle = selectedMuscles.isEmpty() || exercise.targetMuscle in selectedMuscles
            matchesSearch && matchesMuscle
        }

        when (sortMode) {
            ExerciseSortMode.ALPHABETICAL -> filtered.sortedBy { it.exercise.name.lowercase() }
            ExerciseSortMode.ALL_TIME_SETS -> filtered.sortedWith(
                compareByDescending<ExerciseUsageRow> { it.allTimeSetCount }
                    .thenBy { it.exercise.name.lowercase() }
            )
            ExerciseSortMode.RECENT_SETS -> filtered.sortedWith(
                compareByDescending<ExerciseUsageRow> { it.recentSetCount }
                    .thenBy { it.exercise.name.lowercase() }
            )
            // Most recently used first; exercises that have never been used (null
            // lastUsedMs) sort to the bottom, then alphabetically as a tiebreaker.
            ExerciseSortMode.RECENTLY_USED -> filtered.sortedWith(
                compareByDescending<ExerciseUsageRow> { it.lastUsedMs ?: Long.MIN_VALUE }
                    .thenBy { it.exercise.name.lowercase() }
            )
        }
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

    fun updateSortMode(mode: ExerciseSortMode) {
        _sortMode.value = mode
    }

    fun deleteExercise(exerciseId: Long) {
        viewModelScope.launch {
            exerciseRepository.deleteExercise(exerciseId)
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
        if (addToSuperset) {
            // Add-to-existing-superset mode: add exercise and pop back
            addExerciseToExistingSuperset(exercise)
            return
        }

        if (supersetMode && adHocParentId != null && adHocParentId != -1L) {
            // Ad-hoc mode: We have parent A, just clicked B. Start superset and go back.
            startAdHocSuperset(exercise)
            return
        }

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
        return _selectedForSuperset.value.size >= 2
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

    /**
     * Toggle the starred status of an exercise.
     * Maximum of 3 exercises can be starred.
     */
    fun toggleExerciseStarred(exercise: Exercise) {
        viewModelScope.launch {
            if (!exercise.isStarred) {
                // Check if we already have 3 starred
                val currentStarredCount = exerciseRepository.getStarredExercises().first().size
                if (currentStarredCount >= 3) {
                    // Maximum reached - UI should ideally show a message
                    return@launch
                }
            }
            exerciseRepository.updateStarredStatus(exercise.id, !exercise.isStarred)
        }
    }
    private fun addExerciseToExistingSuperset(exercise: Exercise) {
        viewModelScope.launch {
            supersetManager.addExercise(exercise)
            _exerciseAddedToSuperset.emit(exercise.id)
        }
    }

    private fun startAdHocSuperset(secondExercise: Exercise) {
        viewModelScope.launch {
            val firstExercise = exerciseRepository.getExercise(adHocParentId!!).first() ?: return@launch
            supersetManager.startSuperset(listOf(firstExercise, secondExercise))
            // Emit the SECOND exercise ID so the UI navigates to the new one
            _supersetStarted.emit(secondExercise.id)
        }
    }
}
