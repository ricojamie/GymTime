package com.example.gymtime.ui.analytics.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun InsightPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
internal fun InsightPrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalLoggerActionColors.current
    Button(onClick, modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(
        containerColor = colors.fill, contentColor = colors.onFill
    )) { Text(text) }
}

@Composable
internal fun InsightSectionTitle(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal data class InsightChartDot(val timestampMs: Long, val value: Double)

/** Decoration only: exact values and dates are exposed as text by the caller. */
@Composable
internal fun InsightLineChart(points: List<InsightChartDot>, unit: String, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val spread = (maximum - minimum).takeIf { it > 0.0 } ?: (maximum.coerceAtLeast(1.0) * 0.1)
    val lower = (minimum - spread * 0.15).coerceAtLeast(0.0)
    val upper = maximum + spread * 0.15
    val firstDate = points.minOf { it.timestampMs }
    val dateSpan = (points.maxOf { it.timestampMs } - firstDate).coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${insightNumber(minimum)}–${insightNumber(maximum)} $unit", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(128.dp).clearAndSetSemantics { }) {
            val gutter = 8.dp.toPx()
            val chartWidth = (size.width - gutter * 2).coerceAtLeast(1f)
            val chartHeight = (size.height - gutter * 2).coerceAtLeast(1f)
            repeat(3) { i ->
                val y = gutter + chartHeight * i / 2f
                drawLine(gridColor, Offset(gutter, y), Offset(size.width - gutter, y), 1.dp.toPx())
            }
            val offsets = points.map { point ->
                Offset(
                    if (points.size == 1) size.width / 2 else gutter + chartWidth * ((point.timestampMs - firstDate).toDouble() / dateSpan).toFloat(),
                    gutter + chartHeight * (1.0 - (point.value - lower) / (upper - lower)).toFloat()
                )
            }
            if (offsets.size > 1) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
            }
            offsets.forEach { drawCircle(lineColor, 4.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(insightDate(firstDate), style = MaterialTheme.typography.labelMedium)
            if (points.size > 1) Text(insightDate(points.maxOf { it.timestampMs }), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Labels/counts remain accessible outside the decorative bars. */
@Composable
internal fun InsightWeeklyBars(counts: List<Int>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.secondary
    val base = MaterialTheme.colorScheme.outlineVariant
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(modifier.fillMaxWidth().height(80.dp).clearAndSetSemantics { }) {
        if (counts.isEmpty()) return@Canvas
        val cell = size.width / counts.size
        drawLine(base, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 1.dp.toPx())
        counts.forEachIndexed { index, count ->
            val barWidth = (cell * 0.6f).coerceAtMost(28.dp.toPx())
            val height = ((size.height - 8.dp.toPx()) * count / maxCount).coerceAtLeast(if (count == 0) 0f else 4.dp.toPx())
            drawRoundRect(fill, Offset(cell * index + (cell - barWidth) / 2, size.height - height),
                androidx.compose.ui.geometry.Size(barWidth, height), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        }
    }
}

internal fun insightNumber(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
    .trimEnd('0').trimEnd('.', ',')

internal fun insightLoggedNumber(value: Float): String = value.toString().removeSuffix(".0")

internal fun insightDate(timestampMs: Long): String = Instant.ofEpochMilli(timestampMs)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
