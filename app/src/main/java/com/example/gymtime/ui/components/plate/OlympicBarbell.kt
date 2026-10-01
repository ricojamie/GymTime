package com.example.gymtime.ui.components.plate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtime.util.PlateCalculator

/** A code-native illustration. The exact list below the drawing remains the loadout authority. */
@Composable
internal fun OlympicBarbell(
    platesPerSide: List<Float>,
    barWeight: Float,
    loadingSides: Int,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val plates = platesPerSide.sortedDescending()
    val visible = plates.take(7)
    val hidden = plates.size - visible.size
    val sideDescription = if (loadingSides == 1) "right sleeve only" else "each loaded side"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(122.dp).semantics {
                contentDescription = "${PlateCalculator.formatWeight(barWeight)} pound Olympic style bar, " +
                    "${plates.size} plates on $sideDescription. Exact sizes are listed below." +
                    if (hidden > 0) " Drawing shows seven plates; $hidden additional plates omitted per loaded side." else ""
            }
        ) {
            val middle = size.height * 0.49f
            val shaftStart = size.width * 0.25f
            val shaftEnd = size.width * 0.75f
            val sleeveStart = size.width * 0.025f
            val sleeveEnd = size.width * 0.975f
            val shaftHeight = 7.dp.toPx()
            val sleeveHeight = 14.dp.toPx()

            // Soft contact shadow gives the equipment depth without elevating the entire screen.
            drawOval(
                color = Color.Black.copy(alpha = 0.10f),
                topLeft = Offset(size.width * 0.035f, middle + 39.dp.toPx()),
                size = Size(size.width * 0.93f, 7.dp.toPx())
            )
            metal(sleeveStart, shaftStart, middle, sleeveHeight, 3.dp.toPx())
            metal(shaftEnd, sleeveEnd, middle, sleeveHeight, 3.dp.toPx())
            metal(shaftStart, shaftEnd, middle, shaftHeight, 2.dp.toPx())

            // Knurled grip zones and smooth center make the silhouette recognizably a barbell.
            listOf(0.29f to 0.42f, 0.58f to 0.71f).forEach { (start, end) ->
                var x = size.width * start
                while (x < size.width * end) {
                    drawLine(Color(0xFF646C78).copy(alpha = 0.72f), Offset(x, middle - shaftHeight / 2f),
                        Offset(x + 3.dp.toPx(), middle + shaftHeight / 2f), strokeWidth = 0.6.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.60f), Offset(x, middle + shaftHeight / 2f),
                        Offset(x + 3.dp.toPx(), middle - shaftHeight / 2f), strokeWidth = 0.5.dp.toPx())
                    x += 3.dp.toPx()
                }
            }
            listOf(0.435f, 0.565f).forEach { fraction ->
                drawLine(Color(0xFF495260), Offset(size.width * fraction, middle - shaftHeight / 2f),
                    Offset(size.width * fraction, middle + shaftHeight / 2f), strokeWidth = 1.dp.toPx())
            }
            // Chrome shoulders physically separate the shaft from the thicker loading sleeves.
            metal(shaftStart - 4.dp.toPx(), shaftStart + 1.dp.toPx(), middle, 23.dp.toPx(), 1.dp.toPx())
            metal(shaftEnd - 1.dp.toPx(), shaftEnd + 4.dp.toPx(), middle, 23.dp.toPx(), 1.dp.toPx())

            fun drawStack(right: Boolean) {
                var cursor = if (right) shaftEnd + 5.dp.toPx() else shaftStart - 5.dp.toPx()
                val plateZone = size.width * 0.195f
                val desiredWidths = visible.map { if (it >= 25f) 12.dp.toPx() else 8.dp.toPx() }
                val desiredTotal = desiredWidths.sum() + visible.size * 1.dp.toPx()
                val scale = if (desiredTotal > plateZone) plateZone / desiredTotal else 1f
                visible.forEachIndexed { index, weight ->
                    val width = desiredWidths[index] * scale
                    val x = if (right) cursor else cursor - width
                    val height = when {
                        weight >= 45f -> 91.dp.toPx()
                        weight >= 35f -> 82.dp.toPx()
                        weight >= 25f -> 73.dp.toPx()
                        weight >= 15f -> 63.dp.toPx()
                        weight >= 10f -> 55.dp.toPx()
                        weight >= 5f -> 44.dp.toPx()
                        else -> 34.dp.toPx()
                    }
                    plate(x, middle - height / 2f, width, height, Color(PlateCalculator.getPlateColor(weight)))
                    cursor += (width + 1.dp.toPx()) * if (right) 1f else -1f
                }
                // Collar sits outside the shown load. Extra plates are disclosed, never painted as the complete stack.
                if (visible.isNotEmpty()) {
                    val width = 4.dp.toPx()
                    val x = if (right) cursor + 1.dp.toPx() else cursor - width - 1.dp.toPx()
                    metal(x, x + width, middle, 26.dp.toPx(), 1.dp.toPx())
                }
            }
            if (loadingSides > 1) drawStack(right = false)
            drawStack(right = true)
            metal(sleeveStart, sleeveStart + 3.dp.toPx(), middle, 17.dp.toPx(), 1.dp.toPx())
            metal(sleeveEnd - 3.dp.toPx(), sleeveEnd, middle, 17.dp.toPx(), 1.dp.toPx())
        }
        Text(
            text = when {
                hidden > 0 -> "+$hidden more on each loaded side · full load listed below"
                plates.isEmpty() -> "Bare bar · ${PlateCalculator.formatWeight(barWeight)} lb"
                loadingSides == 1 -> "One sleeve loaded · ${PlateCalculator.formatWeight(barWeight)} lb bar"
                loadingSides > 2 -> "$loadingSides loaded sides · two representative sleeves shown"
                else -> "Mirror this load · ${PlateCalculator.formatWeight(barWeight)} lb bar"
            },
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
    }
}

private fun DrawScope.metal(start: Float, end: Float, center: Float, height: Float, radius: Float) {
    val top = center - height / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF8A939F), Color(0xFFE8EDF1), Color(0xFFB5BFCA), Color(0xFF646D7A)),
            startY = top, endY = top + height
        ),
        topLeft = Offset(start, top), size = Size((end - start).coerceAtLeast(1f), height),
        cornerRadius = CornerRadius(radius, radius)
    )
    drawLine(Color.White.copy(alpha = 0.8f), Offset(start + radius, top + height * 0.23f),
        Offset(end - radius, top + height * 0.23f), strokeWidth = 0.7.dp.toPx())
}

private fun DrawScope.plate(x: Float, y: Float, width: Float, height: Float, color: Color) {
    val radius = CornerRadius(2.dp.toPx(), 3.dp.toPx())
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(color.copy(red = color.red * 0.60f, green = color.green * 0.60f, blue = color.blue * 0.60f),
                color, color, Color.Black.copy(alpha = 0.65f)),
            startX = x, endX = x + width
        ),
        topLeft = Offset(x, y), size = Size(width, height), cornerRadius = radius
    )
    drawRoundRect(Color.Black.copy(alpha = 0.27f), Offset(x, y), Size(width, height), radius,
        style = Stroke(0.8.dp.toPx()))
    drawLine(Color.White.copy(alpha = 0.34f), Offset(x + width * 0.28f, y + 4.dp.toPx()),
        Offset(x + width * 0.28f, y + height - 4.dp.toPx()), strokeWidth = 0.8.dp.toPx())
    // Moulded rim detail, restrained enough to remain legible at phone width.
    listOf(0.09f, 0.91f).forEach { fraction ->
        drawLine(Color.Black.copy(alpha = 0.24f), Offset(x + 1.dp.toPx(), y + height * fraction),
            Offset(x + width - 1.dp.toPx(), y + height * fraction), strokeWidth = 1.3.dp.toPx())
    }
}
