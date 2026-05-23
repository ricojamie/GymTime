package com.example.gymtime.ui.workout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.wear.ActiveWearSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ResumeExerciseItem(
    val exerciseId: Long,
    val exerciseName: String,
    val targetMuscle: String,
    val setCount: Int,
    val bestWeight: Float?,
    val supersetGroupId: String?,
    val orderIndex: Int,
    val plannedSets: Int? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val restSeconds: Int? = null,
    val isSkipped: Boolean = false,
    val addedDuringWorkout: Boolean = false
)

@HiltViewModel
class WorkoutResumeViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val workoutRepository: WorkoutRepository,
    private val activeWearSessionRepository: ActiveWearSessionRepository
) : ViewModel() {

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout

    private val _todaysExercises = MutableStateFlow<List<ResumeExerciseItem>>(emptyList())
    val todaysExercises: StateFlow<List<ResumeExerciseItem>> = _todaysExercises

    init {
        loadTodaysWorkout()
    }

    private val _finishWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val finishWorkoutEvent = _finishWorkoutEvent.receiveAsFlow()

    private fun loadTodaysWorkout() {
        viewModelScope.launch {
            workoutDao.getOngoingWorkout().collectLatest { workout ->
                _currentWorkout.value = workout
                if (workout == null) {
                    _todaysExercises.value = emptyList()
                    Log.d("WorkoutResumeVM", "No ongoing workout found")
                    return@collectLatest
                }

                Log.d("WorkoutResumeVM", "Ongoing workout found: ${workout.id}, startedFromRoutine=${workout.startedFromRoutine}")

                if (workout.startedFromRoutine && workoutRepository.hasWorkoutPlan(workout.id)) {
                    workoutRepository.getWorkoutPlanSummaries(workout.id).collectLatest { plan ->
                        _todaysExercises.value = plan.map { it.toResumeExerciseItem() }
                    }
                } else {
                    setDao.getWorkoutExerciseSummaries(workout.id).collectLatest { exercises ->
                        _todaysExercises.value = exercises.mapIndexed { index, exercise ->
                            exercise.toResumeExerciseItem(index)
                        }
                    }
                }
            }
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            val workout = _currentWorkout.value ?: return@launch
            workoutRepository.finishWorkout(workout.id)
            activeWearSessionRepository.clear()
            Log.d("WorkoutResumeVM", "Workout finished: ${workout.id}")
            _finishWorkoutEvent.send(workout.id)
        }
    }
}

private fun WorkoutPlanSummary.toResumeExerciseItem(): ResumeExerciseItem = ResumeExerciseItem(
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    targetMuscle = targetMuscle,
    setCount = setCount,
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
