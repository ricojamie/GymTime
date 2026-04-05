package com.example.gymtime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.ActiveRoutineStatus
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.data.repository.WorkoutStartResult
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.util.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val volumeOrbRepository: VolumeOrbRepository
) : ViewModel() {
    val userName: Flow<String> = userPreferencesRepository.userName
    val ongoingWorkout: StateFlow<Workout?> = workoutRepository.getOngoingWorkoutFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val activeRoutineStatus: StateFlow<ActiveRoutineStatus?> = routineRepository.getActiveRoutineStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasActiveRoutine: StateFlow<Boolean> = activeRoutineStatus.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeRoutineName: StateFlow<String?> = activeRoutineStatus.map { it?.routine?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nextRoutineDayName: StateFlow<String?> = activeRoutineStatus.map { it?.nextDay?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _startRoutineWorkoutEvent = Channel<WorkoutStartResult>(Channel.BUFFERED)
    val startRoutineWorkoutEvent = _startRoutineWorkoutEvent.receiveAsFlow()

    private val _weeklyVolume = MutableStateFlow(0f)
    val weeklyVolume: StateFlow<Float> = _weeklyVolume.asStateFlow()

    private val _weeklyVolumeTrend = MutableStateFlow<List<Float>>(emptyList())
    val weeklyVolumeTrend: StateFlow<List<Float>> = _weeklyVolumeTrend.asStateFlow()

    val volumeOrbState: StateFlow<VolumeOrbState> = volumeOrbRepository.orbState

    private val _streakResult = MutableStateFlow(StreakCalculator.StreakResult(
        state = StreakCalculator.StreakState.RESTING,
        streakDays = 0,
        skipsRemaining = 2,
        allowedSkipsPerWeek = 2,
        nextResetDate = java.util.Date()
    ))
    val streakResult: StateFlow<StreakCalculator.StreakResult> = _streakResult.asStateFlow()

    val bestStreak: StateFlow<Int> = userPreferencesRepository.bestStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _ytdWorkouts = MutableStateFlow(0)
    val ytdWorkouts: StateFlow<Int> = _ytdWorkouts.asStateFlow()

    private val _ytdVolume = MutableStateFlow(0f)
    val ytdVolume: StateFlow<Float> = _ytdVolume.asStateFlow()

    private val _lastYearVolume = MutableStateFlow(0f)
    val lastYearVolume: StateFlow<Float> = _lastYearVolume.asStateFlow()

    init {
        loadWeeklyVolume()
        refreshVolumeOrb()
        loadStreakData()
        loadYtdWorkouts()
        loadYtdVolume()
        loadLastYearVolume()
    }

    fun startNextRoutineWorkout() {
        viewModelScope.launch {
            routineRepository.startNextRoutineWorkout()?.let {
                _startRoutineWorkoutEvent.send(it)
            }
        }
    }

    private fun refreshVolumeOrb() {
        viewModelScope.launch {
            volumeOrbRepository.refresh()
        }
    }

    fun clearOrbOverflowAnimation() {
        volumeOrbRepository.clearOverflowAnimation()
    }

    private fun loadYtdVolume() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfYear = cal.timeInMillis
            val endOfToday = System.currentTimeMillis()
            _ytdVolume.value = workoutRepository.getTotalVolume(startOfYear, endOfToday)
        }
    }

    private fun loadLastYearVolume() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            cal.set(Calendar.YEAR, currentYear - 1)
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfLastYear = cal.timeInMillis
            cal.set(Calendar.MONTH, Calendar.DECEMBER)
            cal.set(Calendar.DAY_OF_MONTH, 31)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endOfLastYear = cal.timeInMillis
            _lastYearVolume.value = workoutRepository.getTotalVolume(startOfLastYear, endOfLastYear)
        }
    }

    private fun loadWeeklyVolume() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val weekStart = calendar.timeInMillis
            val weekEnd = System.currentTimeMillis()

            _weeklyVolume.value = workoutRepository.getTotalVolume(weekStart, weekEnd)

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

                dailyVolumes.add(workoutRepository.getTotalVolume(dayStart, dayEnd))
            }
            _weeklyVolumeTrend.value = dailyVolumes
        }
    }

    private fun loadStreakData() {
        viewModelScope.launch {
            val dateStrings = workoutRepository.getWorkoutDatesWithWorkingSets()
            val allowedRestDays = userPreferencesRepository.restDaysPerWeek.firstOrNull() ?: 2
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val workoutDates = dateStrings.mapNotNull { dateStr ->
                try {
                    dateFormat.parse(dateStr)
                } catch (_: Exception) {
                    null
                }
            }
            val result = StreakCalculator.calculateStreak(
                workoutDates = workoutDates,
                allowedSkipsPerWeek = allowedRestDays
            )
            _streakResult.value = result
            userPreferencesRepository.updateBestStreakIfNeeded(result.streakDays)
        }
    }

    private fun loadYtdWorkouts() {
        viewModelScope.launch {
            _ytdWorkouts.value = workoutRepository.getYearToDateWorkoutCount()
        }
    }

    fun refreshData() {
        loadWeeklyVolume()
        refreshVolumeOrb()
        loadStreakData()
        loadYtdWorkouts()
        loadYtdVolume()
        loadLastYearVolume()
    }
}
