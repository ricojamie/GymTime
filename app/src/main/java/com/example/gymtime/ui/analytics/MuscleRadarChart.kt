package com.example.gymtime.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.MuscleDistribution
import com.example.gymtime.ui.theme.LocalAppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MuscleRadarChart(
    data: List<MuscleDistribution>,
    selectedRangeLabel: String,
    modifier: Modifier = Modifier
) {
    val cardColor = LocalAppColors.current.surfaceCards
    val emptyColor = LocalAppColors.current.textSecondary
    val gridColor = LocalAppColors.current.textTertiary.copy(alpha = 0.18f)
    val labelColor = LocalAppColors.current.textPrimary
    val accentColor = MaterialTheme.colorScheme.primary

    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(cardColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No body-part data yet", color = emptyColor)
        }
        return
    }

    val density = LocalDensity.current
    val chartData = remember(data) {
        val nonCardio = data
            .filterNot { it.muscle.equals("Cardio", ignoreCase = true) }
            .sortedBy { preferredRadarIndex(it.muscle) }

        val positiveOnly = nonCardio.filter { it.setVolume > 0 }
        when {
            positiveOnly.size >= 3 -> positiveOnly
            nonCardio.size >= 3 -> nonCardio
            else -> positiveOnly
        }
    }

    if (chartData.size < 3) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(cardColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Log at least 3 body parts for the radar chart", color = emptyColor)
        }
        return
    }

    val maxValue = remember(chartData) { maxOf(1, chartData.maxOfOrNull { it.setVolume } ?: 1) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(cardColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Sets Per Body Part",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Text(
            text = selectedRangeLabel,
            color = LocalAppColors.current.textTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.size(12.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val center = Offset(widthPx / 2f, heightPx / 2f)
            val radius = minOf(widthPx, heightPx) * 0.26f
            val labelRadius = radius + with(density) { 44.dp.toPx() }
            val labelWidthPx = with(density) { 96.dp.toPx() }
            val stepCount = 4

            Canvas(modifier = Modifier.matchParentSize()) {
                repeat(stepCount) { index ->
                    val factor = (index + 1) / stepCount.toFloat()
                    val ringPath = Path()
                    chartData.forEachIndexed { pointIndex, _ ->
                        val angle = radarAngle(pointIndex, chartData.size)
                        val point = Offset(
                            x = center.x + cos(angle).toFloat() * radius * factor,
                            y = center.y + sin(angle).toFloat() * radius * factor
                        )
                        if (pointIndex == 0) {
                            ringPath.moveTo(point.x, point.y)
                        } else {
                            ringPath.lineTo(point.x, point.y)
                        }
                    }
                    ringPath.close()
                    drawPath(
                        path = ringPath,
                        color = gridColor,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                chartData.forEachIndexed { pointIndex, _ ->
                    val angle = radarAngle(pointIndex, chartData.size)
                    drawLine(
                        color = gridColor,
                        start = center,
                        end = Offset(
                            x = center.x + cos(angle).toFloat() * radius,
                            y = center.y + sin(angle).toFloat() * radius
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val polygon = Path()
                chartData.forEachIndexed { pointIndex, entry ->
                    val factor = entry.setVolume / maxValue.toFloat()
                    val angle = radarAngle(pointIndex, chartData.size)
                    val point = Offset(
                        x = center.x + cos(angle).toFloat() * radius * factor,
                        y = center.y + sin(angle).toFloat() * radius * factor
                    )
                    if (pointIndex == 0) {
                        polygon.moveTo(point.x, point.y)
                    } else {
                        polygon.lineTo(point.x, point.y)
                    }
                    drawCircle(accentColor, radius = 4.dp.toPx(), center = point)
                }
                polygon.close()

                drawPath(
                    path = polygon,
                    color = accentColor.copy(alpha = 0.22f)
                )
                drawPath(
                    path = polygon,
                    color = accentColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            chartData.forEachIndexed { index, item ->
                val angle = radarAngle(index, chartData.size)
                val rawCenterX = center.x + cos(angle).toFloat() * labelRadius
                val rawCenterY = center.y + sin(angle).toFloat() * labelRadius
                val clampedX = rawCenterX.coerceIn(labelWidthPx / 2f, widthPx - labelWidthPx / 2f)
                val xDp = with(density) { (clampedX - (labelWidthPx / 2f)).toDp() }
                val yDp = with(density) {
                    val topOffsetPx = 18.dp.toPx()
                    val bottomPaddingPx = 44.dp.toPx()
                    (rawCenterY - topOffsetPx).coerceIn(0f, heightPx - bottomPaddingPx).toDp()
                }

                Text(
                    text = "${item.muscle}\n${item.setVolume} sets",
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .width(96.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun radarAngle(index: Int, count: Int): Double {
    return (-PI / 2.0) + ((2 * PI) / count) * index
}

private fun preferredRadarIndex(muscle: String): Int {
    return when (muscle.lowercase()) {
        "shoulders" -> 0
        "chest" -> 1
        "triceps" -> 2
        "core" -> 3
        "legs" -> 4
        "back" -> 5
        "biceps" -> 6
        else -> Int.MAX_VALUE
    }
}
