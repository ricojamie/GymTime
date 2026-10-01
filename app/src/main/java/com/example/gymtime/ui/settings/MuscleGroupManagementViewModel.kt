package com.example.gymtime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val exerciseDao: ExerciseDao,
    userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val newUiEnabled = userPreferencesRepository.newUiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val muscleGroups: StateFlow<List<MuscleGroup>> = muscleGroupDao.getAllMuscleGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dialog state: null = closed, "" = add mode, non-empty = edit mode (original name)
    val editingMuscle: StateFlow<String?> = savedStateHandle.getStateFlow("editing_muscle", null)

    val muscleNameInput: StateFlow<String> = savedStateHandle.getStateFlow("muscle_name_input", "")

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _deleteCheckResult = MutableStateFlow<Pair<String, DeleteCheckResult>?>(null)
    val deleteCheckResult: StateFlow<Pair<String, DeleteCheckResult>?> = _deleteCheckResult.asStateFlow()

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
                // Check for duplicates (case-insensitive)
                val exists = muscleGroupDao.muscleGroupExists(trimmedName)
                val isRename = editingName?.isNotEmpty() == true && editingName.equals(trimmedName, ignoreCase = true)

                if (exists > 0 && !isRename) {
                    _validationError.value = "A muscle group with this name already exists"
                    return@launch
                }

                if (editingName.isNullOrEmpty()) {
                    // Add new
                    muscleGroupDao.insertMuscleGroup(MuscleGroup(trimmedName))
                } else {
                    // Rename: update exercises first, then delete old and insert new
                    if (!editingName.equals(trimmedName, ignoreCase = true)) {
                        exerciseDao.updateExercisesTargetMuscle(editingName, trimmedName)
                        muscleGroupDao.deleteMuscleGroupByName(editingName)
                        muscleGroupDao.insertMuscleGroup(MuscleGroup(trimmedName))
                    } else if (editingName != trimmedName) {
                        // Case change only - just update exercises and recreate muscle group
                        exerciseDao.updateExercisesTargetMuscle(editingName, trimmedName)
                        muscleGroupDao.deleteMuscleGroupByName(editingName)
                        muscleGroupDao.insertMuscleGroup(MuscleGroup(trimmedName))
                    }
                }

                clearDialog()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _validationError.value = "Couldn't save this body part. Please try again."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun checkCanDelete(name: String) {
        if (name.isWarmupMuscleGroup()) return
        viewModelScope.launch {
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
        }
    }

    fun deleteMuscleGroup(name: String) {
        if (name.isWarmupMuscleGroup()) return
        viewModelScope.launch {
            muscleGroupDao.deleteMuscleGroupByName(name)
            clearDeleteDialog()
        }
    }

    fun deleteWithExercises(name: String) {
        if (name.isWarmupMuscleGroup()) return
        viewModelScope.launch {
            // Update exercises to have no muscle group (or you could delete them)
            // For safety, we'll just clear the targetMuscle field by setting to "Uncategorized"
            exerciseDao.updateExercisesTargetMuscle(name, "Uncategorized")
            muscleGroupDao.deleteMuscleGroupByName(name)
            clearDeleteDialog()
        }
    }
}
