package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RoutineDayFormViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val exerciseDao: ExerciseDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Retrieve routineId as Long (NavType.LongType)
    private val routineId: Long = savedStateHandle.get<Long>("routineId") ?: 0L
    
    // Retrieve dayId as String (NavType.StringType) -> convert to Long
    private val dayId: Long? = savedStateHandle.get<String>("dayId")?.toLongOrNull()

    private val _dayName = MutableStateFlow("")
    val dayName: StateFlow<String> = _dayName.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedExerciseIds: StateFlow<Set<Long>> = _selectedExerciseIds.asStateFlow()

    // Maintain exercise order
    private val _selectedExerciseOrder = MutableStateFlow<List<Long>>(emptyList())

    // Track which exercise index is linked as a superset with the next one
    // e.g., if set contains 0, index 0 and 1 are linked.
    private val _supersetLinks = MutableStateFlow<Set<Int>>(emptySet())
    val supersetLinks: StateFlow<Set<Int>> = _supersetLinks.asStateFlow()

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
        _selectedExerciseIds
    ) { name, exercises ->
        name.isNotBlank() && exercises.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Unit>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (dayId != null) {
            viewModelScope.launch {
                routineRepository.getRoutineDayWithExercises(dayId).collectLatest { dayWithExercises ->
                    dayWithExercises?.let {
                        _dayName.value = it.day.name
                        val exercises = it.exercises.sortedBy { it.routineExercise.orderIndex }
                        _selectedExerciseIds.value = exercises.map { it.exercise.id }.toSet()
                        _selectedExerciseOrder.value = exercises.map { it.exercise.id }
                        
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
                    }
                }
            }
        }
    }

    fun updateDayName(name: String) {
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
        if (!_selectedExerciseIds.value.contains(exerciseId)) {
            _selectedExerciseIds.value = _selectedExerciseIds.value + exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value + exerciseId
        }
    }

    fun removeExercise(exerciseId: Long) {
        if (_selectedExerciseIds.value.contains(exerciseId)) {
            val index = _selectedExerciseOrder.value.indexOf(exerciseId)
            _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value - exerciseId
            
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

    fun toggleSupersetLink(index: Int) {
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

    fun saveDay() {
        viewModelScope.launch {
            val name = _dayName.value.trim()
            val exerciseIds = _selectedExerciseOrder.value

            if (name.isBlank() || exerciseIds.isEmpty()) return@launch

            if (dayId != null) {
                // Edit mode: Update day info and replace exercises
                val existingDay = routineRepository.getDaysForRoutine(routineId).first().find { it.id == dayId }
                val orderIndex = existingDay?.orderIndex ?: 0
                
                routineRepository.updateRoutineDay(RoutineDay(id = dayId, routineId = routineId, name = name, orderIndex = orderIndex))
                routineRepository.deleteAllExercisesForDay(dayId)
                insertExercises(dayId, exerciseIds)
            } else {
                // Create mode
                val maxOrder = routineRepository.getDaysForRoutine(routineId).first().maxOfOrNull { it.orderIndex } ?: -1
                val newDayId = routineRepository.insertRoutineDay(
                    RoutineDay(routineId = routineId, name = name, orderIndex = maxOrder + 1)
                )
                insertExercises(newDayId, exerciseIds)
            }

            _saveSuccessEvent.send(Unit)
        }
    }

    private suspend fun insertExercises(targetDayId: Long, exerciseIds: List<Long>) {
        val links = _supersetLinks.value
        val groupMap = mutableMapOf<Int, String>() // linkIndex -> UUID
        
        exerciseIds.forEachIndexed { index, exerciseId ->
            // Determine superset info
            var groupId: String? = null
            var orderIndexInSuperset = 0

            // If this exercise is the start of a link OR the end of a link
            val isLinkStart = links.contains(index)
            val isLinkEnd = index > 0 && links.contains(index - 1)

            if (isLinkStart || isLinkEnd) {
                // Find or create group ID for this block
                // A block is a sequence of linked indices: 0-1, 1-2, 2-3...
                // The group ID for index 'index' depends on whether 'index-1' was linked.
                
                // Traverse backwards to find the start of this superset chain
                var chainStart = index
                while (chainStart > 0 && links.contains(chainStart - 1)) {
                    chainStart--
                }
                
                groupId = groupMap.getOrPut(chainStart) { UUID.randomUUID().toString() }
                orderIndexInSuperset = index - chainStart
            }

            routineRepository.insertRoutineExercise(
                RoutineExercise(
                    routineDayId = targetDayId,
                    exerciseId = exerciseId,
                    orderIndex = index,
                    supersetGroupId = groupId,
                    supersetOrderIndex = orderIndexInSuperset
                )
            )
        }
    }
}