package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineFormViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long? = savedStateHandle.get<String>("routineId")?.toLongOrNull()

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    val isEditMode: StateFlow<Boolean> = MutableStateFlow(routineId != null)

    val isSaveEnabled: StateFlow<Boolean> = _routineName
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Long>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (routineId != null) {
            viewModelScope.launch {
                routineRepository.getRoutineById(routineId).firstOrNull()?.let { routine ->
                    _routineName.value = routine.name
                }
            }
        }
    }

    fun updateRoutineName(name: String) {
        _routineName.value = name
    }

    fun saveRoutine() {
        viewModelScope.launch {
            val name = _routineName.value.trim()
            if (name.isBlank()) return@launch

            if (routineId != null) {
                // Edit mode
                routineRepository.updateRoutine(Routine(id = routineId, name = name))
                _saveSuccessEvent.send(routineId)
            } else {
                // Create mode
                val newId = routineRepository.insertRoutine(Routine(name = name))
                _saveSuccessEvent.send(newId)
            }
        }
    }
}
