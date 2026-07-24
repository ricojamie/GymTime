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
import com.example.gymtime.domain.share.ShareWorkoutUseCase
import com.example.gymtime.domain.summary.WorkoutRecapFacts
import com.example.gymtime.domain.summary.WorkoutRecapFactsUseCase
import com.example.gymtime.util.ShareImagePalette
import com.example.gymtime.util.WorkoutShareFormatter
import com.example.gymtime.util.WorkoutShareImageGenerator
import com.example.gymtime.util.WorkoutSharePayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutSummaryStats(
    val duration: String,
    val totalVolume: Float,
    val totalSets: Int,
    val exerciseCount: Int,
    val muscleGroups: List<String>
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
    private val narrativeGenerator: NarrativeGenerator
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    private val _workoutStats = MutableStateFlow<WorkoutSummaryStats?>(null)
    val workoutStats: StateFlow<WorkoutSummaryStats?> = _workoutStats

    private val _selectedRating = MutableStateFlow<Int?>(null)
    val selectedRating: StateFlow<Int?> = _selectedRating

    private val _ratingNote = MutableStateFlow("")
    val ratingNote: StateFlow<String> = _ratingNote

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

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

    private val _recap = MutableStateFlow<String?>(null)
    val recap: StateFlow<String?> = _recap.asStateFlow()

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
            // Refresh orb state
            volumeOrbRepository.refresh()
            // Get this session's contribution
            _sessionContribution.value = volumeOrbRepository.getSessionContribution(workoutId)
        }
    }

    fun clearOrbOverflowAnimation() {
        volumeOrbRepository.clearOverflowAnimation()
    }

    private fun loadWorkoutStats() {
        viewModelScope.launch {
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                val sets = setDao.getSetsForWorkout(workoutId).first()

                // Calculate duration
                val durationMs = (workout.endTime?.time ?: System.currentTimeMillis()) - workout.startTime.time
                val minutes = (durationMs / 1000 / 60).toInt()
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                val durationString = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                // Calculate total volume (exclude warmup sets)
                val workingSets = sets.filter { !it.isWarmup }
                val totalVolume = workingSets.sumOf { set ->
                    ((set.weight ?: 0f) * (set.reps ?: 0)).toDouble()
                }.toFloat()

                // Get unique exercises and muscle groups
                val exerciseIds = sets.map { it.exerciseId }.distinct()
                val exercises = exerciseIds.mapNotNull { id ->
                    exerciseDao.getExerciseByIdSync(id)
                }
                val muscleGroups = exercises.map { it.targetMuscle }.distinct().sorted()

                _workoutStats.value = WorkoutSummaryStats(
                    duration = durationString,
                    totalVolume = totalVolume,
                    totalSets = workingSets.size,
                    exerciseCount = exerciseIds.size,
                    muscleGroups = muscleGroups
                )

                Log.d("PostWorkoutSummaryVM", "Loaded stats: duration=$durationString, volume=$totalVolume, sets=${workingSets.size}")
            } catch (e: Exception) {
                Log.e("PostWorkoutSummaryVM", "Error loading workout stats", e)
            }
        }
    }

    fun updateRating(rating: Int) {
        _selectedRating.value = if (_selectedRating.value == rating) null else rating
    }

    fun updateRatingNote(note: String) {
        _ratingNote.value = note
    }

    fun saveAndFinish() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val workout = workoutDao.getWorkoutById(workoutId).first()
                val updatedWorkout = workout.copy(
                    rating = _selectedRating.value,
                    ratingNote = _ratingNote.value.takeIf { it.isNotBlank() }
                )
                workoutDao.updateWorkout(updatedWorkout)
                Log.d("PostWorkoutSummaryVM", "Saved rating: ${_selectedRating.value}")
                _navigationEvent.send(Unit)
            } catch (e: Exception) {
                Log.e("PostWorkoutSummaryVM", "Error saving rating", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun skipAndFinish() {
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
                Log.e("PostWorkoutSummaryVM", "Error building share text", e)
            }
        }
    }

    private companion object {
        const val WORKOUT_RECAP_PROMPT_VERSION = 1
    }
}
