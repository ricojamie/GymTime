package com.example.gymtime.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.domain.analytics.MomentumConfidence
import com.example.gymtime.domain.analytics.MomentumDataStatus
import com.example.gymtime.domain.analytics.MomentumDirection
import com.example.gymtime.domain.analytics.MuscleMomentum
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.LocalAppColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun StrengthMomentumMapCard(
    state: StrengthMomentumState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val improvingCount = state.muscles.count {
        it.direction == MomentumDirection.UP || it.direction == MomentumDirection.STRONG_UP
    }
    val stableCount = state.muscles.count { it.direction == MomentumDirection.FLAT }
    val decliningCount = state.muscles.count {
        it.direction == MomentumDirection.DOWN || it.direction == MomentumDirection.STRONG_DOWN
    }
    val buildingCount = state.muscles.count { it.status != MomentumDataStatus.READY }

    GlowCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STRENGTH MOMENTUM",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalAppColors.current.textTertiary,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = onInfoClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Strength momentum info",
                                tint = LocalAppColors.current.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "Recent sessions vs preceding sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textSecondary
                    )
                    Text(
                        text = buildList {
                            if (improvingCount > 0) add("↑ $improvingCount")
                            if (stableCount > 0) add("• $stableCount")
                            if (decliningCount > 0) add("↓ $decliningCount")
                            if (buildingCount > 0) add("○ $buildingCount building")
                        }.joinToString("  ").ifBlank { "Building baseline" },
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalAppColors.current.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BodyAnatomyView(
                    muscles = state.muscles,
                    isBack = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                BodyAnatomyView(
                    muscles = state.muscles,
                    isBack = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MomentumLegend()

            Spacer(modifier = Modifier.height(14.dp))

            MomentumStatsRow(state = state)
        }
    }
}

@Composable
private fun BodyAnatomyView(
    muscles: List<MuscleMomentum>,
    isBack: Boolean,
    modifier: Modifier = Modifier
) {
    val outlinePath = remember(isBack) {
        PathParser()
            .parsePathString(if (isBack) BACK_OUTLINE_PATH else FRONT_OUTLINE_PATH)
            .toPath()
    }

    val musclePaths: Map<String, Path> = remember(isBack) {
        val source = if (isBack) BACK_MUSCLE_PATHS else FRONT_MUSCLE_PATHS
        source.mapValues { (_, pathStrings) ->
            val combined = Path()
            pathStrings.forEach { pathString ->
                val parsed = PathParser().parsePathString(pathString).toPath()
                combined.addPath(parsed)
            }
            combined
        }
    }

    val slugToMomentum: Map<String, MuscleMomentum> = remember(muscles, isBack) {
        val mapping = if (isBack) BACK_APP_MUSCLE_TO_SLUGS else FRONT_APP_MUSCLE_TO_SLUGS
        val byName = muscles.associateBy { it.muscle.lowercase(Locale.US) }
        buildMap {
            mapping.forEach { (appMuscle, slugs) ->
                val mm = byName[appMuscle.lowercase(Locale.US)]
                if (mm != null) {
                    slugs.forEach { slug -> put(slug, mm) }
                }
            }
        }
    }

    val accessibilitySummary = remember(muscles, isBack) {
        val mappedNames = (if (isBack) BACK_APP_MUSCLE_TO_SLUGS else FRONT_APP_MUSCLE_TO_SLUGS).keys
        muscles.filter { muscle -> mappedNames.any { it.equals(muscle.muscle, ignoreCase = true) } }
            .joinToString(", ") { muscle -> "${muscle.muscle} ${spokenMomentum(muscle)}" }
    }

    val neutralMuscleColor = Color(0xFF2A2A2E)
    val outlineStrokeColor = Color.White.copy(alpha = 0.18f)
    val muscleBorderColor = Color.Black.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (isBack) {
                "Back strength momentum. $accessibilitySummary"
            } else {
                "Front strength momentum. $accessibilitySummary"
            }
        }
    ) {
        val sx = size.width / BODY_VIEWBOX_WIDTH
        val sy = size.height / BODY_VIEWBOX_HEIGHT
        val s = min(sx, sy)
        val drawnW = BODY_VIEWBOX_WIDTH * s
        val drawnH = BODY_VIEWBOX_HEIGHT * s
        val offsetX = (size.width - drawnW) / 2f
        val offsetY = (size.height - drawnH) / 2f
        val backShift = if (isBack) -BACK_VIEWBOX_X_OFFSET else 0f

        withTransform({
            translate(offsetX, offsetY)
            scale(s, s, pivot = Offset.Zero)
            translate(backShift, 0f)
        }) {
            musclePaths.forEach { (slug, path) ->
                val momentum = slugToMomentum[slug]
                val fill = momentum?.let(::colorForMomentum) ?: neutralMuscleColor
                drawPath(path, color = fill)
                drawPath(path, color = muscleBorderColor, style = Stroke(width = 1.5f))
            }
            drawPath(outlinePath, color = outlineStrokeColor, style = Stroke(width = 2.5f))
        }
    }
}

@Composable
private fun MomentumLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("↑ Improving", Color(0xFF22C55E))
        LegendItem("• Stable", Color(0xFF71717A))
        LegendItem("↓ Declining", Color(0xFFEF4444))
        LegendItem("○ Need data", Color(0xFF27272A))
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = LocalAppColors.current.textTertiary
        )
    }
}

@Composable
private fun MomentumStatsRow(
    state: StrengthMomentumState,
    modifier: Modifier = Modifier
) {
    val best = state.topImproving.firstOrNull()
    val worst = state.topDeclining.firstOrNull()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        MomentumStatColumn(
            label = "Best",
            item = best,
            modifier = Modifier.weight(1f)
        )
        MomentumStatColumn(
            label = "Watch",
            item = worst,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MomentumStatColumn(
    label: String,
    item: MuscleMomentum?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item?.muscle ?: "Building",
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = formatPercent(item?.percentChange),
            style = MaterialTheme.typography.labelMedium,
            color = item?.let(::colorForMomentum) ?: Color(0xFF71717A),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StrengthMomentumDetailSheet(
    state: StrengthMomentumState,
    onClose: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Strength Momentum",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary
            )
            Text(
                text = "Up to ${state.recentSessionCount} recent sessions vs the same number before",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textTertiary
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        items(state.muscles, key = { it.muscle }) { muscle ->
            MomentumDetailRow(muscle = muscle)
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            Text(
                text = "Weighted lifts use estimated 1RM; reps-only exercises use reps. At least ${state.minimumSessionsPerSide} matched sessions per side are required.",
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Close",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClose)
                    .padding(12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MomentumDetailRow(muscle: MuscleMomentum) {
    val color = colorForMomentum(muscle)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = muscle.muscle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = directionSymbol(muscle) + " " + formatPercent(muscle.percentChange),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        DivergingMomentumBar(muscle = muscle, color = color)

        Text(
            text = statusDescription(muscle),
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textSecondary
        )

        val improving = muscle.improvingContributors.take(2).joinToString { exercise ->
            "${exercise.exerciseName} ${formatPercent(exercise.percentChange)}"
        }
        val declining = muscle.decliningContributors.take(2).joinToString { exercise ->
            "${exercise.exerciseName} ${formatPercent(exercise.percentChange)}"
        }
        val contributors = listOfNotNull(
            improving.takeIf { it.isNotBlank() }?.let { "Up: $it" },
            declining.takeIf { it.isNotBlank() }?.let { "Down: $it" }
        ).joinToString("  ")

        if (contributors.isNotBlank()) {
            Text(
                text = contributors,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary,
                maxLines = 2
            )
        } else {
            Text(
                text = when (muscle.status) {
                    MomentumDataStatus.STALE -> "No qualifying strength session in the last 6 weeks"
                    MomentumDataStatus.BUILDING_BASELINE -> "Building a matched-session baseline"
                    MomentumDataStatus.READY -> "Exercises are within the stable range"
                },
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary
            )
        }

        val weeklyVolume = weeklyVolumeText(muscle)
        if (weeklyVolume.isNotBlank()) {
            Text(
                text = weeklyVolume,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DivergingMomentumBar(muscle: MuscleMomentum, color: Color) {
    val magnitude = ((kotlin.math.abs(muscle.percentChange ?: 0f) / 10f).coerceIn(0f, 1f))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(LocalAppColors.current.inputBackground, RoundedCornerShape(5.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if ((muscle.percentChange ?: 0f) < 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(magnitude)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if ((muscle.percentChange ?: 0f) > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(magnitude)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.45f))
        )
    }
}

private fun statusDescription(muscle: MuscleMomentum): String {
    val latest = muscle.latestSessionTimestamp?.let {
        SimpleDateFormat("MMM d", Locale.US).format(Date(it))
    }
    val sessions = muscle.contributingExercises
        .filter { it.status == MomentumDataStatus.READY }
        .minOfOrNull { minOf(it.recentSessionCount, it.baselineSessionCount) }
    return when (muscle.status) {
        MomentumDataStatus.READY -> buildString {
            append(if (muscle.confidence == MomentumConfidence.STANDARD) "Standard confidence" else "Early trend")
            sessions?.let { append(" · $it vs $it sessions") }
            latest?.let { append(" · Last trained $it") }
        }
        MomentumDataStatus.STALE -> latest?.let { "Stale · Last trained $it" } ?: "Stale"
        MomentumDataStatus.BUILDING_BASELINE -> latest?.let { "Building baseline · Last trained $it" }
            ?: "Building baseline"
    }
}

private fun directionSymbol(muscle: MuscleMomentum): String = when (muscle.direction) {
    MomentumDirection.STRONG_UP, MomentumDirection.UP -> "↑"
    MomentumDirection.STRONG_DOWN, MomentumDirection.DOWN -> "↓"
    MomentumDirection.FLAT -> "•"
    MomentumDirection.NO_BASELINE -> "○"
}

private fun spokenMomentum(muscle: MuscleMomentum): String = when (muscle.status) {
    MomentumDataStatus.STALE -> "stale"
    MomentumDataStatus.BUILDING_BASELINE -> "building baseline"
    MomentumDataStatus.READY -> when (muscle.direction) {
        MomentumDirection.STRONG_UP, MomentumDirection.UP -> "improving ${formatPercent(muscle.percentChange)}"
        MomentumDirection.STRONG_DOWN, MomentumDirection.DOWN -> "declining ${formatPercent(muscle.percentChange)}"
        else -> "stable"
    }
}

private fun weeklyVolumeText(muscle: MuscleMomentum): String {
    if (muscle.currentWeekVolume <= 0f && muscle.previousWeekVolume <= 0f) return ""
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val current = numberFormat.format(muscle.currentWeekVolume.toLong())
    val previous = numberFormat.format(muscle.previousWeekVolume.toLong())
    val delta = if (muscle.previousWeekVolume > 0f) {
        val pct = ((muscle.currentWeekVolume - muscle.previousWeekVolume) / muscle.previousWeekVolume) * 100f
        val sign = if (pct > 0f) "+" else ""
        " ($sign${String.format(Locale.US, "%.0f", pct)}%)"
    } else {
        ""
    }
    return "Week volume: $current lbs vs $previous lbs$delta"
}

@Composable
fun WeeklyLoadBar(
    state: VolumeOrbState,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val accent = MaterialTheme.colorScheme.primary
    val fillColor = if (state.hasOverflowed) Color(0xFFFFD700) else accent
    val progress = state.progressPercent.coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "WEEKLY LOAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isFirstWeek) {
                        "Building baseline"
                    } else {
                        "${numberFormat.format(state.currentWeekVolume.toLong())} / ${numberFormat.format(state.lastWeekVolume.toLong())} lbs"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary
                )
            }
            Text(
                text = when {
                    state.isFirstWeek -> "--"
                    state.hasOverflowed -> "Goal crushed"
                    else -> "${(state.progressPercent * 100).toInt()}%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.hasOverflowed) fillColor else LocalAppColors.current.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(LocalAppColors.current.inputBackground, RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (state.isFirstWeek) 0.08f else progress.coerceAtLeast(0.04f))
                    .background(fillColor, RoundedCornerShape(6.dp))
            )
        }
    }
}

private fun colorForMomentum(momentum: MuscleMomentum): Color {
    if (momentum.status == MomentumDataStatus.BUILDING_BASELINE) return Color(0xFF27272A)
    if (momentum.status == MomentumDataStatus.STALE) return Color(0xFF374151)
    val percent = momentum.percentChange ?: return Color(0xFF27272A)
    if (kotlin.math.abs(percent) < 2f) return Color(0xFF71717A)
    val intensity = (((kotlin.math.abs(percent) - 2f) / 8f).coerceIn(0f, 1f) * 0.65f) + 0.35f
    return if (percent > 0f) {
        lerp(Color(0xFF71717A), Color(0xFF22C55E), intensity)
    } else {
        lerp(Color(0xFF71717A), Color(0xFFEF4444), intensity)
    }
}

private fun formatPercent(value: Float?): String {
    return value?.let {
        val sign = if (it > 0f) "+" else ""
        "$sign${String.format(Locale.US, "%.1f", it)}%"
    } ?: "--"
}
