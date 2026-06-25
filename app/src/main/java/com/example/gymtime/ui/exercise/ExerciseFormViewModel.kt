package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.DistanceUnit
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
    private val fromWorkout: Boolean = savedStateHandle.get<Boolean>("fromWorkout") ?: false

    val isFromWorkout: StateFlow<Boolean> = MutableStateFlow(fromWorkout)

    private val _exerciseName = MutableStateFlow("")
    val exerciseName: StateFlow<String> = _exerciseName

    private val _targetMuscle = MutableStateFlow("")
    val targetMuscle: StateFlow<String> = _targetMuscle

    // Null until the user explicitly picks a type — Log Type is a required field
    // so we don't silently create exercises with the wrong (default) type.
    private val _logType = MutableStateFlow<LogType?>(null)
    val logType: StateFlow<LogType?> = _logType

    private val _defaultDistanceUnit = MutableStateFlow(DistanceUnit.MILES)
    val defaultDistanceUnit: StateFlow<DistanceUnit> = _defaultDistanceUnit

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes

    private val _defaultRestSeconds = MutableStateFlow("90")
    val defaultRestSeconds: StateFlow<String> = _defaultRestSeconds

    private val _repTarget = MutableStateFlow("")
    val repTarget: StateFlow<String> = _repTarget

    private var existingIsStarred: Boolean = false

    val availableMuscles: Flow<List<String>> = muscleGroupDao.getAllMuscleGroups().map { groups ->
        groups.map { it.name }.sorted()
    }

    private val _isEditMode = MutableStateFlow(exerciseId != null)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    // Save button enabled only when required fields are filled
    val isSaveEnabled: StateFlow<Boolean> = combine(
        _exerciseName,
        _targetMuscle,
        _defaultRestSeconds,
        _logType,
        _repTarget
    ) { name, muscle, rest, logType, repTarget ->
        val validRepTarget = repTarget.isBlank() ||
            ((logType == LogType.WEIGHT_REPS || logType == LogType.REPS_ONLY) &&
                (repTarget.toIntOrNull() ?: 0) > 0)

        name.isNotBlank() &&
            muscle.isNotBlank() &&
            logType != null &&
            rest.toIntOrNull() != null &&
            (rest.toIntOrNull() ?: 0) > 0 &&
            validRepTarget
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Returns new exercise ID for create, null for edit
    private val _saveSuccessEvent = Channel<Long?>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        // If edit mode, load existing exercise
        exerciseId?.let { id ->
            viewModelScope.launch {
                exerciseDao.getExerciseById(id).first().let { exercise ->
                    _exerciseName.value = exercise.name
                    _targetMuscle.value = exercise.targetMuscle
                    _logType.value = exercise.logType
                    _defaultDistanceUnit.value = exercise.defaultDistanceUnit
                    _notes.value = exercise.notes ?: ""
                    _defaultRestSeconds.value = exercise.defaultRestSeconds.toString()
                    _repTarget.value = exercise.repTarget?.toString() ?: ""
                    existingIsStarred = exercise.isStarred
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

    fun updateDefaultDistanceUnit(unit: DistanceUnit) {
        _defaultDistanceUnit.value = unit
    }

    fun updateNotes(notes: String) {
        _notes.value = notes
    }

    fun updateDefaultRestSeconds(seconds: String) {
        _defaultRestSeconds.value = seconds
    }

    fun updateRepTarget(target: String) {
        _repTarget.value = target
    }

    fun saveExercise() {
        viewModelScope.launch {
            val selectedLogType = _logType.value ?: return@launch
            val target = _repTarget.value.toIntOrNull()
                ?.takeIf { selectedLogType == LogType.WEIGHT_REPS || selectedLogType == LogType.REPS_ONLY }

            val exercise = Exercise(
                id = exerciseId ?: 0,
                name = _exerciseName.value.trim(),
                targetMuscle = _targetMuscle.value,
                logType = selectedLogType,
                defaultDistanceUnit = _defaultDistanceUnit.value,
                isCustom = true,
                notes = _notes.value.takeIf { it.isNotBlank() },
                defaultRestSeconds = _defaultRestSeconds.value.toIntOrNull() ?: 90,
                isStarred = existingIsStarred,
                repTarget = target
            )

            val resultId = if (exerciseId == null) {
                // Create new - return ID for navigation
                exerciseDao.insertExercise(exercise)
            } else {
                // Update existing - return null (no navigation needed)
                exerciseDao.updateExercise(exercise)
                null
            }

            _saveSuccessEvent.send(resultId)
        }
    }
}
