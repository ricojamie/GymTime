package com.example.gymtime.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.ActiveRoutineStatus
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.ui.routine.preview.RoutineBrowseUiState
import com.example.gymtime.ui.routine.preview.routineBrowseState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    preferences: UserPreferencesRepository
) : ViewModel() {
    val newUiEnabled = preferences.newUiEnabled
    private val retry = MutableStateFlow(0)
    private val working = MutableStateFlow(false)
    private val actionError = MutableStateFlow<String?>(null)
    val previewState: StateFlow<RoutineBrowseUiState> = combine(
        routineBrowseState(routineRepository, retry), working, actionError
    ) { data, busy, error -> data.copy(isWorking = busy, actionError = error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutineBrowseUiState())

    fun retryLoad() { actionError.value = null; retry.value++ }
    fun dismissError() { actionError.value = null; if (previewState.value.loadError != null) retryLoad() }

    val routines: Flow<List<Routine>> = routineRepository.getAllRoutines()

    val activeRoutineId: Flow<Long?> = routineRepository.getActiveRoutineStatus().map { it?.routine?.id }

    val canCreateMoreRoutines: StateFlow<Boolean> = routineRepository.getAllRoutines()
        .map { it.size < RoutineRepository.MAX_ROUTINES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setActiveRoutine(routineId: Long?) {
        perform { routineRepository.setActiveRoutine(routineId) }
    }

    fun deleteRoutine(routine: Routine) {
        perform { routineRepository.deleteRoutine(routine) }
    }

    fun deleteRoutineById(id: Long) {
        perform {
            routineRepository.getRoutineById(id).first()?.let { routineRepository.deleteRoutine(it) }
        }
    }

    fun duplicateRoutine(routineId: Long) {
        perform {
            require(routineRepository.getAllRoutines().first().size < RoutineRepository.MAX_ROUTINES) { "You can save up to 10 routines." }
            check(routineRepository.duplicateRoutine(routineId) != null) { "This routine no longer exists." }
        }
    }

    private fun perform(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        actionError.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { actionError.value = error.message ?: "Couldn't update the routine. Try again." }
            finally { working.value = false }
        }
    }
}
