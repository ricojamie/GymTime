package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.ui.routine.preview.PreviewRoutineDayStartState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDayStartViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    private val retry = MutableStateFlow(0)
    private val startingDayId = MutableStateFlow<Long?>(null)
    private val startError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedState = retry.flatMapLatest {
        combine(
            routineRepository.getRoutineById(routineId),
            routineRepository.getDaysWithExercisesForRoutine(routineId)
        ) { routine, days ->
            PreviewRoutineDayStartState(
                routineName = routine?.name.orEmpty(),
                routineExists = routine != null,
                nextDayOrderIndex = routine?.nextDayOrderIndex,
                days = days.sortedBy { it.day.orderIndex },
                isLoading = false
            )
        }.onStart { emit(PreviewRoutineDayStartState()) }
            .catch { emit(PreviewRoutineDayStartState(isLoading = false, loadError = "Couldn't load workout days. Try again.")) }
    }

    val previewState: StateFlow<PreviewRoutineDayStartState> = combine(
        loadedState,
        startingDayId,
        startError
    ) { state, day, error ->
        state.copy(startingDayId = day, startError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreviewRoutineDayStartState())

    fun retryLoad() {
        startError.value = null
        retry.value++
    }

    fun dismissError() {
        startError.value = null
        if (previewState.value.loadError != null) retryLoad()
    }

    private val _startWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val startWorkoutEvent = _startWorkoutEvent.receiveAsFlow()

    fun startWorkoutFromDay(dayId: Long) {
        if (startingDayId.value != null) return
        startingDayId.value = dayId
        startError.value = null
        viewModelScope.launch {
            try {
                val day = routineRepository.getRoutineDayWithExercises(dayId).first()
                require(day?.day?.routineId == routineId) { "This day no longer belongs to the routine." }
                val start = routineRepository.startRoutineDay(dayId)
                    ?: throw IllegalStateException("Add an exercise before starting this day.")
                _startWorkoutEvent.send(start.firstExerciseId)
            } catch (cancelled: CancellationException) {
                startingDayId.value = null
                throw cancelled
            } catch (error: Exception) {
                startingDayId.value = null
                startError.value = error.message ?: "Couldn't start the workout. Try again."
            }
        }
    }

    fun onWorkoutOpened() {
        startingDayId.value = null
    }

    fun onWorkoutNavigationFailed() {
        startingDayId.value = null
        startError.value = "Your workout is saved, but couldn't open. Try Start day again."
    }
}
