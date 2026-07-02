package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.RoutineDayStat
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.domain.analytics.RoutineStats
import com.example.gymtime.domain.analytics.RoutineStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineDetailUiState(
    val routine: Routine? = null,
    val days: List<RoutineDayWithExercises> = emptyList(),
    val dayStats: Map<Long, RoutineDayStat> = emptyMap(),
    val canAddMoreDays: Boolean = true
)

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val routineStatsUseCase: RoutineStatsUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L

    private val _stats = MutableStateFlow<RoutineStats?>(null)
    val stats: StateFlow<RoutineStats?> = _stats.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _stats.value = routineStatsUseCase.getStats(routineId)
        }
    }

    val uiState: StateFlow<RoutineDetailUiState> = combine(
        routineRepository.getRoutineById(routineId),
        routineRepository.getDaysWithExercisesForRoutine(routineId),
        routineRepository.getRoutineDayStats(routineId)
    ) { routine, days, dayStats ->
        RoutineDetailUiState(
            routine = routine,
            days = days,
            dayStats = dayStats,
            canAddMoreDays = days.size < RoutineRepository.MAX_DAYS_PER_ROUTINE
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutineDetailUiState())

    private val _startWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val startWorkoutEvent = _startWorkoutEvent.receiveAsFlow()

    fun startWorkoutFromDay(dayId: Long) {
        viewModelScope.launch {
            routineRepository.startRoutineDay(dayId)?.let { start ->
                _startWorkoutEvent.send(start.firstExerciseId)
            }
        }
    }

    fun setActive() {
        viewModelScope.launch { routineRepository.setActiveRoutine(routineId) }
    }

    fun setNextDay(dayId: Long) {
        viewModelScope.launch { routineRepository.setNextDay(routineId, dayId) }
    }

    fun moveDay(dayId: Long, direction: Int) {
        viewModelScope.launch { routineRepository.moveDay(routineId, dayId, direction) }
    }

    fun duplicateDay(dayId: Long) {
        viewModelScope.launch { routineRepository.duplicateDay(dayId) }
    }

    fun deleteDay(day: RoutineDay) {
        viewModelScope.launch { routineRepository.deleteRoutineDay(day) }
    }
}
