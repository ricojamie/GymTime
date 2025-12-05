package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: MuscleGroupDao
) : ViewModel() {

    private val exerciseId: Long? = savedStateHandle.get<String>("exerciseId")?.toLongOrNull()

    private val _exerciseName = MutableStateFlow("")
    val exerciseName: StateFlow<String> = _exerciseName

    private val _targetMuscle = MutableStateFlow("")
    val targetMuscle: StateFlow<String> = _targetMuscle

    private val _logType = MutableStateFlow(LogType.WEIGHT_REPS)
    val logType: StateFlow<LogType> = _logType

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes

    private val _defaultRestSeconds = MutableStateFlow("90")
    val defaultRestSeconds: StateFlow<String> = _defaultRestSeconds

    val availableMuscles: Flow<List<String>> = muscleGroupDao.getAllMuscleGroups().map { groups ->
        groups.map { it.name }.sorted()
    }

    private val _isEditMode = MutableStateFlow(exerciseId != null)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    // Save button enabled only when required fields are filled
    val isSaveEnabled: StateFlow<Boolean> = combine(
        _exerciseName,
        _targetMuscle,
        _defaultRestSeconds
    ) { name, muscle, rest ->
        name.isNotBlank() && muscle.isNotBlank() && rest.toIntOrNull() != null && (rest.toIntOrNull() ?: 0) > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Unit>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        // If edit mode, load existing exercise
        exerciseId?.let { id ->
            viewModelScope.launch {
                exerciseDao.getExerciseById(id).first().let { exercise ->
                    _exerciseName.value = exercise.name
                    _targetMuscle.value = exercise.targetMuscle
                    _logType.value = exercise.logType
                    _notes.value = exercise.notes ?: ""
                    _defaultRestSeconds.value = exercise.defaultRestSeconds.toString()
                }
            }
        }
    }

    fun updateExerciseName(name: String) {
        _exerciseName.value = name
    }

    fun updateTargetMuscle(muscle: String) {
        _targetMuscle.value = muscle
    }

    fun updateLogType(type: LogType) {
        _logType.value = type
    }

    fun updateNotes(notes: String) {
        _notes.value = notes
    }

    fun updateDefaultRestSeconds(seconds: String) {
        _defaultRestSeconds.value = seconds
    }

    fun saveExercise() {
        viewModelScope.launch {
            val exercise = Exercise(
                id = exerciseId ?: 0,
                name = _exerciseName.value.trim(),
                targetMuscle = _targetMuscle.value,
                logType = _logType.value,
                isCustom = true,
                notes = _notes.value.takeIf { it.isNotBlank() },
                defaultRestSeconds = _defaultRestSeconds.value.toIntOrNull() ?: 90
            )

            if (exerciseId == null) {
                // Create new
                exerciseDao.insertExercise(exercise)
            } else {
                // Update existing
                exerciseDao.updateExercise(exercise)
            }

            _saveSuccessEvent.send(Unit)
        }
    }
}
