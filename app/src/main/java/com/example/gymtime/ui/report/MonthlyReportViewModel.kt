package com.example.gymtime.ui.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.ai.NarrativeFingerprint
import com.example.gymtime.ai.NarrativeGenerator
import com.example.gymtime.ai.NarrativeKind
import com.example.gymtime.ai.NarrativeRequest
import com.example.gymtime.ai.NarrativeValidationRules
import com.example.gymtime.domain.report.MonthlyReport
import com.example.gymtime.domain.report.MonthlyReportNarrative
import com.example.gymtime.domain.report.MonthlyReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val monthlyReportUseCase: MonthlyReportUseCase,
    private val narrativeGenerator: NarrativeGenerator
) : ViewModel() {
    private val deviceZoneId = ZoneId.systemDefault()

    private val _report = MutableStateFlow<MonthlyReport?>(null)
    val report: StateFlow<MonthlyReport?> = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _narrative = MutableStateFlow<String?>(null)
    val narrative: StateFlow<String?> = _narrative.asStateFlow()
    private var narrativeJob: Job? = null
    private var lastReportRefreshDate: LocalDate? = null

    init {
        load()
    }

    private fun load() {
        lastReportRefreshDate = LocalDate.now(deviceZoneId)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val loadedReport = monthlyReportUseCase()
                _report.value = loadedReport
                _narrative.value = null
                loadNarrative(loadedReport)
            } catch (e: Exception) {
                Log.e("MonthlyReportVM", "Error loading monthly report", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadNarrative(report: MonthlyReport) {
        narrativeJob?.cancel()
        narrativeJob = viewModelScope.launch {
            val facts = MonthlyReportNarrative.canonicalFacts(report)
            narrativeGenerator.generate(
                NarrativeRequest(
                    kind = NarrativeKind.MONTHLY_REPORT,
                    subjectKey = SimpleDateFormat("yyyy-MM", Locale.US).format(report.periodStart),
                    subjectStartEpochMs = report.periodStart.time,
                    sourceFingerprint = NarrativeFingerprint.sha256(facts),
                    promptVersion = MONTHLY_PROMPT_VERSION,
                    prompt = buildMonthlyPrompt(facts),
                    fallbackText = MonthlyReportNarrative.template(report),
                    validationRules = NarrativeValidationRules(
                        maxWords = 70,
                        maxSentences = 3,
                        allowedNumbers = allowedNumbers(report),
                        allowedNames = (report.topMuscles.map { it.muscle } +
                            report.undertrainedMuscles +
                            report.newPRs.map { it.exerciseName }).toSet()
                    ),
                    maxOutputTokens = 110
                )
            ).collect { result -> _narrative.value = result.text }
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
        add(report.workoutCount.toString())
        add(report.previousWorkoutCount.toString())
        add(report.totalVolume.toString())
        add(report.totalVolume.toInt().toString())
        add(report.previousTotalVolume.toString())
        add(report.previousTotalVolume.toInt().toString())
        add(report.totalWorkingSets.toString())
        add(report.previousWorkingSets.toString())
        add(report.trainingDays.toString())
        add(report.activeWeeks.toString())
        add(report.weeksInPeriod.toString())
        add(report.endOfMonthStreakDays.toString())
        add(Calendar.getInstance().apply { time = report.periodStart }.get(Calendar.YEAR).toString())
        report.volumeChangePercent?.let {
            add(it.toString())
            add(kotlin.math.abs(it).toInt().toString())
            add("${kotlin.math.abs(it).toInt()}%")
        }
        report.newPRs.forEach {
            add(it.weight.toString())
            add(it.weight.toInt().toString())
            add(it.reps.toString())
        }
    }

    private companion object {
        const val MONTHLY_PROMPT_VERSION = 1
    }
}
