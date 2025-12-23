package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RoutineDayStartViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val workoutDao: WorkoutDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Retrieve routineId as Long (NavType.LongType)
    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val routineName: Flow<String> = routineRepository.getRoutineById(routineId).map { it?.name ?: "" }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val daysWithExercises = routineRepository.getDaysForRoutine(routineId)
        .flatMapLatest { days ->
            if (days.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(days.map { day ->
                    routineRepository.getRoutineDayWithExercises(day.id)
                }) { array ->
                    array.filterNotNull().toList()
                }
            }
        }

    private val _startWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val startWorkoutEvent = _startWorkoutEvent.receiveAsFlow()

    fun startWorkoutFromDay(dayId: Long) {
        viewModelScope.launch {
            // Get exercises for this day
            val exercises = routineRepository.getExerciseListForDay(dayId).first()

            if (exercises.isEmpty()) return@launch

            // Create workout linked to routine day
            val workoutId = workoutDao.insertWorkout(
                Workout(
                    startTime = Date(),
                    endTime = null,
                    name = null, // Could set a default name like "Leg Day" here if desired
                    note = null,
                    routineDayId = dayId
                )
            )

            _startWorkoutEvent.send(exercises.first().id)
        }
    }
}