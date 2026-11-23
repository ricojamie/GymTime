package com.example.gymtime.ui.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.ai.NarrativeFingerprint
import com.example.gymtime.ai.NarrativeGenerator
import com.example.gymtime.ai.NarrativeKind
import com.example.gymtime.ai.NarrativeRequest
import com.example.gymtime.ai.NarrativeValidationRules
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.MuscleGroupDao
import com.example.gymtime.domain.report.MonthlyReport
import com.example.gymtime.domain.report.MonthlyReportNarrative
import com.example.gymtime.domain.report.MonthlyReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val monthlyReportUseCase: MonthlyReportUseCase,
    private val narrativeGenerator: NarrativeGenerator,
    private val exerciseDao: ExerciseDao,
    private val muscleGroupDao: MuscleGroupDao
) : ViewModel() {
    private val deviceZoneId = ZoneId.systemDefault()

    private val _report = MutableStateFlow<MonthlyReport?>(null)
    val report: StateFlow<MonthlyReport?> = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _narrative = MutableStateFlow<String?>(null)
    val narrative: StateFlow<String?> = _narrative.asStateFlow()

    private var loadJob: Job? = null
    private var narrativeJob: Job? = null
    private var lastReportRefreshDate: LocalDate? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        narrativeJob?.cancel()
        lastReportRefreshDate = LocalDate.now(deviceZoneId)
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val loadedReport = monthlyReportUseCase()
                ensureActive()
                _report.value = loadedReport
                _narrative.value = null
                loadNarrative(loadedReport)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MonthlyReportVM", "Error loading monthly report", e)
                if (isActive) {
                    _error.value = "Couldn't load your monthly report. Please try again."
                }
            } finally {
                if (isActive) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun loadNarrative(report: MonthlyReport) {
        narrativeJob?.cancel()
        narrativeJob = viewModelScope.launch {
            val fallbackText = MonthlyReportNarrative.template(report)
            try {
                val facts = MonthlyReportNarrative.canonicalFacts(report)
                val exerciseNames = exerciseDao.getAllExercisesSync().map { it.name }
                val muscleNames = muscleGroupDao.getAllMuscleGroupNames()
                val knownNames = buildSet {
                    addAll(exerciseNames)
                    addAll(muscleNames)
                    if (muscleNames.any { it.equals("Core", ignoreCase = true) }) add("Abs")
                    if (muscleNames.any { it.equals("Abs", ignoreCase = true) }) add("Core")
                }
                val subjectKey = Instant.ofEpochMilli(report.periodStart.time)
                    .atZone(deviceZoneId)
                    .format(SUBJECT_KEY_FORMATTER)

                narrativeGenerator.generate(
                    NarrativeRequest(
                        kind = NarrativeKind.MONTHLY_REPORT,
                        subjectKey = subjectKey,
                        subjectStartEpochMs = report.periodStart.time,
                        sourceFingerprint = NarrativeFingerprint.sha256(facts),
                        promptVersion = MONTHLY_PROMPT_VERSION,
                        prompt = buildMonthlyPrompt(facts),
                        fallbackText = fallbackText,
                        validationRules = NarrativeValidationRules(
                            maxWords = 70,
                            maxSentences = 3,
                            allowedNumbers = allowedNumbers(report),
                            allowedNames = (
                                report.topMuscles.map { it.muscle } +
                                    report.undertrainedMuscles +
                                    report.newPRs.map { it.exerciseName }
                                ).toSet(),
                            knownNames = knownNames
                        ),
                        maxOutputTokens = 110
                    )
                ).collect { result ->
                    _narrative.value = result.text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MonthlyReportVM", "Error loading monthly narrative", e)
                if (isActive && _narrative.value == null) {
                    _narrative.value = fallbackText
                }
            }
        }
    }

    fun refreshIfDateChanged() {
        val today = LocalDate.now(deviceZoneId)
        if (lastReportRefreshDate != today) {
            load()
        }
    }

    private fun buildMonthlyPrompt(facts: String): String = """
        ## FACTS
        $facts
        ## TASK
        Write a concise 2-3 sentence monthly strength-training recap in plain English.
        Mention only facts supplied above. Prioritize consistency, volume change, PRs, and a lagging muscle when present.
        Do not give medical or workout-programming advice. Do not invent labels, numbers, or achievements.
        Output only the recap, with at most 70 words.
    """.trimIndent()

    private fun allowedNumbers(report: MonthlyReport): Set<String> = buildSet {
        fun addFiniteFloat(value: Float) {
            if (!value.isFinite()) return
            add(value.toString())
            add(value.toInt().toString())
            add(value.roundToInt().toString())
            add(String.format(Locale.US, "%.1f", value))
        }

        add(report.workoutCount.toString())
        add(report.previousWorkoutCount.toString())
        addFiniteFloat(report.totalVolume)
        addFiniteFloat(report.previousTotalVolume)
        add(report.totalWorkingSets.toString())
        add(report.previousWorkingSets.toString())
        add(report.exerciseCount.toString())
        add(report.trainingDays.toString())
        add(report.activeWeeks.toString())
        add(report.weeksInPeriod.toString())
        add(report.endOfMonthStreakDays.toString())
        add(
            Instant.ofEpochMilli(report.periodStart.time)
                .atZone(deviceZoneId)
                .year
                .toString()
        )
        report.volumeChangePercent?.takeIf { it.isFinite() }?.let { change ->
            val absChange = abs(change)
            addFiniteFloat(change)
            addFiniteFloat(absChange)
            add("${absChange.toInt()}%")
            add("${absChange.roundToInt()}%")
            add("${String.format(Locale.US, "%.1f", absChange)}%")
        }
        report.averageRating?.let { addFiniteFloat(it) }
        report.topMuscles.forEach { add(it.setCount.toString()) }
        report.newPRs.forEach { pr ->
            addFiniteFloat(pr.weight)
            add(pr.reps.toString())
        }
    }

    private companion object {
        const val MONTHLY_PROMPT_VERSION = 1
        val SUBJECT_KEY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
    }
}
