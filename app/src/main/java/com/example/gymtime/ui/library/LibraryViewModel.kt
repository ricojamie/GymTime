package com.example.gymtime.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.ui.routine.preview.RoutineBrowseUiState
import com.example.gymtime.ui.routine.preview.routineBrowseState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val routineRepository: RoutineRepository
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val working = MutableStateFlow(false)
    private val actionError = MutableStateFlow<String?>(null)

    val routinePreviewState: StateFlow<RoutineBrowseUiState> = combine(
        routineBrowseState(routineRepository, retry),
        working,
        actionError
    ) { data, busy, error ->
        data.copy(isWorking = busy, actionError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutineBrowseUiState())

    fun retryRoutines() {
        actionError.value = null
        retry.value++
    }

    fun dismissRoutineError() {
        actionError.value = null
        if (routinePreviewState.value.loadError != null) retryRoutines()
    }

    fun setActiveRoutine(id: Long?) {
        perform { routineRepository.setActiveRoutine(id) }
    }

    fun deleteRoutineById(id: Long) {
        perform {
            routineRepository.getRoutineById(id).first()?.let { routineRepository.deleteRoutine(it) }
        }
    }

    fun duplicateRoutine(id: Long) {
        perform {
            check(routineRepository.duplicateRoutine(id) != null) { "This routine no longer exists." }
        }
    }

    private fun perform(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        actionError.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                actionError.value = error.message ?: "Couldn't update the routine. Try again."
            } finally {
                working.value = false
            }
        }
    }
}
