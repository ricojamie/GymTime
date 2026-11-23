package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.util.TimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Presentation only: the screen supplies live history and owns whether this sheet is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerProgressSheet(
    exerciseName: String,
    logType: LogType,
    state: LoggerProgressState,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
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
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            IronLogSheetHeader(
                title = "Your progression",
                subtitle = exerciseName,
                onClose = onDismiss,
                closeContentDescription = "Close progression",
                modifier = Modifier.padding(start = 24.dp, end = 8.dp)
            )
            Spacer(Modifier.height(IronLogSpace.ItemGap))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = scheme.surface,
                contentColor = scheme.primary
            ) {
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
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                ) {
                    when (selectedTab) {
                        0 -> {
                            state.allTimeBest?.let { best ->
                                item {
                                    IronLogCard(
                                        highlighted = true,
                                        showBorder = true,
                                        shape = IronLogRadius.CardShape,
                                        containerColor = scheme.primaryContainer,
                                        contentColor = scheme.onPrimaryContainer,
                                        borderColor = scheme.primary.copy(alpha = 0.45f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "ALL-TIME BEST · ${state.recordLabel.uppercase()}",
                                            style = IronLogType.sectionEyebrow(),
                                            color = scheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = formatLoggerSet(best, logType),
                                            style = IronLogType.tabular(MaterialTheme.typography.headlineSmall).copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                            item {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                ) {
                                    listOf(1 to "1M", 3 to "3M", 6 to "6M", 0 to "All").forEach { (months, label) ->
                                        FilterChip(
                                            selected = rangeMonths == months,
                                            onClick = { rangeMonths = months },
                                            label = { Text(label) },
                                            shape = IronLogRadius.MicroShape
                                        )
                                    }
                                }
                            }
                            item {
                                Text(
                                    text = state.metricLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            state.metricDescription?.let { description ->
                                item {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (logType == LogType.DISTANCE_TIME) {
                                item {
                                    Text(
                                        text = "Distance is shown in ${state.metricUnit}. Steps and floors are tracked separately.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
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
                                        text = "One dot per workout. Warmups and unfinished sets are excluded. Dates and exact sets are listed below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                                items(visiblePoints.asReversed(), key = { it.workoutId }) { point ->
                                    DataRow(
                                        label = formatDate(point.date),
                                        value = if (state.isEstimatedStrength) {
                                            "${formatLoggerNumber(point.value)} ${state.metricUnit} estimated · ${formatLoggerSet(point.set, logType)}"
                                        } else {
                                            formatLoggerSet(point.set, logType)
                                        }
                                    )
                                }
                            }
                        }
                        1 -> {
                            item {
                                Text(
                                    text = "Completed working sets only",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            if (state.records.isEmpty()) {
                                item { EmptyProgress("Your first working set will start your records.") }
                            } else {
                                items(state.records, key = { it.label }) { record ->
                                    IronLogCard(
                                        shape = IronLogRadius.CardShape,
                                        containerColor = scheme.surfaceContainerLow,
                                        showBorder = true,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = record.label,
                                            style = IronLogType.sectionEyebrow(MaterialTheme.typography.labelLarge),
                                            color = scheme.primary
                                        )
                                        Text(
                                            text = record.estimatedValue?.let { "${formatLoggerNumber(it)} lb" }
                                                ?: formatLoggerSet(record.set, logType),
                                            style = IronLogType.tabular(MaterialTheme.typography.titleLarge).copy(
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = if (record.estimatedValue != null) {
                                                "From ${formatLoggerSet(record.set, logType)} · ${formatDate(record.set.timestamp)}"
                                            } else {
                                                formatDate(record.set.timestamp)
                                            },
                                            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                            color = scheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (logType == LogType.WEIGHT_REPS) {
                                    item {
                                        Text(
                                            text = "Estimated one-rep max uses the Epley formula for sets of 1–15 reps. It is an estimate, not a logged lift.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant
                                        )
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
                                        Column(
                                            modifier = Modifier.padding(top = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                        ) {
                                            IronLogSectionHeader(
                                                title = if (session.isCurrent) {
                                                    "This workout · ${formatDate(session.date)}"
                                                } else {
                                                    formatDate(session.date)
                                                }
                                            )
                                            IronLogDivider(insetStart = 0.dp)
                                        }
                                    }
                                    items(session.sets, key = { "set-${it.id}" }) { set ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = formatLoggerSet(set, logType),
                                                style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            val details = buildList {
                                                if (set.isWarmup) add("Warmup")
                                                if (!set.isComplete) add("Unfinished")
                                                set.rpe?.let { add("RPE ${formatLoggerNumber(it)}") }
                                            }
                                            if (details.isNotEmpty()) {
                                                Text(
                                                    text = details.joinToString(" · "),
                                                    style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                                                    color = scheme.onSurfaceVariant
                                                )
                                            }
                                            set.note?.takeIf { it.isNotBlank() }?.let {
                                                Text(
                                                    text = it,
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
                }
            }
        }
    }
}

@Composable
private fun EmptyProgress(message: String) {
    IronLogEmptyState(
        title = "Nothing logged yet",
        body = message
    )
}

@Composable
private fun DataRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                fontWeight = FontWeight.SemiBold
            )
        )
        IronLogDivider(modifier = Modifier.padding(top = 8.dp), insetStart = 0.dp)
    }
}

@Composable
private fun ProgressChart(state: LoggerProgressState) {
    val points = state.points
    val chartStats = remember(points) {
        val minValue = points.minOf { it.value }
        val maxValue = points.maxOf { it.value }
        val valueRange = (maxValue - minValue).takeIf { it > 0f } ?: maxOf(maxValue * 0.1f, 1f)
        val yMin = (minValue - valueRange * 0.15f).coerceAtLeast(0f)
        val yMax = maxValue + valueRange * 0.15f
        val firstDate = points.first().date.time
        val lastDate = points.last().date.time
        val dateRange = (lastDate - firstDate).coerceAtLeast(1L)
        ChartStats(minValue, maxValue, yMin, yMax, firstDate, lastDate, dateRange)
    }
    val scheme = MaterialTheme.colorScheme
    val color = scheme.primary
    val gridColor = scheme.outlineVariant
    val chartDescription = remember(state.metricLabel, state.metricUnit, points.size, chartStats) {
        "${state.metricLabel}. ${points.size} workouts. Lowest ${formatLoggerNumber(chartStats.minValue)} ${state.metricUnit}. Highest ${formatLoggerNumber(chartStats.maxValue)} ${state.metricUnit}. Exact values follow the chart."
    }
    val currentPoints by rememberUpdatedState(points)
    val currentStats by rememberUpdatedState(chartStats)
    val currentColor by rememberUpdatedState(color)
    val currentGridColor by rememberUpdatedState(gridColor)

    IronLogCard(
        shape = IronLogRadius.CardShape,
        containerColor = scheme.surfaceContainerLow,
        showBorder = true,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${formatLoggerNumber(chartStats.yMax)} ${state.metricUnit}",
                style = IronLogType.tabular(MaterialTheme.typography.labelMedium)
            )
            Text(
                text = "${points.size} workouts",
                style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                color = scheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = chartDescription }
                .drawWithCache {
                    val pts = currentPoints
                    val stats = currentStats
                    val lineColor = currentColor
                    val guideColor = currentGridColor
                    val inset = 6.dp.toPx()
                    val gridStrokeWidth = 1.dp.toPx()
                    val lineStroke = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    val dotRadius = 4.dp.toPx()
                    val plotWidth = (size.width - inset * 2).coerceAtLeast(1f)
                    val plotHeight = (size.height - inset * 2).coerceAtLeast(1f)
                    val yRange = (stats.yMax - stats.yMin).coerceAtLeast(0.000001f)
                    val positions = pts.map { point ->
                        Offset(
                            x = if (stats.firstDate == stats.lastDate) {
                                size.width / 2f
                            } else {
                                inset + ((point.date.time - stats.firstDate).toDouble() / stats.dateRange).toFloat() * plotWidth
                            },
                            y = inset + (1f - (point.value - stats.yMin) / yRange) * plotHeight
                        )
                    }
                    val path = Path().apply {
                        positions.forEachIndexed { index, point ->
                            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                        }
                    }
                    onDrawBehind {
                        repeat(3) { index ->
                            val y = inset + plotHeight * index / 2f
                            drawLine(
                                color = guideColor,
                                start = Offset(inset, y),
                                end = Offset(size.width - inset, y),
                                strokeWidth = gridStrokeWidth
                            )
                        }
                        drawPath(path, color = lineColor, style = lineStroke)
                        positions.forEach { drawCircle(lineColor, radius = dotRadius, center = it) }
                    }
                }
        )
        Text(
            text = "${formatLoggerNumber(chartStats.yMin)} ${state.metricUnit}",
            style = IronLogType.tabular(MaterialTheme.typography.labelMedium)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDate(points.first().date),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            if (points.size > 1) {
                Text(
                    text = formatDate(points.last().date),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ChartStats(
    val minValue: Float,
    val maxValue: Float,
    val yMin: Float,
    val yMax: Float,
    val firstDate: Long,
    val lastDate: Long,
    val dateRange: Long
)

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

private val progressDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

private fun formatDate(date: Date): String {
    val locale = Locale.getDefault()
    val formatter = progressDateFormatters.getOrPut(locale) {
        DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    }
    return Instant.ofEpochMilli(date.time)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter)
}
