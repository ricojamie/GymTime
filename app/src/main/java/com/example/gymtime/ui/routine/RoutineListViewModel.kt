package com.example.gymtime.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val routines: Flow<List<Routine>> = routineRepository.getAllRoutines()

    val activeRoutineId: Flow<Long?> = userPreferencesRepository.activeRoutineId

    val canCreateMoreRoutines: StateFlow<Boolean> = routineRepository.getAllRoutines()
        .map { it.size < 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setActiveRoutine(routineId: Long?) {
        viewModelScope.launch {
            userPreferencesRepository.setActiveRoutineId(routineId)
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            // If deleting active routine, clear active state
            val currentActiveId = userPreferencesRepository.activeRoutineId.first()
            if (currentActiveId == routine.id) {
                userPreferencesRepository.setActiveRoutineId(null)
            }
            routineRepository.deleteRoutine(routine)
        }
    }
}
