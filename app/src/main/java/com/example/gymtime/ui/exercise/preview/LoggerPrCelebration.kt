package com.example.gymtime.ui.exercise.preview

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMascotBadge
import com.example.gymtime.ui.components.IronLogMascotStyle
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.anodizedMicroGrain
import com.example.gymtime.ui.theme.prShockwaveOverlay
import com.example.gymtime.ui.theme.telemetrySpotlight
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PrSetRow(
    set: WorkoutSet,
    number: Int,
    recordLabels: List<String>,
    onEdit: (WorkoutSet) -> Unit,
    onDelete: (WorkoutSet) -> Unit,
    onNote: (WorkoutSet) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    var menu by remember { mutableStateOf(false) }

    val rowInteraction = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.985f,
            highlightAlpha = 0.06f,
            cornerRadius = IronLogRadius.Plate
        )
    }

    IronLogCard(
        modifier = modifier.fillMaxWidth(),
        highlighted = true,
        spotlight = true,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Normal set rows start at 52 dp; PRs add only 10%, with room to grow for notes or larger text.
        Row(
            modifier = Modifier.heightIn(min = 52.dp * 1.1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = rowInteraction,
                        indication = pressIndication,
                        onClickLabel = "Edit personal record set $number"
                    ) {
                        haptics.keyTap()
                        onEdit(set)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                TrophyMedallion()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                        IronLogStatusChip(
                            label = "PR",
                            tone = IronLogStatusTone.PR
                        )
                        Text(
                            text = "PERSONAL RECORD",
                            style = IronLogType.technicalEyebrow(),
                            color = surfaces.accent
                        )
                    }
                    OdometerText(
                        text = previewSetSummary(set),
                        style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.onSurface
                    )
                    set.note?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    set.rpe?.let {
                        Text(
                            text = "RPE $it",
                            style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = {
                        haptics.tick()
                        menu = true
                    },
                    modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                ) {
                    Icon(Icons.Default.MoreVert, "Options for set $number")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Text(
                        "Set $number${recordLabels.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = " · ").orEmpty()}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    DropdownMenuItem(text = { Text("Edit set") }, onClick = { menu = false; onEdit(set) })
                    DropdownMenuItem(text = { Text("Set note") }, onClick = { menu = false; onNote(set) })
                    DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; onDelete(set) })
                }
            }
        }
    }
}

@Composable
fun PrCelebrationBanner(
    exerciseName: String,
    setSummary: String,
    recordLabels: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val reducedMotion = LocalReducedMotion.current

    val entranceScale = remember { Animatable(if (reducedMotion) 1f else 0.88f) }
    val shockwaveProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(exerciseName, setSummary, reducedMotion) {
        if (reducedMotion) {
            entranceScale.snapTo(1f)
            shockwaveProgress.snapTo(1f)
        } else {
            entranceScale.snapTo(0.88f)
            shockwaveProgress.snapTo(0.001f)
            coroutineScope {
                launch { entranceScale.animateTo(1f, IronMotion.RewardPop) }
                launch {
                    shockwaveProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    val stageBrush = remember(surfaces) {
        Brush.verticalGradient(listOf(surfaces.telemetryStageTop, surfaces.telemetryStageBottom))
    }
    val rimStroke = remember(surfaces) {
        IronLogBorder.specularRimStroke(surfaces, highlighted = true, width = 1.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = entranceScale.value
                scaleX = s
                scaleY = s
            }
            .clip(IronLogRadius.PlateShape)
            .background(brush = stageBrush)
            .telemetrySpotlight(
                accent = surfaces.accent,
                centerFraction = Offset(0.16f, 0.35f),
                intensity = if (surfaces.isDark) 0.24f else 0.16f,
                vignetteAlpha = if (surfaces.isDark) 0.10f else 0f
            )
            .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.025f else 0.014f)
            .prShockwaveOverlay(progressProvider = { shockwaveProgress.value }, accent = surfaces.accent)
            .border(border = rimStroke, shape = IronLogRadius.PlateShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IronLogMascotBadge(
                size = 40.dp,
                style = IronLogMascotStyle.HERO_CREST,
                accentColor = surfaces.accent,
                showGlow = true
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IronLogLedPip(color = surfaces.ledGlow, size = 5.dp, pulsing = true)
                    IronLogStatusChip(label = "PR", tone = IronLogStatusTone.PR)
                    Text(
                        text = "NEW PR!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = colors.onSurface
                    )
                }
                OdometerText(
                    text = setSummary,
                    style = IronLogType.monumentalNumeral(MaterialTheme.typography.titleLarge).copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.onSurface
                )
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                if (recordLabels.isNotEmpty()) {
                    Text(
                        text = recordLabels.joinToString(" · "),
                        style = IronLogType.telemetryLabel(MaterialTheme.typography.bodySmall),
                        color = surfaces.accent
                    )
                }
            }
            IconButton(
                onClick = {
                    haptics.tick()
                    onDismiss()
                },
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss PR celebration",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrophyMedallion() {
    val accent = LocalLoggerActionColors.current
    Surface(
        shape = CircleShape,
        color = accent.fill,
        contentColor = accent.onFill,
        border = IronLogBorder.stroke(Color.White.copy(alpha = 0.28f), width = IronLogBorder.Thin)
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(26.dp))
        }
    }
}

private data class ConfettiPiece(val x: Float, val drift: Float, val fall: Float, val spin: Float)

/** Decorative, finite animation. Respects [LocalReducedMotion] and system duration scale. */
@Composable
fun PrConfettiBurst(eventId: Long, startedAt: Long, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    if (reducedMotion) return

    val scheme = MaterialTheme.colorScheme
    val progress = remember(eventId) { Animatable(0f) }
    val pieces = remember(eventId) {
        val random = Random(eventId)
        List(28) {
            ConfettiPiece(
                x = random.nextFloat(),
                drift = random.nextFloat() - 0.5f,
                fall = 0.3f + random.nextFloat() * 0.4f,
                spin = random.nextFloat() * 540f
            )
        }
    }
    val colors = remember(scheme.primary, scheme.secondary, scheme.tertiary) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary)
    }
    LaunchedEffect(eventId) {
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val fraction = (elapsed / 1_100f).coerceIn(0f, 1f)
        progress.snapTo(fraction)
        if (fraction < 1f) progress.animateTo(1f, tween((1_100L - elapsed).toInt(), easing = LinearEasing))
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val phase = progress.value
        if (phase >= 1f) return@Canvas
        val bouncePx = 40.dp.toPx()
        val rectWidthPx = 5.dp.toPx()
        val rectHeightPx = 10.dp.toPx()
        val halfWidthPx = rectWidthPx / 2f
        val halfHeightPx = rectHeightPx / 2f
        val confettiSize = Size(rectWidthPx, rectHeightPx)
        val wave = sin(phase * 3.14f) * bouncePx
        val alpha = 1f - phase
        pieces.forEachIndexed { index, piece ->
            val center = Offset(
                size.width * (piece.x + piece.drift * phase * 0.4f),
                size.height * (piece.fall * phase * phase) + wave
            )
            rotate(piece.spin * phase, center) {
                drawRect(
                    color = colors[index % colors.size].copy(alpha = alpha),
                    topLeft = Offset(center.x - halfWidthPx, center.y - halfHeightPx),
                    size = confettiSize
                )
            }
        }
    }
}
