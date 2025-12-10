package com.example.gymtime.ui.exercise

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.service.RestTimerService
import com.example.gymtime.util.OneRepMaxCalculator
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val volumeOrbRepository: VolumeOrbRepository,
    private val supersetManager: SupersetManager
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])

    // Expose superset state for the UI
    val isInSupersetMode = supersetManager.isInSupersetMode
    val supersetExercises = supersetManager.supersetExercises
    val currentSupersetIndex = supersetManager.currentExerciseIndex

    // Auto-switch event for superset navigation
    private val _autoSwitchEvent = Channel<Long>(Channel.BUFFERED)
    val autoSwitchEvent = _autoSwitchEvent.receiveAsFlow()

    // Service binding
    private var timerService: RestTimerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as? RestTimerService.TimerBinder
            timerService = serviceBinder?.getService()
            serviceBound = true
            Log.d("ExerciseLoggingVM", "Service connected")

            // Sync timer state from service
            timerService?.let { service ->
                viewModelScope.launch {
                    service.remainingSeconds.collectLatest { seconds ->
                        _restTime.value = seconds
                    }
                }
                viewModelScope.launch {
                    service.isRunning.collectLatest { running ->
                        _isTimerRunning.value = running
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
            Log.d("ExerciseLoggingVM", "Service disconnected")
        }
    }

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

    private val _duration = MutableStateFlow("") // For DURATION and DISTANCE_TIME exercises
    val duration: StateFlow<String> = _duration

    private val _distance = MutableStateFlow("") // For WEIGHT_DISTANCE and DISTANCE_TIME exercises
    val distance: StateFlow<String> = _distance

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

    // Personal bests by rep count - Map of reps -> PB with timestamp
    private val _personalBestsByReps = MutableStateFlow<Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>>(emptyMap())
    val personalBestsByReps: StateFlow<Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>> = _personalBestsByReps

    // Workout overview data
    private val _workoutOverview = MutableStateFlow<List<WorkoutExerciseSummary>>(emptyList())
    val workoutOverview: StateFlow<List<WorkoutExerciseSummary>> = _workoutOverview

    // Timer auto-start setting
    val timerAutoStart = userPreferencesRepository.timerAutoStart

    // Plate calculator settings
    val barWeight = userPreferencesRepository.barWeight
    val loadingSides = userPreferencesRepository.loadingSides
    val availablePlates = userPreferencesRepository.availablePlates

    // Volume Orb state
    val volumeOrbState: StateFlow<VolumeOrbState> = volumeOrbRepository.orbState

    private val _navigationEvents = Channel<Long>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    init {
        // Bind to timer service
        val serviceIntent = Intent(context, RestTimerService::class.java)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("ExerciseLoggingVM", "Binding to RestTimerService")

        viewModelScope.launch {
            // Load exercise
            exerciseDao.getExerciseById(exerciseId).collectLatest { exercise ->
                _exercise.value = exercise
                // Set timer to exercise's default rest time
                exercise?.let {
                    exerciseDefaultRestSeconds = it.defaultRestSeconds
                    _restTime.value = it.defaultRestSeconds
                    
                    // Sync SupersetManager if in superset mode
                    if (supersetManager.isInSupersetMode.value) {
                         val index = supersetManager.getOrderIndex(exerciseId)
                         if (index != -1) {
                             supersetManager.setCurrentExerciseIndex(index)
                         }
                    }
                }
                Log.d("ExerciseLoggingVM", "Exercise loaded: ${exercise?.name}, defaultRest: ${exercise?.defaultRestSeconds}s")
            }
        }

        viewModelScope.launch {
            // Load personal bests by rep count for this exercise with timestamps
            val pbsWithTimestamps = setDao.getPersonalBestsWithTimestamps(exerciseId)
            val pbMap = pbsWithTimestamps.associateBy { it.reps }
            _personalBestsByReps.value = filterDominatedPBs(pbMap)
            Log.d("ExerciseLoggingVM", "Personal bests loaded with timestamps: ${_personalBestsByReps.value}")
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

                // Auto-prefill from last workout ONLY if this is the first set (no logged sets yet)
                // This runs when entering the exercise screen with empty weight/reps
                if (_loggedSets.value.isEmpty() && previousSets.isNotEmpty()) {
                    val lastSet = previousSets.first() // First set from previous workout
                    if (_weight.value.isBlank()) {
                        lastSet.weight?.let { _weight.value = it.toString() }
                    }
                    if (_reps.value.isBlank()) {
                        lastSet.reps?.let { _reps.value = it.toString() }
                    }
                    Log.d("ExerciseLoggingVM", "Pre-filled from last workout: ${lastSet.weight} x ${lastSet.reps}")
                }
            }
        }
    }

    /**
     * Filters out "dominated" personal bests.
     * A PB (repsA, weightA) is dominated by (repsB, weightB) if:
     * 1. weightB >= weightA AND repsB >= repsA
     * 2. AND (weightB > weightA OR repsB > repsA) - strictly better in at least one metric
     *
     * Example: 135x11 dominates 135x10. 135x10 is removed.
     */
    private fun filterDominatedPBs(rawPBs: Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>): Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp> {
        val result = mutableMapOf<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>()

        // Convert to list for easier comparison
        val candidates = rawPBs.entries.toList()

        for (candidate in candidates) {
            val repsA = candidate.key
            val pbA = candidate.value
            var isDominated = false

            for (challenger in candidates) {
                if (candidate == challenger) continue // Don't compare against self

                val repsB = challenger.key
                val pbB = challenger.value

                // Check dominance condition using weight values
                val strictlyBetter = (pbB.maxWeight > pbA.maxWeight) || (repsB > repsA)
                val atLeastAsGood = (pbB.maxWeight >= pbA.maxWeight) && (repsB >= repsA)

                if (atLeastAsGood && strictlyBetter) {
                    isDominated = true
                    break
                }
            }

            if (!isDominated) {
                result[repsA] = pbA
            }
        }

        return result
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

    fun updateDuration(value: String) {
        _duration.value = value
    }

    fun updateDistance(value: String) {
        _distance.value = value
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

            val isWarmup = _isWarmup.value
            val note = _setNote.value.takeIf { it.isNotBlank() }
            val setTimestamp = Date()

            // Get superset data if in superset mode
            val supersetGroupId = if (supersetManager.isInSupersetMode.value) {
                supersetManager.supersetGroupId.value
            } else null
            val supersetOrderIndex = if (supersetGroupId != null) {
                supersetManager.getOrderIndex(exercise.id)
            } else 0

            // Build set based on exercise LogType
            val newSet = when (exercise.logType) {
                LogType.WEIGHT_REPS -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = _weight.value.toFloatOrNull(),
                    reps = _reps.value.toIntOrNull(),
                    rpe = _rpe.value.toFloatOrNull(),
                    durationSeconds = null,
                    distanceMeters = null,
                    isWarmup = isWarmup,
                    isComplete = true,
                    timestamp = setTimestamp,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
                LogType.REPS_ONLY -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = null,
                    reps = _reps.value.toIntOrNull(),
                    rpe = _rpe.value.toFloatOrNull(),
                    durationSeconds = null,
                    distanceMeters = null,
                    isWarmup = isWarmup,
                    isComplete = true,
                    timestamp = setTimestamp,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
                LogType.DURATION -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = null,
                    reps = null,
                    rpe = _rpe.value.toFloatOrNull(),
                    durationSeconds = _duration.value.toIntOrNull(),
                    distanceMeters = null,
                    isWarmup = isWarmup,
                    isComplete = true,
                    timestamp = setTimestamp,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
                LogType.WEIGHT_DISTANCE -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = _weight.value.toFloatOrNull(),
                    reps = null,
                    rpe = _rpe.value.toFloatOrNull(),
                    durationSeconds = null,
                    distanceMeters = _distance.value.toFloatOrNull(),
                    isWarmup = isWarmup,
                    isComplete = true,
                    timestamp = setTimestamp,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
                LogType.DISTANCE_TIME -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = null,
                    reps = null,
                    rpe = _rpe.value.toFloatOrNull(),
                    durationSeconds = _duration.value.toIntOrNull(),
                    distanceMeters = _distance.value.toFloatOrNull(),
                    isWarmup = isWarmup,
                    isComplete = true,
                    timestamp = setTimestamp,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
            }

            setDao.insertSet(newSet)

            // Update personal bests if this is a new record for this rep count (only for WEIGHT_REPS)
            if (exercise.logType == LogType.WEIGHT_REPS) {
                val newWeight = _weight.value.toFloatOrNull()
                val newReps = _reps.value.toIntOrNull()
                if (!isWarmup && newWeight != null && newReps != null) {
                    val currentPBs = _personalBestsByReps.value.toMutableMap()
                    val currentPBForReps = currentPBs[newReps]

                    // If this is a raw improvement for this specific rep count, update and re-filter
                    if (currentPBForReps == null || newWeight > currentPBForReps.maxWeight) {
                        // Use the same timestamp as the set
                        currentPBs[newReps] = com.example.gymtime.data.db.dao.PBWithTimestamp(newReps, newWeight, setTimestamp.time)
                        _personalBestsByReps.value = filterDominatedPBs(currentPBs)
                        Log.d("ExerciseLoggingVM", "New PB! $newWeight x $newReps (first achieved at ${setTimestamp.time})")
                    }
                }
            }

            // Clear RPE and note (primary inputs persist for next set)
            _rpe.value = ""
            _setNote.value = ""
            _isWarmup.value = false

            // Refresh volume orb after logging set
            volumeOrbRepository.onSetLogged()

            // Auto-switch to next exercise if in superset mode
            if (supersetManager.isInSupersetMode.value) {
                val nextExerciseId = supersetManager.switchToNextExercise()
                if (nextExerciseId > 0) {
                    Log.d("ExerciseLoggingVM", "Superset auto-switching to exercise: $nextExerciseId")
                    _autoSwitchEvent.send(nextExerciseId)
                }
            }
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
        val seconds = _restTime.value
        Log.d("ExerciseLoggingVM", "Starting timer: $seconds seconds")

        val serviceIntent = Intent(context, RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_START_TIMER
            putExtra(RestTimerService.EXTRA_SECONDS, seconds)
        }
        context.startService(serviceIntent)
    }

    fun stopTimer() {
        Log.d("ExerciseLoggingVM", "Stopping timer")

        val serviceIntent = Intent(context, RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_STOP_TIMER
        }
        context.startService(serviceIntent)
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

            // Exit superset mode when finishing workout
            supersetManager.exitSupersetMode()

            _navigationEvents.send(workout.id) // Send workoutId to navigate to summary
        }
    }

    /**
     * Exit superset mode. Called when user taps "Exit Superset" or navigates away.
     */
    fun exitSupersetMode() {
        supersetManager.exitSupersetMode()
        Log.d("ExerciseLoggingVM", "Exited superset mode")
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

    // Calculate plates for a given target weight
    suspend fun calculatePlates(targetWeight: Float): PlateLoadout {
        val plates = availablePlates.first()
        val bar = barWeight.first()
        val sides = loadingSides.first()

        return PlateCalculator.calculatePlates(
            targetWeight = targetWeight,
            availablePlates = plates,
            barWeight = bar,
            loadingSides = sides
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Unbind service
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
            Log.d("ExerciseLoggingVM", "Service unbound")
        }
    }
}
