package com.example.gymtime.ui.analytics.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Composable
internal fun InsightPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    IronLogCard(
        modifier = modifier,
        highlighted = highlighted,
        showBorder = true,
        containerColor = if (highlighted) scheme.primaryContainer else scheme.surfaceContainerLow,
        contentColor = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface,
        borderColor = IronLogBorder.borderColor(scheme, highlighted = highlighted),
        shape = IronLogRadius.CardShape,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
        content = content
    )
}

@Composable
internal fun InsightPrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IronLogPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        shape = IronLogRadius.ControlShape
    )
}

@Composable
internal fun InsightSectionTitle(
    title: String,
    detail: String? = null,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = detailColor
            )
        }
    }
}

@Immutable
internal data class InsightChartDot(val timestampMs: Long, val value: Double)

@Immutable
private data class PreparedLineChart(
    val points: List<InsightChartDot>,
    val lower: Double,
    val upper: Double,
    val firstDate: Long,
    val dateSpan: Long,
    val rangeLabel: String,
    val startDateLabel: String,
    val endDateLabel: String?
)

/** Decoration only: exact values and dates are exposed as text by the caller. */
@Composable
internal fun InsightLineChart(points: List<InsightChartDot>, unit: String, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val prepared = remember(points, unit) {
        val minimum = points.minOf { it.value }
        val maximum = points.maxOf { it.value }
        val spread = (maximum - minimum).takeIf { it > 0.0 } ?: (maximum.coerceAtLeast(1.0) * 0.1)
        val lower = (minimum - spread * 0.15).coerceAtLeast(0.0)
        val upper = maximum + spread * 0.15
        val firstDate = points.minOf { it.timestampMs }
        val lastDate = points.maxOf { it.timestampMs }
        val dateSpan = (lastDate - firstDate).coerceAtLeast(1L)
        PreparedLineChart(
            points = points,
            lower = lower,
            upper = upper,
            firstDate = firstDate,
            dateSpan = dateSpan,
            rangeLabel = "${insightNumber(minimum)}–${insightNumber(maximum)} $unit",
            startDateLabel = insightDate(firstDate),
            endDateLabel = if (points.size > 1) insightDate(lastDate) else null
        )
    }

    val drawModifier = remember(prepared, lineColor, gridColor) {
        Modifier.drawWithCache {
            val gutter = 8.dp.toPx()
            val chartWidth = (size.width - gutter * 2).coerceAtLeast(1f)
            val chartHeight = (size.height - gutter * 2).coerceAtLeast(1f)
            val gridStrokeWidth = 1.dp.toPx()
            val lineStroke = Stroke(width = 3.dp.toPx())
            val dotRadius = 4.dp.toPx()
            val valueSpan = (prepared.upper - prepared.lower).coerceAtLeast(1e-6)
            val gridLines = Array(3) { i ->
                val y = gutter + chartHeight * i / 2f
                Offset(gutter, y) to Offset(size.width - gutter, y)
            }
            val offsets = prepared.points.map { point ->
                Offset(
                    x = if (prepared.points.size == 1) {
                        size.width / 2f
                    } else {
                        gutter + chartWidth * ((point.timestampMs - prepared.firstDate).toDouble() / prepared.dateSpan).toFloat()
                    },
                    y = gutter + chartHeight * (1.0 - (point.value - prepared.lower) / valueSpan).toFloat()
                )
            }
            val path = if (offsets.size > 1) {
                Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    for (i in 1 until offsets.size) {
                        lineTo(offsets[i].x, offsets[i].y)
                    }
                }
            } else {
                null
            }
            onDrawBehind {
                gridLines.forEach { (start, end) ->
                    drawLine(gridColor, start, end, gridStrokeWidth)
                }
                if (path != null) {
                    drawPath(path, lineColor, style = lineStroke)
                }
                offsets.forEach { center ->
                    drawCircle(lineColor, dotRadius, center)
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = prepared.rangeLabel,
            style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clearAndSetSemantics { }
                .then(drawModifier)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = prepared.startDateLabel,
                style = IronLogType.tabular(MaterialTheme.typography.labelMedium)
            )
            prepared.endDateLabel?.let { endLabel ->
                Text(
                    text = endLabel,
                    style = IronLogType.tabular(MaterialTheme.typography.labelMedium)
                )
            }
        }
    }
}

/** Labels/counts remain accessible outside the decorative bars. */
@Composable
internal fun InsightWeeklyBars(counts: List<Int>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.secondary
    val base = MaterialTheme.colorScheme.outlineVariant
    val drawModifier = remember(counts, fill, base) {
        Modifier.drawWithCache {
            if (counts.isEmpty()) {
                onDrawBehind { }
            } else {
                val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                val cell = size.width / counts.size
                val baseStroke = 1.dp.toPx()
                val baseY = size.height - 2.dp.toPx()
                val maxBarWidth = 28.dp.toPx()
                val minPositiveHeight = 4.dp.toPx()
                val verticalPad = 8.dp.toPx()
                val cornerRadius = CornerRadius(4.dp.toPx())
                val bars = counts.mapIndexed { index, count ->
                    val barWidth = (cell * 0.6f).coerceAtMost(maxBarWidth)
                    val height = ((size.height - verticalPad) * count / maxCount)
                        .coerceAtLeast(if (count == 0) 0f else minPositiveHeight)
                    Offset(cell * index + (cell - barWidth) / 2f, size.height - height) to Size(barWidth, height)
                }
                onDrawBehind {
                    drawLine(base, Offset(0f, baseY), Offset(size.width, baseY), baseStroke)
                    bars.forEach { (topLeft, barSize) ->
                        if (barSize.height > 0f) {
                            drawRoundRect(fill, topLeft, barSize, cornerRadius)
                        }
                    }
                }
            }
        }
    }
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clearAndSetSemantics { }
            .then(drawModifier)
    )
}

private val insightFullDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

internal fun insightNumber(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
    .trimEnd('0').trimEnd('.', ',')

internal fun insightLoggedNumber(value: Float): String = value.toString().removeSuffix(".0")

internal fun insightDate(timestampMs: Long): String {
    val locale = Locale.getDefault()
    val formatter = insightFullDateFormatters.computeIfAbsent(locale) { loc ->
        DateTimeFormatter.ofPattern("MMM d, yyyy", loc)
    }
    return Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).format(formatter)
}
