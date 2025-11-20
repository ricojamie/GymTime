package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ExerciseLoggingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])

    private val _exercise = MutableStateFlow<Exercise?>(null)
    val exercise: StateFlow<Exercise?> = _exercise

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout

    private val _loggedSets = MutableStateFlow<List<Set>>(emptyList())
    val loggedSets: StateFlow<List<Set>> = _loggedSets

    // Form state
    private val _weight = MutableStateFlow("")
    val weight: StateFlow<String> = _weight

    private val _reps = MutableStateFlow("")
    val reps: StateFlow<String> = _reps

    private val _rpe = MutableStateFlow("")
    val rpe: StateFlow<String> = _rpe

    private val _restTime = MutableStateFlow(90) // Default rest in seconds
    val restTime: StateFlow<Int> = _restTime

    private val _isWarmup = MutableStateFlow(false)
    val isWarmup: StateFlow<Boolean> = _isWarmup

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    init {
        viewModelScope.launch {
            // Load exercise
            exerciseDao.getExerciseById(exerciseId).collectLatest { exercise ->
                _exercise.value = exercise
            }
        }

        viewModelScope.launch {
            // Load or create workout
            val ongoing = workoutDao.getOngoingWorkout().first()
            if (ongoing != null) {
                _currentWorkout.value = ongoing
            } else {
                // Create new workout
                val newWorkout = Workout(
                    startTime = Date(),
                    endTime = null,
                    name = null,
                    note = null
                )
                val workoutId = workoutDao.insertWorkout(newWorkout)
                val createdWorkout = workoutDao.getWorkoutById(workoutId).first()
                _currentWorkout.value = createdWorkout
            }

            // Load logged sets for this exercise in current workout
            _currentWorkout.value?.let { workout ->
                setDao.getSetsForWorkout(workout.id).collectLatest { sets ->
                    _loggedSets.value = sets.filter { it.exerciseId == exerciseId }
                }
            }
        }
    }

    fun updateWeight(value: String) {
        _weight.value = value
    }

    fun updateReps(value: String) {
        _reps.value = value
    }

    fun updateRpe(value: String) {
        _rpe.value = value
    }

    fun updateRestTime(seconds: Int) {
        _restTime.value = seconds
    }

    fun toggleWarmup() {
        _isWarmup.value = !_isWarmup.value
    }

    fun logSet() {
        viewModelScope.launch {
            val workout = _currentWorkout.value ?: return@launch
            val exercise = _exercise.value ?: return@launch

            val newSet = Set(
                workoutId = workout.id,
                exerciseId = exercise.id,
                weight = _weight.value.toFloatOrNull(),
                reps = _reps.value.toIntOrNull(),
                rpe = _rpe.value.toFloatOrNull(),
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = _isWarmup.value,
                isComplete = true,
                timestamp = Date()
            )

            setDao.insertSet(newSet)

            // Clear form
            _weight.value = ""
            _reps.value = ""
            _rpe.value = ""
            _isWarmup.value = false
            
            // Start timer
            _isTimerRunning.value = true
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            val workout = _currentWorkout.value ?: return@launch
            val updatedWorkout = workout.copy(endTime = Date())
            workoutDao.updateWorkout(updatedWorkout)
        }
    }
}
