package com.example.gymtime.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.ActiveRoutineStatus
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {

    val routines: Flow<List<Routine>> = routineRepository.getAllRoutines()

    val activeRoutineId: Flow<Long?> = routineRepository.getActiveRoutineStatus().map { it?.routine?.id }

    val canCreateMoreRoutines: StateFlow<Boolean> = routineRepository.getAllRoutines()
        .map { it.size < RoutineRepository.MAX_ROUTINES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setActiveRoutine(routineId: Long?) {
        viewModelScope.launch {
            routineRepository.setActiveRoutine(routineId)
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            routineRepository.deleteRoutine(routine)
        }
    }

    fun duplicateRoutine(routineId: Long) {
        viewModelScope.launch {
            routineRepository.duplicateRoutine(routineId)
        }
    }
}
