package com.example.gymtime.ui.home.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.domain.analytics.ExerciseMomentum
import com.example.gymtime.domain.analytics.MomentumConfidence
import com.example.gymtime.domain.analytics.MomentumDataStatus
import com.example.gymtime.domain.analytics.MomentumDirection
import com.example.gymtime.domain.analytics.MuscleMomentum
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogFilterPill
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rendering only: Home owns loading, selected muscle, and the sheet lifecycle. */
@Composable
fun PreviewStrengthTrendsCard(
    state: StrengthMomentumState,
    isLoading: Boolean,
    error: String?,
    onMuscleClick: (String) -> Unit,
    onInfoClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val hasLoadedData = state.muscles.isNotEmpty()
    val muscles = remember(state.muscles) { strengthDisplayMuscles(state) }
    val hairlineColor = IronLogBorder.dividerColor()
    val matrixBorder = remember(surfaces) {
        IronLogBorder.specularRimStroke(
            surfaces = surfaces,
            highlighted = false,
            width = IronLogBorder.Thin
        )
    }

    IronLogCard(
        modifier = modifier,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "STRENGTH SPECTRUM",
                    style = IronLogType.technicalEyebrow(),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Recent vs previous sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IronLogSculptedKeycap(
                onClick = onInfoClick,
                hapticType = KeycapHaptic.KEY_TAP,
                shape = IronLogRadius.KeycapShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "METHOD",
                    style = IronLogType.technicalEyebrow(),
                    color = scheme.onSurface
                )
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (hasLoadedData) 0.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = if (hasLoadedData) "Refreshing trends…" else "Loading strength trends…",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        } else if (error != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (hasLoadedData) "$error\nShowing previously loaded trends." else error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                IronLogSculptedKeycap(
                    onClick = onRetry,
                    hapticType = KeycapHaptic.KEY_TAP,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Try again",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (hasLoadedData || (!isLoading && error == null)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val scaledWidth = maxWidth.value / LocalDensity.current.fontScale
                val columns = when {
                    scaledWidth >= 280f -> 3
                    scaledWidth >= 180f -> 2
                    else -> 1
                }
                val rows = remember(muscles, columns) { muscles.chunked(columns) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IronLogRadius.ControlShape)
                        .background(surfaces.wellFill)
                        .border(border = matrixBorder, shape = IronLogRadius.ControlShape)
                ) {
                    rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) {
                            HorizontalDivider(
                                thickness = IronLogBorder.Thin,
                                color = hairlineColor
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEachIndexed { colIndex, muscle ->
                                if (colIndex > 0) {
                                    VerticalDivider(
                                        modifier = Modifier.fillMaxHeight(),
                                        thickness = IronLogBorder.Thin,
                                        color = hairlineColor
                                    )
                                }
                                PreviewStrengthTrendCell(
                                    muscle = muscle,
                                    wide = row.size == 1,
                                    surfaceBackground = surfaces.wellFill,
                                    onClick = { onMuscleClick(muscle.muscle) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                            if (row.size in 2 until columns) {
                                repeat(columns - row.size) {
                                    VerticalDivider(
                                        modifier = Modifier.fillMaxHeight(),
                                        thickness = IronLogBorder.Thin,
                                        color = hairlineColor
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewStrengthTrendCell(
    muscle: MuscleMomentum,
    wide: Boolean,
    surfaceBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalIronHaptics.current
    val trendColor = strengthTrendColor(muscle.status, muscle.percentChange, surfaceBackground)
    val trend = strengthTrendText(muscle.status, muscle.percentChange)
    val label = strengthMuscleLabel(muscle.muscle)
    val isReady = muscle.status == MomentumDataStatus.READY && muscle.percentChange != null && muscle.percentChange.isFinite()

    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.96f,
            highlightAlpha = 0.08f,
            cornerRadius = IronLogRadius.Tight
        )
    }

    val cellModifier = modifier
        .clickable(
            interactionSource = interactionSource,
            indication = pressIndication,
            role = Role.Button,
            onClick = {
                haptics.segmentTick()
                onClick()
            }
        )
        .clearAndSetSemantics {
            contentDescription = "$label, $trend. View exercise trends."
        }

    if (wide) {
        Row(
            modifier = cellModifier
                .heightIn(min = 54.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    style = IronLogType.technicalEyebrow(),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SpectrumTickStrip(
                    status = muscle.status,
                    percentChange = muscle.percentChange,
                    accentColor = trendColor,
                    inactiveColor = scheme.onSurfaceVariant.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth(0.55f)
                )
            }
            TrendDeltaBadge(
                text = trend,
                isReady = isReady,
                trendColor = trendColor,
                fallbackColor = scheme.onSurfaceVariant
            )
        }
    } else {
        Column(
            modifier = cellModifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    style = IronLogType.technicalEyebrow(),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isReady) {
                    IronLogLedPip(
                        color = trendColor,
                        size = 4.dp,
                        pulsing = false
                    )
                }
            }

            TrendDeltaBadge(
                text = trend,
                isReady = isReady,
                trendColor = trendColor,
                fallbackColor = scheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            SpectrumTickStrip(
                status = muscle.status,
                percentChange = muscle.percentChange,
                accentColor = trendColor,
                inactiveColor = scheme.onSurfaceVariant.copy(alpha = 0.22f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TrendDeltaBadge(
    text: String,
    isReady: Boolean,
    trendColor: Color,
    fallbackColor: Color
) {
    if (isReady) {
        OdometerText(
            text = text,
            style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = trendColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Text(
            text = text,
            style = IronLogType.telemetryLabel(),
            color = fallbackColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpectrumTickStrip(
    status: MomentumDataStatus,
    percentChange: Float?,
    accentColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val tickCount = 7
    val isReady = status == MomentumDataStatus.READY && percentChange != null && percentChange.isFinite()
    val targetLitCount = remember(isReady, percentChange) {
        if (!isReady || percentChange == null) {
            0
        } else {
            val magnitude = (abs(percentChange) / 15f).coerceIn(0f, 1f)
            (1 + (magnitude * (tickCount - 1)).roundToInt()).coerceIn(1, tickCount)
        }
    }

    val mountReveal = remember(status, targetLitCount) {
        Animatable(if (reducedMotion || targetLitCount == 0) 1f else 0f)
    }
    LaunchedEffect(status, targetLitCount, reducedMotion) {
        if (targetLitCount > 0 && !reducedMotion) {
            mountReveal.snapTo(0f)
            mountReveal.animateTo(1f, IronMotion.SpatialLayout)
        } else {
            mountReveal.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .height(6.dp)
            .drawBehind {
                val gap = 2.dp.toPx()
                val totalGap = gap * (tickCount - 1)
                val barWidth = ((size.width - totalGap) / tickCount.toFloat()).coerceAtLeast(1f)
                val radius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                val reveal = mountReveal.value.coerceIn(0f, 1f)

                for (i in 0 until tickCount) {
                    val x = i * (barWidth + gap)
                    val isLit = i < targetLitCount
                    val tickThreshold = (i + 1).toFloat() / tickCount.toFloat()
                    val litAlpha = if (isLit) {
                        ((reveal - (tickThreshold * 0.55f)) / 0.45f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val drawColor = if (litAlpha > 0.01f) {
                        lerp(inactiveColor, accentColor, litAlpha)
                    } else {
                        inactiveColor
                    }
                    val tickHeight = if (isLit) size.height else size.height * 0.65f
                    drawRoundRect(
                        color = drawColor,
                        topLeft = Offset(x, size.height - tickHeight),
                        size = Size(barWidth, tickHeight),
                        cornerRadius = radius
                    )
                }
            }
    )
}

/** Place in a themed ModalBottomSheet. A null selection opens the method and overview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewStrengthTrendDetails(
    state: StrengthMomentumState,
    selectedMuscle: String?,
    onSelectMuscle: (String) -> Unit,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val muscles = remember(state.muscles) { strengthDisplayMuscles(state) }
    val selected = muscles.firstOrNull { it.muscle.equals(selectedMuscle, ignoreCase = true) }
    val displayedMuscles = remember(selected, muscles) { selected?.let { listOf(it) } ?: muscles }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        IronLogSheetHeader(
            title = selected?.let { "${strengthMuscleLabel(it.muscle)} trends" } ?: "Strength trends",
            subtitle = "Recent vs previous sessions",
            onClose = onClose
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            muscles.forEach { muscle ->
                IronLogFilterPill(
                    label = strengthMuscleLabel(muscle.muscle),
                    selected = selected?.muscle == muscle.muscle,
                    onClick = { onSelectMuscle(muscle.muscle) },
                    showLedPip = true
                )
            }
        }
        if (selected == null) {
            PreviewStrengthMethod(state)
            HorizontalDivider(thickness = IronLogBorder.Thin, color = IronLogBorder.dividerColor())
        }
        displayedMuscles.forEachIndexed { muscleIndex, muscle ->
            if (muscleIndex > 0) {
                HorizontalDivider(thickness = IronLogBorder.Thin, color = IronLogBorder.dividerColor())
            }
            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strengthMuscleLabel(muscle.muscle),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val deltaColor = strengthTrendColor(muscle.status, muscle.percentChange, scheme.surface)
                    val deltaText = strengthTrendText(muscle.status, muscle.percentChange)
                    if (muscle.status == MomentumDataStatus.READY && muscle.percentChange != null) {
                        OdometerText(
                            text = deltaText,
                            style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = deltaColor
                        )
                    } else {
                        Text(
                            text = deltaText,
                            style = IronLogType.tabular(MaterialTheme.typography.titleMedium).copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = deltaColor
                        )
                    }
                }
                if (muscle.hasMixedContributors) {
                    Text(
                        text = "Some exercises are up and others are down. The body-part trend is their median change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (muscle.contributingExercises.isEmpty()) {
                    Text(
                        text = "Log completed working sets across at least four sessions of an exercise to build a comparison.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    IronLogGroupedCard(showBorder = true, shape = IronLogRadius.PlateShape) {
                        muscle.contributingExercises.forEachIndexed { exerciseIndex, exercise ->
                            if (exerciseIndex > 0) {
                                IronLogDivider()
                            }
                            PreviewStrengthExerciseContribution(exercise)
                        }
                    }
                }
            }
        }
        if (selected != null) {
            HorizontalDivider(thickness = IronLogBorder.Thin, color = IronLogBorder.dividerColor())
            PreviewStrengthMethod(state)
        }
    }
}

@Composable
private fun PreviewStrengthExerciseContribution(exercise: ExerciseMomentum) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = exercise.exerciseName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = when (exercise.status) {
                MomentumDataStatus.READY -> "${strengthTrendText(exercise.status, exercise.percentChange)} · Compared"
                MomentumDataStatus.BUILDING_BASELINE -> "Not enough data · Building baseline"
                MomentumDataStatus.STALE -> "No recent data · Last trained ${formatStrengthDate(exercise.latestSessionTimestamp)}"
            },
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                fontWeight = FontWeight.Medium
            ),
            color = strengthTrendColor(exercise.status, exercise.percentChange, scheme.surfaceContainerLow)
        )
        Text(
            text = "Matched samples: ${exercise.recentSessionCount} recent + ${exercise.baselineSessionCount} previous sessions",
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = scheme.onSurfaceVariant
        )
        if (exercise.recentValue != null && exercise.baselineValue != null) {
            Text(
                text = "Performance score: ${strengthScore(exercise.recentValue)} recent · ${strengthScore(exercise.baselineValue)} previous",
                style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                color = scheme.onSurfaceVariant
            )
        }
        if (exercise.status == MomentumDataStatus.READY && exercise.confidence == MomentumConfidence.LOW) {
            Text(
                text = "Early comparison — fewer sessions can make the trend move more.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewStrengthMethod(state: StrengthMomentumState) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
        Text("How this is calculated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Each exercise compares the same number of recent and previous finished workouts: up to ${state.recentSessionCount} per side, with at least ${state.minimumSessionsPerSide} per side before a trend appears. Only completed, non-warmup weight-and-reps or reps-only sets from the past year count.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Text(
            text = "Each workout uses its best working set, so adding lighter backoff sets does not lower its score. Weighted sets use estimated one-rep max (weight × (1 + reps ÷ 30)) for positive weight and 1–15 reps; reps-only sets use repetitions. Higher-rep weighted sets remain in the exercise's Records and History but do not count toward this estimate. The recent and previous scores are the median of those workout scores.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Text(
            text = "Each body part shows the median change among exercises with enough recent data, with each exercise capped at ±20% for this combined number. Changes under 2% in either direction are treated as steady. An exercise without a session in the past 42 days shows No recent data. These are trends in your logged performance, not predictions or a measure of muscle growth.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

private val strengthDateFormatters = ConcurrentHashMap<Locale, DateTimeFormatter>()

private fun formatStrengthDate(timestampMs: Long): String {
    val locale = Locale.getDefault()
    val formatter = strengthDateFormatters.computeIfAbsent(locale) { loc ->
        DateTimeFormatter.ofPattern("MMM d, yyyy", loc)
    }
    return Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter)
}

private val strengthDefaultMuscles = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Abs", "Legs")

private fun strengthDisplayMuscles(state: StrengthMomentumState): List<MuscleMomentum> {
    val bodyParts = state.muscles.filterNot {
        it.muscle.isWarmupMuscleGroup() || it.muscle.trim().equals("Cardio", ignoreCase = true)
    }.distinctBy { strengthMuscleKey(it.muscle) }
    val defaults = strengthDefaultMuscles.map { name ->
        bodyParts.firstOrNull { strengthMuscleKey(it.muscle) == strengthMuscleKey(name) }
            ?: MuscleMomentum(name, null, MomentumDirection.NO_BASELINE, emptyList())
    }
    return defaults + bodyParts.filter { candidate ->
        strengthDefaultMuscles.none { strengthMuscleKey(it) == strengthMuscleKey(candidate.muscle) }
    }
}

private fun strengthMuscleKey(name: String): String = when (name.trim().lowercase(Locale.ROOT)) {
    "core", "abs" -> "abs"
    else -> name.trim().lowercase(Locale.ROOT)
}

private fun strengthMuscleLabel(name: String): String = if (strengthMuscleKey(name) == "abs") "Core" else name

private fun strengthTrendText(status: MomentumDataStatus, change: Float?): String = when {
    status == MomentumDataStatus.STALE -> "No recent data"
    status != MomentumDataStatus.READY || change == null || !change.isFinite() -> "Not enough data"
    abs(change) < 0.05f -> "0.0%"
    else -> String.format(Locale.getDefault(), "%+.1f%%", change)
}

private fun strengthScore(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun strengthTrendColor(status: MomentumDataStatus, change: Float?, background: Color): Color {
    val scheme = MaterialTheme.colorScheme
    val base = when {
        status != MomentumDataStatus.READY || change == null || !change.isFinite() -> scheme.onSurfaceVariant
        change >= 2f -> scheme.primary
        change <= -2f -> if (scheme.background.luminance() < 0.5f) Color(0xFFFFC45E) else Color(0xFF8A4B00)
        else -> scheme.onSurface
    }
    return remember(base, background) { strengthReadableOn(base, background) }
}

private fun strengthReadableOn(color: Color, background: Color): Color {
    fun contrast(foreground: Color): Float =
        (max(foreground.luminance(), background.luminance()) + 0.05f) /
            (min(foreground.luminance(), background.luminance()) + 0.05f)
    if (contrast(color) >= 4.5f) return color
    val target = if (contrast(Color.Black) >= contrast(Color.White)) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(18) {
        val mid = (low + high) / 2f
        if (contrast(lerp(color, target, mid)) >= 4.5f) high = mid else low = mid
    }
    return lerp(color, target, high)
}
