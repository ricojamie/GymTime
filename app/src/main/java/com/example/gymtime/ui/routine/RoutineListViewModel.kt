package com.example.gymtime.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val routines: Flow<List<Routine>> = routineDao.getAllRoutines()

    val activeRoutineId: Flow<Long?> = userPreferencesRepository.activeRoutineId

    val canCreateMoreRoutines: StateFlow<Boolean> = routineDao.getRoutineCount()
        .map { count -> count < 3 }
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
            routineDao.deleteRoutine(routine)
        }
    }
}
