package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.RoutineDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDayListViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Retrieve routineId as Long directly, as defined in NavType.LongType
    private val _routineId = MutableStateFlow(savedStateHandle.get<Long>("routineId") ?: 0L)
    val routineId: StateFlow<Long> = _routineId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val routineName: Flow<String> = _routineId
        .flatMapLatest { id ->
            routineRepository.getRoutineById(id).map { it?.name ?: "" }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val days: Flow<List<RoutineDay>> = _routineId
        .flatMapLatest { id ->
            routineRepository.getDaysForRoutine(id)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val canAddMoreDays: StateFlow<Boolean> = _routineId
        .flatMapLatest { id ->
            routineRepository.getDaysForRoutine(id).map { it.size }
        }
        .map { count -> count < 7 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun deleteDay(day: RoutineDay) {
        viewModelScope.launch {
            routineRepository.deleteRoutineDay(day)
        }
    }
}