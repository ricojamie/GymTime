package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
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
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<String>("routineId")?.toLong() ?: 0L

    val routineName: Flow<String> = routineDao.getRoutineById(routineId).map { it?.name ?: "" }

    val daysWithExercises = routineDao.getDaysForRoutine(routineId)
        .flatMapLatest { days ->
            if (days.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(days.map { day ->
                    routineDao.getRoutineDayWithExercises(day.id)
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
            val exercises = routineDao.getExerciseListForDay(dayId).first()

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

            // Navigate to first exercise (placeholder - usually we'd go to workout overview or first exercise)
            // For this app's flow, we probably want to go to the ExerciseLogging screen for the first exercise
            // But we need to handle the workout session state. 
            // Assuming the existing app flow handles active workout state via global or shared VM/Repo
            // Since I can't see the "ActiveWorkoutManager" equivalent, I'll assume passing the exercise ID 
            // to the logging screen is the entry point, and the logging VM will pick up the active workout.
            // Wait, the logging screen takes an exerciseId. 
            
            _startWorkoutEvent.send(exercises.first().id)
        }
    }
}
