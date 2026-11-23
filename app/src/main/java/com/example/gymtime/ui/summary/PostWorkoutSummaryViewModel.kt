package com.example.gymtime.ui.summary

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.ai.NarrativeFingerprint
import com.example.gymtime.ai.NarrativeGenerator
import com.example.gymtime.ai.NarrativeKind
import com.example.gymtime.ai.NarrativeRequest
import com.example.gymtime.ai.NarrativeValidationRules
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.domain.summary.WorkoutRecapFacts
import com.example.gymtime.domain.summary.WorkoutRecapFactsUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.WorkoutShareFormatter
import com.example.gymtime.util.WorkoutShareImageGenerator
import com.example.gymtime.util.WorkoutSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class WorkoutSummaryStats(
    val duration: String,
    val totalVolume: Float,
    val totalSets: Int,
    val exerciseCount: Int,
    val muscleGroups: List<String>,
    val workoutName: String = "Workout",
    val workoutNote: String? = null
)

data class SummaryFeedbackState(
    val isSaving: Boolean = false,
    val error: String? = null
)

data class SummaryWeeklyVolume(
    val thisWeek: Float,
    val lastWeek: Float,
    val sessionContribution: Float
)

data class PostWorkoutSummaryUiState(
    val stats: WorkoutSummaryStats? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val selectedRating: Int? = null,
    val note: String = "",
    val hasUnsavedFeedback: Boolean = false,
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val saveError: String? = null,
    val recap: String? = null,
    val weeklyVolume: SummaryWeeklyVolume? = null
)

private data class SummaryLoadState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val originalRating: Int? = null,
    val originalNote: String = ""
)

private data class LoadedSummary(
    val stats: WorkoutSummaryStats?,
    val isLoading: Boolean,
    val loadError: String?,
    val originalRating: Int?,
    val originalNote: String,
    val weeklyVolume: SummaryWeeklyVolume?
)

@HiltViewModel
class PostWorkoutSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: MuscleGroupDao,
    private val volumeOrbRepository: VolumeOrbRepository,
    private val shareWorkoutUseCase: ShareWorkoutUseCase,
    private val workoutShareImageGenerator: WorkoutShareImageGenerator,
    private val workoutRecapFactsUseCase: WorkoutRecapFactsUseCase,
    private val narrativeGenerator: NarrativeGenerator
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    private val _workoutStats = MutableStateFlow<WorkoutSummaryStats?>(null)
    val workoutStats: StateFlow<WorkoutSummaryStats?> = _workoutStats.asStateFlow()

    private var ratingDraftPresent = savedStateHandle.get<Boolean>(DRAFT_READY) == true ||
        savedStateHandle.get<Boolean>(RATING_EDITED) == true
    private var noteDraftPresent = savedStateHandle.get<Boolean>(DRAFT_READY) == true ||
        savedStateHandle.get<Boolean>(NOTE_EDITED) == true
    val selectedRating: StateFlow<Int?> = savedStateHandle.getStateFlow(RATING_DRAFT, null)
    val ratingNote: StateFlow<String> = savedStateHandle.getStateFlow(NOTE_DRAFT, "")

    private val _feedbackState = MutableStateFlow(SummaryFeedbackState())
    private val _isExporting = MutableStateFlow(false)
    private val _loadState = MutableStateFlow(SummaryLoadState())
    private var statsJob: Job? = null
    private var orbJob: Job? = null
    private var recapJob: Job? = null
    private var initialRecapReady = CompletableDeferred<Unit>()
    private var hasExited = false

    private val _navigationEvent = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val _shareEvent = Channel<WorkoutSharePayload>(Channel.BUFFERED)
    val shareEvent = _shareEvent.receiveAsFlow()

    private val _copyEvent = Channel<String>(Channel.BUFFERED)
    val copyEvent = _copyEvent.receiveAsFlow()

    private val _sessionContribution = MutableStateFlow(0f)
    val sessionContribution: StateFlow<Float> = _sessionContribution.asStateFlow()
    private val _weeklyVolumeLoaded = MutableStateFlow(false)

    private val _recap = MutableStateFlow<String?>(null)

    private val weeklyVolume = combine(
        volumeOrbRepository.orbState,
        _sessionContribution,
        _weeklyVolumeLoaded
    ) { volume, contribution, loaded ->
        if (loaded) SummaryWeeklyVolume(volume.currentWeekVolume, volume.lastWeekVolume, contribution) else null
    }

    private val loadedState = combine(_workoutStats, _loadState, weeklyVolume) { stats, load, weekly ->
        LoadedSummary(
            stats = stats,
            isLoading = load.isLoading,
            loadError = load.error,
            originalRating = load.originalRating,
            originalNote = load.originalNote,
            weeklyVolume = weekly
        )
    }

    private val interactionState = combine(_feedbackState, _isExporting, _recap) { feedback, exporting, recapText ->
        Triple(feedback, exporting, recapText)
    }

    val uiState: StateFlow<PostWorkoutSummaryUiState> = combine(
        loadedState,
        selectedRating,
        ratingNote,
        interactionState
    ) { loaded, rating, note, (feedback, exporting, recapText) ->
        PostWorkoutSummaryUiState(
            stats = loaded.stats,
            isLoading = loaded.isLoading,
            loadError = loaded.loadError,
            selectedRating = rating,
            note = note,
            hasUnsavedFeedback = loaded.stats != null && !loaded.isLoading &&
                (rating != loaded.originalRating || note != loaded.originalNote),
            isSaving = feedback.isSaving,
            isExporting = exporting,
            saveError = feedback.error,
            recap = recapText,
            weeklyVolume = loaded.weeklyVolume
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PostWorkoutSummaryUiState())

    init {
        loadWorkoutStats()
        loadVolumeOrbData()
        loadWorkoutRecap()
    }

    private fun loadWorkoutRecap() {
        recapJob?.cancel()
        val ready = CompletableDeferred<Unit>()
        initialRecapReady = ready
        recapJob = viewModelScope.launch {
            try {
                val facts = workoutRecapFactsUseCase(workoutId) ?: return@launch
                val allExercises = exerciseDao.getAllExercisesSync()
                val allMuscles = muscleGroupDao.getAllMuscleGroupNames()
                val knownNames = buildSet {
                    addAll(allMuscles)
                    allExercises.forEach { exercise ->
                        add(exercise.name)
                        add(exercise.targetMuscle)
                    }
                    addAll(facts.muscles)
                    addAll(facts.personalRecordExercises)
                    facts.leadingMuscle?.let(::add)
                }
                val canonicalFacts = facts.canonicalFacts()
                narrativeGenerator.generate(
                    NarrativeRequest(
                        kind = NarrativeKind.POST_WORKOUT,
                        subjectKey = workoutId.toString(),
                        subjectStartEpochMs = null,
                        workoutId = workoutId,
                        sourceFingerprint = NarrativeFingerprint.sha256(canonicalFacts),
                        promptVersion = WORKOUT_RECAP_PROMPT_VERSION,
                        prompt = buildWorkoutRecapPrompt(canonicalFacts),
                        fallbackText = facts.templateNarrative(),
                        validationRules = NarrativeValidationRules(
                            maxWords = 30,
                            maxSentences = 1,
                            allowedNumbers = allowedNumbers(facts),
                            allowedNames = (facts.muscles + facts.personalRecordExercises).toSet(),
                            knownNames = knownNames
                        ),
                        maxOutputTokens = 60
                    )
                ).collect { result ->
                    _recap.value = result.text
                    ready.complete(Unit)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("PostWorkoutSummaryVM", "Error building workout recap", e)
            } finally {
                ready.complete(Unit)
            }
        }
    }

    private fun buildWorkoutRecapPrompt(facts: String): String = """
        ## FACTS
        $facts
        ## TASK
        Write one concise, shareable sentence recapping this workout.
        Lead with a PR or a verified six-week best when present; otherwise summarize the session plainly.
        Use only supplied facts. Do not invent exercise labels, numbers, comparisons, or advice.
        Output only one sentence with at most 30 words.
    """.trimIndent()

    private fun allowedNumbers(facts: WorkoutRecapFacts): Set<String> = buildSet {
        add(facts.totalVolume.toString())
        add(facts.totalVolume.toInt().toString())
        if (facts.totalVolume.isFinite() && facts.totalVolume % 1f != 0f) {
            add(String.format(Locale.US, "%.1f", facts.totalVolume))
        }
        add(facts.workingSetCount.toString())
        add(facts.exerciseCount.toString())
        add(facts.comparableWorkoutCount.toString())
        add("6")
    }

    private fun loadVolumeOrbData() {
        orbJob?.cancel()
        orbJob = viewModelScope.launch {
            try {
                volumeOrbRepository.refresh()
                _sessionContribution.value = volumeOrbRepository.getSessionContribution(workoutId)
                _weeklyVolumeLoaded.value = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("PostWorkoutSummaryVM", "Error loading weekly volume", e)
            }
        }
    }

    private fun loadWorkoutStats() {
        statsJob?.cancel()
        _loadState.value = _loadState.value.copy(isLoading = true, error = null)
        statsJob = viewModelScope.launch {
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                    ?: throw IllegalStateException("This workout is no longer available.")
                val setInfos = setDao.getWorkoutSetsWithExercises(workoutId)

                val durationMs = ((workout.endTime?.time ?: System.currentTimeMillis()) - workout.startTime.time)
                    .coerceAtLeast(0)
                val minutes = (durationMs / 1000 / 60).toInt()
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                val durationString = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                val workingSets = setInfos.filter {
                    it.set.isComplete && !it.set.isWarmup && !it.targetMuscle.isWarmupMuscleGroup()
                }
                val totalVolume = workingSets.sumOf { info ->
                    ((info.set.weight ?: 0f) * (info.set.reps ?: 0)).toDouble()
                }.toFloat()

                val exerciseCount = workingSets.asSequence().map { it.set.exerciseId }.distinct().count()
                val muscleGroups = workingSets.asSequence().map { it.targetMuscle }.distinct().sorted().toList()

                _workoutStats.value = WorkoutSummaryStats(
                    duration = durationString,
                    totalVolume = totalVolume,
                    totalSets = workingSets.size,
                    exerciseCount = exerciseCount,
                    muscleGroups = muscleGroups,
                    workoutName = workout.name?.takeIf { it.isNotBlank() } ?: "Workout",
                    workoutNote = workout.note
                )

                // Restored drafts win over database feedback, including an explicitly cleared rating.
                if (!ratingDraftPresent) savedStateHandle[RATING_DRAFT] = workout.rating
                if (!noteDraftPresent) savedStateHandle[NOTE_DRAFT] = workout.ratingNote.orEmpty()
                ratingDraftPresent = true
                noteDraftPresent = true
                savedStateHandle[DRAFT_READY] = true
                _loadState.value = SummaryLoadState(
                    isLoading = false,
                    originalRating = workout.rating,
                    originalNote = workout.ratingNote.orEmpty()
                )

                Log.d("PostWorkoutSummaryVM", "Loaded stats: duration=$durationString, volume=$totalVolume, sets=${workingSets.size}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _workoutStats.value = null
                _loadState.value = _loadState.value.copy(
                    isLoading = false,
                    error = "Couldn't load this workout. Try again."
                )
                Log.e("PostWorkoutSummaryVM", "Error loading workout stats", e)
            }
        }
    }

    fun updateRating(rating: Int) {
        if (_feedbackState.value.isSaving || hasExited || rating !in 1..5) return
        ratingDraftPresent = true
        savedStateHandle[RATING_EDITED] = true
        savedStateHandle[RATING_DRAFT] = if (selectedRating.value == rating) null else rating
        _feedbackState.value = SummaryFeedbackState()
    }

    fun updateRatingNote(note: String) {
        if (_feedbackState.value.isSaving || hasExited) return
        noteDraftPresent = true
        savedStateHandle[NOTE_EDITED] = true
        savedStateHandle[NOTE_DRAFT] = note.take(MAX_NOTE_LENGTH)
        _feedbackState.value = SummaryFeedbackState()
    }

    fun retryLoadStats() {
        loadWorkoutStats()
        loadVolumeOrbData()
        loadWorkoutRecap()
    }

    fun saveAndFinish() {
        if (_feedbackState.value.isSaving || hasExited || _loadState.value.isLoading || _workoutStats.value == null) return
        // Claim synchronously so consecutive taps cannot launch competing writes.
        _feedbackState.value = SummaryFeedbackState(isSaving = true)
        val rating = selectedRating.value
        val note = ratingNote.value
        viewModelScope.launch {
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                    ?: throw IllegalStateException("This workout is no longer available.")
                val updatedWorkout = workout.copy(
                    rating = rating,
                    ratingNote = note.takeIf { it.isNotBlank() }
                )
                workoutDao.updateWorkout(updatedWorkout)
                hasExited = true
                _navigationEvent.send(Unit)
            } catch (e: CancellationException) {
                _feedbackState.value = _feedbackState.value.copy(isSaving = false)
                throw e
            } catch (e: Exception) {
                _feedbackState.value = SummaryFeedbackState(
                    isSaving = false,
                    error = "Couldn't save your feedback. Your workout is saved; tap Done to retry."
                )
                Log.e("PostWorkoutSummaryVM", "Error saving rating", e)
            }
        }
    }

    fun skipAndFinish() {
        if (_feedbackState.value.isSaving || hasExited) return
        hasExited = true
        viewModelScope.launch {
            _navigationEvent.send(Unit)
        }
    }

    fun onShareClicked(palette: ShareImagePalette) {
        if (_isExporting.value || _feedbackState.value.isSaving || hasExited || _loadState.value.isLoading || _workoutStats.value == null) return
        _isExporting.value = true
        viewModelScope.launch {
            try {
                initialRecapReady.await()
                shareWorkoutUseCase.buildShareableWorkout(workoutId, _recap.value)?.let { workout ->
                    _shareEvent.send(
                        WorkoutSharePayload(
                            imageUri = workoutShareImageGenerator.generate(workout, palette),
                            text = WorkoutShareFormatter.format(workout)
                        )
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feedbackState.value = _feedbackState.value.copy(error = "Couldn't share this workout. Try again.")
                Log.e("PostWorkoutSummaryVM", "Error building share text", e)
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun onCopyClicked() {
        if (_isExporting.value || _feedbackState.value.isSaving || hasExited || _loadState.value.isLoading || _workoutStats.value == null) return
        _isExporting.value = true
        viewModelScope.launch {
            try {
                initialRecapReady.await()
                shareWorkoutUseCase.buildShareableWorkout(workoutId, _recap.value)?.let { workout ->
                    _copyEvent.send(WorkoutShareFormatter.format(workout))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feedbackState.value = _feedbackState.value.copy(error = "Couldn't copy this workout. Try again.")
                Log.e("PostWorkoutSummaryVM", "Error building share text", e)
            } finally {
                _isExporting.value = false
            }
        }
    }

    private companion object {
        const val WORKOUT_RECAP_PROMPT_VERSION = 1
        const val RATING_DRAFT = "summary_rating_draft"
        const val NOTE_DRAFT = "summary_note_draft"
        const val DRAFT_READY = "summary_draft_ready"
        const val RATING_EDITED = "summary_rating_edited"
        const val NOTE_EDITED = "summary_note_edited"
        const val MAX_NOTE_LENGTH = 2_000
    }
}
