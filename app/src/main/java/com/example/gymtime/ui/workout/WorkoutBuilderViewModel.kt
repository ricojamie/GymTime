package com.example.gymtime.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.data.repository.WorkoutStartResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val allExercises = exerciseRepository.getAllExercises()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedMuscle = MutableStateFlow<String?>(null)
    val selectedMuscle: StateFlow<String?> = _selectedMuscle

    private val _selectedExerciseIds = MutableStateFlow<List<Long>>(emptyList())
    val selectedExerciseIds: StateFlow<List<Long>> = _selectedExerciseIds

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _workoutStarted = Channel<WorkoutStartResult>(Channel.BUFFERED)
    val workoutStarted = _workoutStarted.receiveAsFlow()

    val availableMuscles: Flow<List<String>> = allExercises.map { exercises ->
        exercises.map { it.targetMuscle }.distinct().sorted()
    }

    val filteredExercises: Flow<List<Exercise>> = combine(
        allExercises,
        _searchQuery,
        _selectedMuscle
    ) { exercises, query, muscle ->
        exercises
            .filter { exercise ->
                exercise.name.contains(query.trim(), ignoreCase = true) &&
                    (muscle == null || exercise.targetMuscle == muscle)
            }
            .sortedBy { it.name.lowercase() }
    }

    val selectedExercises: Flow<List<Exercise>> = combine(
        allExercises,
        _selectedExerciseIds
    ) { exercises, selectedIds ->
        val exerciseById = exercises.associateBy { it.id }
        selectedIds.mapNotNull(exerciseById::get)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectMuscle(muscle: String?) {
        _selectedMuscle.value = muscle
    }

    fun toggleExercise(exerciseId: Long) {
        val selected = _selectedExerciseIds.value.toMutableList()
        val index = selected.indexOf(exerciseId)
        if (index == -1) selected.add(exerciseId) else selected.removeAt(index)
        _selectedExerciseIds.value = selected
    }

    fun addExercise(exerciseId: Long) {
        if (exerciseId !in _selectedExerciseIds.value) {
            _selectedExerciseIds.value = _selectedExerciseIds.value + exerciseId
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
        _selectedExerciseIds.value = selected
    }

    fun startWorkout() {
        val exerciseIds = _selectedExerciseIds.value
        if (exerciseIds.isEmpty() || _isStarting.value) return

        viewModelScope.launch {
            _isStarting.value = true
            _errorMessage.value = null
            try {
                _workoutStarted.send(workoutRepository.startPlannedWorkout(exerciseIds))
            } catch (error: Throwable) {
                _errorMessage.value = error.message ?: "Could not start the workout."
            } finally {
                _isStarting.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
