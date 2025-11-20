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
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workoutDao: WorkoutDao
) : ViewModel() {
    val userName: Flow<String> = userPreferencesRepository.userName
    val ongoingWorkout: StateFlow<Workout?> = workoutDao.getOngoingWorkout().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val workouts: Flow<List<WorkoutWithMuscles>> = workoutDao.getWorkoutsWithMuscles()

    // TODO: Replace with real data
    val hasActiveRoutine = true
    val nextWorkoutName = "Legs"
    val streak = 3
    val poundsLifted = 22000
    val pbs = 3
}
