package com.example.gymtime.ui.analytics.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.domain.analytics.TrainingInsightExercise
import com.example.gymtime.domain.analytics.TrainingInsightMuscle
import com.example.gymtime.domain.analytics.TrainingInsightSet
import com.example.gymtime.domain.analytics.TrainingInsightsPeriod
import com.example.gymtime.domain.progression.progressLabel
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // The modal owns its separate system/IME insets. The parent NavHost already owns app insets.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = IronLogRadius.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            IronLogSheetHeader(
                title = title,
                subtitle = subtitle,
                onClose = onDismiss,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsightExercisePicker(
    exercises: List<TrainingInsightExercise>,
    muscles: List<String>,
    selectedExerciseId: Long?,
    query: String,
    muscleFilter: String?,
    onQueryChange: (String) -> Unit,
    onMuscleChange: (String?) -> Unit,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val visible = remember(exercises, muscleFilter, query) {
        val needle = query.trim()
        exercises.filter {
            (muscleFilter == null || it.muscle == muscleFilter) &&
                it.name.contains(needle, ignoreCase = true)
        }
    }

    InsightSheet(
        title = "Choose an exercise",
        subtitle = "Favorites and frequently logged exercises come first",
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search exercises") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = IronLogRadius.ControlShape
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = muscleFilter == null,
                        onClick = { onMuscleChange(null) },
                        shape = IronLogRadius.ControlShape,
                        label = { Text("All body parts") },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    )
                    muscles.forEach { muscle ->
                        FilterChip(
                            selected = muscleFilter == muscle,
                            onClick = { onMuscleChange(muscle) },
                            shape = IronLogRadius.ControlShape,
                            label = { Text(muscle) },
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = "No exercises match. Clear the search or choose another body part.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(visible, key = { it.id }) { exercise ->
                val isSelected = exercise.id == selectedExerciseId
                IronLogCard(
                    onClick = { onSelect(exercise.id) },
                    highlighted = isSelected,
                    showBorder = true,
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = IronLogRadius.CardShape,
                    modifier = Modifier
                        .heightIn(min = 68.dp)
                        .semantics { selected = isSelected },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = exercise.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            IronLogStatusChip(label = "Selected", tone = IronLogStatusTone.ACTIVE)
                        }
                    }
                    Text(
                        text = "${exercise.muscle}${if (exercise.isStarred) " · Favorite" else ""} · ${exercise.totalWorkingSets} working sets",
                        style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun InsightExerciseEvidenceSheet(exercise: TrainingInsightExercise, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    InsightSheet(
        title = exercise.name,
        subtitle = "${exercise.muscle} · completed working history · read only",
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            item { IronLogSectionHeader("All-time records") }
            if (exercise.records.isEmpty()) {
                item {
                    Text(
                        text = "No completed working-set records yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    IronLogGroupedCard(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        showBorder = true,
                        shape = IronLogRadius.CardShape
                    ) {
                        exercise.records.forEachIndexed { index, record ->
                            if (index > 0) {
                                IronLogDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(IronLogSpace.CardPadding),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(record.metric.label, style = MaterialTheme.typography.labelLarge)
                                    IronLogStatusChip(label = "Record", tone = IronLogStatusTone.PR)
                                }
                                Text(
                                    text = "${insightNumber(record.value)} ${record.metric.unit}",
                                    style = IronLogType.tabular(MaterialTheme.typography.titleLarge).copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${insightSetLabel(record.set, exercise.logType)} · ${insightDate(record.set.timestampMs)}",
                                    style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                                    color = scheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IronLogDivider(insetStart = 0.dp)
                    IronLogSectionHeader("Full working history")
                    Text(
                        text = "Every completed working set. Chart sampling does not remove history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            if (exercise.history.isEmpty()) {
                item {
                    Text(
                        text = "No completed working sets logged for this exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            exercise.history.forEach { session ->
                item(key = "workout-${session.workoutId}") {
                    Text(
                        text = "${session.workoutName?.takeIf { it.isNotBlank() } ?: "Workout"} · ${insightDate(session.workoutStartMs)}",
                        style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(session.sets, key = { "set-${it.id}" }) { set ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = insightSetLabel(set, exercise.logType),
                            style = IronLogType.tabular(MaterialTheme.typography.bodyLarge)
                        )
                        if (!set.note.isNullOrBlank()) {
                            Text(
                                text = set.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightMuscleEvidenceSheet(
    muscle: TrainingInsightMuscle,
    period: TrainingInsightsPeriod,
    onExercise: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    InsightSheet(
        title = muscle.name,
        subtitle = periodLabel(period),
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${muscle.currentWorkingSets} working sets now · ${muscle.previousWorkingSets} in the previous equal period",
                        style = IronLogType.tabular(MaterialTheme.typography.bodyLarge).copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = lastLoggedLabel(muscle.daysSinceLastLogged),
                        style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                        color = scheme.onSurfaceVariant
                    )
                    muscle.latestTimestampMs?.let {
                        Text(
                            text = "Last logged ${insightDate(it)}",
                            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { IronLogSectionHeader("Contributing exercises") }
            if (muscle.contributors.isEmpty()) {
                item {
                    Text(
                        text = "No completed working sets for this body part yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    IronLogGroupedCard(showBorder = true) {
                        muscle.contributors.forEachIndexed { index, contribution ->
                            if (index > 0) {
                                IronLogDivider()
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .clickable { onExercise(contribution.exerciseId) }
                                    .padding(IronLogSpace.CardPadding),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = contribution.exerciseName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${contribution.currentWorkingSets} current · ${contribution.previousWorkingSets} prior · ${contribution.totalWorkingSets} all time",
                                    style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                                    color = scheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Open exercise history & records",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.primary
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Counts use the exercise’s primary muscle. Core and Abs share the displayed Abs group. Custom body parts remain visible even with zero sets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private val methodMomentFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

@Composable
internal fun InsightMethodsSheet(period: TrainingInsightsPeriod?, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val periodBoundsText = remember(period) {
        period?.let {
            val zone = ZoneId.of(it.zoneId)
            val formatter = methodMomentFormatters.computeIfAbsent(Locale.getDefault()) { loc ->
                DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", loc)
            }
            fun moment(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(formatter)
            "Current: ${moment(it.currentStartMs)} to ${moment(it.currentEndMs)}\nPrevious: ${moment(it.previousStartMs)} to ${moment(it.previousEndMs)}\nTime zone: ${it.zoneId}\nWindow start is included; window end is excluded."
        }
    }

    InsightSheet(title = "How to read your insights", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.SectionGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    InsightSectionTitle("Date windows")
                    Text("4 weeks means a rolling 28 days through now; 12 weeks means 84. Counts compare with the immediately preceding equal period. Local date labels can include partial days at the edges.")
                    if (periodBoundsText != null) {
                        Text(
                            text = periodBoundsText,
                            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Text("A completed workout with working activity is anchored to its start date, counted once even if it crosses midnight. Warmups, unfinished sets and open workouts are excluded. Empty and warmup-only workouts do not count.")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    InsightSectionTitle("Lift progress")
                    Text("Estimated one-rep max uses the same calculation as Home and the logger. It is an estimate within one exercise, not measured strength or muscle growth. Only positive weight and 1–15 reps qualify; high-rep sets still count in history and actual weight records.")
                    Text("Other exercises use a labelled metric suited to their log type. Distance units are normalized where convertible; steps and floors remain separate. The graph shows one best comparable set per workout, dated by that session’s last working set. Workout counts use the workout’s start date.")
                    Text("Estimated-strength and reps-only comparisons use the last 365 days and need at least four comparable sessions. At six or more sessions, the latest three session bests are compared with the preceding three using medians; with four or five, two are compared with two. This is independent of the 4/12-week count window. Changes smaller than 2% are similar. No current comparison is claimed if the last comparable session is more than 42 days old. Other raw metrics show history without an estimated-strength comparison.")
                    Text("Graphs retain at most 80 sampled session dots within the selected 4/12-week window. Chart values are rounded to two decimals; actual set values are shown alongside. Full working history is never sampled.")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    InsightSectionTitle("Body parts & rhythm")
                    Text("Body parts count working sets using one primary muscle per exercise. Core and Abs share the displayed Abs group. This does not estimate recovery, readiness or muscle growth.")
                    Text("Weekly bars count unique completed workouts. Weeks start Monday. Edge weeks show their actual clipped dates; the current week is ‘so far’, and the first edge week may be ‘partial’. Counts are a description of your log, not a training target.")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    InsightSectionTitle("Records & evidence")
                    Text("A first observation is a benchmark. Later events must strictly improve the same metric; ties are not new records. A new rep-count benchmark is labelled separately from a personal record.")
                    Text("The overview shows up to six sets with record events in the selected period, one representative metric per set, preferring a new record to a benchmark. Tap a record, body part or observation for the exercise’s complete working history and all-time records. These views are read only and never start or reopen a workout.")
                }
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
