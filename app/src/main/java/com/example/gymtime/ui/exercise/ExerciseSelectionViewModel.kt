package com.example.gymtime.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.data.repository.WorkoutPlanEditResult
import com.example.gymtime.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import javax.inject.Inject

enum class ExerciseSortMode(val label: String) {
    ALPHABETICAL("A-Z"),
    ALL_TIME_SETS("All-time sets"),
    RECENT_SETS("90d sets"),
    RECENTLY_USED("Recently used")
}

data class PreviewPickerState(
    val isLoading: Boolean = true,
    val rows: List<ExerciseUsageRow> = emptyList(),
    val selectedMuscles: Set<String> = emptySet(),
    val sortMode: ExerciseSortMode = ExerciseSortMode.RECENTLY_USED,
    val lastSets: Map<Long, ExerciseLastSetRow> = emptyMap()
)

private data class PreviewPickerFilters(
    val sortMode: ExerciseSortMode,
    val query: String,
    val muscles: Set<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseSelectionViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val supersetManager: SupersetManager,
    private val pickerSessionState: ExercisePickerSessionState,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutMode: Boolean = savedStateHandle["workoutMode"] ?: false
    private val supersetMode: Boolean = savedStateHandle["supersetMode"] ?: false
    private val adHocParentId: Long? = savedStateHandle["adHocParentId"]
    private val addToSuperset: Boolean = savedStateHandle["addToSuperset"] ?: false
    private val swapInstanceId: Long = savedStateHandle["swapInstanceId"] ?: -1L

    // Track if we're in workout mode (passed from navigation, not DB query)
    val isWorkoutMode: StateFlow<Boolean> = MutableStateFlow(workoutMode)
    val isSwapMode: Boolean = swapInstanceId > 0
    val isImmediateSupersetSelection: Boolean =
        addToSuperset || (supersetMode && adHocParentId != null && adHocParentId > 0L)

    sealed interface SwapExerciseEvent {
        data class Success(val exerciseId: Long) : SwapExerciseEvent
        data class Error(val message: String) : SwapExerciseEvent
    }

    private val _swapExerciseEvent = Channel<SwapExerciseEvent>(Channel.BUFFERED)
    val swapExerciseEvent = _swapExerciseEvent.receiveAsFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedMuscles = MutableStateFlow<Set<String>>(emptySet())
    val selectedMuscles: StateFlow<Set<String>> = _selectedMuscles

    private val _sortMode = MutableStateFlow(ExerciseSortMode.ALPHABETICAL)
    val sortMode: StateFlow<ExerciseSortMode> = _sortMode

    private val previewSession = MutableStateFlow<ExercisePickerSession?>(null)
    private val previewPreferences = previewSession.flatMapLatest { session ->
        session?.preferences ?: flowOf(PreviewPickerPreferences())
    }
    val previewSelectedMuscles: StateFlow<Set<String>> = previewPreferences.map { it.muscles }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val previewSortMode: StateFlow<ExerciseSortMode> = previewPreferences.map { it.sortMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExerciseSortMode.RECENTLY_USED)

    private val _selectionMessages = Channel<String>(Channel.BUFFERED)
    val selectionMessages = _selectionMessages.receiveAsFlow()

    // Superset selection mode state
    private val _isSupersetModeEnabled = MutableStateFlow(supersetMode)
    val isSupersetModeEnabled: StateFlow<Boolean> = _isSupersetModeEnabled

    private val _selectedForSuperset = MutableStateFlow<List<Exercise>>(emptyList())
    val selectedForSuperset: StateFlow<List<Exercise>> = _selectedForSuperset

    private val _supersetStarted = Channel<Long>(Channel.BUFFERED)
    val supersetStarted = _supersetStarted.receiveAsFlow()

    // Event when exercise is added to existing superset (pop back)
    private val _exerciseAddedToSuperset = Channel<Long>(Channel.BUFFERED)
    val exerciseAddedToSuperset = _exerciseAddedToSuperset.receiveAsFlow()

    // Expose add-to-superset mode for UI
    val isAddToSupersetMode: Boolean = addToSuperset

    // Maximum exercises allowed in a superset
    val maxSupersetExercises = 10

    // Single-flight and serialization guards
    private var swapInFlight = false
    private var swapCompleted = false
    private var addToSupersetInFlight = false
    private var addToSupersetCompleted = false
    private var adHocSupersetInFlight = false
    private var adHocSupersetCompleted = false
    private val starToggleMutex = Mutex()
    private val starToggleInFlightIds = mutableSetOf<Long>()

    private val recentUsageStartMs: Long = ZonedDateTime.now().minusDays(90).toInstant().toEpochMilli()

    private val allExercises: Flow<List<ExerciseUsageRow>> =
        exerciseRepository.getExercisesWithUsageStats(recentUsageStartMs)

    // Get muscle groups from database
    val availableMuscles: Flow<List<String>> = exerciseRepository.getAllMuscleGroups().map { groups ->
        groups.map { it.name }.sorted()
    }

    // Filtered exercises based on search query and selected muscles.
    val filteredExercises: Flow<List<ExerciseUsageRow>> = combine(
        allExercises,
        _searchQuery,
        _selectedMuscles,
        _sortMode
    ) { rows, query, selectedMuscles, sortMode ->
        val filtered = rows.filter { row ->
            val exercise = row.exercise
            val matchesSearch = exercise.name.contains(query, ignoreCase = true)
            val matchesMuscle = selectedMuscles.isEmpty() || exercise.targetMuscle in selectedMuscles
            matchesSearch && matchesMuscle
        }

        when (sortMode) {
            ExerciseSortMode.ALPHABETICAL -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
            )
            ExerciseSortMode.ALL_TIME_SETS -> filtered.sortedWith(
                compareByDescending<ExerciseUsageRow> { it.allTimeSetCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
            )
            ExerciseSortMode.RECENT_SETS -> filtered.sortedWith(
                compareByDescending<ExerciseUsageRow> { it.recentSetCount }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
            )
            // Most recently used first; exercises that have never been used (null
            // lastUsedMs) sort to the bottom, then alphabetically as a tiebreaker.
            ExerciseSortMode.RECENTLY_USED -> filtered.sortedWith(
                compareByDescending<ExerciseUsageRow> { it.lastUsedMs ?: Long.MIN_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
            )
        }
    }.flowOn(Dispatchers.Default)

    private val _previewPickerState = MutableStateFlow(PreviewPickerState())
    val previewPickerState: StateFlow<PreviewPickerState> = _previewPickerState.asStateFlow()

    init {
        viewModelScope.launch {
            _previewPickerState.subscriptionCount
                .map { it > 0 }
                .transformLatest { active ->
                    if (active) {
                        emit(true)
                    } else {
                        delay(PREVIEW_STOP_TIMEOUT_MILLIS)
                        emit(false)
                    }
                }
                .distinctUntilChanged()
                .collectLatest { subscribed ->
                    if (!subscribed) {
                        previewSession.value = null
                        _previewPickerState.value = PreviewPickerState()
                    } else {
                        try {
                            buildPreviewPickerFlow().collect { state ->
                                _previewPickerState.value = state
                            }
                        } finally {
                            previewSession.value = null
                            _previewPickerState.value = PreviewPickerState()
                        }
                    }
                }
        }
    }

    private fun buildPreviewPickerFlow(): Flow<PreviewPickerState> {
        val lastSetsByExerciseId = exerciseRepository.observeLastWorkoutSets()
            .map { rows -> rows.associateBy { it.set.exerciseId } }

        val boundSessionFlow: Flow<ExercisePickerSession?> = workoutRepository.getOngoingWorkoutFlow()
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { workoutId ->
                flow {
                    // Disable chips while rebinding to a different workout. Metadata changes
                    // within the same workout do not restart this flow or flash a loading state.
                    previewSession.value = null
                    emit(null)
                    val completed = workoutRepository.getLastCompletedWorkout()?.let {
                        WorkoutCompletionMarker(it.id, it.endTime?.time)
                    }
                    val session = pickerSessionState.bind(workoutId, completed)
                    previewSession.value = session
                    emit(session)
                }
            }
            .onStart { emit(null) }

        val activeFiltersFlow: Flow<PreviewPickerFilters?> = boundSessionFlow.flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                combine(session.preferences, _searchQuery) { preferences, query ->
                    PreviewPickerFilters(preferences.sortMode, query, preferences.muscles)
                }
            }
        }

        return combine(
            allExercises,
            lastSetsByExerciseId,
            activeFiltersFlow
        ) { rows, lastSetsMap, filters ->
            if (filters == null) {
                PreviewPickerState()
            } else {
                val tokens = filters.query.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                val filtered = rows.filter { row ->
                    val matchesSearch = tokens.all { row.exercise.name.contains(it, ignoreCase = true) }
                    val matchesMuscle = filters.muscles.isEmpty() || row.exercise.targetMuscle in filters.muscles
                    matchesSearch && matchesMuscle
                }
                val comparator = when (filters.sortMode) {
                    ExerciseSortMode.ALPHABETICAL -> compareBy<ExerciseUsageRow, String>(String.CASE_INSENSITIVE_ORDER) {
                        it.exercise.name
                    }
                    ExerciseSortMode.ALL_TIME_SETS -> compareByDescending<ExerciseUsageRow> { it.allTimeSetCount }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
                    ExerciseSortMode.RECENT_SETS -> compareByDescending<ExerciseUsageRow> { it.recentSetCount }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
                    ExerciseSortMode.RECENTLY_USED -> compareByDescending<ExerciseUsageRow> { it.lastUsedMs ?: Long.MIN_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.exercise.name }
                }
                PreviewPickerState(
                    isLoading = false,
                    rows = filtered.sortedWith(comparator),
                    selectedMuscles = filters.muscles,
                    sortMode = filters.sortMode,
                    lastSets = lastSetsMap
                )
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun togglePreviewMuscleFilter(muscle: String) {
        previewSession.value?.toggleMuscle(muscle)
    }

    fun clearPreviewMuscleFilters() {
        previewSession.value?.clearMuscles()
    }

    fun updatePreviewSortMode(mode: ExerciseSortMode) {
        previewSession.value?.changeSort(mode)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Accept a form result before the picker removes it from its saved state. */
    suspend fun handleCreatedExercise(exerciseId: Long) {
        val exercise = try {
            exerciseRepository.getExercise(exerciseId).first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (exercise == null) {
            _selectionMessages.send("Your exercise was saved. Select it from the library to continue.")
            return
        }

        // Wait briefly for an active picker observation to rebind its workout session;
        // if unobserved (or timed out), bind directly so this call never hangs indefinitely.
        val session = previewSession.value
            ?: if (_previewPickerState.subscriptionCount.value > 0) {
                withTimeoutOrNull(SESSION_REBIND_TIMEOUT_MILLIS) {
                    previewSession.filterNotNull().first()
                }
            } else {
                null
            }
            ?: run {
                val workoutId = workoutRepository.getOngoingWorkoutFlow().first()?.id
                val completed = workoutRepository.getLastCompletedWorkout()?.let {
                    WorkoutCompletionMarker(it.id, it.endTime?.time)
                }
                pickerSessionState.bind(workoutId, completed).also { previewSession.value = it }
            }

        val selectedMuscles = session.preferences.value.muscles
        if (selectedMuscles.isNotEmpty() && exercise.targetMuscle !in selectedMuscles) {
            session.toggleMuscle(exercise.targetMuscle)
        }
        val isSelectingForWorkout = isSwapMode || isImmediateSupersetSelection || _isSupersetModeEnabled.value
        _searchQuery.value = if (isSelectingForWorkout) "" else exercise.name

        when {
            isSwapMode -> swapPlannedExercise(exercise.id)
            isImmediateSupersetSelection -> toggleExerciseSelection(exercise)
            _isSupersetModeEnabled.value && !isExerciseSelected(exercise.id) -> toggleExerciseSelection(exercise)
        }
    }

    fun toggleMuscleFilter(muscle: String) {
        val current = _selectedMuscles.value.toMutableSet()
        if (muscle in current) {
            current.remove(muscle)
        } else {
            current.add(muscle)
        }
        _selectedMuscles.value = current
    }

    fun clearMuscleFilters() {
        _selectedMuscles.value = emptySet()
    }

    fun updateSortMode(mode: ExerciseSortMode) {
        _sortMode.value = mode
    }

    fun deleteExercise(exerciseId: Long) {
        viewModelScope.launch {
            exerciseRepository.deleteExercise(exerciseId)
        }
    }

    fun swapPlannedExercise(exerciseId: Long) {
        if (!isSwapMode || swapInFlight || swapCompleted) return
        swapInFlight = true
        viewModelScope.launch {
            try {
                when (workoutRepository.swapWorkoutPlanExercise(swapInstanceId, exerciseId)) {
                    WorkoutPlanEditResult.Updated -> {
                        swapCompleted = true
                        _swapExerciseEvent.send(SwapExerciseEvent.Success(exerciseId))
                    }
                    WorkoutPlanEditResult.DuplicateExercise ->
                        _swapExerciseEvent.send(SwapExerciseEvent.Error("That exercise is already in today's plan"))
                    WorkoutPlanEditResult.HasLoggedSets ->
                        _swapExerciseEvent.send(SwapExerciseEvent.Error("Exercises with logged sets can't be swapped"))
                    WorkoutPlanEditResult.NotFound,
                    WorkoutPlanEditResult.Removed ->
                        _swapExerciseEvent.send(SwapExerciseEvent.Error("This planned exercise is no longer available"))
                }
            } finally {
                swapInFlight = false
            }
        }
    }

    // Superset mode functions

    /**
     * Toggle superset selection mode on/off.
     * Clears selection when turning off.
     */
    fun toggleSupersetMode() {
        _isSupersetModeEnabled.value = !_isSupersetModeEnabled.value
        if (!_isSupersetModeEnabled.value) {
            clearSupersetSelection()
        }
    }

    /**
     * Toggle selection of an exercise for superset.
     * If already selected, removes it. If not, adds it (up to max).
     */
    fun toggleExerciseSelection(exercise: Exercise) {
        if (addToSuperset) {
            // Add-to-existing-superset mode: add exercise and pop back
            addExerciseToExistingSuperset(exercise)
            return
        }

        if (supersetMode && adHocParentId != null && adHocParentId > 0L) {
            // Ad-hoc mode: We have parent A, just clicked B. Start superset and go back.
            startAdHocSuperset(exercise)
            return
        }

        val current = _selectedForSuperset.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == exercise.id }

        if (existingIndex >= 0) {
            // Already selected, remove it
            current.removeAt(existingIndex)
        } else if (current.size < maxSupersetExercises) {
            // Not selected and under limit, add it
            current.add(exercise)
        } else {
            viewModelScope.launch {
                _selectionMessages.send("A superset can have up to $maxSupersetExercises exercises")
            }
        }

        _selectedForSuperset.value = current
    }

    /**
     * Check if an exercise is selected for superset.
     */
    fun isExerciseSelected(exerciseId: Long): Boolean {
        return _selectedForSuperset.value.any { it.id == exerciseId }
    }

    /**
     * Get the selection order number (1-based) for an exercise.
     * Returns null if not selected.
     */
    fun getSelectionOrder(exerciseId: Long): Int? {
        val index = _selectedForSuperset.value.indexOfFirst { it.id == exerciseId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Check if we have enough exercises selected to start a superset.
     */
    fun canStartSuperset(): Boolean {
        return _selectedForSuperset.value.size >= 2
    }

    /**
     * Start the superset with selected exercises.
     * This initializes the SupersetManager and returns the first exercise ID.
     */
    fun startSuperset(): Long {
        val exercises = _selectedForSuperset.value
        require(exercises.size >= 2) { "Need at least 2 exercises for superset" }

        supersetManager.startSuperset(exercises)

        // Clear local selection state (superset is now active in manager)
        _isSupersetModeEnabled.value = false
        _selectedForSuperset.value = emptyList()

        return exercises.first().id
    }

    /**
     * Clear superset selection without starting.
     */
    fun clearSupersetSelection() {
        _selectedForSuperset.value = emptyList()
    }

    /**
     * Toggle the starred status of an exercise.
     * Maximum of 3 exercises can be starred.
     */
    fun toggleExerciseStarred(exercise: Exercise) {
        if (!starToggleInFlightIds.add(exercise.id)) return
        viewModelScope.launch {
            try {
                starToggleMutex.withLock {
                    if (!exercise.isStarred) {
                        // Check if we already have 3 starred
                        val currentStarredCount = exerciseRepository.getStarredExercises().first().size
                        if (currentStarredCount >= 3) {
                            _selectionMessages.send("Track PRs for up to 3 exercises. Untrack one to add another.")
                            return@withLock
                        }
                    }
                    exerciseRepository.updateStarredStatus(exercise.id, !exercise.isStarred)
                }
            } finally {
                starToggleInFlightIds.remove(exercise.id)
            }
        }
    }

    private fun addExerciseToExistingSuperset(exercise: Exercise) {
        if (addToSupersetInFlight || addToSupersetCompleted) return
        addToSupersetInFlight = true
        viewModelScope.launch {
            try {
                val current = supersetManager.supersetExercises.value
                if (current.size >= maxSupersetExercises && current.none { it.id == exercise.id }) {
                    _selectionMessages.send("A superset can have up to $maxSupersetExercises exercises")
                    return@launch
                }
                supersetManager.addExercise(exercise)
                addToSupersetCompleted = true
                _exerciseAddedToSuperset.send(exercise.id)
            } finally {
                addToSupersetInFlight = false
            }
        }
    }

    private fun startAdHocSuperset(secondExercise: Exercise) {
        val parentId = adHocParentId ?: return
        if (parentId <= 0L || adHocSupersetInFlight || adHocSupersetCompleted) return
        if (secondExercise.id == parentId) {
            viewModelScope.launch {
                _selectionMessages.send("Select a different exercise to superset with")
            }
            return
        }
        adHocSupersetInFlight = true
        viewModelScope.launch {
            try {
                val firstExercise = exerciseRepository.getExercise(parentId).first()
                if (firstExercise == null) {
                    _selectionMessages.send("Starting exercise is no longer available")
                    return@launch
                }
                supersetManager.startSuperset(listOf(firstExercise, secondExercise))
                adHocSupersetCompleted = true
                // Emit the SECOND exercise ID so the UI navigates to the new one
                _supersetStarted.send(secondExercise.id)
            } finally {
                adHocSupersetInFlight = false
            }
        }
    }

    private companion object {
        const val PREVIEW_STOP_TIMEOUT_MILLIS = 5_000L
        const val SESSION_REBIND_TIMEOUT_MILLIS = 2_000L
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
