package com.example.gymtime.ui.exercise

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.domain.recommendation.ExerciseAttemptRecommendation
import com.example.gymtime.domain.recommendation.ExerciseAttemptRecommendationUseCase
import com.example.gymtime.service.RestTimerService
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout
import com.example.gymtime.util.TimeUtils
import com.example.gymtime.wear.ActiveWearSessionRepository
import com.example.gymtime.wear.WearDraftPatch
import com.example.gymtime.wear.WearSessionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class WorkoutStats(
    val totalSets: Int,
    val totalVolume: Float,
    val exerciseCount: Int,
    val duration: String
)

data class MuscleBreakdown(
    val muscle: String,
    val setCount: Int,
    val volume: Float
)

data class WorkoutPanelData(
    val exercises: List<WorkoutExerciseSummary> = emptyList(),
    val setPreviews: Map<Long, List<String>> = emptyMap(),
    val muscleBreakdown: List<MuscleBreakdown> = emptyList(),
    val stats: WorkoutStats = WorkoutStats(0, 0f, 0, "0m")
)

data class PersonalRecords(
    val heaviestWeight: Set?,
    val bestE1RM: Pair<Set, Float>?, // Set and calculated E1RM
    val bestE10RM: Pair<Set, Float>?  // Set and calculated E10RM (premium feature)
)

private data class WearWorkoutState(
    val workoutId: Long?,
    val exercise: Exercise?,
    val loggedSets: List<Set>
)

private data class WearPrimaryFields(
    val weight: String,
    val reps: String,
    val rpe: String,
    val duration: String,
    val distance: String
)

private data class WearFormState(
    val primaryFields: WearPrimaryFields,
    val calories: String,
    val isWarmup: Boolean,
    val selectedDistanceUnit: DistanceUnit
)

private data class WearTimerState(
    val restSeconds: Int,
    val timerRemainingSeconds: Int,
    val timerRunning: Boolean
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExerciseLoggingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val volumeOrbRepository: VolumeOrbRepository,
    private val supersetManager: SupersetManager,
    private val routineRepository: RoutineRepository,
    private val recommendationUseCase: ExerciseAttemptRecommendationUseCase,
    private val activeWearSessionRepository: ActiveWearSessionRepository
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

    private val _calories = MutableStateFlow("")
    val calories: StateFlow<String> = _calories

    private val _reps = MutableStateFlow("")
    val reps: StateFlow<String> = _reps

    private val _rpe = MutableStateFlow("")
    val rpe: StateFlow<String> = _rpe

    private val _duration = MutableStateFlow("") // For DURATION and DISTANCE_TIME exercises
    val duration: StateFlow<String> = _duration

    private val _distance = MutableStateFlow("") // For WEIGHT_DISTANCE and DISTANCE_TIME exercises
    val distance: StateFlow<String> = _distance

    private val _selectedDistanceUnit = MutableStateFlow(DistanceUnit.MILES)
    val selectedDistanceUnit: StateFlow<DistanceUnit> = _selectedDistanceUnit

    private val _restTime = MutableStateFlow(90) // Represents the timer SETTING
    val restTime: StateFlow<Int> = _restTime

    private val _countdownTimer = MutableStateFlow(0) // Represents the active COUNTDOWN
    val countdownTimer: StateFlow<Int> = _countdownTimer

    // Store the exercise's default rest time
    private var exerciseDefaultRestSeconds: Int = 90

    // Flag to ensure we only prefill weight/reps once per ViewModel instance
    private var hasPrefilled = false

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

    private val _attemptRecommendation = MutableStateFlow<ExerciseAttemptRecommendation?>(null)
    val attemptRecommendation: StateFlow<ExerciseAttemptRecommendation?> = _attemptRecommendation

    // Workout overview data
    private val _workoutOverview = MutableStateFlow<List<WorkoutExerciseSummary>>(emptyList())
    val workoutOverview: StateFlow<List<WorkoutExerciseSummary>> = _workoutOverview

    private val _workoutPanelData = MutableStateFlow(WorkoutPanelData())
    val workoutPanelData: StateFlow<WorkoutPanelData> = _workoutPanelData

    private val _currentPlanItem = MutableStateFlow<WorkoutPlanSummary?>(null)
    val currentPlanItem: StateFlow<WorkoutPlanSummary?> = _currentPlanItem

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

    private var wearPublisherId: Long? = null
    private var wearPublishJob: Job? = null

    // Next exercise in routine
    private val _nextExerciseId = MutableStateFlow<Long?>(null)
    val nextExerciseId: StateFlow<Long?> = _nextExerciseId

    init {
        // Bind to timer service
        val serviceIntent = Intent(context, RestTimerService::class.java)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("ExerciseLoggingVM", "Binding to RestTimerService")

        observeWearCommands()

        viewModelScope.launch {
            // Load exercise via Repository
            exerciseRepository.getExercise(exerciseId).collectLatest { ex ->
                _exercise.value = ex
                // Set timer to exercise's default rest time
                ex?.let {
                    exerciseDefaultRestSeconds = it.defaultRestSeconds
                    _restTime.value = it.defaultRestSeconds
                    _selectedDistanceUnit.value = it.defaultDistanceUnit
                    _attemptRecommendation.value = runCatching {
                        recommendationUseCase.getRecommendation(it)
                    }.getOrNull()
                    
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
            // Load personal bests via Repository
            _personalBestsByReps.value = exerciseRepository.getPersonalBestsByReps(exerciseId)

            // Also find the overall heaviest set for the "Best set" hint
            val heaviest = exerciseRepository.getHeaviestSet(exerciseId)
            heaviest?.let {
                _bestWeight.value = it.weight?.toString()
                _bestReps.value = it.reps?.toString()
            }
        }

        viewModelScope.launch {
            // Load or create workout via Repository
            val workout = workoutRepository.getCurrentWorkout()
            _currentWorkout.value = workout
        }

        viewModelScope.launch {
            _currentWorkout.filterNotNull().collectLatest { workout ->
                if (workout.startedFromRoutine) {
                    workoutRepository.ensureWorkoutPlanInstance(workout.id, exerciseId)
                } else {
                    _currentPlanItem.value = null
                    _nextExerciseId.value = null
                }
            }
        }

        viewModelScope.launch {
            _currentWorkout.filterNotNull().flatMapLatest { workout ->
                if (workout.startedFromRoutine) {
                    workoutRepository.getWorkoutPlanSummaries(workout.id)
                } else {
                    flowOf(emptyList())
                }
            }.collectLatest { planItems ->
                val currentItem = planItems.firstOrNull { it.exerciseId == exerciseId }
                _currentPlanItem.value = currentItem

                currentItem?.restSeconds?.let { _restTime.value = it }

                val currentIndex = planItems.indexOfFirst { it.exerciseId == exerciseId }
                val currentGroupId = currentItem?.supersetGroupId

                if (currentGroupId != null) {
                    val group = planItems
                        .filter { it.supersetGroupId == currentGroupId }
                        .sortedBy { it.supersetOrderIndex }

                    val indexInGroup = group.indexOfFirst { it.exerciseId == exerciseId }
                    if (indexInGroup != -1) {
                        val nextInRotation = (indexInGroup + 1) % group.size
                        _nextExerciseId.value = group[nextInRotation].exerciseId
                    }

                    val groupExercises = group.mapNotNull { workoutItem ->
                        exerciseRepository.getExercise(workoutItem.exerciseId).first()
                    }
                    if (groupExercises.size >= 2) {
                        if (!supersetManager.isInSupersetMode.value || supersetManager.supersetGroupId.value != currentGroupId) {
                            supersetManager.startSuperset(groupExercises, currentGroupId)
                        }
                        val orderIndex = groupExercises.indexOfFirst { it.id == exerciseId }
                        if (orderIndex != -1) {
                            supersetManager.setCurrentExerciseIndex(orderIndex)
                        }
                    }
                } else if (currentIndex != -1 && currentIndex < planItems.lastIndex) {
                    _nextExerciseId.value = planItems[currentIndex + 1].exerciseId
                } else if (!supersetManager.isInSupersetMode.value) {
                    _nextExerciseId.value = null
                }
            }
        }

        viewModelScope.launch {
            // Load and observe sets for current workout via Repository
            _currentWorkout.filterNotNull().collectLatest { workout ->
                workoutRepository.getSetsForWorkout(workout.id).collectLatest { sets ->
                    _loggedSets.value = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.timestamp }
                }
            }
        }

        viewModelScope.launch {
            // Load last workout sets for this exercise via Repository
            _currentWorkout.filterNotNull().collectLatest { workout ->
                val previousSets = workoutRepository.getLastWorkoutSetsForExercise(exerciseId, workout.id)
                _lastWorkoutSets.value = previousSets

                // Prefill weight/reps (only once per ViewModel instance)
                if (!hasPrefilled) {
                    hasPrefilled = true

                    // In superset mode, prefer stored values from this session
                    val supersetValues = if (supersetManager.isInSupersetMode.value) {
                        supersetManager.getLastLoggedValues(exerciseId)
                    } else null

                    if (supersetValues != null) {
                        _weight.value = supersetValues.weight
                        _calories.value = supersetValues.calories
                        _reps.value = supersetValues.reps
                        _duration.value = supersetValues.duration
                        _distance.value = supersetValues.distance
                        supersetValues.distanceUnit?.let { _selectedDistanceUnit.value = it }
                    } else if (previousSets.isNotEmpty()) {
                        val lastSet = previousSets.first()
                        lastSet.distanceUnit?.let { _selectedDistanceUnit.value = it }
                        lastSet.weight?.let { _weight.value = it.toString() }
                        lastSet.calories?.let { _calories.value = it.toString() }
                        lastSet.reps?.let { _reps.value = it.toString() }
                        lastSet.durationSeconds?.let { _duration.value = TimeUtils.formatSecondsToHMS(it) }
                        _distance.value = formatDistanceForEditing(lastSet, _selectedDistanceUnit.value)
                    }
                }
            }
        }
    }

    fun updateWeight(value: String) {
        _weight.value = value
    }

    fun startWearPublishing() {
        if (wearPublishJob != null) return
        val publisherId = activeWearSessionRepository.beginPublishing()
        wearPublisherId = publisherId
        wearPublishJob = publishWearSnapshots(publisherId)
    }

    fun stopWearPublishing() {
        val publisherId = wearPublisherId
        wearPublishJob?.cancel()
        wearPublishJob = null
        wearPublisherId = null
        if (publisherId != null) {
            activeWearSessionRepository.stopPublishing(publisherId)
        }
    }

    fun updateCalories(value: String) {
        _calories.value = value
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

    fun updateSelectedDistanceUnit(unit: DistanceUnit) {
        _selectedDistanceUnit.value = unit
    }

    fun updateRestTime(seconds: Int) {
        _restTime.value = seconds
    }

    fun adjustRestTime(deltaSeconds: Int) {
        if (_isTimerRunning.value) {
            val serviceIntent = Intent(context, RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_ADJUST_TIME
                putExtra(RestTimerService.EXTRA_DELTA_SECONDS, deltaSeconds)
            }
            context.startService(serviceIntent)
        } else {
            _restTime.value = (_restTime.value + deltaSeconds).coerceAtLeast(0)
        }
    }

    fun applyAttemptRecommendation() {
        val recommendation = _attemptRecommendation.value ?: return
        if (!recommendation.canApply) return

        recommendation.targetWeight?.let { _weight.value = formatWeightForInput(it) }
        recommendation.targetReps?.let { _reps.value = it.toString() }
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
            val distanceUnit = _selectedDistanceUnit.value
            val distanceValue = _distance.value.toFloatOrNull()
            val normalizedDistance = distanceValue?.let { TimeUtils.distanceToMeters(it, distanceUnit) }
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
                    distanceValue = distanceValue,
                    distanceUnit = distanceUnit,
                    distanceMeters = normalizedDistance,
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
                    distanceValue = distanceValue,
                    distanceUnit = distanceUnit,
                    distanceMeters = normalizedDistance,
                    isWarmup = isWarmup,
                    isComplete = true,
                    timestamp = setTimestamp,
                    note = note,
                    supersetGroupId = supersetGroupId,
                    supersetOrderIndex = supersetOrderIndex
                )
                LogType.WEIGHT_TIME -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = _weight.value.toFloatOrNull(),
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
                LogType.CALORIES_TIME -> Set(
                    workoutId = workout.id,
                    exerciseId = exercise.id,
                    weight = null,
                    calories = _calories.value.toFloatOrNull(),
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
            }

            // Log set via Repository
            workoutRepository.logSet(newSet)
            activeWearSessionRepository.confirmSetSaved()

            // Update personal bests if this is a new record for this rep count (only for WEIGHT_REPS)
            if (exercise.logType == LogType.WEIGHT_REPS) {
                val newWeight = _weight.value.toFloatOrNull()
                val newReps = _reps.value.toIntOrNull()
                if (!isWarmup && newWeight != null && newReps != null) {
                    val currentPBs = _personalBestsByReps.value.toMutableMap()
                    val currentPBForReps = currentPBs[newReps]

                    // If this is a raw improvement for this specific rep count, update...
                    // Note: We might want to refresh from Repository here, but local update for UI responsiveness
                    if (currentPBForReps == null || newWeight > currentPBForReps.maxWeight) {
                         // Refresh PBs
                         _personalBestsByReps.value = exerciseRepository.getPersonalBestsByReps(exerciseId)
                    }
                }
            }

            // Clear RPE and note (primary inputs persist for next set)
            _rpe.value = ""
            _setNote.value = ""
            _isWarmup.value = false

            // Auto-switch to next exercise if in superset mode
            if (supersetManager.isInSupersetMode.value) {
                // Save current form values before switching
                supersetManager.saveLastLoggedValues(
                    exerciseId,
                    LastLoggedValues(
                        weight = _weight.value,
                        calories = _calories.value,
                        reps = _reps.value,
                        duration = _duration.value,
                        distance = _distance.value,
                        distanceUnit = _selectedDistanceUnit.value
                    )
                )

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
        set.distanceUnit?.let { _selectedDistanceUnit.value = it }
        _weight.value = set.weight?.toString() ?: ""
        _calories.value = set.calories?.toString() ?: ""
        _reps.value = set.reps?.toString() ?: ""
        _duration.value = set.durationSeconds?.let { TimeUtils.formatSecondsToHMS(it) } ?: ""
        _distance.value = formatDistanceForEditing(set, _selectedDistanceUnit.value)
        _isWarmup.value = set.isWarmup
    }

    fun saveEditedSet() {
        viewModelScope.launch {
            _editingSet.value?.let { set ->
                val exercise = _exercise.value
                val currentUnit = _selectedDistanceUnit.value
                val rawDistance = _distance.value.toFloatOrNull()
                val updatedSet = set.copy(
                    weight = _weight.value.toFloatOrNull(),
                    calories = _calories.value.toFloatOrNull(),
                    reps = _reps.value.toIntOrNull(),
                    durationSeconds = TimeUtils.parseHMSToSeconds(_duration.value),
                    distanceValue = rawDistance,
                    distanceUnit = if (exercise?.logType == LogType.WEIGHT_DISTANCE || exercise?.logType == LogType.DISTANCE_TIME) currentUnit else null,
                    distanceMeters = rawDistance?.let { TimeUtils.distanceToMeters(it, currentUnit) },
                    isWarmup = _isWarmup.value
                )
                // Update via Repository
                workoutRepository.updateSet(updatedSet)
                Log.d("ExerciseLoggingVM", "Set updated: id=${set.id}")

                // Clear editing state and form
                _editingSet.value = null
                _weight.value = ""
                _calories.value = ""
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
        _calories.value = ""
        _reps.value = ""
        _rpe.value = ""
        _duration.value = ""
        _distance.value = ""
        _isWarmup.value = false
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            Log.e("DELETE_DEBUG", "Attempting to delete set: $setId")
            // Delete via Repository
            workoutRepository.deleteSet(setId)
            Log.d("ExerciseLoggingVM", "Set deleted: id=$setId")
        }
    }

    fun updateSetNote(set: Set, note: String?) {
        viewModelScope.launch {
            val updatedSet = set.copy(note = note?.takeIf { it.isNotBlank() })
            // Update via Repository
            workoutRepository.updateSet(updatedSet)
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
        ContextCompat.startForegroundService(context, serviceIntent)
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
            // Finish via Repository
            workoutRepository.finishWorkout(workout.id)
            activeWearSessionRepository.clear()

            // Exit superset mode when finishing workout
            supersetManager.exitSupersetMode()

            _navigationEvents.send(workout.id) // Send workoutId to navigate to summary
        }
    }

    fun exitSupersetMode() {
        supersetManager.exitSupersetMode()
        Log.d("ExerciseLoggingVM", "Exited superset mode")
    }

    // Load workout overview data
    fun loadWorkoutOverview() {
        viewModelScope.launch {
            _currentWorkout.value?.let { workout ->
                combine(
                    workoutRepository.getWorkoutOverview(workout.id, workout.routineDayId),
                    workoutRepository.getSetsForWorkout(workout.id)
                ) { overview, sets ->
                    overview to sets
                }.collectLatest { (overview, sets) ->
                    _workoutOverview.value = overview
                    _workoutPanelData.value = buildWorkoutPanelData(workout, overview, sets)
                    Log.d("ExerciseLoggingVM", "Workout overview loaded: ${overview.size} exercises, ${sets.size} sets")
                }
            }
        }
    }

    private fun buildWorkoutPanelData(
        workout: Workout,
        overview: List<WorkoutExerciseSummary>,
        allSets: List<Set>
    ): WorkoutPanelData {
        val workingSets = allSets.filter { !it.isWarmup }
        val setPreviews = workingSets
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) ->
                sets.sortedBy { it.timestamp }.map { formatSetPreview(it) }
            }

        val muscleByExerciseId = overview.associate { it.exerciseId to it.targetMuscle }
        val muscleBreakdown = workingSets
            .mapNotNull { set ->
                val muscle = muscleByExerciseId[set.exerciseId] ?: return@mapNotNull null
                val volume = (set.weight ?: 0f) * (set.reps ?: 0)
                Triple(muscle, 1, volume)
            }
            .groupBy { it.first }
            .map { (muscle, entries) ->
                MuscleBreakdown(
                    muscle = muscle,
                    setCount = entries.sumOf { it.second },
                    volume = entries.sumOf { it.third.toDouble() }.toFloat()
                )
            }
            .sortedByDescending { it.volume }

        val totalVolume = workingSets.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat()
        val durationMs = Date().time - workout.startTime.time
        val minutes = durationMs / 1000 / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        val duration = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

        val stats = WorkoutStats(
            totalSets = workingSets.size,
            totalVolume = totalVolume,
            exerciseCount = overview.size,
            duration = duration
        )

        return WorkoutPanelData(
            exercises = overview,
            setPreviews = setPreviews,
            muscleBreakdown = muscleBreakdown,
            stats = stats
        )
    }

    private fun formatSetPreview(set: Set): String {
        val w = set.weight
        val r = set.reps
        val dur = set.durationSeconds
        val dist = set.distanceMeters
        return when {
            w != null && w > 0f && r != null && r > 0 -> "${w.toInt()}×$r"
            r != null && r > 0 -> "${r}r"
            w != null && w > 0f && dur != null && dur > 0 -> "${w.toInt()}×${formatDurationShort(dur)}"
            w != null && w > 0f && dist != null && dist > 0f -> "${w.toInt()}×${dist.toInt()}m"
            dur != null && dur > 0 -> formatDurationShort(dur)
            dist != null && dist > 0f -> "${dist.toInt()}m"
            else -> "—"
        }
    }

    private fun formatDurationShort(seconds: Int): String {
        if (seconds < 60) return "${seconds}s"
        val m = seconds / 60
        val s = seconds % 60
        return if (s == 0) "${m}m" else "${m}m${s}s"
    }

    // Calculate personal records for the current exercise
    suspend fun getPersonalRecords(): PersonalRecords {
        // Delegate to Repository
        return exerciseRepository.getPersonalRecords(exerciseId)
    }

    // Get exercise history grouped by workout
    suspend fun getExerciseHistory(): Map<Long, List<Set>> {
        // Delegate to Repository
        return exerciseRepository.getExerciseHistory(exerciseId)
    }

    // Calculate workout stats
    fun getWorkoutStats(): WorkoutStats {
        val workout = _currentWorkout.value
        val overview = _workoutOverview.value
        // Delegate calculation to Repository
        return workoutRepository.calculateWorkoutStats(workout, overview)
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
        stopWearPublishing()
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

    private fun formatWeightForInput(weight: Float): String {
        return if (weight % 1f == 0f) weight.toInt().toString() else weight.toString()
    }

    private fun formatDistanceForEditing(set: Set, fallbackUnit: DistanceUnit): String {
        val unit = set.distanceUnit ?: fallbackUnit
        return when {
            set.distanceValue != null -> TimeUtils.formatDistance(set.distanceValue, unit)
            set.distanceMeters != null && unit.isConvertibleToMeters -> {
                TimeUtils.formatDistance(TimeUtils.metersToDistance(set.distanceMeters, unit), unit)
            }
            else -> ""
        }
    }

    private fun observeWearCommands() {
        viewModelScope.launch {
            activeWearSessionRepository.draftPatches.collectLatest { patch ->
                applyWearDraftPatch(patch)
            }
        }

        viewModelScope.launch {
            activeWearSessionRepository.logRequests.collectLatest { patch ->
                if (patch != null && !applyWearDraftPatch(patch)) return@collectLatest
                if (!isCurrentDraftLoggable()) return@collectLatest

                logSet()
                if (timerAutoStart.first()) {
                    startTimer()
                }
                resetTimerToDefault()
            }
        }
    }

    private fun applyWearDraftPatch(patch: WearDraftPatch): Boolean {
        val workout = _currentWorkout.value ?: return false
        if (patch.workoutId != workout.id || patch.exerciseId != exerciseId) return false

        patch.weight?.let { _weight.value = it }
        patch.reps?.let { _reps.value = it }
        patch.rpe?.let { _rpe.value = it }
        patch.duration?.let { _duration.value = it }
        patch.distance?.let { _distance.value = it }
        patch.calories?.let { _calories.value = it }
        patch.isWarmup?.let { _isWarmup.value = it }
        return true
    }

    private fun publishWearSnapshots(publisherId: Long): Job {
        return viewModelScope.launch {
            val workoutState = combine(_currentWorkout, _exercise, _loggedSets) { workout, exercise, loggedSets ->
                WearWorkoutState(
                    workoutId = workout?.id,
                    exercise = exercise,
                    loggedSets = loggedSets
                )
            }
            val primaryFields = combine(_weight, _reps, _rpe, _duration, _distance) { weight, reps, rpe, duration, distance ->
                WearPrimaryFields(
                    weight = weight,
                    reps = reps,
                    rpe = rpe,
                    duration = duration,
                    distance = distance
                )
            }
            val formState = combine(primaryFields, _calories, _isWarmup, _selectedDistanceUnit) { fields, calories, isWarmup, unit ->
                WearFormState(
                    primaryFields = fields,
                    calories = calories,
                    isWarmup = isWarmup,
                    selectedDistanceUnit = unit
                )
            }
            val timerState = combine(_restTime, _countdownTimer, _isTimerRunning) { restTime, countdownTimer, isTimerRunning ->
                WearTimerState(
                    restSeconds = restTime,
                    timerRemainingSeconds = countdownTimer,
                    timerRunning = isTimerRunning
                )
            }

            combine(
                workoutState,
                formState,
                timerState
            ) { workout, form, timer ->
                WearSessionSnapshot.fromLogger(
                    workoutId = workout.workoutId,
                    exercise = workout.exercise,
                    loggedSets = workout.loggedSets,
                    weight = form.primaryFields.weight,
                    reps = form.primaryFields.reps,
                    rpe = form.primaryFields.rpe,
                    duration = form.primaryFields.duration,
                    distance = form.primaryFields.distance,
                    calories = form.calories,
                    isWarmup = form.isWarmup,
                    selectedDistanceUnit = form.selectedDistanceUnit,
                    restSeconds = timer.restSeconds,
                    timerRemainingSeconds = timer.timerRemainingSeconds,
                    timerRunning = timer.timerRunning
                )
            }.collectLatest { snapshot ->
                activeWearSessionRepository.publish(publisherId, snapshot)
            }
        }
    }

    private fun isCurrentDraftLoggable(): Boolean {
        return when (_exercise.value?.logType) {
            LogType.WEIGHT_REPS -> _weight.value.isNotBlank() && _reps.value.isNotBlank()
            LogType.REPS_ONLY -> _reps.value.isNotBlank()
            LogType.DURATION -> _duration.value.isNotBlank()
            LogType.WEIGHT_DISTANCE -> _weight.value.isNotBlank() && _distance.value.isNotBlank()
            LogType.DISTANCE_TIME -> _distance.value.isNotBlank() && _duration.value.isNotBlank()
            LogType.WEIGHT_TIME -> _weight.value.isNotBlank() && _duration.value.isNotBlank()
            LogType.CALORIES_TIME -> _calories.value.isNotBlank() && _duration.value.isNotBlank()
            null -> false
        }
    }
}
