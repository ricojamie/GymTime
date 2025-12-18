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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import com.example.gymtime.data.VolumeOrbState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Size variants for the Volume Orb.
 */
enum class OrbSize(val dp: Dp) {
    SMALL(64.dp),   // Logging screen
    MEDIUM(120.dp), // Post-workout
    LARGE(180.dp)   // Home screen
}

// Colors for the orb
private val OrbGoldColor = Color(0xFFFFD700)
private val OrbGoldColorDark = Color(0xFFDAA520)
private val OrbGlassHighlight = Color.White.copy(alpha = 0.25f)
private val OrbRimLight = Color.White.copy(alpha = 0.15f)

/**
 * Simple particle class for the orb's engagement effect.
 */
private data class OrbParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    var size: Float,
    var life: Float // 0.0 to 1.0
)

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

    // Particle system state
    var particles by remember { mutableStateOf(listOf<OrbParticle>()) }
    var lastVolume by remember { mutableStateOf(state.currentWeekVolume) }

    // Spawn particles when volume increases
    SideEffect {
        if (state.currentWeekVolume > lastVolume) {
            val newParticles = List(10) {
                OrbParticle(
                    x = 0.5f + (Random.nextFloat() - 0.5f) * 0.4f,
                    y = 0.8f, // Spawn near bottom
                    vx = (Random.nextFloat() - 0.5f) * 0.02f,
                    vy = -0.01f - Random.nextFloat() * 0.03f,
                    alpha = 1.0f,
                    size = 2f + Random.nextFloat() * 4f,
                    life = 1.0f
                )
            }
            particles = (particles + newParticles).take(50)
            lastVolume = state.currentWeekVolume
        }
    }

    // Update particles
    val particleTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_tick"
    )

    LaunchedEffect(particleTick) {
        if (particles.isNotEmpty()) {
            particles = particles.mapNotNull { p ->
                p.life -= 0.02f
                if (p.life <= 0) null
                else {
                    p.x += p.vx
                    p.y += p.vy
                    p.alpha = p.life
                    p
                }
            }
        }
    }

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

    // Determine colors based on overflow state and theme
    val isOverflowed = state.hasOverflowed
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorDark = primaryColor.copy(alpha = 0.8f)

    val liquidColor = if (isOverflowed) OrbGoldColor else primaryColor
    val liquidColorDark = if (isOverflowed) OrbGoldColorDark else primaryColorDark
    val glowColor = if (isOverflowed) OrbGoldColor.copy(alpha = 0.5f) else primaryColor.copy(alpha = 0.4f)

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
            val radius = sizePx / 2 - sizePx * 0.08f

            // 1. Outer Glow (Pulsing)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowPulse * 0.4f),
                        glowColor.copy(alpha = glowPulse * 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.4f
                ),
                radius = radius * 1.4f,
                center = center
            )

            // 2. Spherical Background (Inner Shadow)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF050505)
                    ),
                    center = Offset(center.x + radius * 0.2f, center.y + radius * 0.2f),
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = center
            )

            // 3. Liquid Fill with Wave
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

            // 4. Bubbles/Sparks (Procedural)
            particles.forEach { p ->
                val px = center.x - radius + (p.x * radius * 2)
                val py = center.y - radius + (p.y * radius * 2)

                val dx = px - center.x
                val dy = py - center.y
                if (dx*dx + dy*dy < radius*radius) {
                    drawCircle(
                        color = Color.White.copy(alpha = p.alpha * 0.4f),
                        radius = p.size,
                        center = Offset(px, py)
                    )
                }
            }

            // 5. Glass Gloss (Top Highlight)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbGlassHighlight, Color.Transparent),
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 0.6f
                ),
                radius = radius * 0.6f,
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
            )

            // 6. Rim Light (Edge Polish)
            drawCircle(
                color = OrbRimLight,
                radius = radius,
                center = center,
                style = Stroke(width = sizePx * 0.015f)
            )

            // 7. Overflow Ring
            if (showOverflowRing || overflowRingScale > 1f) {
                drawCircle(
                    color = OrbGoldColor.copy(alpha = overflowRingAlpha * 0.6f),
                    radius = radius * 1.1f * overflowRingScale,
                    center = center,
                    style = Stroke(width = sizePx * 0.02f, cap = StrokeCap.Round)
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

// ============================================================================
// Volume Progress Bar - Compact horizontal variant for logging screen
// ============================================================================

/**
 * Compact horizontal progress bar for the logging screen.
 * Uses the same VolumeOrbState but takes up less vertical space.
 * Tap to show volume details tooltip.
 */
@Composable
fun VolumeProgressBar(
    state: VolumeOrbState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var showTooltip by remember { mutableStateOf(false) }

    // Animated fill level (0 to 1, capped visually at 1.2 for overflow indication)
    val targetFill = (state.progressPercent).coerceIn(0f, 1.2f)
    val animatedFill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "bar_fill"
    )

    // Tooltip fade animation
    val tooltipAlpha by animateFloatAsState(
        targetValue = if (showTooltip) 1f else 0f,
        animationSpec = tween(200),
        label = "tooltip_alpha"
    )

    // Auto-hide tooltip after 2 seconds
    LaunchedEffect(showTooltip) {
        if (showTooltip) {
            kotlinx.coroutines.delay(2000)
            showTooltip = false
        }
    }

    // Subtle shimmer animation for overflow state
    val infiniteTransition = rememberInfiniteTransition(label = "bar_shimmer")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // Determine colors based on overflow state and theme
    val isOverflowed = state.hasOverflowed
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorDark = primaryColor.copy(alpha = 0.8f)
    
    val fillColor = if (isOverflowed) OrbGoldColor else primaryColor
    val fillColorDark = if (isOverflowed) OrbGoldColorDark else primaryColorDark
    val glowColor = if (isOverflowed) OrbGoldColor.copy(alpha = 0.5f) else primaryColor.copy(alpha = 0.4f)

    val barHeight = 12.dp
    val cornerRadius = 6.dp

    // Format volume for display
    fun formatVolume(volume: Float): String {
        return when {
            volume >= 1000 -> String.format("%.1fk", volume / 1000)
            else -> String.format("%.0f", volume)
        }
    }

    Column(modifier = modifier) {
        // Tooltip row (above the bar)
        if (tooltipAlpha > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = tooltipAlpha }
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (state.isFirstWeek) "First week"
                           else "${formatVolume(state.currentWeekVolume)} / ${formatVolume(state.lastWeekVolume)} lbs",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = if (state.isFirstWeek) "—"
                           else "${(state.progressPercent * 100).toInt()}%",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = if (isOverflowed) OrbGoldColor else primaryColor,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showTooltip = !showTooltip }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // 1. Draw background track
            drawRoundRect(
                color = Color(0xFF1a1a1a),
                topLeft = Offset.Zero,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
            )

            // 2. Draw subtle inner shadow/depth
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.3f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                topLeft = Offset.Zero,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
            )

            // 3. Draw fill
            if (animatedFill > 0) {
                val fillWidth = (width * animatedFill.coerceAtMost(1f))

                // Main fill gradient
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(fillColorDark, fillColor),
                        startX = 0f,
                        endX = fillWidth
                    ),
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                )

                // Top highlight for 3D effect
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = height * 0.5f
                    ),
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, height * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )

                // Shimmer effect for overflow
                if (isOverflowed) {
                    val shimmerWidth = width * 0.3f
                    val shimmerX = (shimmerPhase * (fillWidth + shimmerWidth)) - shimmerWidth

                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            startX = shimmerX,
                            endX = shimmerX + shimmerWidth
                        ),
                        topLeft = Offset.Zero,
                        size = Size(fillWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                    )
                }

                // Glow effect at the leading edge
                if (animatedFill > 0.05f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.6f),
                                Color.Transparent
                            ),
                            center = Offset(fillWidth - height / 2, height / 2),
                            radius = height * 1.5f
                        ),
                        radius = height * 1.5f,
                        center = Offset(fillWidth - height / 2, height / 2)
                    )
                }
            }

            // 4. Draw border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.1f),
                topLeft = Offset.Zero,
                size = Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
                style = Stroke(width = 1f)
            )
            }
        }
    }
}

// Previews for VolumeProgressBar
@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeProgressBarPreviewEmpty() {
    Box(modifier = Modifier.padding(16.dp)) {
        VolumeProgressBar(
            state = VolumeOrbState(
                lastWeekVolume = 10000f,
                currentWeekVolume = 0f,
                progressPercent = 0f,
                isFirstWeek = false
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeProgressBarPreviewHalf() {
    Box(modifier = Modifier.padding(16.dp)) {
        VolumeProgressBar(
            state = VolumeOrbState(
                lastWeekVolume = 10000f,
                currentWeekVolume = 5000f,
                progressPercent = 0.5f,
                isFirstWeek = false
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeProgressBarPreviewFull() {
    Box(modifier = Modifier.padding(16.dp)) {
        VolumeProgressBar(
            state = VolumeOrbState(
                lastWeekVolume = 10000f,
                currentWeekVolume = 10000f,
                progressPercent = 1f,
                isFirstWeek = false
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun VolumeProgressBarPreviewOverflow() {
    Box(modifier = Modifier.padding(16.dp)) {
        VolumeProgressBar(
            state = VolumeOrbState(
                lastWeekVolume = 10000f,
                currentWeekVolume = 12000f,
                progressPercent = 1.2f,
                isFirstWeek = false,
                hasOverflowed = true
            )
        )
    }
}
