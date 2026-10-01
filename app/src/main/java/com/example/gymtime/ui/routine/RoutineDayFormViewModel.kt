package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

private data class RoutineDayDraft(
    val name: String = "",
    val exerciseOrder: List<Long> = emptyList(),
    val targetSets: Map<Long, String> = emptyMap(),
    val targetRepMin: Map<Long, String> = emptyMap(),
    val targetRepMax: Map<Long, String> = emptyMap(),
    val targetRestSeconds: Map<Long, String> = emptyMap(),
    val supersetLinks: Set<Int> = emptySet(),
    val notes: Map<Long, String> = emptyMap()
) : java.io.Serializable

@HiltViewModel
class RoutineDayFormViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val exerciseDao: ExerciseDao,
    private val savedStateHandle: SavedStateHandle,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // Retrieve routineId as Long (NavType.LongType)
    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    
    // Retrieve dayId as String (NavType.StringType) -> convert to Long
    private val dayId: Long? = savedStateHandle.get<String>("dayId")?.toLongOrNull()
    private val restoredDraft = savedStateHandle.get<RoutineDayDraft>("dayDraft")
    val newUiEnabled = preferencesRepository.newUiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _isLoading = MutableStateFlow(dayId != null && restoredDraft == null)
    val isLoading = _isLoading.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _notes = MutableStateFlow(restoredDraft?.notes ?: emptyMap())
    val notes = _notes.asStateFlow()
    val pickerQuery = savedStateHandle.getStateFlow("pickerQuery", "")
    val pickerMuscle = savedStateHandle.getStateFlow("pickerMuscle", "")
    fun updatePickerQuery(value: String) { savedStateHandle["pickerQuery"] = value }
    fun updatePickerMuscle(value: String) { savedStateHandle["pickerMuscle"] = value }

    private val _dayName = MutableStateFlow(restoredDraft?.name ?: "")
    val dayName: StateFlow<String> = _dayName.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow(restoredDraft?.exerciseOrder?.toSet() ?: emptySet())
    val selectedExerciseIds: StateFlow<Set<Long>> = _selectedExerciseIds.asStateFlow()

    // Maintain exercise order
    private val _selectedExerciseOrder = MutableStateFlow(restoredDraft?.exerciseOrder ?: emptyList())

    private val _targetSets = MutableStateFlow(restoredDraft?.targetSets ?: emptyMap())
    val targetSets: StateFlow<Map<Long, String>> = _targetSets.asStateFlow()

    private val _targetRepMin = MutableStateFlow(restoredDraft?.targetRepMin ?: emptyMap())
    val targetRepMin: StateFlow<Map<Long, String>> = _targetRepMin.asStateFlow()

    private val _targetRepMax = MutableStateFlow(restoredDraft?.targetRepMax ?: emptyMap())
    val targetRepMax: StateFlow<Map<Long, String>> = _targetRepMax.asStateFlow()

    private val _targetRestSeconds = MutableStateFlow(restoredDraft?.targetRestSeconds ?: emptyMap())
    val targetRestSeconds: StateFlow<Map<Long, String>> = _targetRestSeconds.asStateFlow()

    // Track which exercise index is linked as a superset with the next one
    // e.g., if set contains 0, index 0 and 1 are linked.
    private val _supersetLinks = MutableStateFlow(restoredDraft?.supersetLinks ?: emptySet())
    val supersetLinks: StateFlow<Set<Int>> = _supersetLinks.asStateFlow()

    private val currentDraft = combine(
        combine(
            _dayName,
            _selectedExerciseOrder,
            _targetSets,
            _targetRepMin,
            _targetRepMax
        ) { name, order, sets, repMin, repMax ->
            RoutineDayDraft(
                name = name,
                exerciseOrder = order,
                targetSets = sets,
                targetRepMin = repMin,
                targetRepMax = repMax
            )
        },
        _targetRestSeconds,
        _supersetLinks,
        _notes
    ) { partial, rest, links, notes ->
        partial.copy(targetRestSeconds = rest, supersetLinks = links, notes = notes)
    }

    private val _baselineDraft = MutableStateFlow<RoutineDayDraft?>(
        savedStateHandle.get<RoutineDayDraft>("dayBaseline") ?: if (dayId == null) RoutineDayDraft() else null
    )

    val hasUnsavedChanges: StateFlow<Boolean> = combine(
        currentDraft,
        _baselineDraft
    ) { current, baseline ->
        baseline != null && current != baseline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedExercises: Flow<List<Exercise>> = combine(
        _selectedExerciseOrder,
        exerciseDao.getAllExercises()
    ) { orderedIds, allExercises ->
        orderedIds.mapNotNull { id -> allExercises.find { it.id == id } }
    }

    val availableExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    val isEditMode: StateFlow<Boolean> = MutableStateFlow(dayId != null)

    val isSaveEnabled: StateFlow<Boolean> = combine(
        _dayName,
        _selectedExerciseIds, _isLoading, _isSaving
    ) { name, exercises, loading, saving ->
        name.isNotBlank() && exercises.isNotEmpty() && !loading && !saving && _baselineDraft.value != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Unit>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (dayId != null && restoredDraft == null) loadDay()
        viewModelScope.launch {
            combine(currentDraft, _baselineDraft) { draft, baseline -> draft to baseline }
                .collect { (draft, baseline) ->
                    if (baseline != null) {
                        savedStateHandle["dayDraft"] = draft
                        savedStateHandle["dayBaseline"] = baseline
                    }
                }
        }
    }

    fun retryLoad() { if (dayId != null && !_isSaving.value) loadDay() }

    private fun loadDay() {
        if (dayId == null) return
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val loaded = routineRepository.getRoutineDayWithExercises(dayId).firstOrNull()
                    ?: throw IllegalStateException("This day no longer exists.")
                require(loaded.day.routineId == routineId) { "This day no longer belongs to this routine." }
                loaded.let {
                        _dayName.value = it.day.name
                        val exercises = it.exercises.sortedBy { it.routineExercise.orderIndex }
                        _selectedExerciseIds.value = exercises.map { it.exercise.id }.toSet()
                        _selectedExerciseOrder.value = exercises.map { it.exercise.id }
                        _targetSets.value = exercises.associate { exercise ->
                            exercise.exercise.id to exercise.routineExercise.targetSets.toString()
                        }
                        _targetRepMin.value = exercises.associate { exercise ->
                            exercise.exercise.id to (exercise.routineExercise.targetRepsMin?.toString() ?: "")
                        }
                        _targetRepMax.value = exercises.associate { exercise ->
                            exercise.exercise.id to (exercise.routineExercise.targetRepsMax?.toString() ?: "")
                        }
                        _targetRestSeconds.value = exercises.associate { exercise ->
                            exercise.exercise.id to (exercise.routineExercise.targetRestSeconds?.toString() ?: "")
                        }
                        _notes.value = exercises.associate { exercise -> exercise.exercise.id to exercise.routineExercise.notes.orEmpty() }
                        
                        // Reconstruct superset links
                        val links = mutableSetOf<Int>()
                        exercises.forEachIndexed { index, exercise ->
                            if (index < exercises.size - 1) {
                                val currentGroup = exercise.routineExercise.supersetGroupId
                                val nextGroup = exercises[index + 1].routineExercise.supersetGroupId
                                if (currentGroup != null && currentGroup == nextGroup) {
                                    links.add(index)
                                }
                            }
                        }
                        _supersetLinks.value = links

                        _baselineDraft.value = RoutineDayDraft(
                            name = it.day.name,
                            exerciseOrder = exercises.map { item -> item.exercise.id },
                            targetSets = _targetSets.value,
                            targetRepMin = _targetRepMin.value,
                            targetRepMax = _targetRepMax.value,
                            targetRestSeconds = _targetRestSeconds.value,
                            supersetLinks = links,
                            notes = _notes.value
                        )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _error.value = failure.message ?: "Couldn't load this day. Try again." }
            finally { _isLoading.value = false }
        }
    }

    fun updateDayName(name: String) {
        if (_isLoading.value || _isSaving.value) return
        _dayName.value = name
    }

    fun toggleExercise(exerciseId: Long) {
        if (_selectedExerciseIds.value.contains(exerciseId)) {
            removeExercise(exerciseId)
        } else {
            addExercise(exerciseId)
        }
    }

    fun addExercise(exerciseId: Long) {
        if (_isLoading.value || _isSaving.value) return
        if (!_selectedExerciseIds.value.contains(exerciseId)) {
            _selectedExerciseIds.value = _selectedExerciseIds.value + exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value + exerciseId
            _targetSets.value = _targetSets.value + (exerciseId to "3")
            _targetRepMin.value = _targetRepMin.value + (exerciseId to "")
            _targetRepMax.value = _targetRepMax.value + (exerciseId to "")
            // Blank means this routine inherits Exercise.defaultRestSeconds at display time.
            _targetRestSeconds.value = _targetRestSeconds.value + (exerciseId to "")
            _notes.value = _notes.value + (exerciseId to "")
        }
    }

    fun removeExercise(exerciseId: Long) {
        if (_isLoading.value || _isSaving.value) return
        if (_selectedExerciseIds.value.contains(exerciseId)) {
            val index = _selectedExerciseOrder.value.indexOf(exerciseId)
            _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value - exerciseId
            _targetSets.value = _targetSets.value - exerciseId
            _targetRepMin.value = _targetRepMin.value - exerciseId
            _targetRepMax.value = _targetRepMax.value - exerciseId
            _targetRestSeconds.value = _targetRestSeconds.value - exerciseId
            _notes.value = _notes.value - exerciseId
            
            // Re-adjust superset links when an exercise is removed
            val currentLinks = _supersetLinks.value.toMutableSet()
            val newLinks = mutableSetOf<Int>()
            
            // This is a bit complex: if we remove index i, 
            // any link at i-1 is broken (because i is gone),
            // and any link at i is broken (because i is gone).
            // Links > i need to shift down by 1.
            currentLinks.forEach { linkIndex ->
                when {
                    linkIndex < index - 1 -> newLinks.add(linkIndex)
                    linkIndex > index -> newLinks.add(linkIndex - 1)
                    // index or index-1 are ignored
                }
            }
            _supersetLinks.value = newLinks
        }
    }

    /**
     * Move an exercise up (-1) or down (+1) in the day's order.
     * Superset pairs move as a single unit so linked exercises stay adjacent.
     */
    fun moveExercise(exerciseId: Long, delta: Int) {
        if (_isLoading.value || _isSaving.value) return
        val order = _selectedExerciseOrder.value
        val links = _supersetLinks.value

        // Keep imported linked groups adjacent too; the editor creates pairs, but never splits saved groups.
        val blocks = mutableListOf<List<Int>>()
        var i = 0
        while (i < order.size) {
            val start = i
            while (i + 1 < order.size && links.contains(i)) i++
            blocks.add((start..i).toList())
            i++
        }

        val blockIndex = blocks.indexOfFirst { block -> block.any { order[it] == exerciseId } }
        val targetIndex = blockIndex + delta
        if (blockIndex == -1 || targetIndex !in blocks.indices) return

        val newBlocks = blocks.toMutableList()
        val moved = newBlocks.removeAt(blockIndex)
        newBlocks.add(targetIndex, moved)

        val newOrder = mutableListOf<Long>()
        val newLinks = mutableSetOf<Int>()
        newBlocks.forEach { block ->
            for (offset in 0 until block.size - 1) newLinks.add(newOrder.size + offset)
            block.forEach { oldIndex -> newOrder.add(order[oldIndex]) }
        }
        _selectedExerciseOrder.value = newOrder
        _supersetLinks.value = newLinks
    }

    fun toggleSupersetLink(index: Int) {
        if (_isLoading.value || _isSaving.value || index !in 0 until (_selectedExerciseOrder.value.size - 1)) return
        val currentLinks = _supersetLinks.value.toMutableSet()
        if (currentLinks.contains(index)) {
            currentLinks.remove(index)
        } else {
            // Enforce 2-exercise limit for supersets
            // A link at 'index' connects exercise 'index' and 'index + 1'.
            // To prevent > 2 exercises, we check if 'index - 1' or 'index + 1' are already linked.
            val isPrevLinked = index > 0 && currentLinks.contains(index - 1)
            val isNextLinked = currentLinks.contains(index + 1)
            
            if (!isPrevLinked && !isNextLinked) {
                currentLinks.add(index)
            } else {
                // Could emit an error event here if we had a UI for it
                return 
            }
        }
        _supersetLinks.value = currentLinks
    }
    
    fun isExerciseSelected(exerciseId: Long): Boolean {
        return _selectedExerciseIds.value.contains(exerciseId)
    }

    fun updateTargetSets(exerciseId: Long, value: String) {
        if (_isLoading.value || _isSaving.value) return
        _targetSets.value = _targetSets.value + (exerciseId to value.filter { it.isDigit() }.take(2))
    }

    fun updateTargetRepMin(exerciseId: Long, value: String) {
        if (_isLoading.value || _isSaving.value) return
        _targetRepMin.value = _targetRepMin.value + (exerciseId to value.filter { it.isDigit() }.take(3))
    }

    fun updateTargetRepMax(exerciseId: Long, value: String) {
        if (_isLoading.value || _isSaving.value) return
        _targetRepMax.value = _targetRepMax.value + (exerciseId to value.filter { it.isDigit() }.take(3))
    }

    fun updateTargetRestSeconds(exerciseId: Long, value: String) {
        if (_isLoading.value || _isSaving.value) return
        _targetRestSeconds.value = _targetRestSeconds.value + (exerciseId to value.filter { it.isDigit() }.take(4))
    }

    fun updateExerciseNotes(exerciseId: Long, value: String) {
        if (_isLoading.value || _isSaving.value) return
        _notes.value = _notes.value + (exerciseId to value)
    }

    fun saveDay() {
        if (_isLoading.value || _isSaving.value || _baselineDraft.value == null) return
        val draft = RoutineDayDraft(
            name = _dayName.value.trim(), exerciseOrder = _selectedExerciseOrder.value,
            targetSets = _targetSets.value, targetRepMin = _targetRepMin.value,
            targetRepMax = _targetRepMax.value, targetRestSeconds = _targetRestSeconds.value,
            supersetLinks = _supersetLinks.value, notes = _notes.value
        )
        if (draft.name.isBlank() || draft.exerciseOrder.isEmpty()) return
        val invalidRange = draft.exerciseOrder.any { id ->
            val min = draft.targetRepMin[id]?.toIntOrNull()
            val max = draft.targetRepMax[id]?.toIntOrNull()
            min != null && max != null && min > max
        }
        if (invalidRange) {
            _error.value = "Minimum reps must be no higher than maximum reps."
            return
        }
        _isSaving.value = true // Guard before launching; two rapid taps save only one draft.
        _error.value = null
        viewModelScope.launch {
            try {
                routineRepository.saveRoutineDay(routineId, dayId, draft.name, buildExercises(draft))
                _saveSuccessEvent.send(Unit) // Only after the complete transaction commits.
            } catch (cancelled: CancellationException) {
                _isSaving.value = false
                throw cancelled
            } catch (failure: Exception) {
                _isSaving.value = false
                _error.value = failure.message ?: "Couldn't save this day. Your changes are still here. Try again."
            }
        }
    }

    private fun buildExercises(draft: RoutineDayDraft): List<RoutineExercise> {
        val groups = mutableMapOf<Int, String>()
        return draft.exerciseOrder.mapIndexed { index, id ->
            var start = index
            while (start > 0 && draft.supersetLinks.contains(start - 1)) start--
            val linked = start != index || draft.supersetLinks.contains(index)
            RoutineExercise(
                routineDayId = dayId ?: 0L, exerciseId = id, orderIndex = index,
                targetSets = draft.targetSets[id]?.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                targetRepsMin = draft.targetRepMin[id]?.toIntOrNull(),
                targetRepsMax = draft.targetRepMax[id]?.toIntOrNull(),
                targetRestSeconds = draft.targetRestSeconds[id]?.toIntOrNull(),
                notes = draft.notes[id]?.takeIf { it.isNotEmpty() },
                supersetGroupId = if (linked) groups.getOrPut(start) { UUID.randomUUID().toString() } else null,
                supersetOrderIndex = if (linked) index - start else 0
            )
        }
    }
}
