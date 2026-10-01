package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.entity.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class RoutineFormViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val savedStateHandle: SavedStateHandle,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val routineId: Long? = savedStateHandle.get<String>("routineId")?.toLongOrNull()
    private var existingRoutine: Routine? = null

    val newUiEnabled = preferencesRepository.newUiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _routineName = MutableStateFlow(savedStateHandle.get<String>("nameDraft") ?: "")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    private val _baselineName = MutableStateFlow<String?>(savedStateHandle.get<String>("nameBaseline") ?: if (routineId == null) "" else null)
    private val _isLoading = MutableStateFlow(routineId != null)
    val isLoading = _isLoading.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val hasUnsavedChanges: StateFlow<Boolean> = combine(
        _routineName,
        _baselineName
    ) { current, baseline ->
        baseline != null && current != baseline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isEditMode: StateFlow<Boolean> = MutableStateFlow(routineId != null)

    val isSaveEnabled: StateFlow<Boolean> = combine(_routineName, _isSaving, _isLoading) { name, saving, loading ->
        name.isNotBlank() && !saving && !loading && (routineId == null || existingRoutine != null)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Long>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (routineId != null) {
            loadRoutine()
        }
    }

    fun updateRoutineName(name: String) {
        if (_isSaving.value) return
        _routineName.value = name
        savedStateHandle["nameDraft"] = name
        _error.value = null
    }

    fun retryLoad() { if (routineId != null && !_isSaving.value) loadRoutine() }

    private fun loadRoutine() {
        if (routineId == null) return
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val routine = routineRepository.getRoutineById(routineId).firstOrNull()
                    ?: throw IllegalStateException("This routine no longer exists.")
                existingRoutine = routine
                if (!savedStateHandle.contains("nameDraft")) _routineName.value = routine.name
                if (_baselineName.value == null) _baselineName.value = routine.name
                savedStateHandle["nameBaseline"] = _baselineName.value
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _error.value = failure.message ?: "Couldn't load this routine. Try again." }
            finally { _isLoading.value = false }
        }
    }

    fun saveRoutine() {
        if (_isSaving.value || _isLoading.value || _routineName.value.isBlank()) return
        _isSaving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val name = _routineName.value.trim()
                if (routineId != null) {
                    val current = existingRoutine ?: throw IllegalStateException("Load this routine before saving.")
                    routineRepository.updateRoutine(current.copy(name = name))
                    _saveSuccessEvent.send(routineId)
                } else {
                    val all = routineRepository.getAllRoutines().first()
                    require(all.size < RoutineRepository.MAX_ROUTINES) { "You can have up to ${RoutineRepository.MAX_ROUTINES} routines." }
                    val newId = routineRepository.insertRoutine(Routine(name = name, isActive = all.isEmpty()))
                    _saveSuccessEvent.send(newId)
                }
            } catch (cancelled: CancellationException) { _isSaving.value = false; throw cancelled }
            catch (failure: Exception) {
                _isSaving.value = false
                _error.value = failure.message ?: "Couldn't save your routine. Your name is still here. Try again."
            }
        }
    }
}
