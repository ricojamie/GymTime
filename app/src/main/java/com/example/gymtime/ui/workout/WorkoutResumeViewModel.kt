package com.example.gymtime.ui.workout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.data.repository.WorkoutPlanEditResult
import com.example.gymtime.ui.exercise.SupersetManager
import com.example.gymtime.wear.ActiveWearSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.example.gymtime.ui.workout.preview.CurrentWorkoutUiState
import javax.inject.Inject

data class ResumeExerciseItem(
    val instanceId: Long? = null,
    val exerciseId: Long,
    val exerciseName: String,
    val targetMuscle: String,
    val setCount: Int,
    val anySetCount: Int = setCount,
    val bestWeight: Float?,
    val supersetGroupId: String?,
    val orderIndex: Int,
    val plannedSets: Int? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val restSeconds: Int? = null,
    val isSkipped: Boolean = false,
    val addedDuringWorkout: Boolean = false,
    val completedEntryCount: Int = setCount,
    val bestWeightReps: Int? = null,
    val lastLoggedAtMs: Long? = null
)

@HiltViewModel
class WorkoutResumeViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val workoutRepository: WorkoutRepository,
    private val activeWearSessionRepository: ActiveWearSessionRepository,
    private val supersetManager: SupersetManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val newUiEnabled: Flow<Boolean> = userPreferencesRepository.newUiEnabled

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout

    private val _todaysExercises = MutableStateFlow<List<ResumeExerciseItem>>(emptyList())
    val todaysExercises: StateFlow<List<ResumeExerciseItem>> = _todaysExercises

    private val _isLoading = MutableStateFlow(true)
    private val _isFinishing = MutableStateFlow(false)
    val isFinishing: StateFlow<Boolean> = _isFinishing
    private val _finishError = MutableStateFlow<String?>(null)

    val previewState: StateFlow<CurrentWorkoutUiState> = combine(
        _currentWorkout, _todaysExercises, _isLoading, _isFinishing, _finishError
    ) { workout, exercises, loading, finishing, error ->
        val workingExercises = exercises.filterNot { it.targetMuscle.isWarmupMuscleGroup() }
        val targets = workingExercises.filter { !it.isSkipped && (it.plannedSets ?: 0) > 0 }
        val incompleteTargets = targets.filter { it.setCount < it.plannedSets!! }
        val firstIncomplete = incompleteTargets.firstOrNull()
        val next = firstIncomplete?.supersetGroupId?.let { groupId ->
            incompleteTargets.filter { it.supersetGroupId == groupId }
                .minWithOrNull(compareBy<ResumeExerciseItem> { it.setCount }.thenBy { it.orderIndex })
        } ?: firstIncomplete
        val last = exercises.filter { !it.isSkipped && it.lastLoggedAtMs != null }
            .maxByOrNull { it.lastLoggedAtMs!! }
        CurrentWorkoutUiState(
            workoutId = workout?.id,
            name = workout?.name?.takeIf(String::isNotBlank)
                ?: workout?.routineDayNameSnapshot?.takeIf(String::isNotBlank) ?: "Today's workout",
            routineName = workout?.routineNameSnapshot?.takeIf(String::isNotBlank),
            startedAtMs = workout?.startTime?.time,
            exercises = exercises,
            workingSets = workingExercises.sumOf { it.setCount },
            startedExercises = workingExercises.count { it.setCount > 0 },
            warmupEntries = exercises.sumOf {
                if (it.targetMuscle.isWarmupMuscleGroup()) it.completedEntryCount
                else (it.completedEntryCount - it.setCount).coerceAtLeast(0)
            },
            plannedWorkingSets = targets.sumOf { it.plannedSets!! },
            completedPlannedSets = targets.sumOf { it.setCount.coerceAtMost(it.plannedSets!!) },
            completedTargets = targets.count { it.setCount >= it.plannedSets!! },
            targetExercises = targets.size,
            continueExercise = next ?: last,
            hasNextTarget = next != null,
            isLoading = loading,
            isFinishing = finishing,
            finishError = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrentWorkoutUiState())

    init {
        loadTodaysWorkout()
    }

    private val _finishWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val finishWorkoutEvent = _finishWorkoutEvent.receiveAsFlow()

    private val _planEditMessage = Channel<String>(Channel.BUFFERED)
    val planEditMessage = _planEditMessage.receiveAsFlow()

    private fun loadTodaysWorkout() {
        viewModelScope.launch {
            workoutDao.getOngoingWorkout().collectLatest { workout ->
                _currentWorkout.value = workout
                if (workout == null) {
                    _todaysExercises.value = emptyList()
                    _isLoading.value = false
                    Log.d("WorkoutResumeVM", "No ongoing workout found")
                    return@collectLatest
                }

                Log.d("WorkoutResumeVM", "Ongoing workout found: ${workout.id}, startedFromRoutine=${workout.startedFromRoutine}")

                _isLoading.value = true
                _todaysExercises.value = emptyList()
                val summaries: Flow<List<ResumeExerciseItem>> = if (workoutRepository.hasWorkoutPlan(workout.id)) {
                    combine(workoutRepository.getWorkoutPlanSummaries(workout.id), setDao.getSetsForWorkout(workout.id)) { plan, sets ->
                        plan.map { it.toResumeExerciseItem().withLoggedSets(sets) }
                    }
                } else {
                    combine(setDao.getWorkoutExerciseSummaries(workout.id), setDao.getSetsForWorkout(workout.id)) { exercises, sets ->
                        exercises.mapIndexed { index, exercise -> exercise.toResumeExerciseItem(index).withLoggedSets(sets) }
                    }
                }
                summaries.collectLatest { exercises ->
                    _todaysExercises.value = exercises
                    _isLoading.value = false
                }
            }
        }
    }

    fun finishWorkout() {
        if (_isFinishing.value) return
        val workout = _currentWorkout.value ?: return
        _isFinishing.value = true
        _finishError.value = null
        viewModelScope.launch {
            try {
                workoutRepository.finishWorkout(workout.id)
                // Finishing is already persisted. A watch cleanup failure must not strand navigation.
                runCatching { activeWearSessionRepository.clear() }
                    .onFailure { Log.w("WorkoutResumeVM", "Unable to clear watch session", it) }
                supersetManager.exitSupersetMode()
                Log.d("WorkoutResumeVM", "Workout finished: ${workout.id}")
                _finishWorkoutEvent.send(workout.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("WorkoutResumeVM", "Unable to finish workout", error)
                val message = "Couldn't finish your workout. Your sets are saved; please try again."
                _finishError.value = message
                _planEditMessage.send(message)
            } finally {
                _isFinishing.value = false
            }
        }
    }

    fun removePlannedExercise(instanceId: Long) {
        viewModelScope.launch {
            if (_isFinishing.value) return@launch
            try {
                val message = when (workoutRepository.removeWorkoutPlanExercise(instanceId)) {
                    WorkoutPlanEditResult.Removed -> "Exercise removed from today's plan"
                    WorkoutPlanEditResult.HasLoggedSets -> "Exercises with logged sets can't be removed"
                    WorkoutPlanEditResult.NotFound -> "This planned exercise is no longer available"
                    WorkoutPlanEditResult.DuplicateExercise,
                    WorkoutPlanEditResult.Updated -> "Unable to remove exercise"
                }
                _planEditMessage.send(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("WorkoutResumeVM", "Unable to remove planned exercise", error)
                _planEditMessage.send("Couldn't remove this exercise. Please try again.")
            }
        }
    }
}

private fun ResumeExerciseItem.withLoggedSets(sets: List<com.example.gymtime.data.db.entity.Set>): ResumeExerciseItem {
    val matching = sets.filter { it.exerciseId == exerciseId }
    val completed = matching.filter { it.isComplete }
    val working = completed.filterNot { it.isWarmup }
    val best = working.filter { it.weight != null }.maxWithOrNull(
        compareBy<com.example.gymtime.data.db.entity.Set> { it.weight ?: 0f }.thenBy { it.reps ?: 0 }
    )
    return copy(
        anySetCount = matching.size,
        setCount = working.size,
        completedEntryCount = completed.size,
        bestWeight = best?.weight,
        bestWeightReps = best?.reps,
        lastLoggedAtMs = completed.maxOfOrNull { it.timestamp.time }
    )
}

private fun WorkoutPlanSummary.toResumeExerciseItem(): ResumeExerciseItem = ResumeExerciseItem(
    instanceId = instanceId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    targetMuscle = targetMuscle,
    setCount = setCount,
    anySetCount = anySetCount,
    bestWeight = bestWeight,
    supersetGroupId = supersetGroupId,
    orderIndex = orderIndex,
    plannedSets = plannedSets,
    repMin = repMin,
    repMax = repMax,
    restSeconds = restSeconds,
    isSkipped = isSkipped,
    addedDuringWorkout = addedDuringWorkout
)

private fun WorkoutExerciseSummary.toResumeExerciseItem(orderIndex: Int): ResumeExerciseItem = ResumeExerciseItem(
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    targetMuscle = targetMuscle,
    setCount = setCount,
    bestWeight = bestWeight,
    supersetGroupId = supersetGroupId,
    orderIndex = orderIndex
)
