package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.RoutineDayStat
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.domain.analytics.RoutineStats
import com.example.gymtime.domain.analytics.RoutineStatsUseCase
import com.example.gymtime.ui.routine.preview.PreviewRoutineDetailState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import javax.inject.Inject

data class RoutineDetailUiState(
    val routine: Routine? = null,
    val days: List<RoutineDayWithExercises> = emptyList(),
    val dayStats: Map<Long, RoutineDayStat> = emptyMap(),
    val canAddMoreDays: Boolean = true,
    val isLoading: Boolean = true,
    val loadError: String? = null
)

private data class RoutineOperationState(val startingDayId: Long? = null, val isWorking: Boolean = false, val error: String? = null)

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val routineStatsUseCase: RoutineStatsUseCase,
    savedStateHandle: SavedStateHandle,
    preferences: UserPreferencesRepository
) : ViewModel() {

    val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    val newUiEnabled = preferences.newUiEnabled
    private val retry = MutableStateFlow(0)
    private val operation = MutableStateFlow(RoutineOperationState())
    private val statsLoading = MutableStateFlow(true)
    private val statsError = MutableStateFlow<String?>(null)
    private var statsJob: Job? = null
    private var statsGeneration = 0

    private val _stats = MutableStateFlow<RoutineStats?>(null)
    val stats: StateFlow<RoutineStats?> = _stats.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        val generation = ++statsGeneration
        statsJob?.cancel()
        statsLoading.value = true
        statsError.value = null
        statsJob = viewModelScope.launch {
            try {
                val loaded = routineStatsUseCase.getStats(routineId)
                if (generation == statsGeneration) _stats.value = loaded
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == statsGeneration) statsError.value = "Couldn't load routine stats. Try again." }
            finally { if (generation == statsGeneration) statsLoading.value = false }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RoutineDetailUiState> = retry.flatMapLatest {
        combine(
            routineRepository.getRoutineById(routineId),
            routineRepository.getDaysWithExercisesForRoutine(routineId),
            routineRepository.getRoutineDayStats(routineId)
        ) { routine, days, dayStats ->
            RoutineDetailUiState(routine = routine, days = days.sortedBy { it.day.orderIndex }, dayStats = dayStats,
                canAddMoreDays = days.size < RoutineRepository.MAX_DAYS_PER_ROUTINE, isLoading = false)
        }.onStart { emit(RoutineDetailUiState()) }
            .catch { emit(RoutineDetailUiState(isLoading = false, loadError = "Couldn't load this routine. Try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutineDetailUiState())

    val previewState: StateFlow<PreviewRoutineDetailState> = combine(uiState, stats, statsLoading, statsError, operation) {
            detail, stats, loading, error, operation ->
        PreviewRoutineDetailState(detail, stats, loading, error, operation.startingDayId, operation.isWorking, operation.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreviewRoutineDetailState())

    fun retryLoad() { retry.value++ }
    fun dismissError() {
        operation.value = operation.value.copy(error = null)
        if (uiState.value.loadError != null) retryLoad()
    }

    private val _startWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val startWorkoutEvent = _startWorkoutEvent.receiveAsFlow()

    fun startWorkoutFromDay(dayId: Long) {
        if (operation.value.isWorking || operation.value.startingDayId != null) return
        operation.value = RoutineOperationState(startingDayId = dayId)
        viewModelScope.launch {
            try {
                val day = routineRepository.getRoutineDayWithExercises(dayId).first()
                require(day?.day?.routineId == routineId) { "This day no longer belongs to the routine." }
                val start = routineRepository.startRoutineDay(dayId)
                    ?: throw IllegalStateException("Add an exercise before starting this day.")
                _startWorkoutEvent.send(start.firstExerciseId)
                // Retain the guard until the route opens; another tap cannot enqueue a second navigation.
            } catch (cancelled: CancellationException) {
                operation.value = RoutineOperationState()
                throw cancelled
            } catch (error: Exception) {
                operation.value = RoutineOperationState(error = error.message ?: "Couldn't start the workout. Try again.")
            }
        }
    }

    fun onWorkoutOpened() { operation.value = RoutineOperationState() }
    fun onWorkoutNavigationFailed() { operation.value = RoutineOperationState(error = "Your workout is saved, but couldn't open. Try Start day again.") }

    fun setActive() {
        perform { routineRepository.setActiveRoutine(routineId) }
    }

    fun clearActive() { perform { routineRepository.setActiveRoutine(null) } }

    fun setNextDay(dayId: Long) {
        perform { routineRepository.setNextDay(routineId, dayId) }
    }

    fun moveDay(dayId: Long, direction: Int) {
        perform { routineRepository.moveDay(routineId, dayId, direction) }
    }

    fun duplicateDay(dayId: Long) {
        perform {
            require(routineRepository.getDaysForRoutine(routineId).first().size < RoutineRepository.MAX_DAYS_PER_ROUTINE) { "A routine can have up to 10 days." }
            check(routineRepository.duplicateDay(dayId) != null) { "This day no longer exists." }
        }
    }

    fun deleteDay(day: RoutineDay) {
        perform { routineRepository.deleteRoutineDay(day) }
    }

    private fun perform(block: suspend () -> Unit) {
        if (operation.value.isWorking || operation.value.startingDayId != null) return
        operation.value = RoutineOperationState(isWorking = true)
        viewModelScope.launch {
            try { block(); operation.value = RoutineOperationState() }
            catch (cancelled: CancellationException) { operation.value = RoutineOperationState(); throw cancelled }
            catch (error: Exception) { operation.value = RoutineOperationState(error = error.message ?: "Couldn't update the day. Try again.") }
        }
    }
}
