package com.example.gymtime.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.AddToRoutineResult
import com.example.gymtime.data.RepeatWorkoutResult
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.WorkoutShareFormatter
import com.example.gymtime.util.WorkoutShareImageGenerator
import com.example.gymtime.util.WorkoutSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private sealed interface HistoryFeedState {
        data object Loading : HistoryFeedState
        data class Loaded(val workouts: List<WorkoutWithMuscles>) : HistoryFeedState
        data class Error(val message: String) : HistoryFeedState
    }

    private var detailJob: Job? = null
    private val deletingWorkoutIds = mutableSetOf<Long>()
    private var isResumingWorkout = false
    private var isRepeatingWorkout = false
    private var isSharingWorkout = false
    private var isCopyingWorkout = false
    private var isAddingToRoutine = false

    private val retryTrigger = MutableStateFlow(0)
    private val deletedWorkoutIds = MutableStateFlow<Set<Long>>(emptySet())
    private val cachedWorkouts = MutableStateFlow<List<WorkoutWithMuscles>?>(null)

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyFeedState: StateFlow<HistoryFeedState> = retryTrigger
        .flatMapLatest {
            workoutDao.getWorkoutsWithMuscles()
                .map<List<WorkoutWithMuscles>, HistoryFeedState> { workouts ->
                    cachedWorkouts.value = workouts
                    HistoryFeedState.Loaded(workouts)
                }
                .onStart {
                    val cached = cachedWorkouts.value
                    emit(
                        if (cached == null) {
                            HistoryFeedState.Loading
                        } else {
                            HistoryFeedState.Loaded(cached)
                        }
                    )
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Error loading workouts", error)
                    emit(HistoryFeedState.Error("Couldn't load your history. Try again."))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryFeedState.Loading)

    val allWorkouts: StateFlow<List<WorkoutWithMuscles>> = combine(
        historyFeedState,
        deletedWorkoutIds
    ) { feedState, deletedIds ->
        val workouts = (feedState as? HistoryFeedState.Loaded)?.workouts
            ?: cachedWorkouts.value.orEmpty()
        if (deletedIds.isEmpty()) workouts else workouts.filterNot { it.workout.id in deletedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoading: StateFlow<Boolean> = historyFeedState
        .map { it is HistoryFeedState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val historyError: StateFlow<String?> = historyFeedState
        .map { (it as? HistoryFeedState.Error)?.message }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _selectedWorkoutDetails = MutableStateFlow<List<SetWithExerciseInfo>?>(null)
    val selectedWorkoutDetails: StateFlow<List<SetWithExerciseInfo>?> = _selectedWorkoutDetails.asStateFlow()

    private val _selectedWorkout = MutableStateFlow<WorkoutWithMuscles?>(null)
    val selectedWorkout: StateFlow<WorkoutWithMuscles?> = _selectedWorkout.asStateFlow()

    fun retryLoadWorkouts() {
        cachedWorkouts.value = null
        retryTrigger.update { it + 1 }
    }

    fun selectWorkout(workout: WorkoutWithMuscles) {
        detailJob?.cancel()
        _selectedWorkoutDetails.value = null
        _detailError.value = null
        _selectedWorkout.value = workout
        detailJob = viewModelScope.launch {
            try {
                val details = setDao.getWorkoutSetsWithExercises(workout.workout.id)
                if (_selectedWorkout.value?.workout?.id == workout.workout.id) {
                    _selectedWorkoutDetails.value = details
                }
                Log.d(TAG, "Selected workout ${workout.workout.id} with ${details.size} sets")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading workout details", e)
                if (_selectedWorkout.value?.workout?.id == workout.workout.id) {
                    _detailError.value = "Couldn't load the sets. Try again."
                }
            }
        }
    }

    fun clearSelection() {
        detailJob?.cancel()
        _selectedWorkout.value = null
        _selectedWorkoutDetails.value = null
        _detailError.value = null
    }

    fun deleteWorkout(workoutId: Long) {
        if (!deletingWorkoutIds.add(workoutId)) return
        viewModelScope.launch {
            try {
                val workoutToDelete = cachedWorkouts.value?.find { it.workout.id == workoutId }?.workout
                    ?: workoutDao.getWorkoutByIdSync(workoutId)
                if (workoutToDelete == null) {
                    _userMessage.send("Workout was already removed")
                    if (_selectedWorkout.value?.workout?.id == workoutId) {
                        clearSelection()
                    }
                    return@launch
                }
                deletedWorkoutIds.update { it + workoutId }
                workoutDao.deleteWorkout(workoutToDelete)
                if (_selectedWorkout.value?.workout?.id == workoutId) {
                    clearSelection()
                }
                Log.d(TAG, "Deleted workout $workoutId")
            } catch (e: CancellationException) {
                deletedWorkoutIds.update { it - workoutId }
                throw e
            } catch (e: Exception) {
                deletedWorkoutIds.update { it - workoutId }
                Log.e(TAG, "Error deleting workout", e)
                _userMessage.send("Couldn't delete workout")
            } finally {
                deletingWorkoutIds.remove(workoutId)
            }
        }
    }

    fun resumeWorkout(workoutId: Long) {
        if (isResumingWorkout) return
        isResumingWorkout = true
        viewModelScope.launch {
            try {
                workoutRepository.reopenWorkout(workoutId)
                if (_selectedWorkout.value?.workout?.id == workoutId) {
                    clearSelection()
                }
                _resumeWorkoutEvent.send(workoutId)
                Log.d(TAG, "Reopened workout $workoutId for resume")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error reopening workout", e)
                _userMessage.send(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't resume workout")
            } finally {
                isResumingWorkout = false
            }
        }
    }

    fun repeatWorkout(workoutId: Long) {
        if (isRepeatingWorkout) return
        isRepeatingWorkout = true
        viewModelScope.launch {
            try {
                when (val result = routineRepository.repeatWorkout(workoutId)) {
                    is RepeatWorkoutResult.Started -> {
                        if (_selectedWorkout.value?.workout?.id == workoutId) {
                            clearSelection()
                        }
                        _repeatWorkoutEvent.send(result.result.firstExerciseId)
                        Log.d(TAG, "Repeated workout $workoutId as ${result.result.workoutId}")
                    }
                    RepeatWorkoutResult.OngoingWorkoutExists ->
                        _userMessage.send("Finish your current workout first")
                    RepeatWorkoutResult.NothingToRepeat ->
                        _userMessage.send("Nothing to repeat in this workout")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error repeating workout", e)
                _userMessage.send("Couldn't repeat workout")
            } finally {
                isRepeatingWorkout = false
            }
        }
    }

    suspend fun getDaysForRoutine(routineId: Long): List<RoutineDay> {
        return try {
            routineRepository.getDaysForRoutine(routineId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error loading routine days", e)
            emptyList()
        }
    }

    fun addWorkoutToRoutine(workoutId: Long, routineId: Long, replaceDayId: Long?) {
        if (isAddingToRoutine) return
        isAddingToRoutine = true
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error adding workout to routine", e)
                _userMessage.send("Couldn't add workout to routine")
            } finally {
                isAddingToRoutine = false
            }
        }
    }

    fun shareWorkout(workoutId: Long, palette: ShareImagePalette) {
        if (isSharingWorkout) return
        isSharingWorkout = true
        viewModelScope.launch {
            try {
                val workout = shareWorkoutUseCase.buildShareableWorkout(workoutId)
                if (workout == null) {
                    _userMessage.send("Nothing to share from this workout")
                    return@launch
                }
                _shareEvent.send(
                    WorkoutSharePayload(
                        imageUri = workoutShareImageGenerator.generate(workout, palette),
                        text = WorkoutShareFormatter.format(workout)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing workout", e)
                _userMessage.send("Couldn't share workout")
            } finally {
                isSharingWorkout = false
            }
        }
    }

    fun copyWorkout(workoutId: Long) {
        if (isCopyingWorkout) return
        isCopyingWorkout = true
        viewModelScope.launch {
            try {
                val workout = shareWorkoutUseCase.buildShareableWorkout(workoutId)
                if (workout == null) {
                    _userMessage.send("Nothing to copy from this workout")
                    return@launch
                }
                _copyEvent.send(WorkoutShareFormatter.format(workout))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error building share text", e)
                _userMessage.send("Couldn't copy workout")
            } finally {
                isCopyingWorkout = false
            }
        }
    }
}
