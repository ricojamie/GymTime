package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.RoutineDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDayListViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _routineId = MutableStateFlow(savedStateHandle.get<String>("routineId")?.toLong() ?: 0L)
    val routineId: StateFlow<Long> = _routineId.asStateFlow()

    val routineName: Flow<String> = _routineId
        .flatMapLatest { id ->
            routineDao.getRoutineById(id).map { it?.name ?: "" }
        }

    val days: Flow<List<RoutineDay>> = _routineId
        .flatMapLatest { id ->
            routineDao.getDaysForRoutine(id)
        }

    val canAddMoreDays: StateFlow<Boolean> = _routineId
        .flatMapLatest { id ->
            routineDao.getDayCountForRoutine(id)
        }
        .map { count -> count < 7 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun deleteDay(day: RoutineDay) {
        viewModelScope.launch {
            routineDao.deleteRoutineDay(day)
        }
    }
}
