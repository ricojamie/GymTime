package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDayFormViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<String>("routineId")?.toLong() ?: 0L
    private val dayId: Long? = savedStateHandle.get<String>("dayId")?.toLongOrNull()

    private val _dayName = MutableStateFlow("")
    val dayName: StateFlow<String> = _dayName.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())

    // Maintain exercise order
    private val _selectedExerciseOrder = MutableStateFlow<List<Long>>(emptyList())

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
                routineDao.getRoutineDayWithExercises(dayId).firstOrNull()?.let { dayWithExercises ->
                    _dayName.value = dayWithExercises.day.name
                    val exercises = dayWithExercises.exercises.sortedBy { it.routineExercise.orderIndex }
                    _selectedExerciseIds.value = exercises.map { it.exercise.id }.toSet()
                    _selectedExerciseOrder.value = exercises.map { it.exercise.id }
                }
            }
        }
    }

    fun updateDayName(name: String) {
        _dayName.value = name
    }

    fun addExercise(exerciseId: Long) {
        if (!_selectedExerciseIds.value.contains(exerciseId)) {
            _selectedExerciseIds.value = _selectedExerciseIds.value + exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value + exerciseId
        }
    }

    fun removeExercise(exerciseId: Long) {
        if (_selectedExerciseIds.value.contains(exerciseId)) {
            _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
            _selectedExerciseOrder.value = _selectedExerciseOrder.value - exerciseId
        }
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
                // Preserve the original orderIndex of the day
                val existingDay = routineDao.getDaysForRoutine(routineId).first().find { it.id == dayId }
                val orderIndex = existingDay?.orderIndex ?: 0
                
                routineDao.updateRoutineDay(RoutineDay(id = dayId, routineId = routineId, name = name, orderIndex = orderIndex))
                routineDao.deleteAllExercisesForDay(dayId)
                exerciseIds.forEachIndexed { index, exerciseId ->
                    routineDao.insertRoutineExercise(
                        RoutineExercise(routineDayId = dayId, exerciseId = exerciseId, orderIndex = index)
                    )
                }
            } else {
                // Create mode
                val maxOrder = routineDao.getDaysForRoutine(routineId).first().maxOfOrNull { it.orderIndex } ?: -1
                val newDayId = routineDao.insertRoutineDay(
                    RoutineDay(routineId = routineId, name = name, orderIndex = maxOrder + 1)
                )
                exerciseIds.forEachIndexed { index, exerciseId ->
                    routineDao.insertRoutineExercise(
                        RoutineExercise(routineDayId = newDayId, exerciseId = exerciseId, orderIndex = index)
                    )
                }
            }

            _saveSuccessEvent.send(Unit)
        }
    }
}
