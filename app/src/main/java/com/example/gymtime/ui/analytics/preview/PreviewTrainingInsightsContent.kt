package com.example.gymtime.ui.analytics.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.domain.analytics.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Durable selections arrive as values; only temporary disclosure mechanics live here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewTrainingInsightsContent(
    state: TrainingInsightsUiState,
    onWindowChange: (Int) -> Unit,
    onExerciseChange: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleFilterChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onOpenHome: () -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showMethods by rememberSaveable { mutableStateOf(false) }
    var detailExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    val dashboard = state.dashboard
    val exercise = dashboard?.exercises?.firstOrNull { it.id == state.selectedExerciseId }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Training insights", modifier = Modifier.weight(1f).align(Alignment.CenterVertically).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { showMethods = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("How to read this") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(28 to "4 weeks", 84 to "12 weeks").forEach { (days, label) ->
                    FilterChip(selected = state.windowDays == days, onClick = { onWindowChange(days) },
                        label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        state.error?.let { message -> item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message)
                    TextButton(onRetry, Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                }
            }
        } }
        if (state.isLoading) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (dashboard == null) "Reading your workout history…" else "Updating your insights…",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (dashboard != null && dashboard.period.windowDays != state.windowDays) item {
            Text("Showing the last loaded ${dashboard.period.windowDays / 7}-week period while the requested ${state.windowDays / 7}-week view ${if (state.error != null) "is unavailable. Tap Try again to refresh." else "loads."}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dashboard != null) {
            item {
                if (dashboard.cadence.totalCompletedWorkouts == 0) {
                    InsightPanel(highlighted = true) {
                        InsightSectionTitle("Your next workout starts the story", "Completed workouts with working sets will appear here. Warmups and unfinished sets stay out of the comparisons.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InsightPrimaryAction("Go to Home", onOpenHome)
                            TextButton(onLibrary, Modifier.heightIn(min = 48.dp)) { Text("Explore exercises") }
                        }
                    }
                } else InsightOverview(dashboard)
            }
            items(dashboard.observations.take(2)) { observation ->
                val openEvidence: () -> Unit = {
                    if (observation.exerciseId != null) detailExerciseId = observation.exerciseId
                    else if (observation.muscle != null) detailMuscle = observation.muscle
                }
                if (observation.exerciseId != null || observation.muscle != null) Surface(
                    onClick = openEvidence, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(observation.text, style = MaterialTheme.typography.bodyMedium)
                        Text("See the sets behind this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                LiftProgressPanel(exercise, onChoose = { showPicker = true },
                    onEvidence = { detailExerciseId = exercise?.id })
            }
            item {
                InsightPanel {
                    InsightSectionTitle("Body parts", "Completed working sets · current period / previous equal period")
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dashboard.muscles.forEach { muscle ->
                            Surface(onClick = { detailMuscle = muscle.name }, modifier = Modifier.weight(1f).heightIn(min = 104.dp),
                                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(muscle.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${muscle.currentWorkingSets} sets / ${muscle.previousWorkingSets} prior", style = MaterialTheme.typography.bodyMedium)
                                    Text(lastLoggedLabel(muscle.daysSinceLastLogged), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text("Tap a body part for its exercises and history. One primary muscle per exercise; this is a log count, not recovery or muscle growth.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { RhythmPanel(dashboard, onHistory) }
            item {
                InsightPanel {
                    InsightSectionTitle("Recent records", "First observations are benchmarks. A record must improve on earlier completed working sets.")
                    val recordSets = dashboard.recentRecords.distinctBy { it.set.id }
                    if (recordSets.isEmpty()) {
                        Text("No new record events in this period. Your earlier bests are available in each exercise’s history.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showPicker = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose an exercise") }
                    } else {
                        recordSets.take(6).forEach { event ->
                            RecordEventRow(event, dashboard.exercises.firstOrNull { it.id == event.exerciseId },
                                onClick = { detailExerciseId = event.exerciseId })
                        }
                        Text("Latest ${minOf(6, recordSets.size)} sets with record events in this period · ties are not new records.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showPicker && dashboard != null) InsightExercisePicker(
        exercises = dashboard.exercises, muscles = dashboard.muscles.map { it.name },
        selectedExerciseId = state.selectedExerciseId, query = state.exerciseQuery, muscleFilter = state.muscleFilter,
        onQueryChange = onQueryChange, onMuscleChange = onMuscleFilterChange,
        onSelect = { onExerciseChange(it); showPicker = false }, onDismiss = { showPicker = false }
    )
    dashboard?.exercises?.firstOrNull { it.id == detailExerciseId }?.let { selected ->
        InsightExerciseEvidenceSheet(selected, onDismiss = { detailExerciseId = null })
    }
    dashboard?.muscles?.firstOrNull { it.name == detailMuscle }?.let { muscle ->
        InsightMuscleEvidenceSheet(muscle, dashboard.period,
            onExercise = { detailMuscle = null; detailExerciseId = it }, onDismiss = { detailMuscle = null })
    }
    if (showMethods) InsightMethodsSheet(dashboard?.period, onDismiss = { showMethods = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightOverview(dashboard: TrainingInsightsDashboard) {
    InsightPanel(highlighted = true) {
        InsightSectionTitle("Your training, in view", periodLabel(dashboard.period))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightNumber("${dashboard.cadence.currentWorkouts}", "completed workouts")
            InsightNumber("${dashboard.cadence.currentWorkingSets}", "working sets")
        }
        if (dashboard.cadence.currentWorkouts == 0) Text("A quiet period in your log. Earlier workouts are still in your history.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsightNumber(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftProgressPanel(exercise: TrainingInsightExercise?, onChoose: () -> Unit, onEvidence: () -> Unit) {
    var showValues by rememberSaveable(exercise?.id) { mutableStateOf(false) }
    var showMetricHelp by rememberSaveable(exercise?.id) { mutableStateOf(false) }
    InsightPanel {
        InsightSectionTitle("Lift progress", "Compare an exercise with its own history")
        OutlinedButton(onChoose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise?.name ?: "Choose an exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(exercise?.let { "${it.muscle} · tap to change" } ?: "Search or filter by body part", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (exercise != null) {
            val progress = exercise.progress
            progress.latestBestSet?.let { set ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Latest best working set · ${insightDate(set.timestampMs)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(insightSetLabel(set, exercise.logType), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (exercise.totalWorkingSets == 0) Text("No completed working sets for this exercise yet. Choose another lift to explore its history.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            progress.metric?.let { metric ->
                Text(metric.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX) {
                    val comparableSet = progress.latestComparableSet
                    val comparableValue = progress.latestComparableValue
                    if (comparableSet != null && comparableValue != null) Text(
                        "Latest eligible estimate: ${insightNumber(comparableValue)} ${metric.unit} · ${insightDate(comparableSet.timestampMs)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (progress.chartPoints.isEmpty()) {
                    Text("No ${if (metric.kind == TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX) "eligible estimated-strength" else "${metric.label.lowercase()}"} session points in this loaded period.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InsightLineChart(progress.chartPoints.map { InsightChartDot(it.timestampMs, it.value) }, metric.unit)
                    Text("${progress.chartSessionCount} comparable workouts · ${progress.chartPoints.size} ${if (progress.chartPoints.size < progress.chartSessionCount) "sampled " else ""}dots in this period",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ComparisonText(progress.comparison, metric)
                TextButton(onClick = { showMetricHelp = !showMetricHelp }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showMetricHelp) "Hide metric explanation" else "About this metric")
                }
                if (showMetricHelp) Text(progress.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress.chartPoints.isNotEmpty()) {
                    TextButton(onClick = { showValues = !showValues }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showValues) "Hide chart values" else "Chart values")
                    }
                    if (showValues) progress.chartPoints.forEach { point ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${insightDate(point.timestampMs)} · ${insightNumber(point.value)} ${metric.unit}", style = MaterialTheme.typography.bodyMedium)
                            Text(insightSetLabel(point.set, exercise.logType), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onEvidence, Modifier.heightIn(min = 48.dp)) { Text("Full history & records") }
        }
    }
}

@Composable
private fun ComparisonText(comparison: TrainingInsightComparison, metric: TrainingInsightMetric) {
    if (comparison.status == TrainingInsightProgressStatus.NO_COMPARABLE_DATA && metric.kind != TrainingInsightMetricKind.ESTIMATED_ONE_REP_MAX) {
        Text("Logged ${metric.label.lowercase()} history · no estimated-strength comparison for this metric.",
            style = MaterialTheme.typography.bodyMedium)
        return
    }
    val description = when (comparison.status) {
        TrainingInsightProgressStatus.READY -> {
            val change = comparison.percentChange?.let {
                when {
                    comparison.direction == TrainingInsightDirection.STABLE -> "Similar (less than 2%)"
                    it > 0.0 -> "${insightNumber(abs(it))}% higher"
                    it < 0.0 -> "${insightNumber(abs(it))}% lower"
                    else -> "Unchanged"
                }
            } ?: "Not enough data for a percentage"
            "Recent vs earlier: $change. " +
                "${comparison.recentSessionCount} recent sessions vs ${comparison.baselineSessionCount} earlier sessions."
        }
        TrainingInsightProgressStatus.STALE -> "The last comparable session is over 42 days old, so a current comparison is withheld. Your history remains available."
        TrainingInsightProgressStatus.BUILDING_BASELINE -> "Building a comparison · ${comparison.recentSessionCount + comparison.baselineSessionCount} comparable sessions so far. At least 4 are needed."
        TrainingInsightProgressStatus.NO_COMPARABLE_DATA -> "No comparable sets yet for this metric. Your actual logged sets remain in history."
    }
    Text(description, style = MaterialTheme.typography.bodyMedium)
    if (comparison.status == TrainingInsightProgressStatus.READY) {
        val recent = comparison.recentMedian
        val earlier = comparison.baselineMedian
        if (recent != null && earlier != null) Text("Median session best: ${insightNumber(recent)} ${metric.unit} recent · ${insightNumber(earlier)} ${metric.unit} earlier",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RhythmPanel(dashboard: TrainingInsightsDashboard, onHistory: () -> Unit) {
    var showWeeks by rememberSaveable { mutableStateOf(false) }
    val cadence = dashboard.cadence
    InsightPanel {
        InsightSectionTitle("Training rhythm", "Completed workouts, counted once each")
        Text("${cadence.thisWeekWorkouts} this week so far", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${cadence.currentWorkouts} in this period · ${cadence.previousWorkouts} in the previous ${dashboard.period.windowDays} days",
            style = MaterialTheme.typography.bodyMedium)
        Text("${cadence.currentActiveDays} days with a completed workout · ${cadence.previousActiveDays} in the previous period",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        InsightWeeklyBars(cadence.weeks.map { it.workouts })
        Text("${cadence.weeks.firstOrNull()?.let { "${shortDate(it.startDate)}–${shortDate(it.endDate)}${if (it.isPartialWeek) " · partial" else ""}" }.orEmpty()} → ${cadence.weeks.lastOrNull()?.let { "${shortDate(it.startDate)}–${shortDate(it.endDate)} · so far" }.orEmpty()}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showWeeks = !showWeeks }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (showWeeks) "Hide weekly counts" else "Exact weekly counts")
        }
        if (showWeeks) cadence.weeks.forEach { week ->
            Text("${shortDate(week.startDate)}–${shortDate(week.endDate)}${when { week.isCurrentWeek -> " · so far"; week.isPartialWeek -> " · partial"; else -> "" }}: ${week.workouts} workouts",
                style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onHistory, Modifier.heightIn(min = 48.dp)) { Text("Open workout history") }
    }
}

@Composable
private fun RecordEventRow(event: TrainingInsightRecordEvent, exercise: TrainingInsightExercise?, onClick: () -> Unit) {
    Surface(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(16.dp),
        color = if (event.kind == TrainingInsightRecordKind.PERSONAL_RECORD) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${if (event.kind == TrainingInsightRecordKind.PERSONAL_RECORD) "New record" else "First benchmark"} · ${event.exerciseName}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${event.metric.label}: ${insightNumber(event.value)} ${event.metric.unit}", style = MaterialTheme.typography.bodyMedium)
            if (exercise != null) Text(insightSetLabel(event.set, exercise.logType), style = MaterialTheme.typography.bodyMedium)
            Text(insightDate(event.set.timestampMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun periodLabel(period: TrainingInsightsPeriod): String = "${shortDate(period.currentStartDate)}–${shortDate(period.currentEndDate)} · rolling ${period.windowDays} days"
internal fun shortDate(date: java.time.LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
internal fun lastLoggedLabel(days: Long?): String = when (days) { null -> "No working sets logged"; 0L -> "Last logged today"; 1L -> "Last logged yesterday"; else -> "Last logged $days days ago" }
