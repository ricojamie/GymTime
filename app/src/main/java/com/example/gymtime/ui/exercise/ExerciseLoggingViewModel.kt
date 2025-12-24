package com.example.gymtime.ui.exercise

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.data.db.entity.RoutineExercise
import com.example.gymtime.util.TimeUtils
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
    private val supersetManager: SupersetManager,
    private val routineRepository: RoutineRepository
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
                        _countdownTimer.value = seconds
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

    private val _restTime = MutableStateFlow(90) // Represents the timer SETTING
    val restTime: StateFlow<Int> = _restTime

    private val _countdownTimer = MutableStateFlow(0) // Represents the active COUNTDOWN
    val countdownTimer: StateFlow<Int> = _countdownTimer

    // Store the exercise's default rest time
    private var exerciseDefaultRestSeconds: Int = 90

    private val _isWarmup = MutableStateFlow(false)
    val isWarmup: StateFlow<Boolean> = _isWarmup

    private val _setNote = MutableStateFlow("")
    val setNote: StateFlow<String> = _setNote

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    val timerAudioEnabled = userPreferencesRepository.timerAudioEnabled
    val timerVibrateEnabled = userPreferencesRepository.timerVibrateEnabled

    // Set editing state
    private val _editingSet = MutableStateFlow<Set?>(null)
    val editingSet: StateFlow<Set?> = _editingSet

    // Last workout data
    private val _lastWorkoutSets = MutableStateFlow<List<Set>>(emptyList())
    val lastWorkoutSets: StateFlow<List<Set>> = _lastWorkoutSets

    // Personal bests by rep count - Map of reps -> PB with timestamp
    private val _personalBestsByReps = MutableStateFlow<Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>>(emptyMap())
    val personalBestsByReps: StateFlow<Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>> = _personalBestsByReps

    private val _bestWeight = MutableStateFlow<String?>(null)
    val bestWeight: StateFlow<String?> = _bestWeight

    private val _bestReps = MutableStateFlow<String?>(null)
    val bestReps: StateFlow<String?> = _bestReps

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

    // Next exercise in routine
    private val _nextExerciseId = MutableStateFlow<Long?>(null)
    val nextExerciseId: StateFlow<Long?> = _nextExerciseId

    init {
        // Bind to timer service
        val serviceIntent = Intent(context, RestTimerService::class.java)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("ExerciseLoggingVM", "Binding to RestTimerService")

        viewModelScope.launch {
            // Load exercise
            exerciseDao.getExerciseById(exerciseId).collectLatest { ex ->
                _exercise.value = ex
                // Set timer to exercise's default rest time
                ex?.let {
                    exerciseDefaultRestSeconds = it.defaultRestSeconds
                    _restTime.value = it.defaultRestSeconds
                    
                    // Initial sync with SupersetManager if already in a superset (e.g. from blank workout)
                    if (supersetManager.isInSupersetMode.value) {
                         val orderIndex = supersetManager.getOrderIndex(exerciseId)
                         if (orderIndex != -1) {
                             supersetManager.setCurrentExerciseIndex(orderIndex)
                         }
                    }
                }
                Log.d("ExerciseLoggingVM", "Exercise loaded: ${ex?.name}")
            }
        }

        viewModelScope.launch {
            // Load personal bests by rep count
            val pbsWithTimestamps = setDao.getPersonalBestsWithTimestamps(exerciseId)
            val pbMap = pbsWithTimestamps.associateBy { it.reps }
            _personalBestsByReps.value = filterDominatedPBs(pbMap)

            // Also find the overall heaviest set for the "Best set" hint
            val heaviest = setDao.getPersonalBest(exerciseId)
            heaviest?.let {
                _bestWeight.value = it.weight?.toString()
                _bestReps.value = it.reps?.toString()
            }
        }

        viewModelScope.launch {
            // Load or create workout
            val ongoing = workoutDao.getOngoingWorkout().first()
            if (ongoing != null) {
                _currentWorkout.value = ongoing
            } else {
                val newWorkoutId = workoutDao.insertWorkout(
                    Workout(startTime = Date(), endTime = null, name = null, note = null)
                )
                _currentWorkout.value = workoutDao.getWorkoutById(newWorkoutId).first()
            }
        }

        // Initialize routine data reactively once workout is loaded
        viewModelScope.launch {
            _currentWorkout.filterNotNull().flatMapLatest { workout ->
                if (workout.routineDayId != null) {
                    routineRepository.getRoutineDayWithExercises(workout.routineDayId)
                } else {
                    flowOf(null)
                }
            }.collectLatest { dayWithExercises ->
                val routineExercises = dayWithExercises?.exercises?.sortedBy { it.routineExercise.orderIndex }
                val currentRoutineExercise = routineExercises?.find { it.routineExercise.exerciseId == exerciseId }
                
                // 1. Determine next exercise for navigation
                val currentIndex = routineExercises?.indexOfFirst { it.routineExercise.exerciseId == exerciseId } ?: -1
                val currentGroupId = currentRoutineExercise?.routineExercise?.supersetGroupId
                
                if (currentGroupId != null) {
                    val group = routineExercises!!
                        .filter { it.routineExercise.supersetGroupId == currentGroupId }
                        .sortedBy { it.routineExercise.supersetOrderIndex }
                    
                    val indexInGroup = group.indexOfFirst { it.exercise.id == exerciseId }
                    if (indexInGroup != -1) {
                        val nextInRotation = (indexInGroup + 1) % group.size
                        _nextExerciseId.value = group[nextInRotation].exercise.id
                    }
                } else if (currentIndex != -1 && currentIndex < (routineExercises?.size ?: 0) - 1) {
                    _nextExerciseId.value = routineExercises!![currentIndex + 1].routineExercise.exerciseId
                } else {
                    _nextExerciseId.value = null
                }
                
                // 2. Initialize SupersetManager if part of a routine superset
                currentGroupId?.let { groupId ->
                    val groupExercises = routineExercises!!
                        .filter { it.routineExercise.supersetGroupId == groupId }
                        .sortedBy { it.routineExercise.supersetOrderIndex }
                        .map { it.exercise }
                    
                    if (groupExercises.size >= 2) {
                        if (!supersetManager.isInSupersetMode.value || supersetManager.supersetGroupId.value != groupId) {
                            supersetManager.startSuperset(groupExercises, groupId)
                        }
                        
                        // Sync current index
                        val orderIndex = groupExercises.indexOfFirst { it.id == exerciseId }
                        if (orderIndex != -1) {
                            supersetManager.setCurrentExerciseIndex(orderIndex)
                        }
                    }
                } ?: run {
                    // Logic removed: Do NOT exit superset mode here.
                    // Ad-hoc supersets (created manually) do not have a routineGroupId,
                    // so exiting here would destroy them immediately upon loading.
                }
            }
        }

        viewModelScope.launch {
            // Load and observe sets for current workout
            _currentWorkout.filterNotNull().collectLatest { workout ->
                setDao.getSetsForWorkout(workout.id).collectLatest { sets ->
                    _loggedSets.value = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.timestamp }
                }
            }
        }

        viewModelScope.launch {
            // Load last workout sets for this exercise
            _currentWorkout.filterNotNull().collectLatest { workout ->
                val previousSets = setDao.getLastWorkoutSetsForExercise(exerciseId, workout.id)
                _lastWorkoutSets.value = previousSets
                
                // Prefill if first set
                if (_loggedSets.value.isEmpty() && previousSets.isNotEmpty()) {
                    val lastSet = previousSets.first()
                    if (_weight.value.isBlank()) lastSet.weight?.let { _weight.value = it.toString() }
                    if (_reps.value.isBlank()) lastSet.reps?.let { _reps.value = it.toString() }
                }
            }
        }
    }

    private fun filterDominatedPBs(rawPBs: Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>): Map<Int, com.example.gymtime.data.db.dao.PBWithTimestamp> {
        val result = mutableMapOf<Int, com.example.gymtime.data.db.dao.PBWithTimestamp>()
        val candidates = rawPBs.entries.toList()
        for (candidate in candidates) {
            val repsA = candidate.key
            val pbA = candidate.value
            var isDominated = false
            for (challenger in candidates) {
                if (candidate == challenger) continue
                val repsB = challenger.key
                val pbB = challenger.value
                val strictlyBetter = (pbB.maxWeight > pbA.maxWeight) || (repsB > repsA)
                val atLeastAsGood = (pbB.maxWeight >= pbA.maxWeight) && (repsB >= repsA)
                if (atLeastAsGood && strictlyBetter) {
                    isDominated = true
                    break
                }
            }
            if (!isDominated) result[repsA] = pbA
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
                    durationSeconds = TimeUtils.parseHMSToSeconds(_duration.value),
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
                    distanceMeters = _distance.value.toFloatOrNull()?.let { TimeUtils.milesToMeters(it) },
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
                    durationSeconds = TimeUtils.parseHMSToSeconds(_duration.value),
                    distanceMeters = _distance.value.toFloatOrNull()?.let { TimeUtils.milesToMeters(it) },
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

            // Weight and reps persist to make next set logging easier

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
        _duration.value = set.durationSeconds?.let { TimeUtils.formatSecondsToHMS(it) } ?: ""
        _distance.value = set.distanceMeters?.let { TimeUtils.formatMiles(TimeUtils.metersToMiles(it)) } ?: ""
        _isWarmup.value = set.isWarmup
    }

    fun saveEditedSet() {
        viewModelScope.launch {
            _editingSet.value?.let { set ->
                val updatedSet = set.copy(
                    weight = _weight.value.toFloatOrNull(),
                    reps = _reps.value.toIntOrNull(),
                    durationSeconds = TimeUtils.parseHMSToSeconds(_duration.value),
                    distanceMeters = _distance.value.toFloatOrNull()?.let { TimeUtils.milesToMeters(it) },
                    isWarmup = _isWarmup.value
                )
                setDao.updateSet(updatedSet)
                Log.d("ExerciseLoggingVM", "Set updated: id=${set.id}")

                // Clear editing state and form
                _editingSet.value = null
                _weight.value = ""
                _reps.value = ""
                _rpe.value = ""
                _duration.value = ""
                _distance.value = ""
                _isWarmup.value = false
            }
        }
    }

    fun cancelEditing() {
        _editingSet.value = null
        _weight.value = ""
        _reps.value = ""
        _rpe.value = ""
        _duration.value = ""
        _distance.value = ""
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

    fun toggleTimerVibrate(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTimerVibrateEnabled(enabled)
        }
    }

    fun toggleTimerAudio(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTimerAudioEnabled(enabled)
        }
    }
}
