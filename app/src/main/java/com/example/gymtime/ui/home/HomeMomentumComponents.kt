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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.domain.analytics.MomentumDirection
import com.example.gymtime.domain.analytics.MuscleMomentum
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.LocalAppColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

@Composable
fun StrengthMomentumMapCard(
    state: StrengthMomentumState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val improvingCount = remember(state) {
        state.muscles.count { (it.percentChange ?: 0f) >= 2f }
    }
    val decliningCount = remember(state) {
        state.muscles.count { (it.percentChange ?: 0f) <= -2f }
    }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STRENGTH MOMENTUM",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Last ${state.recentWindowDays}d vs previous ${state.baselineWindowDays}d",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textSecondary
                    )
                }

                Text(
                    text = when {
                        improvingCount > 0 -> "$improvingCount up"
                        decliningCount > 0 -> "$decliningCount down"
                        else -> "baseline"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (improvingCount > 0) Color(0xFF22C55E) else LocalAppColors.current.textTertiary,
                    fontWeight = FontWeight.Bold
                )
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

            Spacer(modifier = Modifier.height(16.dp))

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

    val slugToDirection: Map<String, MomentumDirection> = remember(muscles, isBack) {
        val mapping = if (isBack) BACK_APP_MUSCLE_TO_SLUGS else FRONT_APP_MUSCLE_TO_SLUGS
        val byName = muscles.associateBy { it.muscle.lowercase(Locale.US) }
        buildMap {
            mapping.forEach { (appMuscle, slugs) ->
                val mm = byName[appMuscle.lowercase(Locale.US)]
                if (mm != null && mm.direction != MomentumDirection.NO_BASELINE) {
                    slugs.forEach { slug -> put(slug, mm.direction) }
                }
            }
        }
    }

    val neutralMuscleColor = Color(0xFF2A2A2E)
    val outlineStrokeColor = Color.White.copy(alpha = 0.18f)
    val muscleBorderColor = Color.Black.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
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
                val direction = slugToDirection[slug]
                val fill = if (direction != null) colorForDirection(direction) else neutralMuscleColor
                drawPath(path, color = fill)
                drawPath(path, color = muscleBorderColor, style = Stroke(width = 1.5f))
            }
            drawPath(outlinePath, color = outlineStrokeColor, style = Stroke(width = 2.5f))
        }
    }
}

@Composable
private fun MomentumStatsRow(
    state: StrengthMomentumState,
    modifier: Modifier = Modifier
) {
    val best = state.topImproving.firstOrNull()
    val worst = state.topDeclining.firstOrNull()
    val cardio = state.muscles.firstOrNull { it.muscle.equals("Cardio", ignoreCase = true) }

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
        CardioStatColumn(
            item = cardio,
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
            color = colorForDirection(item?.direction ?: MomentumDirection.NO_BASELINE),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CardioStatColumn(
    item: MuscleMomentum?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "CARDIO",
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = colorForDirection(item?.direction ?: MomentumDirection.NO_BASELINE),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item?.muscle ?: "Building",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Text(
            text = formatPercent(item?.percentChange),
            style = MaterialTheme.typography.labelMedium,
            color = colorForDirection(item?.direction ?: MomentumDirection.NO_BASELINE),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StrengthMomentumDetailSheet(
    state: StrengthMomentumState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Strength Momentum",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )
        Text(
            text = "Last ${state.recentWindowDays} days vs previous ${state.baselineWindowDays} days",
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.textTertiary
        )

        Spacer(modifier = Modifier.height(20.dp))

        state.muscles.forEach { muscle ->
            MomentumDetailRow(muscle = muscle)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "Only exercises with comparable recent and baseline history are scored.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Close",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onClose)
                .padding(12.dp)
        )
    }
}

@Composable
private fun MomentumDetailRow(muscle: MuscleMomentum) {
    val progress = ((muscle.percentChange ?: 0f).coerceIn(-10f, 10f) + 10f) / 20f
    val color = colorForDirection(muscle.direction)

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
                text = formatPercent(muscle.percentChange),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(LocalAppColors.current.inputBackground, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                    .background(color, RoundedCornerShape(4.dp))
            )
        }

        val contributors = muscle.contributingExercises.take(2).joinToString { exercise ->
            "${exercise.exerciseName} ${formatPercent(exercise.percentChange)}"
        }
        if (contributors.isNotBlank()) {
            Text(
                text = contributors,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary,
                maxLines = 2
            )
        } else {
            Text(
                text = "Building baseline",
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary
            )
        }
    }
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

private fun colorForDirection(direction: MomentumDirection): Color {
    return when (direction) {
        MomentumDirection.STRONG_UP -> Color(0xFF22C55E)
        MomentumDirection.UP -> Color(0xFFA3E635)
        MomentumDirection.FLAT -> Color(0xFF71717A)
        MomentumDirection.DOWN -> Color(0xFFF59E0B)
        MomentumDirection.STRONG_DOWN -> Color(0xFFEF4444)
        MomentumDirection.NO_BASELINE -> Color(0xFF27272A)
    }
}

private fun formatPercent(value: Float?): String {
    return value?.let {
        val sign = if (it > 0f) "+" else ""
        "$sign${String.format(Locale.US, "%.1f", it)}%"
    } ?: "--"
}
