package com.example.gymtime.ui.exercise

import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.dao.WorkoutPlanSummary
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.db.entity.isWarmupLibraryExercise
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.domain.progression.LoggerProgressState
import com.example.gymtime.domain.progression.buildLoggerProgress
import com.example.gymtime.domain.progression.loggerRecordLabels
import com.example.gymtime.service.RestTimerService
import com.example.gymtime.smartlog.SetDraft
import com.example.gymtime.smartlog.SmartLogDraftStore
import com.example.gymtime.smartlog.SmartLogQueueSnapshot
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout
import com.example.gymtime.util.TimeUtils
import com.example.gymtime.wear.ActiveWearSessionRepository
import com.example.gymtime.wear.WearDraftPatch
import com.example.gymtime.wear.WearSessionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class SmartLogQueueUiState(
    val currentSetNumber: Int,
    val totalSets: Int,
    val exerciseName: String
)

private data class ProgressScopeKey(
    val exerciseId: Long,
    val logType: LogType,
    val distanceUnit: DistanceUnit,
    val isWarmupLibrary: Boolean,
    val workoutId: Long,
    val workoutStartMs: Long
)

private data class WearWorkoutState(
    val workoutId: Long?,
    val exercise: Exercise?,
    val loggedSets: List<Set>
)

private data class WearTextFields(
    val weight: String,
    val reps: String,
    val rpe: String,
    val duration: String,
    val distance: String,
    val calories: String
)

private data class WearFormState(
    val textFields: WearTextFields,
    val isWarmup: Boolean,
    val selectedDistanceUnit: DistanceUnit
)

private data class WearTimerState(
    val restSeconds: Int,
    val timerRemainingSeconds: Int,
    val timerRunning: Boolean
)

private data class SetFormSnapshot(
    val weight: String,
    val calories: String,
    val reps: String,
    val rpe: String,
    val duration: String,
    val distance: String,
    val distanceUnit: DistanceUnit,
    val isWarmup: Boolean,
    val note: String
)

private data class ParsedSetInput(
    val weight: Float? = null,
    val calories: Float? = null,
    val reps: Int? = null,
    val rpe: Float? = null,
    val durationSeconds: Int? = null,
    val distanceValue: Float? = null,
    val distanceUnit: DistanceUnit? = null,
    val distanceMeters: Float? = null,
    val isWarmup: Boolean,
    val note: String?
)

private data class SetInputValidationResult(
    val parsedInput: ParsedSetInput? = null,
    val message: String? = null
) {
    val isValid: Boolean get() = parsedInput != null
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ExerciseLoggingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val supersetManager: SupersetManager,
    private val activeWearSessionRepository: ActiveWearSessionRepository,
    private val smartLogDraftStore: SmartLogDraftStore,
    private val loggerCelebrations: LoggerCelebrationState = LoggerCelebrationState()
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])
    private var activeSmartLogToken: String? = savedStateHandle[KEY_DRAFT_TOKEN]
    private var lastAppliedSmartLogIndex: Int? = null
    val smartLogDraftToken: String? get() = activeSmartLogToken

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
    private var bindRequested = false
    private var serviceSyncJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as? RestTimerService.TimerBinder
            timerService = serviceBinder?.getService()
            serviceBound = timerService != null
            Log.d("ExerciseLoggingVM", "Service connected")

            serviceSyncJob?.cancel()
            serviceSyncJob = timerService?.let { service ->
                viewModelScope.launch {
                    launch {
                        service.remainingSeconds.collectLatest { seconds ->
                            _countdownTimer.value = seconds
                        }
                    }
                    launch {
                        service.isRunning.collectLatest { running ->
                            _isTimerRunning.value = running
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceSyncJob?.cancel()
            serviceSyncJob = null
            timerService = null
            serviceBound = false
            Log.d("ExerciseLoggingVM", "Service disconnected")
        }
    }

    private val _exercise = MutableStateFlow<Exercise?>(null)
    val exercise: StateFlow<Exercise?> = _exercise

    private val _currentWorkout = MutableStateFlow<Workout?>(null)
    val currentWorkout: StateFlow<Workout?> = _currentWorkout

    val prCelebration = loggerCelebrations.celebration

    fun dismissPrCelebration(id: Long) = loggerCelebrations.dismiss(id)
    fun claimPrCelebrationHaptic(id: Long): Boolean = loggerCelebrations.claimHaptic(id)

    // Collect full history when observed. Room invalidates on writes, so distinctUntilChanged
    // prevents rebuilding multi-year progress when unrelated exercises' sets change.
    val previewProgress: StateFlow<LoggerProgressState> = combine(_exercise, _currentWorkout) { exercise, workout ->
        exercise to workout
    }
        .distinctUntilChanged { old, new ->
            progressKey(old.first, old.second) == progressKey(new.first, new.second)
        }
        .flatMapLatest { (exercise, workout) ->
            if (exercise == null || workout == null) {
                flowOf(LoggerProgressState())
            } else {
                exerciseRepository.observeFullExerciseHistory(exerciseId)
                    .distinctUntilChanged()
                    .map { history ->
                        buildLoggerProgress(exercise, history, workout)
                    }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LoggerProgressState()
        )

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

    // Store the exercise's default rest time and initial load / user-adjustment guards
    private var exerciseDefaultRestSeconds: Int = 90
    private var hasInitializedExerciseDefaults = false
    private var lastAppliedPlanRestSeconds: Int? = null
    private var hasUserAdjustedRestTime = false

    // Flag to ensure we only prefill weight/reps once per ViewModel instance
    private var hasPrefilled = false

    private val _isWarmup = MutableStateFlow(false)
    val isWarmup: StateFlow<Boolean> = _isWarmup

    private val _setNote = MutableStateFlow("")
    val setNote: StateFlow<String> = _setNote

    private val _isPersistingSet = MutableStateFlow(false)
    val isPersistingSet: StateFlow<Boolean> = _isPersistingSet
    private val setPersistenceInFlight = AtomicBoolean(false)
    private val isFinishingWorkout = AtomicBoolean(false)

    private val _smartLogQueue = MutableStateFlow<SmartLogQueueUiState?>(null)
    val smartLogQueue: StateFlow<SmartLogQueueUiState?> = _smartLogQueue

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    val timerAudioEnabled = userPreferencesRepository.timerAudioEnabled
    val timerVibrateEnabled = userPreferencesRepository.timerVibrateEnabled

    // Set editing state
    private val _editingSet = MutableStateFlow<Set?>(null)
    val editingSet: StateFlow<Set?> = _editingSet
    private var formBeforeEditing: SetFormSnapshot? = null

    // Validation state (updated synchronously on form/exercise changes to avoid regex work per recomposition)
    private val _isCurrentInputValid = MutableStateFlow(false)
    val isCurrentInputValidState: StateFlow<Boolean> = _isCurrentInputValid.asStateFlow()

    private val _currentInputValidationMessage = MutableStateFlow<String?>(null)
    val currentInputValidationMessageState: StateFlow<String?> = _currentInputValidationMessage.asStateFlow()

    private val _currentPlanItem = MutableStateFlow<WorkoutPlanSummary?>(null)
    val currentPlanItem: StateFlow<WorkoutPlanSummary?> = _currentPlanItem

    private val _planItems = MutableStateFlow<List<WorkoutPlanSummary>>(emptyList())
    private val resolvedExerciseCache = mutableMapOf<Long, Exercise>()

    // Position within a routine day's plan, e.g. "Pull Day · Exercise 2 of 6"
    data class PlanPosition(val index: Int, val total: Int, val dayName: String?)
    private val _planPosition = MutableStateFlow<PlanPosition?>(null)
    val planPosition: StateFlow<PlanPosition?> = _planPosition

    // Timer auto-start setting
    val timerAutoStart = userPreferencesRepository.timerAutoStart

    // Plate calculator settings
    val barWeight = userPreferencesRepository.barWeight
    val loadingSides = userPreferencesRepository.loadingSides
    val availablePlates = userPreferencesRepository.availablePlates
    val plateInventorySettings = userPreferencesRepository.plateInventorySettings

    fun setPlateInventoryCount(weight: Float, count: Int) {
        viewModelScope.launch { userPreferencesRepository.setPlateInventoryCount(weight, count) }
    }

    fun setUsePlateInventory(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setUsePlateInventory(enabled) }
    }

    fun adjustPlateInventoryCount(weight: Float, delta: Int) {
        viewModelScope.launch { userPreferencesRepository.adjustPlateInventoryCount(weight, delta) }
    }

    private val _navigationEvents = Channel<Long>(Channel.BUFFERED)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    private var wearPublisherId: Long? = null
    private var wearPublishJob: Job? = null

    // Adjacent exercises in a routine, superset, or one-off workout plan.
    private val _nextExerciseId = MutableStateFlow<Long?>(null)
    val nextExerciseId: StateFlow<Long?> = _nextExerciseId

    private val _previousExerciseId = MutableStateFlow<Long?>(null)
    val previousExerciseId: StateFlow<Long?> = _previousExerciseId

    init {
        // A logger opened outside the active group starts standalone logging.
        if (supersetManager.isInSupersetMode.value &&
            !supersetManager.switchToExercise(exerciseId)
        ) {
            supersetManager.exitSupersetMode()
        }
        // Bind to timer service
        val serviceIntent = Intent(context, RestTimerService::class.java)
        bindRequested = context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d("ExerciseLoggingVM", "Binding to RestTimerService")

        observeWearCommands()

        viewModelScope.launch {
            // Load exercise via Repository
            exerciseRepository.getExercise(exerciseId).collectLatest { ex ->
                _exercise.value = ex
                if (ex == null) {
                    recomputeValidation()
                    return@collectLatest
                }
                resolvedExerciseCache[ex.id] = ex
                val previousDefaultRest = exerciseDefaultRestSeconds
                exerciseDefaultRestSeconds = ex.defaultRestSeconds

                if (!hasInitializedExerciseDefaults) {
                    hasInitializedExerciseDefaults = true
                    if (!hasUserAdjustedRestTime && _currentPlanItem.value?.restSeconds == null) {
                        _restTime.value = ex.defaultRestSeconds
                    }
                    commitFormChange {
                        if (lastAppliedSmartLogIndex == null && _editingSet.value == null && !hasPrefilled) {
                            _selectedDistanceUnit.value = ex.defaultDistanceUnit
                            setWarmupStateInternal(false)
                        } else {
                            setWarmupStateInternal(_isWarmup.value)
                        }
                    }
                    applySmartLogDraftIfAvailable()
                } else {
                    if (!hasUserAdjustedRestTime &&
                        _currentPlanItem.value?.restSeconds == null &&
                        ex.defaultRestSeconds != previousDefaultRest &&
                        _restTime.value == previousDefaultRest
                    ) {
                        _restTime.value = ex.defaultRestSeconds
                    }
                    commitFormChange {
                        setWarmupStateInternal(_isWarmup.value)
                    }
                    supersetManager.updateExercise(ex)
                }
                Log.d("ExerciseLoggingVM", "Exercise loaded: ${ex.name}")
            }
        }

        viewModelScope.launch {
            // Load or create workout via Repository
            val workout = workoutRepository.getCurrentWorkout()
            _currentWorkout.value = workout
        }

        viewModelScope.launch {
            _currentWorkout.filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .collectLatest { workout ->
                    workoutRepository.ensureWorkoutPlanInstance(workout.id, exerciseId)
                }
        }

        viewModelScope.launch {
            _currentWorkout.filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .flatMapLatest { workout ->
                    workoutRepository.getWorkoutPlanSummaries(workout.id).map { workout to it }
                }
                .collectLatest { (workout, planItems) ->
                    _planItems.value = planItems
                    val currentItem = planItems.firstOrNull { it.exerciseId == exerciseId }
                    _currentPlanItem.value = currentItem

                    val planRest = currentItem?.restSeconds
                    if (planRest != null && planRest != lastAppliedPlanRestSeconds) {
                        lastAppliedPlanRestSeconds = planRest
                        if (!hasUserAdjustedRestTime) {
                            _restTime.value = planRest
                        }
                    } else if (planRest == null) {
                        lastAppliedPlanRestSeconds = null
                    }

                    val currentIndex = planItems.indexOfFirst { it.exerciseId == exerciseId }
                    _previousExerciseId.value = if (currentIndex > 0) {
                        planItems[currentIndex - 1].exerciseId
                    } else {
                        null
                    }

                    _planPosition.value = if (currentIndex != -1) {
                        PlanPosition(
                            index = currentIndex + 1,
                            total = planItems.size,
                            dayName = workout.routineDayNameSnapshot
                        )
                    } else {
                        null
                    }

                    val activePlanGroupIds = planItems
                        .mapNotNull { it.supersetGroupId }
                        .groupingBy { it }
                        .eachCount()
                        .filterValues { it >= 2 }
                        .keys
                    supersetManager.dissolvePlanSupersetIfMissing(workout.id, activePlanGroupIds)

                    val currentGroupId = currentItem?.supersetGroupId
                    if (currentGroupId != null &&
                        currentGroupId in activePlanGroupIds &&
                        !supersetManager.isPlanGroupExited(workout.id, currentGroupId)
                    ) {
                        val group = planItems
                            .filter { it.supersetGroupId == currentGroupId }
                            .sortedBy { it.supersetOrderIndex }
                        val groupIds = group.map { it.exerciseId }

                        if (!supersetManager.isPlanSupersetSynced(workout.id, currentGroupId, groupIds)) {
                            val groupExercises = groupIds.mapNotNull { id ->
                                resolvedExerciseCache[id] ?: exerciseRepository.getExercise(id).first()?.also {
                                    resolvedExerciseCache[id] = it
                                }
                            }
                            if (groupExercises.size >= 2) {
                                supersetManager.syncPlanSuperset(
                                    workoutId = workout.id,
                                    groupId = currentGroupId,
                                    exercises = groupExercises,
                                    activeExerciseId = exerciseId
                                )
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            combine(
                supersetManager.isInSupersetMode,
                supersetManager.supersetExercises,
                _planItems
            ) { inSuperset, supersetList, planItems ->
                if (inSuperset && supersetList.size >= 2) {
                    val indexInSuperset = supersetList.indexOfFirst { it.id == exerciseId }
                    if (indexInSuperset != -1) {
                        supersetList[(indexInSuperset + 1) % supersetList.size].id
                    } else {
                        null
                    }
                } else {
                    val currentIndex = planItems.indexOfFirst { it.exerciseId == exerciseId }
                    if (currentIndex != -1 && currentIndex < planItems.lastIndex) {
                        planItems[currentIndex + 1].exerciseId
                    } else {
                        null
                    }
                }
            }.distinctUntilChanged().collectLatest { nextId ->
                _nextExerciseId.value = nextId
            }
        }

        viewModelScope.launch {
            // Load and observe sets for current workout via Repository
            _currentWorkout.filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .flatMapLatest { workout ->
                    workoutRepository.getSetsForWorkout(workout.id)
                        .map { sets ->
                            sets.filter { it.exerciseId == exerciseId }
                                .sortedWith(compareBy<Set> { it.timestamp }.thenBy { it.id })
                        }
                        .distinctUntilChanged()
                        .flowOn(Dispatchers.Default)
                }
                .collectLatest { sets ->
                    _loggedSets.value = sets
                }
        }

        viewModelScope.launch {
            // Prefill weight/reps from superset or last workout (only once per ViewModel instance)
            _currentWorkout.filterNotNull()
                .distinctUntilChanged { old, new -> old.id == new.id }
                .collectLatest { workout ->
                    val previousSets = workoutRepository.getLastWorkoutSetsForExercise(exerciseId, workout.id)
                    if (!hasPrefilled &&
                        _editingSet.value == null &&
                        smartLogDraftStore.peek(activeSmartLogToken, exerciseId) == null
                    ) {
                        hasPrefilled = true

                        val supersetValues = if (supersetManager.isInSupersetMode.value) {
                            supersetManager.getLastLoggedValues(exerciseId)
                        } else null

                        commitFormChange {
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
                    } else {
                        hasPrefilled = true
                    }
                }
        }
    }

    fun applySmartLogDraft(token: String) {
        if (token.isBlank()) return
        val snapshot = smartLogDraftStore.peek(token, exerciseId) ?: return
        if (activeSmartLogToken != null && activeSmartLogToken != token) {
            smartLogDraftStore.discard(activeSmartLogToken)
        }
        formBeforeEditing = null
        _editingSet.value = null
        activeSmartLogToken = token
        savedStateHandle[KEY_DRAFT_TOKEN] = token
        lastAppliedSmartLogIndex = null
        applySmartLogDraftIfAvailable(snapshot)
    }

    fun updateWeight(value: String) = commitFormChange {
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

    fun updateCalories(value: String) = commitFormChange {
        _calories.value = value
    }

    fun updateReps(value: String) = commitFormChange {
        _reps.value = value
    }

    fun updateRpe(value: String) = commitFormChange {
        _rpe.value = value
    }

    fun updateDuration(value: String) = commitFormChange {
        _duration.value = value
    }

    fun updateDistance(value: String) = commitFormChange {
        _distance.value = value
    }

    fun updateSelectedDistanceUnit(unit: DistanceUnit) = commitFormChange {
        _selectedDistanceUnit.value = unit
    }

    fun updateRestTime(seconds: Int) {
        hasUserAdjustedRestTime = true
        _restTime.value = seconds.coerceAtLeast(0)
    }

    fun adjustRestTime(deltaSeconds: Int) {
        if (_isTimerRunning.value) {
            val serviceIntent = Intent(context, RestTimerService::class.java).apply {
                action = RestTimerService.ACTION_ADJUST_TIME
                putExtra(RestTimerService.EXTRA_DELTA_SECONDS, deltaSeconds)
            }
            try {
                context.startService(serviceIntent)
            } catch (error: IllegalStateException) {
                Log.w("ExerciseLoggingVM", "Unable to adjust timer service via startService; using active timer", error)
                RestTimerService.adjustActiveTimer(deltaSeconds)
            }
        } else {
            hasUserAdjustedRestTime = true
            _restTime.value = (_restTime.value + deltaSeconds).coerceAtLeast(0)
        }
    }

    fun toggleWarmup() = commitFormChange {
        if (isWarmupLibraryExercise()) {
            _isWarmup.value = true
        } else {
            _isWarmup.value = !_isWarmup.value
        }
    }

    fun updateSetNote(note: String) = commitFormChange {
        _setNote.value = note
    }

    fun isCurrentInputValid(): Boolean = _isCurrentInputValid.value

    fun currentInputValidationMessage(): String? = _currentInputValidationMessage.value

    fun logSet(startTimerAfterSave: Boolean = false) {
        if (isFinishingWorkout.get()) return
        val workout = _currentWorkout.value ?: return
        val exercise = _exercise.value ?: return
        val formSnapshot = captureCurrentFormSnapshot()
        val parsedInput = validateSetInput(exercise, formSnapshot).parsedInput ?: return
        if (!beginSetPersistence()) return

        val smartLogTokenAtSave = activeSmartLogToken
        val smartLogIndexAtSave = lastAppliedSmartLogIndex

        // Use the history already loaded by the preview; exclude current workout's cached
        // snapshot so edited or deleted current-workout sets cannot linger over _loggedSets.
        val progressBeforeSave = previewProgress.value
        val setsBeforeSave = (progressBeforeSave.sessions
            .filterNot { it.isCurrent }
            .flatMap { it.sets } + _loggedSets.value)
            .distinctBy { it.id }

        viewModelScope.launch {
            try {
                val newSet = buildLoggedSet(
                    workout = workout,
                    exercise = exercise,
                    parsedInput = parsedInput,
                    timestamp = Date()
                )

                workoutRepository.logSet(newSet)
                if (!progressBeforeSave.isLoading && progressBeforeSave.workoutId == workout.id) {
                    loggerCelebrations.celebrate(exercise.name, newSet, loggerRecordLabels(exercise, newSet, setsBeforeSave))
                }
                onLogSetSavedSuccessfully(
                    formSnapshot = formSnapshot,
                    startTimerAfterSave = startTimerAfterSave,
                    smartLogTokenAtSave = smartLogTokenAtSave,
                    smartLogIndexAtSave = smartLogIndexAtSave
                )
                activeWearSessionRepository.confirmSetSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e("ExerciseLoggingVM", "Failed to log set", error)
            } finally {
                endSetPersistence()
            }
        }
    }

    fun startEditingSet(set: Set) {
        if (_isPersistingSet.value || isFinishingWorkout.get()) return
        if (_editingSet.value == null) {
            formBeforeEditing = captureCurrentFormSnapshot()
        }
        _editingSet.value = set
        commitFormChange {
            set.distanceUnit?.let { _selectedDistanceUnit.value = it }
            _weight.value = set.weight?.let(::formatDecimalForInput) ?: ""
            _calories.value = set.calories?.let(::formatDecimalForInput) ?: ""
            _reps.value = set.reps?.toString() ?: ""
            _rpe.value = set.rpe?.let(::formatDecimalForInput) ?: ""
            _setNote.value = set.note.orEmpty()
            _duration.value = set.durationSeconds?.let { TimeUtils.formatSecondsToHMS(it) } ?: ""
            _distance.value = formatDistanceForEditing(set, _selectedDistanceUnit.value)
            setWarmupStateInternal(set.isWarmup)
        }
    }

    fun saveEditedSet() {
        if (isFinishingWorkout.get()) return
        val set = _editingSet.value ?: return
        val exercise = _exercise.value ?: return
        val formSnapshot = captureCurrentFormSnapshot()
        val parsedInput = validateSetInput(exercise, formSnapshot).parsedInput ?: return
        if (!beginSetPersistence()) return

        viewModelScope.launch {
            try {
                val updatedSet = set.copy(
                    weight = parsedInput.weight,
                    calories = parsedInput.calories,
                    reps = parsedInput.reps,
                    rpe = parsedInput.rpe,
                    durationSeconds = parsedInput.durationSeconds,
                    distanceValue = parsedInput.distanceValue,
                    distanceUnit = parsedInput.distanceUnit,
                    distanceMeters = parsedInput.distanceMeters,
                    isWarmup = exercise.isWarmupLibraryExercise || parsedInput.isWarmup,
                    note = parsedInput.note
                )
                workoutRepository.updateSet(updatedSet)
                Log.d("ExerciseLoggingVM", "Set updated: id=${set.id}")
                restoreFormAfterEditing()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e("ExerciseLoggingVM", "Failed to save edited set", error)
            } finally {
                endSetPersistence()
            }
        }
    }

    fun cancelEditing() {
        if (_isPersistingSet.value || _editingSet.value == null) return
        restoreFormAfterEditing()
    }

    fun cancelSmartLogQueue() {
        smartLogDraftStore.discard(activeSmartLogToken)
        activeSmartLogToken = null
        savedStateHandle[KEY_DRAFT_TOKEN] = null
        lastAppliedSmartLogIndex = null
        _smartLogQueue.value = null
    }

    fun deleteSet(setId: Long) {
        if (_editingSet.value?.id == setId) {
            if (_isPersistingSet.value) return
            restoreFormAfterEditing()
        }
        viewModelScope.launch {
            workoutRepository.deleteSet(setId)
            Log.d("ExerciseLoggingVM", "Set deleted: id=$setId")
        }
    }

    fun updateSetNote(set: Set, note: String?) {
        val normalizedNote = note?.takeIf { it.isNotBlank() }
        if (_editingSet.value?.id == set.id) {
            if (_isPersistingSet.value) return
            _editingSet.value = _editingSet.value?.copy(note = normalizedNote)
            commitFormChange {
                _setNote.value = normalizedNote.orEmpty()
            }
        }
        viewModelScope.launch {
            val updatedSet = set.copy(note = normalizedNote)
            workoutRepository.updateSet(updatedSet)
            Log.d("ExerciseLoggingVM", "Set note updated: id=${set.id}, note=$note")
        }
    }

    fun updateExerciseDetails(name: String, restSeconds: Int, notes: String?) {
        viewModelScope.launch {
            val ex = _exercise.value ?: return@launch
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return@launch
            exerciseRepository.updateExercise(
                ex.copy(
                    name = trimmedName,
                    defaultRestSeconds = restSeconds,
                    notes = notes?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            // Plan rows with no explicit routine/session rest resolve this updated
            // exercise default at query/display time; explicit prescriptions still win.
            Log.d("ExerciseLoggingVM", "Exercise details updated: name=$trimmedName, rest=$restSeconds")
        }
    }

    fun startTimer() {
        val seconds = _restTime.value
        Log.d("ExerciseLoggingVM", "Starting timer: $seconds seconds")

        val serviceIntent = Intent(context, RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_START_TIMER
            putExtra(RestTimerService.EXTRA_SECONDS, seconds)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (error: ForegroundServiceStartNotAllowedException) {
            Log.w("ExerciseLoggingVM", "Foreground service start blocked in background; using bound timer", error)
            timerService?.startTimer(seconds)
        } catch (error: IllegalStateException) {
            Log.w("ExerciseLoggingVM", "Unable to start foreground timer service; using bound timer", error)
            timerService?.startTimer(seconds)
        }
    }

    fun stopTimer() {
        Log.d("ExerciseLoggingVM", "Stopping timer")

        val serviceIntent = Intent(context, RestTimerService::class.java).apply {
            action = RestTimerService.ACTION_STOP_TIMER
        }
        try {
            context.startService(serviceIntent)
        } catch (error: IllegalStateException) {
            Log.w("ExerciseLoggingVM", "Unable to stop timer service via startService; using bound/active timer", error)
            val boundService = timerService
            if (boundService != null) {
                boundService.stopTimer()
            } else {
                RestTimerService.stopActiveTimer()
            }
        }
    }

    fun resetTimerToDefault() {
        hasUserAdjustedRestTime = false
        _restTime.value = getDefaultRestSeconds()
    }

    fun getDefaultRestSeconds(): Int = _currentPlanItem.value?.restSeconds ?: exerciseDefaultRestSeconds

    fun finishWorkout() {
        val workout = _currentWorkout.value ?: return
        if (!isFinishingWorkout.compareAndSet(false, true)) return
        viewModelScope.launch {
            var navigated = false
            try {
                // Wait for any in-flight set persistence so finishWorkout doesn't close
                // the workout before the set commits and advances routine progress.
                _isPersistingSet.first { !it }
                workoutRepository.finishWorkout(workout.id)
                runCatching { activeWearSessionRepository.clear() }
                    .onFailure { Log.w("ExerciseLoggingVM", "Unable to clear Wear session", it) }

                // Exit superset mode and clear exited groups when finishing workout
                supersetManager.exitSupersetMode()
                supersetManager.clearWorkoutExitedPlanGroups(workout.id)

                _navigationEvents.send(workout.id) // Send workoutId to navigate to summary
                navigated = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e("ExerciseLoggingVM", "Failed to finish workout", error)
            } finally {
                if (!navigated) {
                    isFinishingWorkout.set(false)
                }
            }
        }
    }

    fun exitSupersetMode() {
        supersetManager.exitPlanSuperset(
            workoutId = _currentWorkout.value?.id,
            fallbackGroupId = _currentPlanItem.value?.supersetGroupId
        )
        Log.d("ExerciseLoggingVM", "Exited superset mode")
    }

    /** Keeps the singleton superset cursor aligned with a manually tapped pill. */
    fun selectSupersetExercise(exerciseId: Long): Boolean =
        supersetManager.switchToExercise(exerciseId)

    fun prepareForSmartLogExerciseCreation(restrictedToSuperset: Boolean) {
        if (restrictedToSuperset) {
            exitSupersetMode()
        }
    }

    // Calculate plates for a given target weight
    suspend fun calculatePlates(targetWeight: Float): PlateLoadout {
        val plates = availablePlates.first()
        val bar = barWeight.first()
        val sides = loadingSides.first()
        val inventory = plateInventorySettings.first()

        return PlateCalculator.calculatePlates(
            targetWeight = targetWeight,
            availablePlates = plates,
            barWeight = bar,
            loadingSides = sides,
            plateInventory = inventory.counts.takeIf { inventory.enabled }
        )
    }

    override fun onCleared() {
        stopWearPublishing()
        serviceSyncJob?.cancel()
        serviceSyncJob = null
        super.onCleared()
        if (bindRequested || serviceBound) {
            runCatching { context.unbindService(serviceConnection) }
                .onFailure { Log.w("ExerciseLoggingVM", "Unable to unbind RestTimerService", it) }
            bindRequested = false
            serviceBound = false
            timerService = null
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

    private fun progressKey(exercise: Exercise?, workout: Workout?): ProgressScopeKey? {
        if (exercise == null || workout == null) return null
        return ProgressScopeKey(
            exerciseId = exercise.id,
            logType = exercise.logType,
            distanceUnit = exercise.defaultDistanceUnit,
            isWarmupLibrary = exercise.isWarmupLibraryExercise,
            workoutId = workout.id,
            workoutStartMs = workout.startTime.time
        )
    }

    private fun formatDecimalForInput(value: Float): String {
        if (!value.isFinite()) return ""
        if (value == 0f) return "0"
        return if (value % 1f == 0f && value in Int.MIN_VALUE.toFloat()..Int.MAX_VALUE.toFloat()) {
            value.toInt().toString()
        } else {
            BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        }
    }

    private fun formatRoundedTwoDecimalsForInput(value: Float): String {
        if (!value.isFinite()) return ""
        val formatted = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        return if (formatted == "-0" || formatted.isEmpty()) "0" else formatted
    }

    private fun formatDistanceForEditing(set: Set, fallbackUnit: DistanceUnit): String {
        val unit = set.distanceUnit ?: fallbackUnit
        return when {
            set.distanceValue != null -> formatDecimalForInput(set.distanceValue)
            set.distanceMeters != null && unit.isConvertibleToMeters -> {
                val converted = TimeUtils.metersToDistance(set.distanceMeters, unit)
                when (unit) {
                    DistanceUnit.KILOMETERS,
                    DistanceUnit.MILES -> TimeUtils.formatDistance(converted, unit)
                    DistanceUnit.METERS,
                    DistanceUnit.YARDS,
                    DistanceUnit.FEET,
                    DistanceUnit.STEPS,
                    DistanceUnit.FLOORS -> formatRoundedTwoDecimalsForInput(converted)
                }
            }
            else -> ""
        }
    }

    private fun applySmartLogDraftIfAvailable(preloadedSnapshot: SmartLogQueueSnapshot? = null) {
        val token = activeSmartLogToken
        val snapshot = preloadedSnapshot ?: smartLogDraftStore.peek(token, exerciseId)
        if (snapshot == null) {
            if (token != null) {
                activeSmartLogToken = null
                savedStateHandle[KEY_DRAFT_TOKEN] = null
            }
            lastAppliedSmartLogIndex = null
            _smartLogQueue.value = null
            return
        }
        if (lastAppliedSmartLogIndex == snapshot.currentIndex) return
        hasPrefilled = true
        lastAppliedSmartLogIndex = snapshot.currentIndex
        applySmartLogSet(snapshot.current)
        _smartLogQueue.value = snapshot.toUiState()
    }

    private fun advanceSmartLogDraft(expectedToken: String?, expectedIndex: Int?) {
        val token = activeSmartLogToken ?: return
        if (token != expectedToken || lastAppliedSmartLogIndex != expectedIndex) return
        if (smartLogDraftStore.peek(token, exerciseId) == null) {
            activeSmartLogToken = null
            savedStateHandle[KEY_DRAFT_TOKEN] = null
            lastAppliedSmartLogIndex = null
            _smartLogQueue.value = null
            return
        }
        val next = smartLogDraftStore.advance(token, exerciseId)
        if (next == null) {
            activeSmartLogToken = null
            savedStateHandle[KEY_DRAFT_TOKEN] = null
            lastAppliedSmartLogIndex = null
            _smartLogQueue.value = null
        } else {
            applySmartLogDraftIfAvailable(next)
        }
    }

    private fun applySmartLogSet(draft: SetDraft) = commitFormChange {
        _weight.value = draft.weight?.let(::formatDecimalForInput) ?: ""
        _calories.value = draft.calories?.let(::formatDecimalForInput) ?: ""
        _reps.value = draft.reps?.toString() ?: ""
        _rpe.value = draft.rpe?.let(::formatDecimalForInput) ?: ""
        _duration.value = draft.durationSeconds?.let(TimeUtils::formatSecondsToHMS) ?: ""
        _distance.value = draft.distanceValue?.let(::formatDecimalForInput) ?: ""
        draft.distanceUnit?.let { _selectedDistanceUnit.value = it }
        setWarmupStateInternal(draft.isWarmup)
        _setNote.value = draft.note.orEmpty()
    }

    private fun SmartLogQueueSnapshot.toUiState() = SmartLogQueueUiState(
        currentSetNumber = currentIndex + 1,
        totalSets = total,
        exerciseName = exerciseName
    )

    private fun observeWearCommands() {
        viewModelScope.launch {
            activeWearSessionRepository.draftPatches.collectLatest { patch ->
                applyWearDraftPatch(patch)
            }
        }

        viewModelScope.launch {
            activeWearSessionRepository.logRequests.collectLatest { patch ->
                if (isWearCommandBlocked()) return@collectLatest
                if (patch != null && !applyWearDraftPatch(patch)) return@collectLatest
                val autoStart = timerAutoStart.first()
                if (isWearCommandBlocked()) return@collectLatest
                logSet(startTimerAfterSave = autoStart)
            }
        }
    }

    private fun isWearCommandBlocked(): Boolean =
        _editingSet.value != null || setPersistenceInFlight.get() || isFinishingWorkout.get()

    private fun applyWearDraftPatch(patch: WearDraftPatch): Boolean {
        if (isWearCommandBlocked()) return false
        val workout = _currentWorkout.value ?: return false
        if (patch.workoutId != workout.id || patch.exerciseId != exerciseId) return false

        commitFormChange {
            patch.weight?.let { _weight.value = it }
            patch.reps?.let { _reps.value = it }
            patch.rpe?.let { _rpe.value = it }
            patch.duration?.let { _duration.value = it }
            patch.distance?.let { _distance.value = it }
            patch.calories?.let { _calories.value = it }
            patch.isWarmup?.let(::setWarmupStateInternal)
        }
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
            }.distinctUntilChanged()

            var firstTextEmission = true
            val textFields = combine(_weight, _reps, _rpe, _duration, _distance, _calories) { values ->
                WearTextFields(
                    weight = values[0],
                    reps = values[1],
                    rpe = values[2],
                    duration = values[3],
                    distance = values[4],
                    calories = values[5]
                )
            }
                .distinctUntilChanged()
                .debounce {
                    if (firstTextEmission) {
                        firstTextEmission = false
                        0L
                    } else {
                        WEAR_DRAFT_DEBOUNCE_MILLIS
                    }
                }

            val formState = combine(textFields, _isWarmup, _selectedDistanceUnit) { fields, isWarmup, unit ->
                WearFormState(
                    textFields = fields,
                    isWarmup = isWarmup,
                    selectedDistanceUnit = unit
                )
            }.distinctUntilChanged()

            val timerState = combine(_restTime, _countdownTimer, _isTimerRunning) { restTime, countdownTimer, isTimerRunning ->
                WearTimerState(
                    restSeconds = restTime,
                    timerRemainingSeconds = countdownTimer,
                    timerRunning = isTimerRunning
                )
            }.distinctUntilChanged()

            combine(
                workoutState,
                formState,
                timerState
            ) { workout, form, timer ->
                WearSessionSnapshot.fromLogger(
                    workoutId = workout.workoutId,
                    exercise = workout.exercise,
                    loggedSets = workout.loggedSets,
                    weight = form.textFields.weight,
                    reps = form.textFields.reps,
                    rpe = form.textFields.rpe,
                    duration = form.textFields.duration,
                    distance = form.textFields.distance,
                    calories = form.textFields.calories,
                    isWarmup = form.isWarmup,
                    selectedDistanceUnit = form.selectedDistanceUnit,
                    restSeconds = timer.restSeconds,
                    timerRemainingSeconds = timer.timerRemainingSeconds,
                    timerRunning = timer.timerRunning
                )
            }
                .distinctUntilChanged()
                .onEach { snapshot ->
                    activeWearSessionRepository.publish(publisherId, snapshot)
                }
                .flowOn(Dispatchers.Default)
                .collect()
        }
    }

    private fun beginSetPersistence(): Boolean {
        if (!setPersistenceInFlight.compareAndSet(false, true)) return false
        _isPersistingSet.value = true
        return true
    }

    private fun endSetPersistence() {
        _isPersistingSet.value = false
        setPersistenceInFlight.set(false)
    }

    private fun captureCurrentFormSnapshot(): SetFormSnapshot = SetFormSnapshot(
        weight = _weight.value,
        calories = _calories.value,
        reps = _reps.value,
        rpe = _rpe.value,
        duration = _duration.value,
        distance = _distance.value,
        distanceUnit = _selectedDistanceUnit.value,
        isWarmup = _isWarmup.value,
        note = _setNote.value
    )

    private fun buildLoggedSet(
        workout: Workout,
        exercise: Exercise,
        parsedInput: ParsedSetInput,
        timestamp: Date
    ): Set {
        val supersetGroupId = if (supersetManager.isInSupersetMode.value &&
            supersetManager.isInCurrentSuperset(exercise.id)
        ) {
            supersetManager.supersetGroupId.value
        } else null
        val supersetOrderIndex = if (supersetGroupId != null) {
            supersetManager.getOrderIndex(exercise.id)
        } else {
            0
        }

        return Set(
            workoutId = workout.id,
            exerciseId = exercise.id,
            weight = parsedInput.weight,
            calories = parsedInput.calories,
            reps = parsedInput.reps,
            rpe = parsedInput.rpe,
            durationSeconds = parsedInput.durationSeconds,
            distanceValue = parsedInput.distanceValue,
            distanceUnit = parsedInput.distanceUnit,
            distanceMeters = parsedInput.distanceMeters,
            isWarmup = exercise.isWarmupLibraryExercise || parsedInput.isWarmup,
            isComplete = true,
            timestamp = timestamp,
            note = parsedInput.note,
            supersetGroupId = supersetGroupId,
            supersetOrderIndex = supersetOrderIndex
        )
    }

    private suspend fun onLogSetSavedSuccessfully(
        formSnapshot: SetFormSnapshot,
        startTimerAfterSave: Boolean,
        smartLogTokenAtSave: String?,
        smartLogIndexAtSave: Int?
    ) {
        commitFormChange {
            _rpe.value = ""
            _setNote.value = ""
            setWarmupStateInternal(false)
        }

        advanceSmartLogDraft(smartLogTokenAtSave, smartLogIndexAtSave)

        if (startTimerAfterSave) {
            startTimer()
        }
        resetTimerToDefault()

        val currentExercise = _exercise.value
        if (currentExercise != null &&
            supersetManager.isInSupersetMode.value &&
            supersetManager.isInCurrentSuperset(currentExercise.id)
        ) {
            supersetManager.saveLastLoggedValues(
                exerciseId,
                LastLoggedValues(
                    weight = formSnapshot.weight,
                    calories = formSnapshot.calories,
                    reps = formSnapshot.reps,
                    duration = formSnapshot.duration,
                    distance = formSnapshot.distance,
                    distanceUnit = formSnapshot.distanceUnit
                )
            )

            val nextExerciseId = supersetManager.switchToNextExercise()
            if (nextExerciseId > 0) {
                Log.d("ExerciseLoggingVM", "Superset auto-switching to exercise: $nextExerciseId")
                _autoSwitchEvent.send(nextExerciseId)
            }
        }
    }

    private fun restoreFormAfterEditing() {
        val draft = formBeforeEditing
        formBeforeEditing = null
        _editingSet.value = null
        if (draft == null) return
        commitFormChange {
            _weight.value = draft.weight
            _calories.value = draft.calories
            _reps.value = draft.reps
            _rpe.value = draft.rpe
            _duration.value = draft.duration
            _distance.value = draft.distance
            _selectedDistanceUnit.value = draft.distanceUnit
            setWarmupStateInternal(draft.isWarmup)
            _setNote.value = draft.note
        }
    }

    private fun isWarmupLibraryExercise(): Boolean =
        _exercise.value?.isWarmupLibraryExercise == true

    private fun setWarmupStateInternal(requestedWarmup: Boolean) {
        _isWarmup.value = requestedWarmup || isWarmupLibraryExercise()
    }

    private inline fun commitFormChange(update: () -> Unit) {
        update()
        recomputeValidation()
    }

    private fun recomputeValidation() {
        val exercise = _exercise.value
        if (exercise == null) {
            _isCurrentInputValid.value = false
            _currentInputValidationMessage.value = null
            return
        }
        val result = validateSetInput(exercise, captureCurrentFormSnapshot())
        _isCurrentInputValid.value = result.isValid
        _currentInputValidationMessage.value = result.message
    }

    private fun validateSetInput(
        exercise: Exercise,
        formSnapshot: SetFormSnapshot
    ): SetInputValidationResult {
        val weight = parseStrictDecimal(formSnapshot.weight)
        val calories = parseStrictDecimal(formSnapshot.calories)
        val reps = parseStrictPositiveInt(formSnapshot.reps)
        val rpe = parseOptionalRpe(formSnapshot.rpe)
        val durationSeconds = parseStrictDurationSeconds(formSnapshot.duration)
        val distanceValue = parseStrictDecimal(formSnapshot.distance)
        val distanceMeters = distanceValue?.let { value ->
            if (value <= 0f) {
                null
            } else {
                TimeUtils.distanceToMeters(value, formSnapshot.distanceUnit)
            }
        }
        val note = formSnapshot.note.trim().takeIf { it.isNotEmpty() }

        fun positiveFloat(value: Float?) = value != null && value > 0f
        fun hasText(value: String) = value.trim().isNotEmpty()

        fun invalidWeightMessage() =
            if (hasText(formSnapshot.weight) && weight == null) "Enter a valid weight." else null

        fun invalidCaloriesMessage() =
            if (hasText(formSnapshot.calories) && !positiveFloat(calories)) "Enter positive calories." else null

        fun invalidRepsMessage() =
            if (hasText(formSnapshot.reps) && reps == null) "Enter whole-number reps." else null

        fun invalidDurationMessage() =
            if (hasText(formSnapshot.duration) && durationSeconds == null) "Enter time as seconds, mm:ss, or hh:mm:ss." else null

        fun invalidDistanceMessage() =
            if (hasText(formSnapshot.distance) && !positiveFloat(distanceValue)) "Enter a positive distance." else null

        fun invalidRpeMessage() =
            if (hasText(formSnapshot.rpe) && rpe == null) "Enter RPE from 0 to 10." else null

        invalidRpeMessage()?.let { return SetInputValidationResult(message = it) }

        return when (exercise.logType) {
            LogType.WEIGHT_REPS -> {
                invalidWeightMessage()?.let { SetInputValidationResult(message = it) }
                    ?: invalidRepsMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (weight == null || reps == null) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                weight = weight,
                                reps = reps,
                                rpe = rpe,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
            LogType.REPS_ONLY -> {
                invalidRepsMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (reps == null) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                reps = reps,
                                rpe = rpe,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
            LogType.DURATION -> {
                invalidDurationMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (durationSeconds == null || durationSeconds <= 0) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                rpe = rpe,
                                durationSeconds = durationSeconds,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
            LogType.WEIGHT_DISTANCE -> {
                invalidWeightMessage()?.let { SetInputValidationResult(message = it) }
                    ?: invalidDistanceMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (weight == null || !positiveFloat(distanceValue)) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                weight = weight,
                                rpe = rpe,
                                distanceValue = distanceValue,
                                distanceUnit = formSnapshot.distanceUnit,
                                distanceMeters = distanceMeters,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
            LogType.DISTANCE_TIME -> {
                invalidDistanceMessage()?.let { SetInputValidationResult(message = it) }
                    ?: invalidDurationMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (!positiveFloat(distanceValue) || durationSeconds == null || durationSeconds <= 0) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                rpe = rpe,
                                durationSeconds = durationSeconds,
                                distanceValue = distanceValue,
                                distanceUnit = formSnapshot.distanceUnit,
                                distanceMeters = distanceMeters,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
            LogType.WEIGHT_TIME -> {
                invalidWeightMessage()?.let { SetInputValidationResult(message = it) }
                    ?: invalidDurationMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (weight == null || durationSeconds == null || durationSeconds <= 0) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                weight = weight,
                                rpe = rpe,
                                durationSeconds = durationSeconds,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
            LogType.CALORIES_TIME -> {
                invalidCaloriesMessage()?.let { SetInputValidationResult(message = it) }
                    ?: invalidDurationMessage()?.let { SetInputValidationResult(message = it) }
                    ?: if (!positiveFloat(calories) || durationSeconds == null || durationSeconds <= 0) {
                        SetInputValidationResult()
                    } else {
                        SetInputValidationResult(
                            parsedInput = ParsedSetInput(
                                calories = calories,
                                rpe = rpe,
                                durationSeconds = durationSeconds,
                                isWarmup = formSnapshot.isWarmup,
                                note = note
                            )
                        )
                    }
            }
        }
    }

    private fun parseStrictPositiveInt(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !POSITIVE_INT_PATTERN.matches(trimmed)) return null
        return trimmed.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun parseStrictDecimal(raw: String): Float? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !STRICT_DECIMAL_PATTERN.matches(trimmed)) return null
        return trimmed.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    }

    private fun parseOptionalRpe(raw: String): Float? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parsed = parseStrictDecimal(trimmed) ?: return null
        return parsed.takeIf { it in 0f..10f }
    }

    private fun parseStrictDurationSeconds(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) {
            return null
        }
        return try {
            val totalSeconds: Long = when (parts.size) {
                1 -> parts[0].toLong()
                2 -> {
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toLong()
                    if (seconds !in 0L..59L) return null
                    if (minutes > (Int.MAX_VALUE - seconds) / 60L) return null
                    (minutes * 60L) + seconds
                }
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toLong()
                    if (minutes !in 0L..59L || seconds !in 0L..59L) return null
                    val minSec = (minutes * 60L) + seconds
                    if (hours > (Int.MAX_VALUE - minSec) / 3600L) return null
                    (hours * 3600L) + minSec
                }
                else -> return null
            }
            if (totalSeconds in 1L..Int.MAX_VALUE.toLong()) totalSeconds.toInt() else null
        } catch (_: NumberFormatException) {
            null
        }
    }

    companion object {
        private const val KEY_DRAFT_TOKEN = "draftToken"
        private const val WEAR_DRAFT_DEBOUNCE_MILLIS = 120L
        private val STRICT_DECIMAL_PATTERN = Regex("""(?:\d+(?:\.\d+)?|\.\d+)""")
        private val POSITIVE_INT_PATTERN = Regex("""\d+""")
    }
}
