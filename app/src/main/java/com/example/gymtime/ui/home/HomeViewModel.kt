package com.example.gymtime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.data.repository.WorkoutStartResult
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.domain.analytics.StrengthMomentumUseCase
import com.example.gymtime.util.StreakCalculator
import com.example.gymtime.util.TimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import javax.inject.Inject

data class RoutineCardState(
    val routineId: Long,
    val routineName: String,
    val nextDayName: String?,
    val dayPosition: String?, // e.g. "Day 2 of 4"
    val exercisePreview: List<String>,
    val lastPerformedLabel: String? // e.g. "Last: Jun 12"
)

@HiltViewModel
class HomeViewModel internal constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val volumeOrbRepository: VolumeOrbRepository,
    private val strengthMomentumUseCase: StrengthMomentumUseCase,
    private val clock: Clock,
    private val computeDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        workoutRepository: WorkoutRepository,
        routineRepository: RoutineRepository,
        volumeOrbRepository: VolumeOrbRepository,
        strengthMomentumUseCase: StrengthMomentumUseCase
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
        volumeOrbRepository = volumeOrbRepository,
        strengthMomentumUseCase = strengthMomentumUseCase,
        clock = Clock.systemDefaultZone(),
        computeDispatcher = Dispatchers.Default
    )

    private val deviceZoneId: ZoneId
        get() = clock.zone

    val userName: Flow<String> = userPreferencesRepository.userName

    val ongoingWorkout: StateFlow<Workout?> = workoutRepository.getOngoingWorkoutFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val routineCardState: StateFlow<RoutineCardState?> = routineRepository.getActiveRoutineStatus()
        .flatMapLatest { status ->
            when {
                status == null -> flowOf(null)
                status.nextDay == null -> flowOf(
                    RoutineCardState(
                        routineId = status.routine.id,
                        routineName = status.routine.name,
                        nextDayName = null,
                        dayPosition = null,
                        exercisePreview = emptyList(),
                        lastPerformedLabel = null
                    )
                )
                else -> combine(
                    routineRepository.getExerciseListForDay(status.nextDay.id),
                    routineRepository.getRoutineDayStats(status.routine.id)
                ) { exercises, dayStats ->
                    RoutineCardState(
                        routineId = status.routine.id,
                        routineName = status.routine.name,
                        nextDayName = status.nextDay.name,
                        dayPosition = status.nextDayPosition?.let { "Day $it of ${status.dayCount}" },
                        exercisePreview = exercises.take(3).map { it.name },
                        lastPerformedLabel = dayStats[status.nextDay.id]?.lastPerformed
                            ?.let { "Last: ${TimeFormatter.formatShortDate(it)}" }
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _startRoutineWorkoutEvent = Channel<WorkoutStartResult>(Channel.BUFFERED)
    val startRoutineWorkoutEvent = _startRoutineWorkoutEvent.receiveAsFlow()

    private val _createRoutineEvent = Channel<Unit>(Channel.BUFFERED)
    val createRoutineEvent = _createRoutineEvent.receiveAsFlow()

    private val _homeMessage = Channel<String>(Channel.BUFFERED)
    val homeMessage = _homeMessage.receiveAsFlow()

    private val _routineStarting = MutableStateFlow(false)
    val routineStarting: StateFlow<Boolean> = _routineStarting.asStateFlow()
    private var createRoutineRequestJob: Job? = null

    private val _weeklyVolume = MutableStateFlow(0f)
    val weeklyVolume: StateFlow<Float> = _weeklyVolume.asStateFlow()

    val volumeOrbState: StateFlow<VolumeOrbState> = volumeOrbRepository.orbState

    private val _streakResult = MutableStateFlow(
        StreakCalculator.StreakResult(
            state = StreakCalculator.StreakState.RESTING,
            streakDays = 0,
            skipsRemaining = 2,
            allowedSkipsPerWeek = 2,
            nextResetDate = Date.from(clock.instant())
        )
    )
    val streakResult: StateFlow<StreakCalculator.StreakResult> = _streakResult.asStateFlow()

    val bestStreak: StateFlow<Int> = userPreferencesRepository.bestStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _ytdWorkouts = MutableStateFlow(0)
    val ytdWorkouts: StateFlow<Int> = _ytdWorkouts.asStateFlow()

    private val _ytdVolume = MutableStateFlow(0f)
    val ytdVolume: StateFlow<Float> = _ytdVolume.asStateFlow()

    private val _lastYearVolume = MutableStateFlow(0f)
    val lastYearVolume: StateFlow<Float> = _lastYearVolume.asStateFlow()

    private val _strengthMomentum = MutableStateFlow(StrengthMomentumState())
    val strengthMomentum: StateFlow<StrengthMomentumState> = _strengthMomentum.asStateFlow()
    private val _strengthMomentumLoading = MutableStateFlow(false)
    val strengthMomentumLoading: StateFlow<Boolean> = _strengthMomentumLoading.asStateFlow()
    private val _strengthMomentumError = MutableStateFlow<String?>(null)
    val strengthMomentumError: StateFlow<String?> = _strengthMomentumError.asStateFlow()

    private var weeklyVolumeJob: Job? = null
    private var volumeOrbJob: Job? = null
    private var strengthMomentumLoadJob: Job? = null
    private var streakJob: Job? = null
    private var ytdWorkoutsJob: Job? = null
    private var ytdVolumeJob: Job? = null
    private var lastYearVolumeJob: Job? = null

    private var hasPausedSinceRefresh: Boolean = false
    private var lastDateSensitiveRefreshDate: LocalDate? = null

    init {
        refreshData()
    }

    fun startNextRoutineWorkout() {
        if (_routineStarting.value) return
        _routineStarting.value = true
        viewModelScope.launch {
            try {
                val result = routineRepository.startNextRoutineWorkout()
                if (result != null) {
                    _startRoutineWorkoutEvent.send(result)
                } else {
                    _homeMessage.send("Choose a routine with a workout day to get started.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = (error as? IllegalStateException)?.message?.takeIf { it.isNotBlank() }
                    ?: "Couldn't start this routine. Please try again."
                _homeMessage.send(message)
            } finally {
                _routineStarting.value = false
            }
        }
    }

    fun requestCreateRoutine() {
        if (createRoutineRequestJob?.isActive == true) return
        createRoutineRequestJob = viewModelScope.launch {
            try {
                if (routineRepository.getAllRoutines().first().size >= RoutineRepository.MAX_ROUTINES) {
                    _homeMessage.send("You can have up to ${RoutineRepository.MAX_ROUTINES} routines. Remove one to create another.")
                } else {
                    _createRoutineEvent.send(Unit)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _homeMessage.send("Couldn't open a new routine. Please try again.")
            }
        }
    }

    private fun refreshVolumeOrb() {
        volumeOrbJob?.cancel()
        volumeOrbJob = viewModelScope.launch {
            try {
                volumeOrbRepository.refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // VolumeOrbRepository keeps the last valid orb state on transient errors.
            }
        }
    }

    fun clearOrbOverflowAnimation() {
        volumeOrbRepository.clearOverflowAnimation()
    }

    private fun loadYtdVolume() {
        ytdVolumeJob?.cancel()
        ytdVolumeJob = viewModelScope.launch {
            try {
                val today = LocalDate.now(clock)
                val startOfYear = today.withDayOfYear(1)
                    .atStartOfDay(deviceZoneId)
                    .toInstant()
                    .toEpochMilli()
                val endOfToday = clock.millis()
                val total = workoutRepository.getTotalVolume(startOfYear, endOfToday)
                ensureActive()
                _ytdVolume.value = total
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep last loaded YTD volume on transient errors.
            }
        }
    }

    private fun loadStrengthMomentum() {
        strengthMomentumLoadJob?.cancel()
        _strengthMomentumLoading.value = true
        _strengthMomentumError.value = null
        strengthMomentumLoadJob = viewModelScope.launch {
            try {
                val momentum = strengthMomentumUseCase.getStrengthMomentum(clock.millis())
                ensureActive()
                _strengthMomentum.value = momentum
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isActive) {
                    _strengthMomentumError.value = "Couldn't update strength trends. Please try again."
                }
            } finally {
                if (isActive) {
                    _strengthMomentumLoading.value = false
                }
            }
        }
    }

    fun retryStrengthMomentum() {
        loadStrengthMomentum()
    }

    private fun loadLastYearVolume() {
        lastYearVolumeJob?.cancel()
        lastYearVolumeJob = viewModelScope.launch {
            try {
                val currentYear = LocalDate.now(clock).year
                val zone = deviceZoneId
                val startOfLastYear = LocalDate.of(currentYear - 1, 1, 1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                val endOfLastYear = LocalDate.of(currentYear, 1, 1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli() - 1L
                val total = workoutRepository.getTotalVolume(startOfLastYear, endOfLastYear)
                ensureActive()
                _lastYearVolume.value = total
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep last loaded prior-year volume on transient errors.
            }
        }
    }

    private fun loadWeeklyVolume() {
        weeklyVolumeJob?.cancel()
        weeklyVolumeJob = viewModelScope.launch {
            try {
                val today = LocalDate.now(clock)
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekStart = monday.atStartOfDay(deviceZoneId).toInstant().toEpochMilli()
                val weekEnd = clock.millis()
                val total = workoutRepository.getTotalVolume(weekStart, weekEnd)
                ensureActive()
                _weeklyVolume.value = total
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep last loaded weekly volume on transient errors.
            }
        }
    }

    private fun loadStreakData() {
        streakJob?.cancel()
        streakJob = viewModelScope.launch {
            try {
                val dateStrings = workoutRepository.getWorkoutDatesWithWorkingSets()
                val allowedRestDays = userPreferencesRepository.restDaysPerWeek.firstOrNull() ?: 2
                val zone = deviceZoneId
                val asOf = Date.from(clock.instant())
                val result = withContext(computeDispatcher) {
                    val workoutDates = dateStrings.mapNotNull { dateStr ->
                        runCatching { LocalDate.parse(dateStr) }
                            .getOrNull()
                            ?.let { localDate ->
                                Date.from(localDate.atStartOfDay(zone).toInstant())
                            }
                    }
                    StreakCalculator.calculateStreak(
                        workoutDates = workoutDates,
                        allowedSkipsPerWeek = allowedRestDays,
                        asOf = asOf
                    )
                }
                ensureActive()
                _streakResult.value = result
                userPreferencesRepository.updateBestStreakIfNeeded(result.streakDays)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep last valid streak snapshot on transient errors.
            }
        }
    }

    private fun loadYtdWorkouts() {
        ytdWorkoutsJob?.cancel()
        ytdWorkoutsJob = viewModelScope.launch {
            try {
                val count = workoutRepository.getYearToDateWorkoutCount()
                ensureActive()
                _ytdWorkouts.value = count
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep last loaded YTD workout count on transient errors.
            }
        }
    }

    fun onScreenPaused() {
        hasPausedSinceRefresh = true
    }

    fun refreshOnResume() {
        val today = LocalDate.now(clock)
        if (hasPausedSinceRefresh || lastDateSensitiveRefreshDate != today) {
            refreshData()
        }
    }

    fun refreshData() {
        hasPausedSinceRefresh = false
        lastDateSensitiveRefreshDate = LocalDate.now(clock)
        loadWeeklyVolume()
        refreshVolumeOrb()
        loadStrengthMomentum()
        loadStreakData()
        loadYtdWorkouts()
        loadYtdVolume()
        loadLastYearVolume()
    }

    fun refreshDataIfDateChanged() {
        val today = LocalDate.now(clock)
        if (lastDateSensitiveRefreshDate != today) {
            refreshData()
        }
    }
}
