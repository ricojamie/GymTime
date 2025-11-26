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
import kotlinx.coroutines.flow.collectLatest
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
            workoutDao.getOngoingWorkout().collectLatest { workout ->
                _currentWorkout.value = workout
                if (workout != null) {
                    Log.d("WorkoutResumeVM", "Ongoing workout found: ${workout.id}")
                    // Load all exercises in this workout reactively
                    setDao.getWorkoutExerciseSummaries(workout.id).collectLatest { exercises ->
                        _todaysExercises.value = exercises
                        Log.d("WorkoutResumeVM", "Loaded ${exercises.size} exercises for workout")
                    }
                } else {
                    // No ongoing workout - reset exercises
                    _todaysExercises.value = emptyList()
                    Log.d("WorkoutResumeVM", "No ongoing workout found")
                }
            }
        }
    }


}
