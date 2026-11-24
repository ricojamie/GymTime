package com.example.gymtime.ui.exercise.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.example.gymtime.ui.components.IronLogFilterPill
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogSegmentedControl
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.anodizedMicroGrain
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
    val surfaces = LocalIronLogSurfaces.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var rangeMonths by rememberSaveable { mutableIntStateOf(0) }
    val visiblePoints = remember(state.points, rangeMonths) {
        if (rangeMonths == 0) state.points else {
            val cutoff = LocalDate.now().minusMonths(rangeMonths.toLong())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            state.points.filter { it.date.time >= cutoff }
        }
    }
    val sheetBackground = remember(surfaces) {
        Brush.verticalGradient(listOf(surfaces.plateElevatedTop, surfaces.canvasBottom))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = surfaces.plateBottom,
        contentColor = scheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.68f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(brush = sheetBackground)
                .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.012f)
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, surfaces.rimTop, Color.Transparent)
                        ),
                        start = Offset(0f, 0.5.dp.toPx()),
                        end = Offset(size.width, 0.5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(top = 12.dp)
        ) {
            IronLogSheetHeader(
                title = "Your progression",
                subtitle = exerciseName,
                onClose = onDismiss,
                closeContentDescription = "Close progression",
                modifier = Modifier.padding(start = 20.dp, end = 8.dp)
            )
            Spacer(Modifier.height(IronLogSpace.ItemGap))
            IronLogSegmentedControl(
                options = listOf("Progress", "Records", "History"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val scrollState = key(selectedTab) { rememberLazyListState() }
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                ) {
                    when (selectedTab) {
                        0 -> {
                            state.allTimeBest?.let { best ->
                                item {
                                    IronLogCard(
                                        highlighted = true,
                                        spotlight = true,
                                        showBorder = true,
                                        shape = IronLogRadius.PlateShape,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                                            Text(
                                                text = "ALL-TIME BEST · ${state.recordLabel.uppercase()}",
                                                style = IronLogType.technicalEyebrow(),
                                                color = surfaces.accent
                                            )
                                        }
                                        OdometerText(
                                            text = formatLoggerSet(best, logType),
                                            style = IronLogType.monumentalNumeral(
                                                MaterialTheme.typography.headlineMedium
                                            ).copy(fontWeight = FontWeight.Bold),
                                            color = scheme.onSurface
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
                                        IronLogFilterPill(
                                            label = label,
                                            selected = rangeMonths == months,
                                            onClick = { rangeMonths = months },
                                            showLedPip = true
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
                                        shape = IronLogRadius.PlateShape,
                                        showBorder = true,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            IronLogLedPip(color = surfaces.ledGlow, size = 5.dp)
                                            Text(
                                                text = record.label,
                                                style = IronLogType.technicalEyebrow(MaterialTheme.typography.labelLarge),
                                                color = surfaces.accent
                                            )
                                        }
                                        OdometerText(
                                            text = record.estimatedValue?.let { "${formatLoggerNumber(it)} lb" }
                                                ?: formatLoggerSet(record.set, logType),
                                            style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = scheme.onSurface
                                        )
                                        Text(
                                            text = if (record.estimatedValue != null) {
                                                "From ${formatLoggerSet(record.set, logType)} · ${formatDate(record.set.timestamp)}"
                                            } else {
                                                formatDate(record.set.timestamp)
                                            },
                                            style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
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
                                                    style = IronLogType.telemetryLabel(MaterialTheme.typography.labelMedium),
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
            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelMedium),
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
    val surfaces = LocalIronLogSurfaces.current
    val reducedMotion = LocalReducedMotion.current
    val color = surfaces.accent
    val gridColor = scheme.outlineVariant.copy(alpha = 0.45f)
    val dotGridColor = scheme.onSurfaceVariant.copy(alpha = if (surfaces.isDark) 0.14f else 0.16f)

    val revealProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(points, reducedMotion) {
        if (reducedMotion) {
            revealProgress.snapTo(1f)
        } else {
            revealProgress.snapTo(0f)
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
    }

    val chartDescription = remember(state.metricLabel, state.metricUnit, points.size, chartStats) {
        "${state.metricLabel}. ${points.size} workouts. Lowest ${formatLoggerNumber(chartStats.minValue)} ${state.metricUnit}. Highest ${formatLoggerNumber(chartStats.maxValue)} ${state.metricUnit}. Exact values follow the chart."
    }
    val currentPoints by rememberUpdatedState(points)
    val currentStats by rememberUpdatedState(chartStats)
    val currentColor by rememberUpdatedState(color)
    val currentGridColor by rememberUpdatedState(gridColor)
    val currentDotGridColor by rememberUpdatedState(dotGridColor)

    IronLogCard(
        shape = IronLogRadius.PlateShape,
        showBorder = true,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${formatLoggerNumber(chartStats.yMax)} ${state.metricUnit}",
                style = IronLogType.telemetryLabel(MaterialTheme.typography.labelMedium)
            )
            Text(
                text = "${points.size} workouts",
                style = IronLogType.telemetryLabel(MaterialTheme.typography.labelMedium),
                color = scheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(164.dp)
                .semantics { contentDescription = chartDescription }
                .drawWithCache {
                    val pts = currentPoints
                    val stats = currentStats
                    val lineColor = currentColor
                    val guideColor = currentGridColor
                    val dotBgColor = currentDotGridColor
                    val inset = 6.dp.toPx()
                    val gridStrokeWidth = 0.75.dp.toPx()
                    val lineStroke = Stroke(
                        width = 2.75.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    val dotRadius = 3.75.dp.toPx()
                    val dotHaloRadius = 7.dp.toPx()
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
                    val fillPath = if (positions.size > 1) {
                        Path().apply {
                            moveTo(positions.first().x, inset + plotHeight)
                            positions.forEach { point -> lineTo(point.x, point.y) }
                            lineTo(positions.last().x, inset + plotHeight)
                            close()
                        }
                    } else {
                        null
                    }
                    val areaBrush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                        startY = inset,
                        endY = inset + plotHeight
                    )
                    val canvasW = size.width
                    val canvasH = size.height
                    val dotStepX = 16.dp.toPx()
                    val dotStepY = 16.dp.toPx()
                    val bgDots = buildList {
                        var x = inset
                        while (x <= canvasW - inset) {
                            var y = inset
                            while (y <= canvasH - inset) {
                                add(Offset(x, y))
                                y += dotStepY
                            }
                            x += dotStepX
                        }
                    }

                    onDrawBehind {
                        val bgDotR = 0.9.dp.toPx()
                        bgDots.forEach { dot ->
                            drawCircle(color = dotBgColor, radius = bgDotR, center = dot)
                        }
                        repeat(3) { index ->
                            val y = inset + plotHeight * index / 2f
                            drawLine(
                                color = guideColor,
                                start = Offset(inset, y),
                                end = Offset(size.width - inset, y),
                                strokeWidth = gridStrokeWidth
                            )
                        }

                        val reveal = revealProgress.value.coerceIn(0f, 1f)
                        if (reveal <= 0.001f) return@onDrawBehind

                        if (positions.size > 1) {
                            val clipRight = inset + plotWidth * reveal + dotHaloRadius
                            clipRect(left = 0f, top = 0f, right = clipRight, bottom = size.height) {
                                if (fillPath != null) {
                                    drawPath(fillPath, brush = areaBrush)
                                }
                                drawPath(path, color = lineColor, style = lineStroke)
                            }
                        }

                        positions.forEachIndexed { index, pos ->
                            val normX = if (positions.size <= 1) 0f else (pos.x - inset) / plotWidth
                            val dotAlpha = if (positions.size <= 1) reveal else ((reveal - normX * 0.9f) / 0.1f).coerceIn(0f, 1f)
                            if (dotAlpha > 0.01f) {
                                if (index == positions.lastIndex) {
                                    drawCircle(
                                        color = lineColor.copy(alpha = 0.25f * dotAlpha),
                                        radius = dotHaloRadius,
                                        center = pos
                                    )
                                }
                                drawCircle(
                                    color = lineColor.copy(alpha = dotAlpha),
                                    radius = dotRadius,
                                    center = pos
                                )
                            }
                        }
                    }
                }
        )
        Text(
            text = "${formatLoggerNumber(chartStats.yMin)} ${state.metricUnit}",
            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelMedium)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDate(points.first().date),
                modifier = Modifier.weight(1f),
                style = IronLogType.telemetryLabel(MaterialTheme.typography.labelSmall),
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            if (points.size > 1) {
                Text(
                    text = formatDate(points.last().date),
                    modifier = Modifier.weight(1f),
                    style = IronLogType.telemetryLabel(MaterialTheme.typography.labelSmall),
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
