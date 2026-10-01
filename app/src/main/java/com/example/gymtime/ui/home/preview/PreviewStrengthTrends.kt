package com.example.gymtime.ui.home.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.domain.analytics.ExerciseMomentum
import com.example.gymtime.domain.analytics.MomentumConfidence
import com.example.gymtime.domain.analytics.MomentumDataStatus
import com.example.gymtime.domain.analytics.MomentumDirection
import com.example.gymtime.domain.analytics.MuscleMomentum
import com.example.gymtime.domain.analytics.StrengthMomentumState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val hasLoadedData = state.muscles.isNotEmpty()
    val muscles = remember(state.muscles) { strengthDisplayMuscles(state) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Strength trends",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Recent vs previous sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onInfoClick, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Method")
                }
            }
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = if (hasLoadedData) 0.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (hasLoadedData) "Refreshing trends…" else "Loading strength trends…",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            } else if (error != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (hasLoadedData) "$error\nShowing previously loaded trends." else error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Try again")
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        muscles.chunked(columns).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { muscle ->
                                    PreviewStrengthTrendCell(
                                        muscle = muscle,
                                        wide = row.size == 1,
                                        onClick = { onMuscleClick(muscle.muscle) },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val background = scheme.surfaceContainerLow
    val trendColor = strengthTrendColor(muscle.status, muscle.percentChange, background)
    val trend = strengthTrendText(muscle.status, muscle.percentChange)
    val label = strengthMuscleLabel(muscle.muscle)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (wide) 52.dp else 68.dp),
        shape = RoundedCornerShape(13.dp),
        color = background,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        if (wide) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    .clearAndSetSemantics { contentDescription = "$label, $trend. View exercise trends." },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = trend,
                    modifier = Modifier.weight(1f, fill = false),
                    style = if (muscle.percentChange != null) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodySmall,
                    fontWeight = if (muscle.percentChange != null) FontWeight.Bold else FontWeight.Normal,
                    color = trendColor
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    .clearAndSetSemantics { contentDescription = "$label, $trend. View exercise trends." },
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = trend,
                    style = if (muscle.percentChange != null) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodySmall,
                    fontWeight = if (muscle.percentChange != null) FontWeight.Bold else FontWeight.Normal,
                    color = trendColor
                )
            }
        }
    }
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
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = selected?.let { "${strengthMuscleLabel(it.muscle)} trends" } ?: "Strength trends",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Done")
            }
        }
        Text(
            text = "Recent vs previous sessions",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            muscles.forEach { muscle ->
                FilterChip(
                    selected = selected?.muscle == muscle.muscle,
                    onClick = { onSelectMuscle(muscle.muscle) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(strengthMuscleLabel(muscle.muscle)) }
                )
            }
        }
        if (selected == null) {
            PreviewStrengthMethod(state)
            HorizontalDivider(color = scheme.outlineVariant)
        }
        (selected?.let { listOf(it) } ?: muscles).forEach { muscle ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Text(
                        text = strengthTrendText(muscle.status, muscle.percentChange),
                        style = MaterialTheme.typography.titleMedium,
                        color = strengthTrendColor(muscle.status, muscle.percentChange, scheme.surface),
                        fontWeight = FontWeight.Bold
                    )
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
                    muscle.contributingExercises.forEach { exercise ->
                        PreviewStrengthExerciseContribution(exercise)
                    }
                }
            }
            HorizontalDivider(color = scheme.outlineVariant)
        }
        if (selected != null) PreviewStrengthMethod(state)
    }
}

@Composable
private fun PreviewStrengthExerciseContribution(exercise: ExerciseMomentum) {
    val scheme = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    MomentumDataStatus.STALE -> "No recent data · Last trained ${dateFormat.format(Date(exercise.latestSessionTimestamp))}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = strengthTrendColor(exercise.status, exercise.percentChange, scheme.surfaceContainerLow),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Matched samples: ${exercise.recentSessionCount} recent + ${exercise.baselineSessionCount} previous sessions",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (exercise.recentValue != null && exercise.baselineValue != null) {
                Text(
                    text = "Performance score: ${strengthScore(exercise.recentValue)} recent · ${strengthScore(exercise.baselineValue)} previous",
                    style = MaterialTheme.typography.bodySmall,
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
}

@Composable
private fun PreviewStrengthMethod(state: StrengthMomentumState) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    return strengthReadableOn(base, background)
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
