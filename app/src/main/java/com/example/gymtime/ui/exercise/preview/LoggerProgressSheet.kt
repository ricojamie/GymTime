package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.domain.progression.LoggerProgressState
import com.example.gymtime.domain.progression.progressLabel
import com.example.gymtime.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId

/** Presentation only: the screen supplies live history and owns whether this sheet is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerProgressSheet(
    exerciseName: String,
    logType: LogType,
    state: LoggerProgressState,
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var rangeMonths by rememberSaveable { mutableIntStateOf(0) }
    val visiblePoints = remember(state.points, rangeMonths) {
        if (rangeMonths == 0) state.points else {
            val cutoff = LocalDate.now().minusMonths(rangeMonths.toLong())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            state.points.filter { it.date.time >= cutoff }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Your progression", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(exerciseName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close progression")
                }
            }
            Spacer(Modifier.height(16.dp))
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Progress", "Records", "History").forEachIndexed { index, title ->
                    Tab(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val scrollState = key(selectedTab) { rememberLazyListState() }
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            state.allTimeBest?.let { best -> item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text("All-time best · ${state.recordLabel.lowercase()}", style = MaterialTheme.typography.labelLarge)
                                        Text(formatLoggerSet(best, logType), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } }
                            item {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1 to "1M", 3 to "3M", 6 to "6M", 0 to "All").forEach { (months, label) ->
                                        FilterChip(selected = rangeMonths == months, onClick = { rangeMonths = months }, label = { Text(label) })
                                    }
                                }
                            }
                            item { Text(state.metricLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            state.metricDescription?.let { description ->
                                item {
                                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (logType == LogType.DISTANCE_TIME) {
                                item {
                                    Text("Distance is shown in ${state.metricUnit}. Steps and floors are tracked separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (visiblePoints.isEmpty()) {
                                item {
                                    EmptyProgress(
                                        if (state.isEstimatedStrength) "No eligible strength estimates in this range. Choose All to see earlier workouts, or History to see every set."
                                        else if (state.points.isNotEmpty()) "No working sets in this range. Choose All to see earlier workouts."
                                        else if (logType == LogType.DISTANCE_TIME) "No completed working sets in ${state.metricUnit} yet. All sets are available in History."
                                        else "Log a completed working set to start your progression."
                                    )
                                }
                            } else {
                                item { ProgressChart(state.copy(points = visiblePoints)) }
                                item {
                                    Text(
                                        "One dot per workout. Warmups and unfinished sets are excluded. Dates and exact sets are listed below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                items(visiblePoints.asReversed(), key = { it.workoutId }) { point ->
                                    DataRow(
                                        formatDate(point.date),
                                        if (state.isEstimatedStrength) {
                                            "${formatLoggerNumber(point.value)} ${state.metricUnit} estimated · ${formatLoggerSet(point.set, logType)}"
                                        } else formatLoggerSet(point.set, logType)
                                    )
                                }
                            }
                        }
                        1 -> {
                            item {
                                Text("Completed working sets only", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.records.isEmpty()) {
                                item { EmptyProgress("Your first working set will start your records.") }
                            } else {
                                items(state.records, key = { it.label }) { record ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(record.label, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                record.estimatedValue?.let { "${formatLoggerNumber(it)} lb" } ?: formatLoggerSet(record.set, logType),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                if (record.estimatedValue != null) {
                                                    "From ${formatLoggerSet(record.set, logType)} · ${formatDate(record.set.timestamp)}"
                                                } else formatDate(record.set.timestamp),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                if (logType == LogType.WEIGHT_REPS) {
                                    item {
                                        Text("Estimated one-rep max uses the Epley formula for sets of 1–15 reps. It is an estimate, not a logged lift.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        else -> {
                            if (state.sessions.isEmpty()) {
                                item { EmptyProgress("Your exercise history will appear here after you log a set.") }
                            } else {
                                state.sessions.forEach { session ->
                                    item(key = "session-${session.workoutId}") {
                                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                if (session.isCurrent) "This workout · ${formatDate(session.date)}" else formatDate(session.date),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            HorizontalDivider()
                                        }
                                    }
                                    items(session.sets, key = { "set-${it.id}" }) { set ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(formatLoggerSet(set, logType), style = MaterialTheme.typography.titleMedium)
                                            val details = buildList {
                                                if (set.isWarmup) add("Warmup")
                                                if (!set.isComplete) add("Unfinished")
                                                set.rpe?.let { add("RPE ${formatLoggerNumber(it)}") }
                                            }
                                            if (details.isNotEmpty()) {
                                                Text(details.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            set.note?.takeIf { it.isNotBlank() }?.let {
                                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProgress(message: String) {
    Text(message, modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DataRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ProgressChart(state: LoggerProgressState) {
    val points = state.points
    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    val valueRange = (maxValue - minValue).takeIf { it > 0f } ?: maxOf(maxValue * 0.1f, 1f)
    val yMin = (minValue - valueRange * 0.15f).coerceAtLeast(0f)
    val yMax = maxValue + valueRange * 0.15f
    val firstDate = points.first().date.time
    val lastDate = points.last().date.time
    val dateRange = (lastDate - firstDate).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val chartDescription = "${state.metricLabel}. ${points.size} workouts. Lowest ${formatLoggerNumber(minValue)} ${state.metricUnit}. Highest ${formatLoggerNumber(maxValue)} ${state.metricUnit}. Exact values follow the chart."
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${formatLoggerNumber(yMax)} ${state.metricUnit}", style = MaterialTheme.typography.labelMedium)
            Text("${points.size} workouts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = chartDescription }
        ) {
            val inset = 6.dp.toPx()
            val plotWidth = (size.width - inset * 2).coerceAtLeast(1f)
            val plotHeight = (size.height - inset * 2).coerceAtLeast(1f)
            repeat(3) { index ->
                val y = inset + plotHeight * index / 2f
                drawLine(gridColor, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
            }
            val positions = points.map { point ->
                Offset(
                    x = if (firstDate == lastDate) size.width / 2f else inset + ((point.date.time - firstDate).toDouble() / dateRange).toFloat() * plotWidth,
                    y = inset + (1f - (point.value - yMin) / (yMax - yMin).coerceAtLeast(0.000001f)) * plotHeight
                )
            }
            val path = Path().apply {
                positions.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(path, color = color, style = Stroke(width = 3.dp.toPx()))
            positions.forEach { drawCircle(color, radius = 4.dp.toPx(), center = it) }
        }
        Text("${formatLoggerNumber(yMin)} ${state.metricUnit}", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDate(points.first().date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            if (points.size > 1) {
                Text(formatDate(points.last().date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun formatLoggerSet(set: Set, logType: LogType): String {
    val weight = set.weight?.let { "${formatLoggerNumber(it)} lb" } ?: "—"
    val reps = set.reps?.let { "$it reps" } ?: "—"
    val duration = set.durationSeconds?.let(TimeUtils::formatSecondsToHMS) ?: "—"
    val calories = set.calories?.let { "${formatLoggerNumber(it)} cal" } ?: "—"
    val distance = when {
        set.distanceValue != null && set.distanceUnit != null ->
            "${TimeUtils.formatDistance(set.distanceValue, set.distanceUnit)} ${set.distanceUnit.progressLabel()}"
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

fun formatLoggerNumber(value: Float): String = String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.', ',')

private fun formatDate(date: Date): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
