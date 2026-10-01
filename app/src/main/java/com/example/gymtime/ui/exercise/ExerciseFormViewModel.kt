package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class ExerciseFormDraft(
    val name: String,
    val targetMuscle: String,
    val logType: LogType?,
    val distanceUnit: DistanceUnit,
    val notes: String,
    val restSeconds: String,
    val repTarget: String
) {
    fun isValid(): Boolean {
        val tracksReps = logType == LogType.WEIGHT_REPS || logType == LogType.REPS_ONLY
        val validRepTarget = !tracksReps || repTarget.isBlank() || (repTarget.toIntOrNull() ?: 0) > 0
        return name.isNotBlank() && targetMuscle.isNotBlank() && logType != null &&
            (restSeconds.toIntOrNull() ?: 0) > 0 && validRepTarget
    }
}

private data class ExerciseFormCoreDraft(
    val name: String,
    val targetMuscle: String,
    val logType: LogType?,
    val distanceUnit: DistanceUnit,
    val notes: String
)

@HiltViewModel
class ExerciseFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: MuscleGroupDao,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val exerciseId: Long? = savedStateHandle.get<String>("exerciseId")?.toLongOrNull()
    private val initialName = savedStateHandle.get<String>("initialName").orEmpty().trim()
    private val initialMuscle = savedStateHandle.get<String>("initialMuscle").orEmpty().trim()
    private val hasRestoredDraft = savedStateHandle.get<Boolean>(DRAFT_PRESENT) == true
    private val previewDefaultsPrepared = savedStateHandle.get<Boolean>(PREVIEW_DEFAULTS) == true

    val newUiEnabled: Flow<Boolean> = userPreferencesRepository.newUiEnabled
    val isFromWorkout: StateFlow<Boolean> = MutableStateFlow(savedStateHandle["fromWorkout"] ?: false)
    val isFromWorkoutBuilder: StateFlow<Boolean> = MutableStateFlow(savedStateHandle["fromWorkoutBuilder"] ?: false)
    val isReturningToPicker: StateFlow<Boolean> = MutableStateFlow(savedStateHandle["returnToPicker"] ?: false)
    val isEditMode: StateFlow<Boolean> = MutableStateFlow(exerciseId != null)

    private val _exerciseName = MutableStateFlow(
        savedStateHandle.get<String>(DRAFT_NAME) ?: if (exerciseId == null) initialName else ""
    )
    val exerciseName: StateFlow<String> = _exerciseName.asStateFlow()

    private val _targetMuscle = MutableStateFlow(
        savedStateHandle.get<String>(DRAFT_MUSCLE) ?: if (exerciseId == null) initialMuscle else ""
    )
    val targetMuscle: StateFlow<String> = _targetMuscle.asStateFlow()

    // The legacy form still asks for a type. The preview explicitly prepares its visible default.
    private val _logType = MutableStateFlow(
        savedStateHandle.get<String>(DRAFT_TYPE)?.let { name -> LogType.entries.find { it.name == name } }
            ?: LogType.WEIGHT_REPS.takeIf { exerciseId == null && previewDefaultsPrepared }
    )
    val logType: StateFlow<LogType?> = _logType.asStateFlow()

    private val _defaultDistanceUnit = MutableStateFlow(
        savedStateHandle.get<String>(DRAFT_DISTANCE_UNIT)
            ?.let { name -> DistanceUnit.entries.find { it.name == name } } ?: DistanceUnit.MILES
    )
    val defaultDistanceUnit: StateFlow<DistanceUnit> = _defaultDistanceUnit.asStateFlow()

    private val _notes = MutableStateFlow(savedStateHandle.get<String>(DRAFT_NOTES).orEmpty())
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _defaultRestSeconds = MutableStateFlow(savedStateHandle.get<String>(DRAFT_REST) ?: "90")
    val defaultRestSeconds: StateFlow<String> = _defaultRestSeconds.asStateFlow()

    private val _repTarget = MutableStateFlow(savedStateHandle.get<String>(DRAFT_REP_TARGET).orEmpty())
    val repTarget: StateFlow<String> = _repTarget.asStateFlow()

    private val _isLoading = MutableStateFlow(exerciseId != null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val currentDraft = combine(
        combine(_exerciseName, _targetMuscle, _logType, _defaultDistanceUnit, _notes) { name, muscle, type, unit, notes ->
            ExerciseFormCoreDraft(name, muscle, type, unit, notes)
        },
        _defaultRestSeconds,
        _repTarget
    ) { core, rest, target ->
        ExerciseFormDraft(core.name, core.targetMuscle, core.logType, core.distanceUnit, core.notes, rest, target)
    }

    private val _baselineDraft = MutableStateFlow<ExerciseFormDraft?>(
        if (exerciseId == null) {
            ExerciseFormDraft(
                initialName, initialMuscle, LogType.WEIGHT_REPS.takeIf { previewDefaultsPrepared },
                DistanceUnit.MILES, "", "90", ""
            )
        } else null
    )

    val hasUnsavedChanges: StateFlow<Boolean> = combine(currentDraft, _baselineDraft) { current, baseline ->
        baseline != null && current != baseline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var existingIsStarred = false
    private var existingIsCustom = true

    val availableMuscles: Flow<List<String>> = muscleGroupDao.getAllMuscleGroups().map { groups ->
        groups.map { it.name }.sorted()
    }

    val similarExercises: Flow<List<Exercise>> = if (exerciseId != null) flowOf(emptyList()) else combine(_exerciseName, exerciseDao.getAllExercises()) { name, exercises ->
        val query = name.trim()
        if (query.length < 3) emptyList() else exercises.asSequence()
            .filter { it.id != exerciseId && namesAreSimilar(query, it.name) }
            .sortedWith(compareBy<Exercise>(
                { !it.name.equals(query, ignoreCase = true) },
                { !it.name.startsWith(query, ignoreCase = true) },
                { it.name.lowercase() }
            ))
            .take(3)
            .toList()
    }.flowOn(Dispatchers.Default).catch { error ->
        if (error is CancellationException) throw error
        // Suggestions are optional; a library read failure must not block creating an exercise.
        emit(emptyList())
    }

    val isSaveEnabled: StateFlow<Boolean> = combine(
        currentDraft, _isLoading, _isSaving, _baselineDraft
    ) { draft, loading, saving, baseline ->
        !loading && !saving && baseline != null && draft.isValid()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // New and reused exercises return their ID; edits return null.
    private val _saveSuccessEvent = Channel<Long?>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (exerciseId != null) loadExercise()
    }

    fun preparePreviewDefaults() {
        if (exerciseId != null || _logType.value != null || snapshotDraft() != _baselineDraft.value) return
        _logType.value = LogType.WEIGHT_REPS
        _baselineDraft.value = snapshotDraft()
        savedStateHandle[PREVIEW_DEFAULTS] = true
        persistDraft()
    }

    fun updateExerciseName(name: String) = updateDraft { _exerciseName.value = name }
    fun updateTargetMuscle(muscle: String) = updateDraft { _targetMuscle.value = muscle }
    fun updateLogType(type: LogType) = updateDraft { _logType.value = type }
    fun updateDefaultDistanceUnit(unit: DistanceUnit) = updateDraft { _defaultDistanceUnit.value = unit }
    fun updateNotes(notes: String) = updateDraft { _notes.value = notes }
    fun updateDefaultRestSeconds(seconds: String) = updateDraft { _defaultRestSeconds.value = seconds }
    fun updateRepTarget(target: String) = updateDraft { _repTarget.value = target }

    fun dismissSaveError() {
        _saveError.value = null
    }

    fun retryLoad() {
        if (exerciseId != null && !_isLoading.value && _baselineDraft.value == null) loadExercise()
    }

    private fun loadExercise() {
        val id = exerciseId ?: return
        _isLoading.value = true
        _loadError.value = null
        viewModelScope.launch {
            try {
                val exercise = exerciseDao.getExerciseById(id).first()
                val baseline = ExerciseFormDraft(
                    exercise.name, exercise.targetMuscle, exercise.logType, exercise.defaultDistanceUnit,
                    exercise.notes.orEmpty(), exercise.defaultRestSeconds.toString(), exercise.repTarget?.toString().orEmpty()
                )
                existingIsStarred = exercise.isStarred
                existingIsCustom = exercise.isCustom
                if (!hasRestoredDraft) {
                    _exerciseName.value = baseline.name
                    _targetMuscle.value = baseline.targetMuscle
                    _logType.value = baseline.logType
                    _defaultDistanceUnit.value = baseline.distanceUnit
                    _notes.value = baseline.notes
                    _defaultRestSeconds.value = baseline.restSeconds
                    _repTarget.value = baseline.repTarget
                }
                _baselineDraft.value = baseline
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _loadError.value = "Couldn't load this exercise. Try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveExercise() {
        val draft = snapshotDraft()
        if (_isLoading.value || _isSaving.value || _baselineDraft.value == null || !draft.isValid()) return
        val selectedLogType = draft.logType ?: return
        // Guard synchronously, before launching, so a second tap cannot enqueue another insert.
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val exercise = Exercise(
                    id = exerciseId ?: 0,
                    name = draft.name.trim(),
                    targetMuscle = draft.targetMuscle.trim(),
                    logType = selectedLogType,
                    defaultDistanceUnit = draft.distanceUnit,
                    isCustom = existingIsCustom,
                    notes = draft.notes.takeIf { it.isNotBlank() },
                    defaultRestSeconds = draft.restSeconds.toInt(),
                    isStarred = existingIsStarred,
                    repTarget = draft.repTarget.toIntOrNull()?.takeIf {
                        selectedLogType == LogType.WEIGHT_REPS || selectedLogType == LogType.REPS_ONLY
                    }
                )
                val resultId = if (exerciseId == null) {
                    exerciseDao.insertExercise(exercise)
                } else {
                    exerciseDao.updateExercise(exercise)
                    null
                }
                _baselineDraft.value = draft
                _saveSuccessEvent.send(resultId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _isSaving.value = false
                _saveError.value = "Couldn't save this exercise. Your changes are still here. Try again."
            }
        }
    }

    fun useExistingExercise(exercise: Exercise) {
        if (exerciseId != null || _isLoading.value || _isSaving.value) return
        val query = _exerciseName.value.trim()
        if (query.length < 3 || !namesAreSimilar(query, exercise.name)) return
        _isSaving.value = true
        _saveError.value = null
        viewModelScope.launch {
            try {
                val existing = exerciseDao.getExerciseByIdSync(exercise.id)
                if (existing == null || !namesAreSimilar(query, existing.name)) {
                    _isSaving.value = false
                    _saveError.value = "That exercise is no longer available. Choose another or create your own."
                    return@launch
                }
                _baselineDraft.value = snapshotDraft()
                _saveSuccessEvent.send(existing.id)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _isSaving.value = false
                _saveError.value = "Couldn't open this exercise. Try again."
            }
        }
    }

    private inline fun updateDraft(update: () -> Unit) {
        if (_isLoading.value || _isSaving.value || _loadError.value != null) return
        update()
        _saveError.value = null
        persistDraft()
    }

    private fun snapshotDraft() = ExerciseFormDraft(
        _exerciseName.value, _targetMuscle.value, _logType.value, _defaultDistanceUnit.value,
        _notes.value, _defaultRestSeconds.value, _repTarget.value
    )

    private fun persistDraft() {
        savedStateHandle[DRAFT_PRESENT] = true
        savedStateHandle[DRAFT_NAME] = _exerciseName.value
        savedStateHandle[DRAFT_MUSCLE] = _targetMuscle.value
        savedStateHandle[DRAFT_TYPE] = _logType.value?.name
        savedStateHandle[DRAFT_DISTANCE_UNIT] = _defaultDistanceUnit.value.name
        savedStateHandle[DRAFT_NOTES] = _notes.value
        savedStateHandle[DRAFT_REST] = _defaultRestSeconds.value
        savedStateHandle[DRAFT_REP_TARGET] = _repTarget.value
    }

    private fun namesAreSimilar(query: String, name: String): Boolean =
        name.isNotBlank() && (name.contains(query, ignoreCase = true) || query.contains(name, ignoreCase = true))

    private companion object {
        const val DRAFT_PRESENT = "exerciseDraft.present"
        const val PREVIEW_DEFAULTS = "exerciseDraft.previewDefaults"
        const val DRAFT_NAME = "exerciseDraft.name"
        const val DRAFT_MUSCLE = "exerciseDraft.muscle"
        const val DRAFT_TYPE = "exerciseDraft.type"
        const val DRAFT_DISTANCE_UNIT = "exerciseDraft.distanceUnit"
        const val DRAFT_NOTES = "exerciseDraft.notes"
        const val DRAFT_REST = "exerciseDraft.rest"
        const val DRAFT_REP_TARGET = "exerciseDraft.repTarget"
    }
}
