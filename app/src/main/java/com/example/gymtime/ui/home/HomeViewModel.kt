package com.example.gymtime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workoutDao: WorkoutDao,
    private val routineDao: com.example.gymtime.data.db.dao.RoutineDao
) : ViewModel() {
    val userName: Flow<String> = userPreferencesRepository.userName
    val ongoingWorkout: StateFlow<Workout?> = workoutDao.getOngoingWorkout().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val workouts: Flow<List<WorkoutWithMuscles>> = workoutDao.getWorkoutsWithMuscles()

    val activeRoutineId: Flow<Long?> = userPreferencesRepository.activeRoutineId

    val hasActiveRoutine: StateFlow<Boolean> = activeRoutineId
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeRoutineName: StateFlow<String?> = activeRoutineId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else routineDao.getRoutineById(id).map { it?.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // TODO: Replace with real data
    val nextWorkoutName = "Legs"
    val streak = 3
    val poundsLifted = 22000
    val pbs = 3
}
