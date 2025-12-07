package com.example.gymtime.ui.exercise

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.util.OneRepMaxCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class WorkoutStats(
    val totalSets: Int,
    val totalVolume: Float,
    val exerciseCount: Int,
    val duration: String
)

data class PersonalRecords(
    val heaviestWeight: Set?,
    val bestE1RM: Pair<Set, Float>?, // Set and calculated E1RM
    val bestE10RM: Pair<Set, Float>?  // Set and calculated E10RM (premium feature)
)

@HiltViewModel
class ExerciseLoggingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])

    private val _exercise = MutableStateFlow<Exercise?>(null)
    val exercise: StateFlow<Exercise?> = _exercise

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout

    private val _loggedSets = MutableStateFlow<List<Set>>(emptyList())
    val loggedSets: StateFlow<List<Set>> = _loggedSets

    // Form state
    private val _weight = MutableStateFlow("")
    val weight: StateFlow<String> = _weight

    private val _reps = MutableStateFlow("")
    val reps: StateFlow<String> = _reps

    private val _rpe = MutableStateFlow("")
    val rpe: StateFlow<String> = _rpe

    private val _restTime = MutableStateFlow(90) // Will be updated from exercise default
    val restTime: StateFlow<Int> = _restTime

    // Store the exercise's default rest time
    private var exerciseDefaultRestSeconds: Int = 90

    private val _isWarmup = MutableStateFlow(false)
    val isWarmup: StateFlow<Boolean> = _isWarmup

    private val _setNote = MutableStateFlow("")
    val setNote: StateFlow<String> = _setNote

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    // Set editing state
    private val _editingSet = MutableStateFlow<Set?>(null)
    val editingSet: StateFlow<Set?> = _editingSet

    // Last workout data
    private val _lastWorkoutSets = MutableStateFlow<List<Set>>(emptyList())
    val lastWorkoutSets: StateFlow<List<Set>> = _lastWorkoutSets

    // Personal bests by rep count - Map of reps -> max weight at that rep count
    private val _personalBestsByReps = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val personalBestsByReps: StateFlow<Map<Int, Float>> = _personalBestsByReps

    // Workout overview data
    private val _workoutOverview = MutableStateFlow<List<WorkoutExerciseSummary>>(emptyList())
    val workoutOverview: StateFlow<List<WorkoutExerciseSummary>> = _workoutOverview

    private val _navigationEvents = Channel<Long>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()


    // Flag to track if pre-fill has happened
    private var hasPrefilled = false

    init {
        viewModelScope.launch {
            // Load exercise
            exerciseDao.getExerciseById(exerciseId).collectLatest { exercise ->
                _exercise.value = exercise
                // Set timer to exercise's default rest time
                exercise?.let {
                    exerciseDefaultRestSeconds = it.defaultRestSeconds
                    _restTime.value = it.defaultRestSeconds
                }
                Log.d("ExerciseLoggingVM", "Exercise loaded: ${exercise?.name}, defaultRest: ${exercise?.defaultRestSeconds}s")
            }
        }

        viewModelScope.launch {
            // Load personal bests by rep count for this exercise
            val pbsByReps = setDao.getPersonalBestsByReps(exerciseId)
            _personalBestsByReps.value = pbsByReps.associate { it.reps to it.maxWeight }
            Log.d("ExerciseLoggingVM", "Personal bests loaded: ${_personalBestsByReps.value}")
        }

        viewModelScope.launch {
            // Load or create workout
            val ongoing = workoutDao.getOngoingWorkout().first()
            if (ongoing != null) {
                _currentWorkout.value = ongoing
                Log.d("ExerciseLoggingVM", "Ongoing workout found: ${ongoing.id}")
            } else {
                // Create new workout
                val newWorkout = Workout(
                    startTime = Date(),
                    endTime = null,
                    name = null,
                    note = null
                )
                val workoutId = workoutDao.insertWorkout(newWorkout)
                val createdWorkout = workoutDao.getWorkoutById(workoutId).first()
                _currentWorkout.value = createdWorkout
                Log.d("ExerciseLoggingVM", "New workout created: ${createdWorkout.id}")
            }

            // Load logged sets for this exercise in current workout
            _currentWorkout.value?.let { workout ->
                setDao.getSetsForWorkout(workout.id).collectLatest { sets ->
                    _loggedSets.value = sets
                        .filter { it.exerciseId == exerciseId }
                        .sortedBy { it.timestamp }
                    Log.d("ExerciseLoggingVM", "Logged sets updated: ${_loggedSets.value.size}")
                }
            }
        }

        viewModelScope.launch {
            // Load last workout data for pre-fill
            _currentWorkout.value?.let { workout ->
                val previousSets = setDao.getLastWorkoutSetsForExercise(
                    exerciseId = exerciseId,
                    currentWorkoutId = workout.id
                )
                _lastWorkoutSets.value = previousSets
                Log.d("ExerciseLoggingVM", "Last workout sets loaded: ${previousSets.size}")

                // Auto-prefill from last workout if we have previous data and inputs are empty
                // This runs when entering the exercise screen with empty weight/reps
                if (!hasPrefilled && previousSets.isNotEmpty()) {
                    val lastSet = previousSets.first() // First set from previous workout
                    if (_weight.value.isBlank()) {
                        lastSet.weight?.let { _weight.value = it.toString() }
                    }
                    if (_reps.value.isBlank()) {
                        lastSet.reps?.let { _reps.value = it.toString() }
                    }
                    hasPrefilled = true
                    Log.d("ExerciseLoggingVM", "Pre-filled from last workout: ${lastSet.weight} x ${lastSet.reps}")
                }
            }
        }
    }

    fun updateWeight(value: String) {
        _weight.value = value
    }

    fun updateReps(value: String) {
        _reps.value = value
    }

    fun updateRpe(value: String) {
        _rpe.value = value
    }

    fun updateRestTime(seconds: Int) {
        _restTime.value = seconds
    }

    fun toggleWarmup() {
        _isWarmup.value = !_isWarmup.value
    }

    fun updateSetNote(note: String) {
        _setNote.value = note
    }

    fun logSet() {
        viewModelScope.launch {
            val workout = _currentWorkout.value ?: return@launch
            val exercise = _exercise.value ?: return@launch

            val newWeight = _weight.value.toFloatOrNull()
            val isWarmup = _isWarmup.value
            val note = _setNote.value.takeIf { it.isNotBlank() }

            val newSet = Set(
                workoutId = workout.id,
                exerciseId = exercise.id,
                weight = newWeight,
                reps = _reps.value.toIntOrNull(),
                rpe = _rpe.value.toFloatOrNull(),
                durationSeconds = null,
                distanceMeters = null,
                isWarmup = isWarmup,
                isComplete = true,
                timestamp = Date(),
                note = note
            )

            setDao.insertSet(newSet)

            // Update personal bests if this is a new record for this rep count
            val newReps = _reps.value.toIntOrNull()
            if (!isWarmup && newWeight != null && newReps != null) {
                val currentPBForReps = _personalBestsByReps.value[newReps]
                if (currentPBForReps == null || newWeight > currentPBForReps) {
                    _personalBestsByReps.value = _personalBestsByReps.value + (newReps to newWeight)
                    Log.d("ExerciseLoggingVM", "New personal best for $newReps reps: $newWeight lbs!")
                }
            }

            // Clear RPE and note (weight/reps persist for next set)
            _rpe.value = ""
            _setNote.value = ""
            _isWarmup.value = false
        }
    }

    fun startEditingSet(set: Set) {
        _editingSet.value = set
        _weight.value = set.weight?.toString() ?: ""
        _reps.value = set.reps?.toString() ?: ""
        _isWarmup.value = set.isWarmup
    }

    fun saveEditedSet() {
        viewModelScope.launch {
            _editingSet.value?.let { set ->
                val updatedSet = set.copy(
                    weight = _weight.value.toFloatOrNull(),
                    reps = _reps.value.toIntOrNull(),
                    isWarmup = _isWarmup.value
                )
                setDao.updateSet(updatedSet)
                Log.d("ExerciseLoggingVM", "Set updated: id=${set.id}")

                // Clear editing state and form
                _editingSet.value = null
                _weight.value = ""
                _reps.value = ""
                _rpe.value = ""
                _isWarmup.value = false
            }
        }
    }

    fun cancelEditing() {
        _editingSet.value = null
        _weight.value = ""
        _reps.value = ""
        _rpe.value = ""
        _isWarmup.value = false
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            Log.e("DELETE_DEBUG", "Attempting to delete set: $setId")
            setDao.deleteSetById(setId)
            Log.d("ExerciseLoggingVM", "Set deleted: id=$setId")
        }
    }

    fun updateSetNote(set: Set, note: String?) {
        viewModelScope.launch {
            val updatedSet = set.copy(note = note?.takeIf { it.isNotBlank() })
            setDao.updateSet(updatedSet)
            Log.d("ExerciseLoggingVM", "Set note updated: id=${set.id}, note=$note")
        }
    }

    fun startTimer() {
        _isTimerRunning.value = true
    }

    fun stopTimer() {
        _isTimerRunning.value = false
    }

    fun resetTimerToDefault() {
        _restTime.value = exerciseDefaultRestSeconds
    }

    fun getDefaultRestSeconds(): Int = exerciseDefaultRestSeconds

    fun finishWorkout() {
        viewModelScope.launch {
            val workout = _currentWorkout.value ?: return@launch
            val updatedWorkout = workout.copy(endTime = Date())
            workoutDao.updateWorkout(updatedWorkout)
            _navigationEvents.send(workout.id) // Send workoutId to navigate to summary
        }
    }

    // Load workout overview data
    fun loadWorkoutOverview() {
        viewModelScope.launch {
            _currentWorkout.value?.let { workout ->
                // If workout is from a routine, include unstarted routine exercises
                val overviewFlow = if (workout.routineDayId != null) {
                    setDao.getWorkoutExerciseSummariesWithRoutine(workout.id, workout.routineDayId)
                } else {
                    setDao.getWorkoutExerciseSummaries(workout.id)
                }
                overviewFlow.collectLatest { overview ->
                    _workoutOverview.value = overview
                    Log.d("ExerciseLoggingVM", "Workout overview loaded: ${overview.size} exercises")
                }
            }
        }
    }

    // Calculate personal records for the current exercise
    suspend fun getPersonalRecords(): PersonalRecords {
        val heaviest = setDao.getPersonalBest(exerciseId)
        val workingSets = setDao.getWorkingSetsForE1RMCalculation(exerciseId)

        // Find best E1RM
        val bestE1RM = workingSets
            .mapNotNull { set ->
                val e1rm = OneRepMaxCalculator.calculateE1RM(
                    set.weight ?: return@mapNotNull null,
                    set.reps ?: return@mapNotNull null
                )
                e1rm?.let { set to it }
            }
            .maxByOrNull { it.second }

        // Find best E10RM (premium feature)
        val bestE10RM = workingSets
            .mapNotNull { set ->
                val e10rm = OneRepMaxCalculator.calculateE10RM(
                    set.weight ?: return@mapNotNull null,
                    set.reps ?: return@mapNotNull null
                )
                e10rm?.let { set to it }
            }
            .maxByOrNull { it.second }

        return PersonalRecords(
            heaviestWeight = heaviest,
            bestE1RM = bestE1RM,
            bestE10RM = bestE10RM
        )
    }

    // Get exercise history grouped by workout
    suspend fun getExerciseHistory(): Map<Long, List<Set>> {
        val allSets = setDao.getExerciseHistoryByWorkout(exerciseId)
        return allSets.groupBy { it.workoutId }
    }

    // Calculate workout stats
    fun getWorkoutStats(): WorkoutStats {
        val workout = _currentWorkout.value
        val overview = _workoutOverview.value

        val totalSets = overview.sumOf { it.setCount }
        val totalVolume = overview.sumOf { summary ->
            ((summary.bestWeight ?: 0f) * summary.setCount).toDouble()
        }.toFloat()

        val duration = workout?.let {
            val durationMs = Date().time - it.startTime.time
            val minutes = durationMs / 1000 / 60
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
        } ?: "0m"

        return WorkoutStats(
            totalSets = totalSets,
            totalVolume = totalVolume,
            exerciseCount = overview.size,
            duration = duration
        )
    }
}
