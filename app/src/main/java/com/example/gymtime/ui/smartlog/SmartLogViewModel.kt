package com.example.gymtime.ui.smartlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.repository.ExerciseRepository
import com.example.gymtime.smartlog.ExerciseMatchCandidate
import com.example.gymtime.smartlog.ExerciseMatchResult
import com.example.gymtime.smartlog.ExerciseMatcher
import com.example.gymtime.smartlog.ParsedLoggingCommand
import com.example.gymtime.smartlog.SmartLogCommandInterpreter
import com.example.gymtime.smartlog.SmartLogDraftStore
import com.example.gymtime.smartlog.SmartLogValidator
import com.example.gymtime.smartlog.ValidatedSmartLogDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SmartLogUiState(
    val input: String = "",
    val isProcessing: Boolean = false,
    val candidates: List<ExerciseMatchCandidate> = emptyList(),
    val review: ValidatedSmartLogDraft? = null,
    val createExerciseName: String? = null,
    val error: String? = null
)

data class SmartLogNavigationEvent(val exerciseId: Long, val draftToken: String)

@HiltViewModel
class SmartLogViewModel internal constructor(
    private val exerciseRepository: ExerciseRepository,
    private val interpreter: SmartLogCommandInterpreter,
    private val matcher: ExerciseMatcher,
    private val validator: SmartLogValidator,
    private val draftStore: SmartLogDraftStore,
    private val computeDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        exerciseRepository: ExerciseRepository,
        interpreter: SmartLogCommandInterpreter,
        matcher: ExerciseMatcher,
        validator: SmartLogValidator,
        draftStore: SmartLogDraftStore
    ) : this(
        exerciseRepository = exerciseRepository,
        interpreter = interpreter,
        matcher = matcher,
        validator = validator,
        draftStore = draftStore,
        computeDispatcher = Dispatchers.Default
    )

    private val _state = MutableStateFlow(SmartLogUiState())
    val state: StateFlow<SmartLogUiState> = _state.asStateFlow()

    private val _navigation = Channel<SmartLogNavigationEvent>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    private var pendingParsed: ParsedLoggingCommand? = null
    private var pendingAllowedExerciseIds: Set<Long>? = null
    private var submitJob: Job? = null
    private var isConfirmingReview: Boolean = false

    fun updateInput(value: String) {
        submitJob?.cancel()
        submitJob = null
        _state.value = _state.value.copy(
            input = value,
            isProcessing = false,
            error = null,
            candidates = emptyList(),
            review = null,
            createExerciseName = null
        )
        pendingParsed = null
        pendingAllowedExerciseIds = null
    }

    fun reportSpeechError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun submit(currentExercise: Exercise?, allowedExerciseIds: Set<Long>? = null) {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        submitJob?.cancel()
        pendingParsed = null
        pendingAllowedExerciseIds = null
        _state.value = _state.value.copy(
            isProcessing = true,
            error = null,
            candidates = emptyList(),
            review = null,
            createExerciseName = null
        )
        val newJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext[Job]
            try {
                val parsed = interpreter.extract(text)
                if (parsed == null) {
                    _state.value = _state.value.copy(
                        error = "I couldn't find a complete set. Try an exercise, weight, and reps."
                    )
                    return@launch
                }
                val createName = parsed.exerciseQuery
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                _state.value = _state.value.copy(createExerciseName = createName)
                val rows = exerciseRepository.getExercisesWithUsageStats(0L).first()
                val result = withContext(computeDispatcher) {
                    matcher.match(parsed.exerciseQuery, rows, currentExercise)
                }
                when (result) {
                    is ExerciseMatchResult.Matched -> validateSelection(
                        result.candidate.exercise,
                        parsed,
                        allowedExerciseIds
                    )
                    is ExerciseMatchResult.NeedsConfirmation -> {
                        pendingParsed = parsed
                        pendingAllowedExerciseIds = allowedExerciseIds
                        _state.value = _state.value.copy(candidates = result.candidates)
                    }
                    ExerciseMatchResult.NoMatch -> {
                        _state.value = _state.value.copy(
                            error = "No close match was found in your exercise library."
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    error = "Couldn't parse that set right now. Try again."
                )
            } finally {
                if (submitJob === currentJob) {
                    submitJob = null
                    _state.value = _state.value.copy(isProcessing = false)
                }
            }
        }
        submitJob = newJob
        newJob.start()
    }

    fun chooseExercise(exercise: Exercise) {
        val parsed = pendingParsed ?: return
        validateSelection(exercise, parsed, pendingAllowedExerciseIds)
    }

    fun confirmReview() {
        if (isConfirmingReview) return
        val draft = _state.value.review ?: return
        isConfirmingReview = true
        val token = draftStore.put(draft)
        reset()
        val sent = _navigation.trySend(SmartLogNavigationEvent(draft.exercise.id, token))
        if (!sent.isSuccess) {
            viewModelScope.launch {
                _navigation.send(SmartLogNavigationEvent(draft.exercise.id, token))
            }
        }
    }

    fun reset() {
        submitJob?.cancel()
        submitJob = null
        pendingParsed = null
        pendingAllowedExerciseIds = null
        isConfirmingReview = false
        _state.value = SmartLogUiState()
    }

    private fun validateSelection(
        exercise: Exercise,
        parsed: ParsedLoggingCommand,
        allowedExerciseIds: Set<Long>?
    ) {
        if (allowedExerciseIds != null && exercise.id !in allowedExerciseIds) {
            _state.value = _state.value.copy(
                isProcessing = false,
                candidates = emptyList(),
                error = "That exercise isn't in the active superset. Open Smart Log outside the superset to switch to it."
            )
            return
        }
        val result = validator.validate(exercise, parsed)
        _state.value = if (result.isValid) {
            _state.value.copy(
                isProcessing = false,
                candidates = emptyList(),
                review = result.draft,
                error = null
            )
        } else {
            _state.value.copy(
                isProcessing = false,
                candidates = emptyList(),
                review = null,
                error = result.errors.joinToString(" ")
            )
        }
    }
}
