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
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.SetDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.VolumeOrbRepository
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.domain.summary.WorkoutRecapFacts
import com.example.gymtime.domain.summary.WorkoutRecapFactsUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.WorkoutShareFormatter
import com.example.gymtime.util.WorkoutShareImageGenerator
import com.example.gymtime.util.WorkoutSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

@HiltViewModel
class PostWorkoutSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val exerciseDao: ExerciseDao,
    private val volumeOrbRepository: VolumeOrbRepository,
    private val shareWorkoutUseCase: ShareWorkoutUseCase,
    private val workoutShareImageGenerator: WorkoutShareImageGenerator,
    private val workoutRecapFactsUseCase: WorkoutRecapFactsUseCase,
    private val narrativeGenerator: NarrativeGenerator,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    private val _workoutStats = MutableStateFlow<WorkoutSummaryStats?>(null)
    val workoutStats: StateFlow<WorkoutSummaryStats?> = _workoutStats

    val newUiEnabled = userPreferencesRepository.newUiEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false
    )

    private var ratingDraftPresent = savedStateHandle.get<Boolean>(DRAFT_READY) == true ||
        savedStateHandle.get<Boolean>(RATING_EDITED) == true
    private var noteDraftPresent = savedStateHandle.get<Boolean>(DRAFT_READY) == true ||
        savedStateHandle.get<Boolean>(NOTE_EDITED) == true
    val selectedRating = savedStateHandle.getStateFlow<Int?>(RATING_DRAFT, null)
    val ratingNote = savedStateHandle.getStateFlow(NOTE_DRAFT, "")

    private val _feedbackState = MutableStateFlow(SummaryFeedbackState())
    val isSaving = _feedbackState.map { it.isSaving }.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    private val _loadState = MutableStateFlow(SummaryLoadState())
    private var statsJob: Job? = null
    private var hasExited = false

    private val _navigationEvent = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val _shareEvent = Channel<WorkoutSharePayload>(Channel.BUFFERED)
    val shareEvent = _shareEvent.receiveAsFlow()

    private val _copyEvent = Channel<String>(Channel.BUFFERED)
    val copyEvent = _copyEvent.receiveAsFlow()

    // Volume Orb state
    val volumeOrbState: StateFlow<VolumeOrbState> = volumeOrbRepository.orbState

    // Session contribution to weekly volume
    private val _sessionContribution = MutableStateFlow(0f)
    val sessionContribution: StateFlow<Float> = _sessionContribution
    private val _weeklyVolumeLoaded = MutableStateFlow(false)

    private val _recap = MutableStateFlow<String?>(null)
    val recap: StateFlow<String?> = _recap.asStateFlow()

    private val weeklyVolume = combine(volumeOrbState, _sessionContribution, _weeklyVolumeLoaded) { volume, contribution, loaded ->
        if (loaded) SummaryWeeklyVolume(volume.currentWeekVolume, volume.lastWeekVolume, contribution) else null
    }
    private val loadedState = combine(_workoutStats, _loadState, weeklyVolume) { stats, load, weekly ->
        PostWorkoutSummaryUiState(stats = stats, isLoading = load.isLoading, loadError = load.error, weeklyVolume = weekly)
    }
    val uiState: StateFlow<PostWorkoutSummaryUiState> = combine(
        loadedState, selectedRating, ratingNote, _feedbackState, _recap
    ) { loaded, rating, note, feedback, recapText ->
        loaded.copy(
            selectedRating = rating,
            note = note,
            hasUnsavedFeedback = rating != _loadState.value.originalRating ||
                note != _loadState.value.originalNote,
            isSaving = feedback.isSaving,
            saveError = feedback.error,
            recap = recapText
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PostWorkoutSummaryUiState())

    init {
        loadWorkoutStats()
        loadVolumeOrbData()
        loadWorkoutRecap()
    }

    private fun loadWorkoutRecap() {
        viewModelScope.launch {
            try {
                val facts = workoutRecapFactsUseCase(workoutId) ?: return@launch
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
                            allowedNames = (facts.muscles + facts.personalRecordExercises).toSet()
                        ),
                        maxOutputTokens = 60
                    )
                ).collect { result -> _recap.value = result.text }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("PostWorkoutSummaryVM", "Error building workout recap", e)
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
        add(facts.workingSetCount.toString())
        add(facts.exerciseCount.toString())
        add(facts.comparableWorkoutCount.toString())
        add("6")
    }

    private fun loadVolumeOrbData() {
        viewModelScope.launch {
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

    fun clearOrbOverflowAnimation() {
        volumeOrbRepository.clearOverflowAnimation()
    }

    private fun loadWorkoutStats() {
        statsJob?.cancel()
        _loadState.value = _loadState.value.copy(isLoading = true, error = null)
        statsJob = viewModelScope.launch {
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                val sets = setDao.getSetsForWorkout(workoutId).first()

                // Calculate duration
                val durationMs = ((workout.endTime?.time ?: System.currentTimeMillis()) - workout.startTime.time).coerceAtLeast(0)
                val minutes = (durationMs / 1000 / 60).toInt()
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                val durationString = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                // Calculate total volume (exclude warmup sets)
                val workingSets = sets.filter { it.isComplete && !it.isWarmup }
                val totalVolume = workingSets.sumOf { set ->
                    ((set.weight ?: 0f) * (set.reps ?: 0)).toDouble()
                }.toFloat()

                // Get unique exercises and muscle groups
                val exerciseIds = workingSets.map { it.exerciseId }.distinct()
                val exercises = exerciseIds.mapNotNull { id ->
                    exerciseDao.getExerciseByIdSync(id)
                }
                val muscleGroups = exercises.map { it.targetMuscle }.distinct().sorted()

                _workoutStats.value = WorkoutSummaryStats(
                    duration = durationString,
                    totalVolume = totalVolume,
                    totalSets = workingSets.size,
                    exerciseCount = exerciseIds.size,
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
                    isLoading = false, originalRating = workout.rating,
                    originalNote = workout.ratingNote.orEmpty()
                )

                Log.d("PostWorkoutSummaryVM", "Loaded stats: duration=$durationString, volume=$totalVolume, sets=${workingSets.size}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _loadState.value = _loadState.value.copy(
                    isLoading = false, error = "Couldn't load this workout. Try again."
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

    fun retryLoadStats() = loadWorkoutStats()

    fun saveAndFinish() {
        if (_feedbackState.value.isSaving || hasExited || _loadState.value.isLoading || _workoutStats.value == null) return
        // Claim synchronously so consecutive taps cannot launch competing writes.
        _feedbackState.value = SummaryFeedbackState(isSaving = true)
        val rating = selectedRating.value
        val note = ratingNote.value
        viewModelScope.launch {
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                val updatedWorkout = workout.copy(
                    rating = rating,
                    ratingNote = note.takeIf { it.isNotBlank() }
                )
                workoutDao.updateWorkout(updatedWorkout)
                hasExited = true
                _navigationEvent.send(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feedbackState.value = SummaryFeedbackState(
                    error = "Couldn't save your feedback. Your workout is saved; tap Done to retry."
                )
                Log.e("PostWorkoutSummaryVM", "Error saving rating", e)
            } finally {
                _feedbackState.value = _feedbackState.value.copy(isSaving = false)
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
        viewModelScope.launch {
            try {
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
            }
        }
    }

    fun onCopyClicked() {
        viewModelScope.launch {
            try {
                shareWorkoutUseCase.buildShareableWorkout(workoutId, _recap.value)?.let { workout ->
                    _copyEvent.send(WorkoutShareFormatter.format(workout))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feedbackState.value = _feedbackState.value.copy(error = "Couldn't copy this workout. Try again.")
                Log.e("PostWorkoutSummaryVM", "Error building share text", e)
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
