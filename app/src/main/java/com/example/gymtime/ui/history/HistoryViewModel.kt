package com.example.gymtime.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HistoryViewModel"

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    // Navigation event for resuming workout
    private val _resumeWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val resumeWorkoutEvent = _resumeWorkoutEvent.receiveAsFlow()

    private val _allWorkouts = MutableStateFlow<List<WorkoutWithMuscles>>(emptyList())
    val allWorkouts: StateFlow<List<WorkoutWithMuscles>> = _allWorkouts.asStateFlow()

    private val _selectedWorkoutDetails = MutableStateFlow<List<SetWithExerciseInfo>?>(null)
    val selectedWorkoutDetails: StateFlow<List<SetWithExerciseInfo>?> = _selectedWorkoutDetails.asStateFlow()

    private val _selectedWorkout = MutableStateFlow<WorkoutWithMuscles?>(null)
    val selectedWorkout: StateFlow<WorkoutWithMuscles?> = _selectedWorkout.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadWorkouts()
    }

    private fun loadWorkouts() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                workoutDao.getWorkoutsWithMuscles().collect { workouts ->
                    _allWorkouts.value = workouts
                    Log.d(TAG, "Loaded ${workouts.size} workouts")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading workouts", e)
                _isLoading.value = false
            }
        }
    }

    fun selectWorkout(workout: WorkoutWithMuscles) {
        viewModelScope.launch {
            try {
                _selectedWorkout.value = workout
                val details = setDao.getWorkoutSetsWithExercises(workout.workout.id)
                _selectedWorkoutDetails.value = details
                Log.d(TAG, "Selected workout ${workout.workout.id} with ${details.size} sets")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading workout details", e)
            }
        }
    }

    fun clearSelection() {
        _selectedWorkout.value = null
        _selectedWorkoutDetails.value = null
    }

    fun deleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            try {
                val workoutToDelete = _allWorkouts.value.find { it.workout.id == workoutId }?.workout
                if (workoutToDelete != null) {
                    workoutDao.deleteWorkout(workoutToDelete)
                    _allWorkouts.value = _allWorkouts.value.filter { it.workout.id != workoutId }
                    clearSelection()
                    Log.d(TAG, "Deleted workout $workoutId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting workout", e)
            }
        }
    }

    fun resumeWorkout(workoutId: Long) {
        viewModelScope.launch {
            try {
                workoutRepository.reopenWorkout(workoutId)
                clearSelection()
                _resumeWorkoutEvent.send(workoutId)
                Log.d(TAG, "Reopened workout $workoutId for resume")
            } catch (e: Exception) {
                Log.e(TAG, "Error reopening workout", e)
            }
        }
    }
}
