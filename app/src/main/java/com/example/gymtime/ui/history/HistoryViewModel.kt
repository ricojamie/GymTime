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
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.AddToRoutineResult
import com.example.gymtime.data.RepeatWorkoutResult
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.WorkoutShareFormatter
import com.example.gymtime.util.WorkoutShareImageGenerator
import com.example.gymtime.util.WorkoutSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HistoryViewModel"

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val shareWorkoutUseCase: ShareWorkoutUseCase,
    private val workoutShareImageGenerator: WorkoutShareImageGenerator
) : ViewModel() {

    // Navigation event for resuming workout
    private val _resumeWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val resumeWorkoutEvent = _resumeWorkoutEvent.receiveAsFlow()

    private val _shareEvent = Channel<WorkoutSharePayload>(Channel.BUFFERED)
    val shareEvent = _shareEvent.receiveAsFlow()

    private val _copyEvent = Channel<String>(Channel.BUFFERED)
    val copyEvent = _copyEvent.receiveAsFlow()

    // Navigation event for repeating a workout (payload = first exercise to open)
    private val _repeatWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val repeatWorkoutEvent = _repeatWorkoutEvent.receiveAsFlow()

    // Transient user-facing messages (toasts)
    private val _userMessage = Channel<String>(Channel.BUFFERED)
    val userMessage = _userMessage.receiveAsFlow()

    val allRoutines: StateFlow<List<Routine>> = routineRepository.getAllRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun repeatWorkout(workoutId: Long) {
        viewModelScope.launch {
            try {
                when (val result = routineRepository.repeatWorkout(workoutId)) {
                    is RepeatWorkoutResult.Started -> {
                        clearSelection()
                        _repeatWorkoutEvent.send(result.result.firstExerciseId)
                        Log.d(TAG, "Repeated workout $workoutId as ${result.result.workoutId}")
                    }
                    RepeatWorkoutResult.OngoingWorkoutExists ->
                        _userMessage.send("Finish your current workout first")
                    RepeatWorkoutResult.NothingToRepeat ->
                        _userMessage.send("Nothing to repeat in this workout")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error repeating workout", e)
                _userMessage.send("Couldn't repeat workout")
            }
        }
    }

    suspend fun getDaysForRoutine(routineId: Long): List<RoutineDay> {
        return try {
            routineRepository.getDaysForRoutine(routineId).first()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading routine days", e)
            emptyList()
        }
    }

    fun addWorkoutToRoutine(workoutId: Long, routineId: Long, replaceDayId: Long?) {
        viewModelScope.launch {
            try {
                val routineName = allRoutines.value.firstOrNull { it.id == routineId }?.name ?: "routine"
                when (routineRepository.createRoutineDayFromWorkout(workoutId, routineId, replaceDayId)) {
                    is AddToRoutineResult.Added ->
                        _userMessage.send(
                            if (replaceDayId != null) "Day replaced in $routineName"
                            else "Added to $routineName"
                        )
                    AddToRoutineResult.NothingToAdd ->
                        _userMessage.send("Nothing to add from this workout")
                    AddToRoutineResult.RoutineFull ->
                        _userMessage.send("$routineName already has the maximum number of days")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding workout to routine", e)
                _userMessage.send("Couldn't add workout to routine")
            }
        }
    }

    fun shareWorkout(workoutId: Long, palette: ShareImagePalette) {
        viewModelScope.launch {
            try {
                shareWorkoutUseCase.buildShareableWorkout(workoutId)?.let { workout ->
                    _shareEvent.send(
                        WorkoutSharePayload(
                            imageUri = workoutShareImageGenerator.generate(workout, palette)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error building share text", e)
            }
        }
    }

    fun copyWorkout(workoutId: Long) {
        viewModelScope.launch {
            try {
                shareWorkoutUseCase.buildShareableWorkout(workoutId)?.let { workout ->
                    _copyEvent.send(WorkoutShareFormatter.format(workout))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error building share text", e)
            }
        }
    }
}
