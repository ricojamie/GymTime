package com.example.gymtime.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.PrimaryAccentDark
import com.example.gymtime.ui.theme.PrimaryAccentLight
import kotlin.math.PI
import kotlin.math.sin

/**
 * Size variants for the Volume Orb.
 */
enum class OrbSize(val dp: Dp) {
    SMALL(64.dp),   // Logging screen
    MEDIUM(120.dp), // Post-workout
    LARGE(180.dp)   // Home screen
}

// Colors for the orb
private val OrbLiquidColor = PrimaryAccent
private val OrbLiquidColorDark = PrimaryAccentDark
private val OrbLiquidColorLight = PrimaryAccentLight
private val OrbGoldColor = Color(0xFFFFD700)
private val OrbGoldColorDark = Color(0xFFDAA520)
private val OrbGlassHighlight = Color.White.copy(alpha = 0.3f)
private val OrbGlassShadow = Color.Black.copy(alpha = 0.4f)
private val OrbGlowColor = PrimaryAccent.copy(alpha = 0.4f)
private val OrbGoldGlowColor = OrbGoldColor.copy(alpha = 0.5f)

/**
 * Animated Volume Orb that displays weekly volume progress.
 *
 * @param state The current orb state from VolumeOrbRepository
 * @param size The size variant to display
 * @param onClick Optional click handler (for tooltip on Home screen)
 * @param onOverflowAnimationComplete Callback when overflow animation finishes
 * @param modifier Modifier for the orb
 */
@Composable
fun VolumeOrb(
    state: VolumeOrbState,
    size: OrbSize,
    onClick: (() -> Unit)? = null,
    onOverflowAnimationComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.toPx() }

    // Animated fill level (0 to 1, capped visually at ~1.3 for overflow)
    val targetFill = (state.progressPercent).coerceIn(0f, 1.3f)
    val animatedFill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "fill"
    )

    // Infinite transition for idle animations
    val infiniteTransition = rememberInfiniteTransition(label = "orb_idle")

    // Pulsing glow animation (2s cycle)
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Wave animation phase (3s cycle)
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Overflow ring animation
    var showOverflowRing by remember { mutableStateOf(false) }
    val overflowRingScale by animateFloatAsState(
        targetValue = if (showOverflowRing) 2f else 1f,
        animationSpec = tween(1000),
        label = "overflow_ring",
        finishedListener = {
            if (showOverflowRing) {
                showOverflowRing = false
                onOverflowAnimationComplete?.invoke()
            }
        }
    )
    val overflowRingAlpha by animateFloatAsState(
        targetValue = if (showOverflowRing) 0f else 1f,
        animationSpec = tween(1000),
        label = "overflow_ring_alpha"
    )

    // Trigger overflow animation when justOverflowed becomes true
    LaunchedEffect(state.justOverflowed) {
        if (state.justOverflowed) {
            showOverflowRing = true
        }
    }

    // Determine colors based on overflow state
    val isOverflowed = state.hasOverflowed
    val liquidColor = if (isOverflowed) OrbGoldColor else OrbLiquidColor
    val liquidColorDark = if (isOverflowed) OrbGoldColorDark else OrbLiquidColorDark
    val glowColor = if (isOverflowed) OrbGoldGlowColor else OrbGlowColor

    Box(
        modifier = modifier
            .size(size.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val center = Offset(sizePx / 2, sizePx / 2)
            val radius = sizePx / 2 - sizePx * 0.08f // Leave space for glow

            // 1. Draw outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowPulse * 0.5f),
                        glowColor.copy(alpha = glowPulse * 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.3f
                ),
                radius = radius * 1.3f,
                center = center
            )

            // 2. Draw glass sphere background (dark gradient for 3D effect)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1a1a1a),
                        Color(0xFF0a0a0a),
                        Color(0xFF050505)
                    ),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = center
            )

            // 3. Draw liquid fill with wave
            if (animatedFill > 0) {
                drawLiquidFill(
                    center = center,
                    radius = radius,
                    fillLevel = animatedFill,
                    wavePhase = wavePhase,
                    liquidColor = liquidColor,
                    liquidColorDark = liquidColorDark,
                    sizePx = sizePx
                )
            }

            // 4. Draw glass reflection highlight (top-left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OrbGlassHighlight,
                        Color.Transparent
                    ),
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 0.5f
                ),
                radius = radius * 0.4f,
                center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f)
            )

            // 5. Draw glass edge highlight (subtle rim)
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = radius,
                center = center,
                style = Stroke(width = sizePx * 0.02f)
            )

            // 6. Draw overflow ring pulse (if animating)
            if (showOverflowRing || overflowRingScale > 1f) {
                drawCircle(
                    color = OrbGoldColor.copy(alpha = overflowRingAlpha * 0.6f),
                    radius = radius * overflowRingScale,
                    center = center,
                    style = Stroke(width = sizePx * 0.03f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Draws the liquid fill with wave animation.
 */
private fun DrawScope.drawLiquidFill(
    center: Offset,
    radius: Float,
    fillLevel: Float,
    wavePhase: Float,
    liquidColor: Color,
    liquidColorDark: Color,
    sizePx: Float
) {
    // Calculate fill height (bottom to top)
    val fillHeight = radius * 2 * fillLevel.coerceAtMost(1f)
    val fillTop = center.y + radius - fillHeight

    // Create clipping path for the circle
    val clipPath = Path().apply {
        addOval(
            oval = androidx.compose.ui.geometry.Rect(
                center = center,
                radius = radius
            )
        )
    }

    clipPath(clipPath) {
        // Draw the liquid body
        val liquidPath = Path().apply {
            // Start at bottom left of the fill area
            moveTo(center.x - radius, center.y + radius)

            // Draw left edge up to the wave start
            lineTo(center.x - radius, fillTop)

            // Draw wave across the top
            val waveAmplitude = sizePx * 0.02f
            val waveFrequency = 2f
            val steps = 50
            for (i in 0..steps) {
                val x = center.x - radius + (radius * 2 * i / steps)
                val waveOffset = sin(wavePhase + (i.toFloat() / steps) * waveFrequency * PI.toFloat()) * waveAmplitude
                lineTo(x, fillTop + waveOffset)
            }

            // Draw right edge down
            lineTo(center.x + radius, center.y + radius)

            // Close the path
            close()
        }

        // Draw gradient fill
        drawPath(
            path = liquidPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    liquidColor,
                    liquidColorDark
                ),
                startY = fillTop,
                endY = center.y + radius
            )
        )

        // Draw subtle highlight on the liquid surface
        if (fillLevel > 0.1f) {
            val highlightWaveAmplitude = sizePx * 0.02f
            drawPath(
                path = Path().apply {
                    moveTo(center.x - radius * 0.6f, fillTop)
                    val highlightSteps = 30
                    for (i in 0..highlightSteps) {
                        val x = center.x - radius * 0.6f + (radius * 1.2f * i / highlightSteps)
                        val waveOffset = sin(wavePhase + (i.toFloat() / highlightSteps) * 2f * PI.toFloat()) * highlightWaveAmplitude * 0.5f
                        lineTo(x, fillTop + waveOffset - sizePx * 0.01f)
                    }
                },
                color = Color.White.copy(alpha = 0.2f),
                style = Stroke(width = sizePx * 0.01f)
            )
        }
    }
}

// Preview for development
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeOrbPreviewEmpty() {
    VolumeOrb(
        state = VolumeOrbState(
            lastWeekVolume = 10000f,
            currentWeekVolume = 0f,
            progressPercent = 0f,
            isFirstWeek = false
        ),
        size = OrbSize.LARGE
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeOrbPreviewHalf() {
    VolumeOrb(
        state = VolumeOrbState(
            lastWeekVolume = 10000f,
            currentWeekVolume = 5000f,
            progressPercent = 0.5f,
            isFirstWeek = false
        ),
        size = OrbSize.LARGE
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeOrbPreviewFull() {
    VolumeOrb(
        state = VolumeOrbState(
            lastWeekVolume = 10000f,
            currentWeekVolume = 10000f,
            progressPercent = 1f,
            isFirstWeek = false
        ),
        size = OrbSize.LARGE
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeOrbPreviewOverflow() {
    VolumeOrb(
        state = VolumeOrbState(
            lastWeekVolume = 10000f,
            currentWeekVolume = 12000f,
            progressPercent = 1.2f,
            isFirstWeek = false,
            hasOverflowed = true
        ),
        size = OrbSize.LARGE
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeOrbPreviewSmall() {
    VolumeOrb(
        state = VolumeOrbState(
            lastWeekVolume = 10000f,
            currentWeekVolume = 7500f,
            progressPercent = 0.75f,
            isFirstWeek = false
        ),
        size = OrbSize.SMALL
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeOrbPreviewMedium() {
    VolumeOrb(
        state = VolumeOrbState(
            lastWeekVolume = 10000f,
            currentWeekVolume = 7500f,
            progressPercent = 0.75f,
            isFirstWeek = false
        ),
        size = OrbSize.MEDIUM
    )
}
