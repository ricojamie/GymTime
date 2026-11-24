package com.example.gymtime.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private val GoalColorDark = Color(0xFFFBBF24)   // amber (dark canvas)
private val GoalColorLight = Color(0xFF92400E)  // deep amber-ochre >= 4.5:1 on light well
private val BestColorDark = Color(0xFF2DD4BF)   // teal (dark canvas)
private val BestColorLight = Color(0xFF115E59)  // deep teal >= 4.5:1 on light well

/**
 * Vernier caliper / lens-barrel tick ruler with momentum fling inertia,
 * magnetic nearest-tick snap ([IronMotion.MagneticDetent]), glowing crosshair needle,
 * pin flags, and [LocalIronHaptics] tick feedback.
 */
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
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current
    val coroutineScope = rememberCoroutineScope()

    val accent = surfaces.accent
    val goalColor = if (surfaces.isDark) GoalColorDark else GoalColorLight
    val bestColor = if (surfaces.isDark) BestColorDark else BestColorLight
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

    var initialValue by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(currentInt) {
        if (initialValue == null && currentInt != null) initialValue = currentInt
    }

    val lo: Int = if (centered) {
        val center = initialValue ?: lastValue ?: bestValue ?: (minValue + step * 9)
        val rawLo = ((center - step * 10).toFloat() / step).roundToInt() * step
        max(minValue, rawLo)
    } else {
        minValue
    }
    val majorEvery = if (centered) 4 else 5

    val activeValue = currentInt ?: initialValue ?: lastValue ?: bestValue ?: goalValue ?: lo
    val externalFloat = parsedFloat ?: activeValue.toFloat()

    val activeValueState by rememberUpdatedState(activeValue)
    val currentIntState = rememberUpdatedState(currentInt)
    val externalFloatState by rememberUpdatedState(externalFloat)
    val onValueChangeState by rememberUpdatedState(onValueChange)
    val hapticsState by rememberUpdatedState(haptics)
    val reducedMotionState by rememberUpdatedState(reducedMotion)
    val lastValueState = rememberUpdatedState(lastValue)
    val goalValueState = rememberUpdatedState(goalValue)
    val bestValueState = rememberUpdatedState(bestValue)

    val unitSnapSpec = remember { IronMotion.magneticDetent<Float>(visibilityThreshold = 0.02f) }
    val visualValueAnim = remember { Animatable(externalFloat) }
    val needlePulseAnim = remember { Animatable(0f) }
    var dragVisualValue by remember { mutableStateOf<Float?>(null) }
    var isGestureActive by remember { mutableStateOf(false) }
    val isGestureActiveState = rememberUpdatedState(isGestureActive)
    val gestureTokenHolder = remember { intArrayOf(0) }
    var activeFlingJob by remember { mutableStateOf<Job?>(null) }
    var pulseJob by remember { mutableStateOf<Job?>(null) }

    fun triggerNeedlePulse() {
        if (reducedMotionState) return
        pulseJob?.cancel()
        pulseJob = coroutineScope.launch {
            needlePulseAnim.snapTo(1f)
            needlePulseAnim.animateTo(0f, IronMotion.EffectDefault)
        }
    }

    LaunchedEffect(externalFloat, isGestureActive, reducedMotion) {
        if (!isGestureActive && abs(visualValueAnim.value - externalFloat) > 0.001f) {
            if (reducedMotion) {
                visualValueAnim.snapTo(externalFloat)
            } else {
                visualValueAnim.animateTo(externalFloat, unitSnapSpec)
            }
        }
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            pulseJob?.cancel()
            needlePulseAnim.snapTo(0f)
        }
    }

    val sidePadPx = with(density) { (if (compact) 8.dp else 14.dp).toPx() }
    val labelTextSizePx = with(density) { 10.5.sp.toPx() }
    val flagTextSizePx = with(density) { 8.5.sp.toPx() }
    val visibleStepCount = if (compact) 12f else 20f

    fun tickSpacing(width: Float): Float {
        val usableWidth = (width - sidePadPx * 2f).coerceAtLeast(1f)
        return usableWidth / visibleStepCount
    }

    fun snapToStep(rawValue: Float): Int =
        ((rawValue / step).roundToInt() * step).coerceAtLeast(minValue)

    fun valueFromX(x: Float, width: Float): Int {
        if (width <= 0f) return activeValueState.coerceAtLeast(minValue)
        val centerX = width / 2f
        val base = dragVisualValue ?: visualValueAnim.value
        val raw = base + ((x - centerX) / tickSpacing(width)) * step
        return snapToStep(raw)
    }

    val labelPaint = remember(textMuted, labelTextSizePx) {
        Paint().apply {
            color = textMuted.toArgb()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            textSize = labelTextSizePx
            fontFeatureSettings = "tnum"
        }
    }
    val flagPaint = remember(flagTextSizePx) {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            textSize = flagTextSizePx
            isFakeBoldText = true
            fontFeatureSettings = "tnum"
        }
    }

    val troughColor = surfaces.wellFill
    val bezelDivider = if (surfaces.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.08f)

    IronLogCard(
        modifier = modifier,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IronLogRecessedWell(
            active = isGestureActive,
            accentColor = accent,
            shape = IronLogRadius.KeycapShape,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 52.dp else 58.dp)
                    .semantics {
                        contentDescription = "$label ruler"
                        stateDescription = displayText
                    }
                    .pointerInput(step, compact, minValue, density.density) {
                        detectTapGestures { offset ->
                            val width = size.width.toFloat()
                            if (width <= 0f) return@detectTapGestures
                            val myToken = ++gestureTokenHolder[0]
                            activeFlingJob?.cancel()
                            val nextValue = valueFromX(offset.x, width)
                            val snapStart = dragVisualValue ?: visualValueAnim.value
                            isGestureActive = true
                            hapticsState.tick()
                            triggerNeedlePulse()
                            onValueChangeState(nextValue.toString())
                            activeFlingJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    visualValueAnim.snapTo(snapStart)
                                    if (gestureTokenHolder[0] == myToken) {
                                        dragVisualValue = null
                                    }
                                    visualValueAnim.animateTo(
                                        targetValue = nextValue.toFloat(),
                                        animationSpec = IronMotion.orSnap(
                                            reducedMotionState,
                                            unitSnapSpec
                                        )
                                    )
                                } finally {
                                    if (gestureTokenHolder[0] == myToken) {
                                        isGestureActive = false
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(step, compact, minValue, density.density) {
                        val velocityTracker = VelocityTracker()
                        var dragStartX = 0f
                        var dragStartFloat = 0f
                        var lastEmittedStep: Int? = null
                        var hasMovedThreshold = false

                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                gestureTokenHolder[0]++
                                activeFlingJob?.cancel()
                                isGestureActive = true
                                velocityTracker.resetTracking()
                                dragStartX = offset.x
                                dragStartFloat = dragVisualValue ?: visualValueAnim.value
                                dragVisualValue = dragStartFloat
                                lastEmittedStep = currentIntState.value ?: activeValueState
                                hasMovedThreshold = false
                            },
                            onDragEnd = {
                                val myToken = gestureTokenHolder[0]
                                val endValue = (dragVisualValue ?: visualValueAnim.value)
                                    .coerceAtLeast(minValue.toFloat())
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val spacing = tickSpacing(width)
                                val vx = velocityTracker.calculateVelocity().x
                                val valueVelocity = (-vx / spacing) * step

                                activeFlingJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    try {
                                        visualValueAnim.snapTo(endValue)
                                        if (gestureTokenHolder[0] == myToken) {
                                            dragVisualValue = null
                                        }
                                        var currentStep = lastEmittedStep ?: snapToStep(endValue)
                                        val minF = minValue.toFloat()

                                        if (!reducedMotionState && abs(vx) > 140f) {
                                            visualValueAnim.updateBounds(lowerBound = minF)
                                            try {
                                                visualValueAnim.animateDecay(
                                                    initialVelocity = valueVelocity,
                                                    animationSpec = exponentialDecay(frictionMultiplier = 2.4f)
                                                ) {
                                                    val candidate = snapToStep(this.value.coerceAtLeast(minF))
                                                    if (candidate != currentStep) {
                                                        currentStep = candidate
                                                        hapticsState.frequentTick()
                                                        triggerNeedlePulse()
                                                        onValueChangeState(candidate.toString())
                                                    }
                                                }
                                            } finally {
                                                visualValueAnim.updateBounds(lowerBound = null)
                                            }
                                        }

                                        val finalStep = snapToStep(visualValueAnim.value.coerceAtLeast(minF))
                                        if (finalStep != currentStep || !hasMovedThreshold) {
                                            if (finalStep != currentStep) {
                                                hapticsState.frequentTick()
                                                triggerNeedlePulse()
                                            }
                                            onValueChangeState(finalStep.toString())
                                        }
                                        visualValueAnim.animateTo(
                                            targetValue = finalStep.toFloat(),
                                            animationSpec = IronMotion.orSnap(
                                                reducedMotionState,
                                                unitSnapSpec
                                            )
                                        )
                                    } finally {
                                        if (gestureTokenHolder[0] == myToken) {
                                            isGestureActive = false
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                val myToken = ++gestureTokenHolder[0]
                                val endValue = (dragVisualValue ?: visualValueAnim.value)
                                    .coerceAtLeast(minValue.toFloat())
                                activeFlingJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    try {
                                        visualValueAnim.snapTo(endValue)
                                        if (gestureTokenHolder[0] == myToken) {
                                            dragVisualValue = null
                                        }
                                        val finalStep = snapToStep(endValue)
                                        onValueChangeState(finalStep.toString())
                                        visualValueAnim.animateTo(
                                            targetValue = finalStep.toFloat(),
                                            animationSpec = IronMotion.orSnap(
                                                reducedMotionState,
                                                unitSnapSpec
                                            )
                                        )
                                    } finally {
                                        if (gestureTokenHolder[0] == myToken) {
                                            isGestureActive = false
                                        }
                                    }
                                }
                            }
                        ) { change, _ ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val width = size.width.toFloat()
                            if (width <= 0f) return@detectHorizontalDragGestures
                            val spacing = tickSpacing(width)
                            val dx = dragStartX - change.position.x
                            val rawFloat = (dragStartFloat + (dx / spacing) * step)
                                .coerceAtLeast(minValue.toFloat())
                            dragVisualValue = rawFloat
                            if (abs(dx) >= spacing * 0.2f) {
                                hasMovedThreshold = true
                            }
                            val candidate = snapToStep(rawFloat)
                            if (hasMovedThreshold && candidate != lastEmittedStep) {
                                lastEmittedStep = candidate
                                hapticsState.frequentTick()
                                triggerNeedlePulse()
                                onValueChangeState(candidate.toString())
                            }
                        }
                    }
                    .drawWithCache {
                        val width = size.width
                        val height = size.height
                        val spacing = tickSpacing(width)
                        val centerX = width * 0.5f
                        val railY = if (compact) height * 0.44f else height * 0.48f
                        val minorH = 4.5.dp.toPx()
                        val midH = 7.dp.toPx()
                        val majorH = 10.dp.toPx()
                        val minorW = 1.dp.toPx()
                        val majorW = 1.75.dp.toPx()
                        val baselineStroke = 1.dp.toPx()
                        val visiblePadding = sidePadPx
                        val ticksEachSide = ((width / spacing) / 2f).roundToInt() + 3

                        val pinTop = 2.dp.toPx()
                        val pinLineStartY = pinTop + 9.dp.toPx()
                        val markerBottom = railY + majorH + 2.dp.toPx()
                        val pinStroke = 1.75.dp.toPx()
                        val flagW = 24.dp.toPx()
                        val flagH = 10.dp.toPx()
                        val flagCorner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        val flagBaselineY = pinTop + flagH - 2.dp.toPx()
                        val tickLabelBaselineY = height - 3.dp.toPx()

                        val edgeFadeW = 22.dp.toPx().coerceAtMost(width * 0.14f)
                        val leftEdgeBrush = Brush.horizontalGradient(
                            colors = listOf(troughColor, Color.Transparent),
                            startX = 0f,
                            endX = edgeFadeW
                        )
                        val rightEdgeBrush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, troughColor),
                            startX = width - edgeFadeW,
                            endX = width
                        )

                        val needleTop = 3.dp.toPx()
                        val needleBottom = railY + majorH + 4.dp.toPx()
                        val baseGlowRadius = 12.dp.toPx()
                        val pulseGlowDelta = 8.dp.toPx()
                        val maxGlowRadius = (baseGlowRadius + pulseGlowDelta).coerceAtLeast(1f)
                        val needleCenter = Offset(centerX, railY)
                        val needleGlowBrush = Brush.radialGradient(
                            colors = listOf(accent, Color.Transparent),
                            center = needleCenter,
                            radius = maxGlowRadius
                        )
                        val caretHalf = 4.dp.toPx()
                        val caretH = 4.5.dp.toPx()
                        val caretPath = Path().apply {
                            moveTo(centerX - caretHalf, needleTop)
                            lineTo(centerX + caretHalf, needleTop)
                            lineTo(centerX, needleTop + caretH)
                            close()
                        }
                        val needleLineStartY = needleTop + caretH * 0.7f
                        val needleBaseStroke = 2.5.dp.toPx()
                        val needlePulseStrokeDelta = 1.dp.toPx()

                        onDrawBehind {
                            if (width <= 0f) return@onDrawBehind
                            val centerValue = dragVisualValue ?: visualValueAnim.value

                            fun xForFloat(v: Float): Float = centerX + (v - centerValue) / step * spacing
                            fun visibleX(x: Float): Boolean = x in -visiblePadding..(width + visiblePadding)

                            drawLine(
                                color = bezelDivider,
                                start = Offset(0f, railY),
                                end = Offset(width, railY),
                                strokeWidth = baselineStroke
                            )

                            val centerStepFloat = centerValue / step
                            val minTickIndex = max(0, floor(centerStepFloat).toInt() - ticksEachSide)
                            val maxTickIndex = max(minTickIndex, ceil(centerStepFloat).toInt() + ticksEachSide)

                            for (tickIndex in minTickIndex..maxTickIndex) {
                                val v = tickIndex * step
                                if (v < minValue) continue
                                val x = xForFloat(v.toFloat())
                                if (!visibleX(x)) continue

                                val edgeFactor = (1f - (abs(x - centerX) / (width * 0.56f)).coerceIn(0f, 1f))
                                    .coerceIn(0.18f, 1f)
                                val isMajor = tickIndex % majorEvery == 0
                                val isMid = !isMajor && (majorEvery % 2 == 0) && (tickIndex % (majorEvery / 2) == 0)
                                val halfH = when {
                                    isMajor -> majorH
                                    isMid -> midH
                                    else -> minorH
                                }
                                val tickAlpha = when {
                                    isMajor -> 0.62f * edgeFactor
                                    isMid -> 0.34f * edgeFactor
                                    else -> 0.18f * edgeFactor
                                }
                                drawLine(
                                    color = textPrimary.copy(alpha = tickAlpha),
                                    start = Offset(x, railY - halfH),
                                    end = Offset(x, railY + halfH),
                                    strokeWidth = if (isMajor) majorW else minorW,
                                    cap = StrokeCap.Round
                                )

                                if (isMajor) {
                                    val tickLabel = v.toString()
                                    val shouldDrawLabel = !compact || run {
                                        val halfLabelWidth = labelPaint.measureText(tickLabel) * 0.5f
                                        x - halfLabelWidth >= 4f && x + halfLabelWidth <= width - 4f
                                    }
                                    if (shouldDrawLabel) {
                                        labelPaint.alpha = (255 * 0.78f * edgeFactor).toInt().coerceIn(40, 255)
                                        drawContext.canvas.nativeCanvas.drawText(
                                            tickLabel,
                                            x,
                                            tickLabelBaselineY,
                                            labelPaint
                                        )
                                    }
                                }
                            }

                            if (!compact) {
                                fun drawPinFlag(targetVal: Int?, color: Color, tag: String) {
                                    if (targetVal == null) return
                                    val px = xForFloat(targetVal.toFloat())
                                    if (!visibleX(px)) return
                                    drawLine(
                                        color = color.copy(alpha = 0.85f),
                                        start = Offset(px, pinLineStartY),
                                        end = Offset(px, markerBottom),
                                        strokeWidth = pinStroke,
                                        cap = StrokeCap.Round
                                    )
                                    drawRoundRect(
                                        color = color.copy(alpha = 0.18f),
                                        topLeft = Offset(px - flagW * 0.5f, pinTop),
                                        size = Size(flagW, flagH),
                                        cornerRadius = flagCorner
                                    )
                                    flagPaint.color = color.toArgb()
                                    drawContext.canvas.nativeCanvas.drawText(
                                        tag,
                                        px,
                                        flagBaselineY,
                                        flagPaint
                                    )
                                }

                                drawPinFlag(lastValueState.value, textMuted, "LAST")
                                drawPinFlag(goalValueState.value, goalColor, "GOAL")
                                drawPinFlag(bestValueState.value, bestColor, "BEST")
                            }

                            drawRect(
                                brush = leftEdgeBrush,
                                size = Size(edgeFadeW, height)
                            )
                            drawRect(
                                brush = rightEdgeBrush,
                                topLeft = Offset(width - edgeFadeW, 0f),
                                size = Size(edgeFadeW, height)
                            )

                            val pulse = needlePulseAnim.value
                            val needleActive = currentIntState.value != null || isGestureActiveState.value
                            if (needleActive) {
                                val glowRadius = baseGlowRadius + pulseGlowDelta * pulse
                                val glowScale = (glowRadius / maxGlowRadius).coerceAtLeast(0.1f)
                                val glowAlpha = 0.20f + 0.28f * pulse
                                scale(scaleX = glowScale, scaleY = glowScale, pivot = needleCenter) {
                                    drawCircle(
                                        brush = needleGlowBrush,
                                        radius = maxGlowRadius,
                                        center = needleCenter,
                                        alpha = glowAlpha
                                    )
                                }
                            }

                            drawPath(
                                path = caretPath,
                                color = accent.copy(alpha = if (needleActive) 1f else 0.45f)
                            )
                            drawLine(
                                color = accent.copy(alpha = if (needleActive) 1f else 0.45f),
                                start = Offset(centerX, needleLineStartY),
                                end = Offset(centerX, needleBottom),
                                strokeWidth = needleBaseStroke + needlePulseStrokeDelta * pulse,
                                cap = StrokeCap.Round
                            )
                        }
                    }
            )
        }

        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentInt != null) LegendItem(accent, "You", displayText, textMuted)
                lastValue?.let { LegendItem(textMuted, "Last", it.toString(), textMuted) }
                goalValue?.let { LegendItem(goalColor, "Goal", it.toString(), textMuted) }
                bestValue?.let { LegendItem(bestColor, "Best", it.toString(), textMuted) }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label · $value",
            color = textColor,
            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelSmall)
        )
    }
}
