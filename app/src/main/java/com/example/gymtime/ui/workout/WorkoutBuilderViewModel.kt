package com.example.gymtime.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.data.repository.WorkoutStartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val allExercises = exerciseRepository.getAllExercises()

    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>(KEY_SEARCH_QUERY) ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMuscle = MutableStateFlow(savedStateHandle.get<String?>(KEY_SELECTED_MUSCLE))
    val selectedMuscle: StateFlow<String?> = _selectedMuscle.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow(
        savedStateHandle.get<LongArray>(KEY_SELECTED_IDS)?.toList().orEmpty()
    )
    val selectedExerciseIds: StateFlow<List<Long>> = _selectedExerciseIds.asStateFlow()

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _workoutStarted = Channel<WorkoutStartResult>(Channel.BUFFERED)
    val workoutStarted = _workoutStarted.receiveAsFlow()

    val availableMuscles: Flow<List<String>> = allExercises
        .map { exercises ->
            exercises.map { it.targetMuscle }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
        .flowOn(Dispatchers.Default)

    val filteredExercises: Flow<List<Exercise>> = combine(
        allExercises,
        _searchQuery,
        _selectedMuscle
    ) { exercises, query, muscle ->
        val trimmedQuery = query.trim()
        exercises
            .filter { exercise ->
                (trimmedQuery.isEmpty() || exercise.name.contains(trimmedQuery, ignoreCase = true)) &&
                    (muscle == null || exercise.targetMuscle == muscle)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.flowOn(Dispatchers.Default)

    val selectedExercises: Flow<List<Exercise>> = combine(
        allExercises,
        _selectedExerciseIds
    ) { exercises, selectedIds ->
        val exerciseById = exercises.associateBy { it.id }
        selectedIds.mapNotNull(exerciseById::get)
    }.flowOn(Dispatchers.Default)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun selectMuscle(muscle: String?) {
        _selectedMuscle.value = muscle
        savedStateHandle[KEY_SELECTED_MUSCLE] = muscle
    }

    fun toggleExercise(exerciseId: Long) {
        val selected = _selectedExerciseIds.value.toMutableList()
        val index = selected.indexOf(exerciseId)
        if (index == -1) selected.add(exerciseId) else selected.removeAt(index)
        updateSelectedIds(selected)
    }

    fun addExercise(exerciseId: Long) {
        if (exerciseId !in _selectedExerciseIds.value) {
            updateSelectedIds(_selectedExerciseIds.value + exerciseId)
        }
    }

    fun moveExercise(exerciseId: Long, offset: Int) {
        val selected = _selectedExerciseIds.value.toMutableList()
        val fromIndex = selected.indexOf(exerciseId)
        if (fromIndex == -1) return
        val toIndex = (fromIndex + offset).coerceIn(selected.indices)
        if (fromIndex == toIndex) return
        val moved = selected.removeAt(fromIndex)
        selected.add(toIndex, moved)
        updateSelectedIds(selected)
    }

    fun startWorkout() {
        val exerciseIds = _selectedExerciseIds.value
        if (exerciseIds.isEmpty() || _isStarting.value) return
        // Claim synchronously before launching so rapid double-taps cannot start two workouts.
        _isStarting.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                _workoutStarted.send(workoutRepository.startPlannedWorkout(exerciseIds))
            } catch (cancelled: CancellationException) {
                _isStarting.value = false
                throw cancelled
            } catch (error: Throwable) {
                _isStarting.value = false
                _errorMessage.value = error.message ?: "Could not start the workout."
            }
        }
    }

    fun onWorkoutOpened() {
        _isStarting.value = false
    }

    fun onWorkoutNavigationFailed() {
        _isStarting.value = false
        _errorMessage.value = "Your workout is saved, but couldn't open. Try starting again."
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun updateSelectedIds(updated: List<Long>) {
        _selectedExerciseIds.value = updated
        savedStateHandle[KEY_SELECTED_IDS] = updated.toLongArray()
    }

    private companion object {
        const val KEY_SEARCH_QUERY = "builder_search_query"
        const val KEY_SELECTED_MUSCLE = "builder_selected_muscle"
        const val KEY_SELECTED_IDS = "builder_selected_exercise_ids"
    }
}
