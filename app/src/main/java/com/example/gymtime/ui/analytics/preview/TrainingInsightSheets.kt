package com.example.gymtime.ui.analytics.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.domain.analytics.*
import com.example.gymtime.domain.progression.progressLabel
import com.example.gymtime.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // The modal owns its separate system/IME insets. The parent NavHost already owns app insets.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            content()
            TextButton(onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp)) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsightExercisePicker(
    exercises: List<TrainingInsightExercise>, muscles: List<String>, selectedExerciseId: Long?,
    query: String, muscleFilter: String?, onQueryChange: (String) -> Unit,
    onMuscleChange: (String?) -> Unit, onSelect: (Long) -> Unit, onDismiss: () -> Unit
) {
    val visible = exercises.filter {
        (muscleFilter == null || it.muscle == muscleFilter) && it.name.contains(query.trim(), ignoreCase = true)
    }
    InsightSheet("Choose an exercise", onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(query, onQueryChange, label = { Text("Search exercises") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = muscleFilter == null, onClick = { onMuscleChange(null) }, label = { Text("All body parts") },
                        modifier = Modifier.heightIn(min = 48.dp))
                    muscles.forEach { muscle ->
                        FilterChip(selected = muscleFilter == muscle, onClick = { onMuscleChange(muscle) }, label = { Text(muscle) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
            item { Text("Favorites and frequently logged exercises come first.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (visible.isEmpty()) item {
                Text("No exercises match. Clear the search or choose another body part.", modifier = Modifier.padding(vertical = 16.dp))
            }
            items(visible, key = { it.id }) { exercise ->
                Surface(onClick = { onSelect(exercise.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .semantics { selected = exercise.id == selectedExerciseId }, shape = RoundedCornerShape(16.dp),
                    color = if (exercise.id == selectedExerciseId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${exercise.muscle}${if (exercise.isStarred) " · favorite" else ""} · ${exercise.totalWorkingSets} working sets",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightExerciseEvidenceSheet(exercise: TrainingInsightExercise, onDismiss: () -> Unit) {
    InsightSheet(exercise.name, onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("${exercise.muscle} · completed working history · read only", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { InsightSectionTitle("All-time records") }
            if (exercise.records.isEmpty()) item { Text("No completed working-set records yet.") }
            items(exercise.records) { record ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.metric.label, style = MaterialTheme.typography.labelLarge)
                        Text("${insightNumber(record.value)} ${record.metric.unit}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(insightSetLabel(record.set, exercise.logType), style = MaterialTheme.typography.bodyMedium)
                        Text(insightDate(record.set.timestampMs), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item { HorizontalDivider(); InsightSectionTitle("Full working history", "Every completed working set. Chart sampling does not remove history.") }
            if (exercise.history.isEmpty()) item { Text("No completed working sets logged for this exercise.") }
            exercise.history.forEach { session ->
                item(key = "workout-${session.workoutId}") {
                    Text("${session.workoutName?.takeIf { it.isNotBlank() } ?: "Workout"} · ${insightDate(session.workoutStartMs)}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(session.sets, key = { "set-${it.id}" }) { set ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(insightSetLabel(set, exercise.logType), style = MaterialTheme.typography.bodyLarge)
                        if (!set.note.isNullOrBlank()) Text(set.note, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightMuscleEvidenceSheet(muscle: TrainingInsightMuscle, period: TrainingInsightsPeriod,
    onExercise: (Long) -> Unit, onDismiss: () -> Unit
) {
    InsightSheet(muscle.name, onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(periodLabel(period), style = MaterialTheme.typography.bodyMedium)
                Text("${muscle.currentWorkingSets} working sets now · ${muscle.previousWorkingSets} in the previous equal period")
                Text(lastLoggedLabel(muscle.daysSinceLastLogged), color = MaterialTheme.colorScheme.onSurfaceVariant)
                muscle.latestTimestampMs?.let { Text("Last logged ${insightDate(it)}", style = MaterialTheme.typography.bodyMedium) }
            }
            item { Text("Exercises contributing to this body part", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (muscle.contributors.isEmpty()) item { Text("No completed working sets for this body part yet.") }
            items(muscle.contributors, key = { it.exerciseId }) { contribution ->
                Surface(onClick = { onExercise(contribution.exerciseId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(contribution.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${contribution.currentWorkingSets} current / ${contribution.previousWorkingSets} prior · ${contribution.totalWorkingSets} all time",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Open exercise history & records", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Text("Counts use the exercise’s primary muscle. Core and Abs share the displayed Abs group. Custom body parts remain visible even with zero sets.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun InsightMethodsSheet(period: TrainingInsightsPeriod?, onDismiss: () -> Unit) {
    InsightSheet("How to read your insights", onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                InsightSectionTitle("Date windows")
                Text("4 weeks means a rolling 28 days through now; 12 weeks means 84. Counts compare with the immediately preceding equal period. Local date labels can include partial days at the edges.")
                if (period != null) {
                    val zone = java.time.ZoneId.of(period.zoneId)
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                    fun moment(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).format(formatter)
                    Text("Current: ${moment(period.currentStartMs)} to ${moment(period.currentEndMs)}\nPrevious: ${moment(period.previousStartMs)} to ${moment(period.previousEndMs)}\nTime zone: ${period.zoneId}\nWindow start is included; window end is excluded.")
                }
                Text("A completed workout with working activity is anchored to its start date, counted once even if it crosses midnight. Warmups, unfinished sets and open workouts are excluded. Empty and warmup-only workouts do not count.")
            }
            item {
                InsightSectionTitle("Lift progress")
                Text("Estimated one-rep max uses the same calculation as Home and the logger. It is an estimate within one exercise, not measured strength or muscle growth. Only positive weight and 1–15 reps qualify; high-rep sets still count in history and actual weight records.")
                Text("Other exercises use a labelled metric suited to their log type. Distance units are normalized where convertible; steps and floors remain separate. The graph shows one best comparable set per workout, dated by that session’s last working set. Workout counts use the workout’s start date.")
                Text("Estimated-strength and reps-only comparisons use the last 365 days and need at least four comparable sessions. At six or more sessions, the latest three session bests are compared with the preceding three using medians; with four or five, two are compared with two. This is independent of the 4/12-week count window. Changes smaller than 2% are similar. No current comparison is claimed if the last comparable session is more than 42 days old. Other raw metrics show history without an estimated-strength comparison.")
                Text("Graphs retain at most 80 sampled session dots within the selected 4/12-week window. Chart values are rounded to two decimals; actual set values are shown alongside. Full working history is never sampled.")
            }
            item {
                InsightSectionTitle("Body parts & rhythm")
                Text("Body parts count working sets using one primary muscle per exercise. Core and Abs share the displayed Abs group. This does not estimate recovery, readiness or muscle growth.")
                Text("Weekly bars count unique completed workouts. Weeks start Monday. Edge weeks show their actual clipped dates; the current week is ‘so far’, and the first edge week may be ‘partial’. Counts are a description of your log, not a training target.")
            }
            item {
                InsightSectionTitle("Records & evidence")
                Text("A first observation is a benchmark. Later events must strictly improve the same metric; ties are not new records. A new rep-count benchmark is labelled separately from a personal record.")
                Text("The overview shows up to six sets with record events in the selected period, one representative metric per set, preferring a new record to a benchmark. Tap a record, body part or observation for the exercise’s complete working history and all-time records. These views are read only and never start or reopen a workout.")
            }
        }
    }
}

internal fun insightSetLabel(set: TrainingInsightSet, logType: LogType): String {
    val weight = set.weight?.let { "${insightLoggedNumber(it)} lb" } ?: "—"
    val reps = set.reps?.let { "$it reps" } ?: "—"
    val duration = set.durationSeconds?.let(TimeUtils::formatSecondsToHMS) ?: "—"
    val calories = set.calories?.let { "${insightLoggedNumber(it)} cal" } ?: "—"
    val distance = when {
        set.distanceValue != null && set.distanceUnit != null -> "${TimeUtils.formatDistance(set.distanceValue, set.distanceUnit)} ${set.distanceUnit.progressLabel()}"
        set.distanceMeters != null && set.distanceUnit?.isConvertibleToMeters != false -> {
            val unit = set.distanceUnit ?: DistanceUnit.METERS
            "${TimeUtils.formatDistance(TimeUtils.metersToDistance(set.distanceMeters, unit), unit)} ${unit.progressLabel()}"
        }
        else -> "—"
    }
    return when (logType) {
        LogType.WEIGHT_REPS -> "$weight × $reps"
        LogType.REPS_ONLY -> reps
        LogType.DURATION -> duration
        LogType.WEIGHT_DISTANCE -> "$weight · $distance"
        LogType.DISTANCE_TIME -> "$distance · $duration"
        LogType.WEIGHT_TIME -> "$weight · $duration"
        LogType.CALORIES_TIME -> "$calories · $duration"
    }
}
