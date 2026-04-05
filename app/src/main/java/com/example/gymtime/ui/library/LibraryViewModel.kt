package com.example.gymtime.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {

    val routines: Flow<List<Routine>> = routineRepository.getAllRoutines()

    val activeRoutineId: Flow<Long?> = routineRepository.getActiveRoutineStatus().map { it?.routine?.id }

    val canCreateMoreRoutines: StateFlow<Boolean> = routineRepository.getAllRoutines()
        .map { routines -> routines.size < 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            routineRepository.deleteRoutine(routine)
        }
    }

    fun toggleRoutineActive(routine: Routine) {
        viewModelScope.launch {
            routineRepository.setActiveRoutine(if (routine.isActive) null else routine.id)
        }
    }
}
