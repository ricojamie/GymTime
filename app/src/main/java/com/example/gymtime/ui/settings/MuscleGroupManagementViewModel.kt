package com.example.gymtime.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.MuscleGroupDeleteResult
import com.example.gymtime.data.repository.MuscleGroupMutationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeleteCheckResult {
    object CanDelete : DeleteCheckResult()
    data class HasExercises(val exerciseCount: Int) : DeleteCheckResult()
    data class BlockedByLoggedSets(val setCount: Int) : DeleteCheckResult()
}

@HiltViewModel
class MuscleGroupManagementViewModel @Inject constructor(
    private val muscleGroupDao: MuscleGroupDao,
    private val exerciseRepository: ExerciseRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val muscleGroups: StateFlow<List<MuscleGroup>> = exerciseRepository.getAllMuscleGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dialog state: null = closed, "" = add mode, non-empty = edit mode (original name)
    val editingMuscle: StateFlow<String?> = savedStateHandle.getStateFlow("editing_muscle", null)

    val muscleNameInput: StateFlow<String> = savedStateHandle.getStateFlow("muscle_name_input", "")

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _deleteCheckResult = MutableStateFlow<Pair<String, DeleteCheckResult>?>(null)
    val deleteCheckResult: StateFlow<Pair<String, DeleteCheckResult>?> = _deleteCheckResult.asStateFlow()

    private var deleteCheckJob: Job? = null

    fun startAddNew() {
        savedStateHandle["editing_muscle"] = ""
        savedStateHandle["muscle_name_input"] = ""
        _validationError.value = null
    }

    fun startEdit(name: String) {
        if (name.isWarmupMuscleGroup()) return
        savedStateHandle["editing_muscle"] = name
        savedStateHandle["muscle_name_input"] = name
        _validationError.value = null
    }

    fun clearDialog() {
        savedStateHandle.set<String?>("editing_muscle", null)
        savedStateHandle["muscle_name_input"] = ""
        _validationError.value = null
    }

    fun clearDeleteDialog() {
        if (_isDeleting.value) return
        deleteCheckJob?.cancel()
        _deleteCheckResult.value = null
    }

    fun updateMuscleNameInput(input: String) {
        savedStateHandle["muscle_name_input"] = input
        _validationError.value = null
    }

    fun saveMuscleGroup() {
        if (_isSaving.value) return
        val trimmedName = muscleNameInput.value.trim()
        val editingName = editingMuscle.value

        if (trimmedName.length < 2) {
            _validationError.value = "Name must be at least 2 characters"
            return
        }
        if (trimmedName.isWarmupMuscleGroup()) {
            _validationError.value = "Warmups is a built-in library category"
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = if (editingName.isNullOrEmpty()) {
                    exerciseRepository.createMuscleGroup(trimmedName)
                } else {
                    exerciseRepository.renameMuscleGroup(editingName, trimmedName)
                }
                when (result) {
                    MuscleGroupMutationResult.Success -> clearDialog()
                    MuscleGroupMutationResult.InvalidName ->
                        _validationError.value = "Name must be at least 2 characters"
                    MuscleGroupMutationResult.DuplicateExists ->
                        _validationError.value = "A muscle group with this name already exists"
                    MuscleGroupMutationResult.ProtectedCategory ->
                        _validationError.value = "Warmups is a built-in library category"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _validationError.value = "Couldn't save this body part. Please try again."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun checkCanDelete(name: String) {
        if (name.isWarmupMuscleGroup() || _isDeleting.value) return
        deleteCheckJob?.cancel()
        deleteCheckJob = viewModelScope.launch {
            try {
                val setCount = muscleGroupDao.getLoggedSetCountForMuscle(name)
                if (setCount > 0) {
                    _deleteCheckResult.value = name to DeleteCheckResult.BlockedByLoggedSets(setCount)
                    return@launch
                }

                val exerciseCount = muscleGroupDao.getExerciseCountForMuscle(name)
                if (exerciseCount > 0) {
                    _deleteCheckResult.value = name to DeleteCheckResult.HasExercises(exerciseCount)
                    return@launch
                }

                _deleteCheckResult.value = name to DeleteCheckResult.CanDelete
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _deleteCheckResult.value = null
            }
        }
    }

    fun deleteMuscleGroup(name: String) {
        performDelete(name, reassignTo = null)
    }

    fun deleteWithExercises(name: String) {
        performDelete(name, reassignTo = "Uncategorized")
    }

    private fun performDelete(name: String, reassignTo: String?) {
        if (name.isWarmupMuscleGroup() || _isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                when (val result = exerciseRepository.deleteMuscleGroupSafely(name, reassignTo = reassignTo)) {
                    MuscleGroupDeleteResult.Deleted,
                    MuscleGroupDeleteResult.ProtectedCategory -> _deleteCheckResult.value = null
                    is MuscleGroupDeleteResult.HasExercises ->
                        _deleteCheckResult.value = name to DeleteCheckResult.HasExercises(result.exerciseCount)
                    is MuscleGroupDeleteResult.BlockedByLoggedSets ->
                        _deleteCheckResult.value = name to DeleteCheckResult.BlockedByLoggedSets(result.setCount)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _deleteCheckResult.value = null
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
