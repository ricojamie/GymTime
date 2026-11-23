package com.example.gymtime.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.tabular
import kotlin.math.max
import kotlin.math.roundToInt

private val GoalColor = Color(0xFFFBBF24)   // amber
private val BestColor = Color(0xFF2DD4BF)   // teal

/** Draggable tick ruler for the active weight or reps field. */
@Composable
fun RulerSliderInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    minValue: Int = 0,
    centered: Boolean = false,
    lastValue: Int? = null,
    goalValue: Int? = null,
    bestValue: Int? = null,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val textPrimary = scheme.onSurface
    val textMuted = scheme.onSurfaceVariant
    val density = LocalDensity.current

    val parsedFloat = value.toFloatOrNull()
    val currentInt = parsedFloat?.roundToInt()
    val displayText = when {
        value.isBlank() -> ""
        parsedFloat == null -> value
        parsedFloat % 1f == 0f -> parsedFloat.toInt().toString()
        else -> value
    }

    // Weight uses the first value we see as its stable center so the visible
    // ruler window does not jump while dragging. Reps can grow upward with the
    // live value because that range is anchored at zero.
    var initialValue by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentInt) {
        if (initialValue == null && currentInt != null) initialValue = currentInt
    }

    // Only `lo` matters now (it seeds the active-value fallback); the ruler scrolls
    // infinitely around the fixed center line, so there is no upper bound to recompute
    // — keeping it out of the gesture keys is what keeps dragging smooth.
    val lo: Int = if (centered) {
        val center = initialValue ?: lastValue ?: bestValue ?: (minValue + step * 9)
        val rawLo = ((center - step * 10).toFloat() / step).roundToInt() * step
        max(minValue, rawLo)
    } else {
        minValue
    }
    val majorEvery = if (centered) 4 else 5

    val activeValue = currentInt ?: initialValue ?: lastValue ?: bestValue ?: goalValue ?: lo
    val activeValueState by rememberUpdatedState(activeValue)
    val onValueChangeState by rememberUpdatedState(onValueChange)

    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartValue by remember { mutableStateOf<Int?>(null) }
    var lastEmittedDragValue by remember { mutableStateOf<Int?>(null) }
    val sidePadPx = with(density) { (if (compact) 8.dp else 16.dp).toPx() }
    val labelTextSizePx = with(density) { 11.sp.toPx() }
    val visibleStepCount = if (compact) 12f else 20f

    fun tickSpacing(width: Float): Float {
        val usableWidth = (width - sidePadPx * 2f).coerceAtLeast(1f)
        return usableWidth / visibleStepCount
    }

    fun valueFromOffset(baseValue: Int, offsetX: Float, width: Float): Int {
        val rawSteps = (offsetX / tickSpacing(width)).roundToInt()
        return (baseValue + rawSteps * step).coerceAtLeast(minValue)
    }

    fun valueFromX(x: Float, width: Float): Int {
        if (width <= 0f) return activeValueState.coerceAtLeast(minValue)
        val centerX = width / 2f
        return valueFromOffset(activeValueState, x - centerX, width)
    }

    fun xFor(v: Int, width: Float): Float {
        val centerX = width / 2f
        return centerX + (v - activeValue).toFloat() / step * tickSpacing(width)
    }

    val labelPaint = remember(textMuted, labelTextSizePx) {
        Paint().apply {
            color = textMuted.toArgb()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            textSize = labelTextSizePx
        }
    }

    IronLogCard(
        modifier = modifier,
        showBorder = true,
        containerColor = scheme.surfaceContainerLow,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else IronLogSpace.CardPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = "$label ruler"
                    stateDescription = displayText
                }
                .pointerInput(step, compact, minValue, density.density) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        if (width <= 0f) return@detectTapGestures
                        val nextValue = valueFromX(offset.x, width)
                        onValueChangeState(nextValue.toString())
                    }
                }
                .pointerInput(step, compact, minValue, density.density) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            dragStartValue = activeValueState
                            lastEmittedDragValue = activeValueState
                        },
                        onDragEnd = {
                            dragStartValue = null
                            lastEmittedDragValue = null
                        },
                        onDragCancel = {
                            dragStartValue = null
                            lastEmittedDragValue = null
                        }
                    ) { change, _ ->
                        change.consume()
                        val width = size.width.toFloat()
                        if (width <= 0f) return@detectHorizontalDragGestures
                        val baseValue = dragStartValue ?: activeValueState
                        // Tape-style drag: the ruler follows your finger, so swiping
                        // right lowers the value and swiping left raises it.
                        val nextValue = valueFromOffset(baseValue, dragStartX - change.position.x, width)
                        if (nextValue != lastEmittedDragValue) {
                            lastEmittedDragValue = nextValue
                            onValueChangeState(nextValue.toString())
                        }
                    }
                }
        ) {
            val width = size.width
            if (width <= 0f) return@Canvas
            val centerX = width / 2f
            val midY = size.height * 0.42f
            val minorH = 7.dp.toPx()
            val majorH = 10.dp.toPx()
            val tickW = 1.5.dp.toPx()
            val markerTop = midY - majorH - 4.dp.toPx()
            val markerBottom = midY + majorH + 4.dp.toPx()
            val visiblePadding = sidePadPx

            fun visibleX(x: Float): Boolean = x in -visiblePadding..(width + visiblePadding)

            // Ticks + major labels, drawn relative to the fixed center line.
            val activeStepIndex = activeValue / step
            val ticksEachSide = ((width / tickSpacing(width)) / 2f).roundToInt() + 2
            var tickIndex = max(0, activeStepIndex - ticksEachSide)
            val lastTickIndex = max(tickIndex, activeStepIndex + ticksEachSide)
            while (tickIndex <= lastTickIndex) {
                val v = tickIndex * step
                val x = xFor(v, width)
                if (visibleX(x)) {
                    val isMajor = tickIndex % majorEvery == 0
                    val h = if (isMajor) majorH else minorH
                    drawLine(
                        color = textPrimary.copy(alpha = if (isMajor) 0.38f else 0.14f),
                        start = Offset(x, midY - h),
                        end = Offset(x, midY + h),
                        strokeWidth = tickW,
                        cap = StrokeCap.Round
                    )
                    if (isMajor) {
                        val tickLabel = v.toString()
                        val halfLabelWidth = labelPaint.measureText(tickLabel) / 2f
                        if (!compact || (x - halfLabelWidth >= 0f && x + halfLabelWidth <= width)) {
                            drawContext.canvas.nativeCanvas.drawText(
                                tickLabel,
                                x,
                                size.height - 3.dp.toPx(),
                                labelPaint
                            )
                        }
                    }
                }
                tickIndex++
            }

            if (!compact && lastValue != null) {
                val lx = xFor(lastValue, width)
                if (visibleX(lx)) {
                    drawLine(
                        color = textMuted,
                        start = Offset(lx, markerTop),
                        end = Offset(lx, markerBottom),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            if (!compact && bestValue != null) {
                val bx = xFor(bestValue, width)
                if (visibleX(bx)) {
                    drawLine(
                        color = BestColor,
                        start = Offset(bx, markerTop),
                        end = Offset(bx, markerBottom),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            if (!compact && goalValue != null) {
                val gx = xFor(goalValue, width)
                if (visibleX(gx)) {
                    drawLine(
                        color = GoalColor,
                        start = Offset(gx, markerTop),
                        end = Offset(gx, markerBottom),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Fixed current logging line. The ruler and context markers move beneath it.
            drawLine(
                color = accent.copy(alpha = if (currentInt != null) 1f else 0.35f),
                start = Offset(centerX, markerTop),
                end = Offset(centerX, markerBottom),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Compact inputs have their record and last-workout context in the screen's cards.
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentInt != null) LegendItem(accent, "You", displayText, textMuted)
                lastValue?.let { LegendItem(textMuted, "Last", it.toString(), textMuted) }
                goalValue?.let { LegendItem(GoalColor, "Goal", it.toString(), textMuted) }
                bestValue?.let { LegendItem(BestColor, "Best", it.toString(), textMuted) }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label · $value",
            color = textColor,
            style = MaterialTheme.typography.labelSmall.tabular()
        )
    }
}
