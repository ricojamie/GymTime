package com.example.gymtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.LocalAppColors
import kotlin.math.max
import kotlin.math.roundToInt

private val GoalColor = Color(0xFFFBBF24)   // amber
private val BestColor = Color(0xFF2DD4BF)   // teal

/**
 * A tick-ruler slider input for whole-number values (reps, weight).
 *
 * Drag the ruler (or tap a position) to set the value, nudge with the +/- chips,
 * or tap the big readout to type an exact value. Optional markers show what was
 * done [lastValue] last time, the [goalValue] (rep target) with a target band, and
 * the all-time [bestValue].
 *
 * The value is held as a String so this is a drop-in for the existing numeric
 * inputs - drags emit clean integers, while tap-to-type still allows decimals.
 */
@Composable
fun RulerSliderInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    minValue: Int = 0,
    unitSuffix: String = "",
    centered: Boolean = false,
    lastValue: Int? = null,
    goalValue: Int? = null,
    bestValue: Int? = null
) {
    val colors = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    var trackWidthPx by remember { mutableStateOf(0f) }
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartValue by remember { mutableStateOf<Int?>(null) }
    val sidePadPx = with(density) { 16.dp.toPx() }
    val visibleStepCount = 20f

    fun tickSpacing(width: Float): Float {
        val usableWidth = (width - sidePadPx * 2f).coerceAtLeast(1f)
        return usableWidth / visibleStepCount
    }

    fun valueFromOffset(baseValue: Int, offsetX: Float, width: Float): Int {
        val rawSteps = (offsetX / tickSpacing(width)).roundToInt()
        return (baseValue + rawSteps * step).coerceAtLeast(minValue)
    }

    fun valueFromX(x: Float): Int {
        if (trackWidthPx <= 0f) return activeValueState.coerceAtLeast(minValue)
        val centerX = trackWidthPx / 2f
        return valueFromOffset(activeValueState, x - centerX, trackWidthPx)
    }

    fun xFor(v: Int, width: Float): Float {
        val centerX = width / 2f
        return centerX + (v - activeValue).toFloat() / step * tickSpacing(width)
    }

    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    fun startEditing() {
        editing = true
    }

    val labelPaint = remember(colors.textTertiary) {
        android.graphics.Paint().apply {
            color = colors.textTertiary.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    labelPaint.textSize = with(density) { 11.sp.toPx() }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCards),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "tap to type",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { startEditing() }
                )
            }

            // Readout with +/- nudge chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NudgeChip(symbol = "-", colors.textTertiary) {
                    val base = currentInt ?: lastValue ?: bestValue ?: lo
                    onValueChange((base - step).coerceAtLeast(minValue).toString())
                }

                if (editing) {
                    BasicTextField(
                        value = value,
                        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }.take(6)) },
                        textStyle = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontSize = 28.sp,
                            color = colors.textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                editing = false
                                focusManager.clearFocus()
                            }
                        ),
                        cursorBrush = SolidColor(colors.cursor),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .focusRequester(focusRequester)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { startEditing() },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = displayText.ifBlank { "0" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = if (displayText.isBlank()) colors.textTertiary.copy(alpha = 0.35f) else colors.textPrimary
                        )
                        if (unitSuffix.isNotEmpty()) {
                            Text(
                                text = " $unitSuffix",
                                fontSize = 13.sp,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                NudgeChip(symbol = "+", colors.textTertiary) {
                    val base = currentInt ?: lastValue ?: bestValue ?: lo
                    onValueChange((base + step).toString())
                }
            }

            // Ruler
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .onSizeChanged { trackWidthPx = it.width.toFloat() }
                    .pointerInput(step) {
                        detectTapGestures { offset -> onValueChange(valueFromX(offset.x).toString()) }
                    }
                    .pointerInput(step) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                dragStartValue = activeValueState
                            },
                            onDragEnd = { dragStartValue = null },
                            onDragCancel = { dragStartValue = null }
                        ) { change, _ ->
                            change.consume()
                            val baseValue = dragStartValue ?: activeValueState
                            // Tape-style drag: the ruler follows your finger, so swiping
                            // right lowers the value and swiping left raises it.
                            val nextValue = valueFromOffset(baseValue, dragStartX - change.position.x, trackWidthPx)
                            onValueChange(nextValue.toString())
                        }
                    }
            ) {
                val width = size.width
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
                            color = colors.textPrimary.copy(alpha = if (isMajor) 0.38f else 0.14f),
                            start = Offset(x, midY - h),
                            end = Offset(x, midY + h),
                            strokeWidth = tickW,
                            cap = StrokeCap.Round
                        )
                        if (isMajor) {
                            drawContext.canvas.nativeCanvas.drawText(
                                v.toString(), x, size.height - 3.dp.toPx(), labelPaint
                            )
                        }
                    }
                    tickIndex++
                }

                // Last-time marker (gray line)
                if (lastValue != null) {
                    val lx = xFor(lastValue, width)
                    if (visibleX(lx)) drawLine(
                        color = colors.textTertiary,
                        start = Offset(lx, markerTop),
                        end = Offset(lx, markerBottom),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Best marker (teal line)
                if (bestValue != null) {
                    val bx = xFor(bestValue, width)
                    if (visibleX(bx)) drawLine(
                        color = BestColor,
                        start = Offset(bx, markerTop),
                        end = Offset(bx, markerBottom),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Goal marker (amber line)
                if (goalValue != null) {
                    val gx = xFor(goalValue, width)
                    if (visibleX(gx)) drawLine(
                        color = GoalColor,
                        start = Offset(gx, markerTop),
                        end = Offset(gx, markerBottom),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
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

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentInt != null) LegendItem(accent, "You", displayText)
                lastValue?.let { LegendItem(colors.textTertiary, "Last", it.toString()) }
                goalValue?.let { LegendItem(GoalColor, "Goal", it.toString()) }
                bestValue?.let { LegendItem(BestColor, "Best", it.toString()) }
            }
        }
    }
}

@Composable
private fun NudgeChip(symbol: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
        Text(
            text = "$label - $value",
            color = colors.textTertiary,
            fontSize = 11.sp
        )
    }
}
