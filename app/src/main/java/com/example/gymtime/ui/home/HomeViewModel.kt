package com.example.gymtime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workoutDao: WorkoutDao,
    private val routineDao: com.example.gymtime.data.db.dao.RoutineDao,
    private val setDao: SetDao
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

    // Weekly volume data (real data from database)
    private val _weeklyVolume = MutableStateFlow(0f)
    val weeklyVolume: StateFlow<Float> = _weeklyVolume.asStateFlow()

    // Weekly volume trend data (last 7 days, day by day)
    private val _weeklyVolumeTrend = MutableStateFlow<List<Float>>(emptyList())
    val weeklyVolumeTrend: StateFlow<List<Float>> = _weeklyVolumeTrend.asStateFlow()

    // TODO: Replace with real data
    val nextWorkoutName = "Legs"
    val streak = 3
    val pbs = 3

    init {
        loadWeeklyVolume()
    }

    private fun loadWeeklyVolume() {
        viewModelScope.launch {
            // Get start of current week (Monday)
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val weekStart = calendar.timeInMillis

            // Get current time as end
            val weekEnd = System.currentTimeMillis()

            // Get total weekly volume
            val volume = setDao.getTotalVolume(weekStart, weekEnd) ?: 0f
            _weeklyVolume.value = volume

            // Get daily volume for the last 7 days for the trend chart
            val dailyVolumes = mutableListOf<Float>()
            for (i in 6 downTo 0) {
                val dayCalendar = Calendar.getInstance()
                dayCalendar.add(Calendar.DAY_OF_YEAR, -i)
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0)
                dayCalendar.set(Calendar.MINUTE, 0)
                dayCalendar.set(Calendar.SECOND, 0)
                dayCalendar.set(Calendar.MILLISECOND, 0)
                val dayStart = dayCalendar.timeInMillis

                dayCalendar.set(Calendar.HOUR_OF_DAY, 23)
                dayCalendar.set(Calendar.MINUTE, 59)
                dayCalendar.set(Calendar.SECOND, 59)
                dayCalendar.set(Calendar.MILLISECOND, 999)
                val dayEnd = dayCalendar.timeInMillis

                val dayVolume = setDao.getTotalVolume(dayStart, dayEnd) ?: 0f
                dailyVolumes.add(dayVolume)
            }
            _weeklyVolumeTrend.value = dailyVolumes
        }
    }

    fun refreshData() {
        loadWeeklyVolume()
    }
}
