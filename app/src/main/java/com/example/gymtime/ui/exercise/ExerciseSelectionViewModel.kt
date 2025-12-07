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
    private val muscleGroupDao: MuscleGroupDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedMuscles = MutableStateFlow<Set<String>>(emptySet())
    val selectedMuscles: StateFlow<Set<String>> = _selectedMuscles

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
}
