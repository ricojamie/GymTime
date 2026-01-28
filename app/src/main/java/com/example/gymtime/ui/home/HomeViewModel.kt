package com.example.gymtime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.util.StreakCalculator
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineDao: com.example.gymtime.data.db.dao.RoutineDao,
    private val volumeOrbRepository: VolumeOrbRepository
) : ViewModel() {
    val userName: Flow<String> = userPreferencesRepository.userName
    val ongoingWorkout: StateFlow<Workout?> = workoutRepository.getOngoingWorkoutFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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

    // Volume Orb state
    val volumeOrbState: StateFlow<VolumeOrbState> = volumeOrbRepository.orbState

    // Streak state
    private val _streakResult = MutableStateFlow(StreakCalculator.StreakResult(
        state = StreakCalculator.StreakState.RESTING,
        streakDays = 0,
        skipsRemaining = 2,
        nextResetDate = java.util.Date()
    ))
    val streakResult: StateFlow<StreakCalculator.StreakResult> = _streakResult.asStateFlow()

    // Best streak (all-time)
    val bestStreak: StateFlow<Int> = userPreferencesRepository.bestStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Year-to-date workout count
    private val _ytdWorkouts = MutableStateFlow(0)
    val ytdWorkouts: StateFlow<Int> = _ytdWorkouts.asStateFlow()

    // Year-to-date total weight
    private val _ytdVolume = MutableStateFlow(0f)
    val ytdVolume: StateFlow<Float> = _ytdVolume.asStateFlow()

    // Last year's total volume (full year)
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

            // Start of last year (Jan 1, previous year, 00:00:00)
            cal.set(Calendar.YEAR, currentYear - 1)
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfLastYear = cal.timeInMillis

            // End of last year (Dec 31, previous year, 23:59:59)
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
            val volume = workoutRepository.getTotalVolume(weekStart, weekEnd)
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

                val dayVolume = workoutRepository.getTotalVolume(dayStart, dayEnd)
                dailyVolumes.add(dayVolume)
            }
            _weeklyVolumeTrend.value = dailyVolumes
        }
    }

    private fun loadStreakData() {
        viewModelScope.launch {
            val dateStrings = workoutRepository.getWorkoutDatesWithWorkingSets()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val workoutDates = dateStrings.mapNotNull { dateStr ->
                try {
                    dateFormat.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
            }
            val result = StreakCalculator.calculateStreak(workoutDates)
            _streakResult.value = result

            // Update best streak if current is higher
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
