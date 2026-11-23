package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

internal fun canMoveRoutineExerciseBlock(
    index: Int,
    delta: Int,
    links: Set<Int>,
    totalSize: Int
): Boolean {
    if (index !in 0 until totalSize || totalSize <= 1) return false
    return when {
        delta < 0 -> {
            var blockStart = index
            while (blockStart > 0 && (blockStart - 1) in links) blockStart--
            blockStart > 0
        }
        delta > 0 -> {
            var blockEnd = index
            while (blockEnd < totalSize - 1 && blockEnd in links) blockEnd++
            blockEnd < totalSize - 1
        }
        else -> false
    }
}

@HiltViewModel
class RoutineDayFormViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val exerciseDao: ExerciseDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    private val dayId: Long? = savedStateHandle.get<String>("dayId")?.toLongOrNull()
    private val restoredDraft = savedStateHandle.get<RoutineDayDraft>("dayDraft")

    private val _isLoading = MutableStateFlow(dayId != null && restoredDraft == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notes = MutableStateFlow(restoredDraft?.notes ?: emptyMap())
    val notes: StateFlow<Map<Long, String>> = _notes.asStateFlow()

    val pickerQuery: StateFlow<String> = savedStateHandle.getStateFlow("pickerQuery", "")
    val pickerMuscle: StateFlow<String> = savedStateHandle.getStateFlow("pickerMuscle", "")
    fun updatePickerQuery(value: String) { savedStateHandle["pickerQuery"] = value }
    fun updatePickerMuscle(value: String) { savedStateHandle["pickerMuscle"] = value }

    private val _dayName = MutableStateFlow(restoredDraft?.name ?: "")
    val dayName: StateFlow<String> = _dayName.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow(restoredDraft?.exerciseOrder?.toSet() ?: emptySet())
    val selectedExerciseIds: StateFlow<Set<Long>> = _selectedExerciseIds.asStateFlow()

    private val _selectedExerciseOrder = MutableStateFlow(restoredDraft?.exerciseOrder ?: emptyList())

    private val _targetSets = MutableStateFlow(restoredDraft?.targetSets ?: emptyMap())
    val targetSets: StateFlow<Map<Long, String>> = _targetSets.asStateFlow()

    private val _targetRepMin = MutableStateFlow(restoredDraft?.targetRepMin ?: emptyMap())
    val targetRepMin: StateFlow<Map<Long, String>> = _targetRepMin.asStateFlow()

    private val _targetRepMax = MutableStateFlow(restoredDraft?.targetRepMax ?: emptyMap())
    val targetRepMax: StateFlow<Map<Long, String>> = _targetRepMax.asStateFlow()

    private val _targetRestSeconds = MutableStateFlow(restoredDraft?.targetRestSeconds ?: emptyMap())
    val targetRestSeconds: StateFlow<Map<Long, String>> = _targetRestSeconds.asStateFlow()

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

    val canEdit: StateFlow<Boolean> = _baselineDraft
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, dayId == null || restoredDraft != null)

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
        val byId = allExercises.associateBy { it.id }
        orderedIds.mapNotNull(byId::get)
    }

    val availableExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    val isEditMode: StateFlow<Boolean> = MutableStateFlow(dayId != null)

    val isSaveEnabled: StateFlow<Boolean> = combine(
        _dayName,
        _selectedExerciseIds,
        _isLoading,
        _isSaving,
        _baselineDraft
    ) { name, exercises, loading, saving, baseline ->
        name.isNotBlank() && exercises.isNotEmpty() && !loading && !saving && baseline != null
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

    fun retryLoad() {
        if (dayId != null && !_isSaving.value) loadDay()
    }

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
                    val exercises = it.exercises.sortedBy { item -> item.routineExercise.orderIndex }
                    _selectedExerciseIds.value = exercises.map { item -> item.exercise.id }.toSet()
                    _selectedExerciseOrder.value = exercises.map { item -> item.exercise.id }
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
                    _notes.value = exercises.associate { exercise ->
                        exercise.exercise.id to exercise.routineExercise.notes.orEmpty()
                    }

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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _error.value = failure.message ?: "Couldn't load this day. Try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun canMutateDraft(): Boolean =
        !_isLoading.value && !_isSaving.value && _baselineDraft.value != null

    fun updateDayName(name: String) {
        if (!canMutateDraft()) return
        _dayName.value = name
        _error.value = null
    }

    fun toggleExercise(exerciseId: Long) {
        if (_selectedExerciseIds.value.contains(exerciseId)) {
            removeExercise(exerciseId)
        } else {
            addExercise(exerciseId)
        }
    }

    fun addExercise(exerciseId: Long) {
        if (!canMutateDraft()) return
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
        if (!canMutateDraft()) return
        if (_selectedExerciseIds.value.contains(exerciseId)) {
            val index = _selectedExerciseOrder.value.indexOf(exerciseId)
            _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value - exerciseId
            _targetSets.value = _targetSets.value - exerciseId
            _targetRepMin.value = _targetRepMin.value - exerciseId
            _targetRepMax.value = _targetRepMax.value - exerciseId
            _targetRestSeconds.value = _targetRestSeconds.value - exerciseId
            _notes.value = _notes.value - exerciseId

            val currentLinks = _supersetLinks.value.toMutableSet()
            val newLinks = mutableSetOf<Int>()
            currentLinks.forEach { linkIndex ->
                when {
                    linkIndex < index - 1 -> newLinks.add(linkIndex)
                    linkIndex > index -> newLinks.add(linkIndex - 1)
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
        if (!canMutateDraft()) return
        val order = _selectedExerciseOrder.value
        val links = _supersetLinks.value
        val index = order.indexOf(exerciseId)
        if (!canMoveRoutineExerciseBlock(index, delta, links, order.size)) return

        val blocks = mutableListOf<List<Int>>()
        var i = 0
        while (i < order.size) {
            val start = i
            while (i + 1 < order.size && links.contains(i)) i++
            blocks.add((start..i).toList())
            i++
        }

        val blockIndex = blocks.indexOfFirst { block -> index in block }
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
        if (!canMutateDraft() || index !in 0 until (_selectedExerciseOrder.value.size - 1)) return
        val currentLinks = _supersetLinks.value.toMutableSet()
        if (currentLinks.contains(index)) {
            currentLinks.remove(index)
        } else {
            val isPrevLinked = index > 0 && currentLinks.contains(index - 1)
            val isNextLinked = currentLinks.contains(index + 1)
            if (!isPrevLinked && !isNextLinked) {
                currentLinks.add(index)
            } else {
                return
            }
        }
        _supersetLinks.value = currentLinks
    }

    fun isExerciseSelected(exerciseId: Long): Boolean = _selectedExerciseIds.value.contains(exerciseId)

    fun updateTargetSets(exerciseId: Long, value: String) {
        if (!canMutateDraft()) return
        _targetSets.value = _targetSets.value + (exerciseId to value.filter { it.isDigit() }.take(2))
    }

    fun updateTargetRepMin(exerciseId: Long, value: String) {
        if (!canMutateDraft()) return
        _targetRepMin.value = _targetRepMin.value + (exerciseId to value.filter { it.isDigit() }.take(3))
        _error.value = null
    }

    fun updateTargetRepMax(exerciseId: Long, value: String) {
        if (!canMutateDraft()) return
        _targetRepMax.value = _targetRepMax.value + (exerciseId to value.filter { it.isDigit() }.take(3))
        _error.value = null
    }

    fun updateTargetRestSeconds(exerciseId: Long, value: String) {
        if (!canMutateDraft()) return
        _targetRestSeconds.value = _targetRestSeconds.value + (exerciseId to value.filter { it.isDigit() }.take(4))
    }

    fun updateExerciseNotes(exerciseId: Long, value: String) {
        if (!canMutateDraft()) return
        _notes.value = _notes.value + (exerciseId to value)
    }

    fun saveDay() {
        if (!canMutateDraft()) return
        val draft = RoutineDayDraft(
            name = _dayName.value.trim(),
            exerciseOrder = _selectedExerciseOrder.value,
            targetSets = _targetSets.value,
            targetRepMin = _targetRepMin.value,
            targetRepMax = _targetRepMax.value,
            targetRestSeconds = _targetRestSeconds.value,
            supersetLinks = _supersetLinks.value,
            notes = _notes.value
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
                routineDayId = dayId ?: 0L,
                exerciseId = id,
                orderIndex = index,
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
