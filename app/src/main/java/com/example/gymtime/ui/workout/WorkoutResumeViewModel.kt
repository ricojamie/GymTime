package com.example.gymtime.ui.workout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutResumeViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) : ViewModel() {

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout

    private val _todaysExercises = MutableStateFlow<List<WorkoutExerciseSummary>>(emptyList())
    val todaysExercises: StateFlow<List<WorkoutExerciseSummary>> = _todaysExercises

    init {
        loadTodaysWorkout()
    }

    private fun loadTodaysWorkout() {
        viewModelScope.launch {
            // Get ongoing workout
            val ongoing = workoutDao.getOngoingWorkout().first()
            if (ongoing != null) {
                _currentWorkout.value = ongoing
                Log.d("WorkoutResumeVM", "Ongoing workout found: ${ongoing.id}")

                // Load all exercises in this workout
                val exercises = setDao.getWorkoutExerciseSummaries(ongoing.id)
                _todaysExercises.value = exercises
                Log.d("WorkoutResumeVM", "Loaded ${exercises.size} exercises for workout")
            } else {
                // No ongoing workout - this shouldn't happen if navigation is correct
                Log.d("WorkoutResumeVM", "No ongoing workout found")
            }
        }
    }

    fun refreshExercises() {
        viewModelScope.launch {
            _currentWorkout.value?.let { workout ->
                val exercises = setDao.getWorkoutExerciseSummaries(workout.id)
                _todaysExercises.value = exercises
                Log.d("WorkoutResumeVM", "Refreshed exercises: ${exercises.size}")
            }
        }
    }
}
