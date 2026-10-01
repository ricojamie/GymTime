package com.example.gymtime.ui.home.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.ui.home.NewUiPreviewToggle
import com.example.gymtime.ui.home.RoutineCardState
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.util.StreakCalculator
import java.text.DateFormat
import java.text.NumberFormat

/** App wiring owns data and navigation; this surface owns only its scrolling runtime. */
@Composable
fun PreviewHomeContent(
    modifier: Modifier = Modifier,
    userName: String,
    newUiEnabled: Boolean,
    ongoingWorkout: Workout?,
    routine: RoutineCardState?,
    strengthMomentum: StrengthMomentumState,
    strengthLoading: Boolean,
    strengthError: String?,
    volumeState: VolumeOrbState,
    streakResult: StreakCalculator.StreakResult,
    onNewUiEnabledChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onStartWorkoutClick: () -> Unit,
    onPlanWorkoutClick: () -> Unit,
    onRoutineStartClick: () -> Unit,
    onRoutineDetailsClick: () -> Unit,
    onChooseRoutineClick: () -> Unit,
    onCreateRoutineClick: () -> Unit,
    onStreakClick: () -> Unit,
    onTrendClick: (String) -> Unit,
    onTrendInfoClick: () -> Unit,
    onRetryTrendsClick: () -> Unit,
    routineStarting: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomePreviewHeader(
                newUiEnabled = newUiEnabled,
                onNewUiEnabledChange = onNewUiEnabledChange,
                onSettingsClick = onSettingsClick
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Hey, ${userName.trim().ifBlank { "Athlete" }}.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (ongoingWorkout == null) "Ready when you are." else "Your workout is ready to resume.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            WorkoutHero(
                workout = ongoingWorkout,
                routineStarting = routineStarting,
                onWorkoutClick = onStartWorkoutClick,
                onPlanWorkoutClick = onPlanWorkoutClick
            )
            PreviewStrengthTrendsCard(
                state = strengthMomentum,
                isLoading = strengthLoading,
                error = strengthError,
                onMuscleClick = onTrendClick,
                onInfoClick = onTrendInfoClick,
                onRetry = onRetryTrendsClick
            )
            HomeMetrics(
                streak = streakResult,
                volume = volumeState,
                onStreakClick = onStreakClick
            )
            RoutineFooter(
                routine = routine,
                hasOngoingWorkout = ongoingWorkout != null,
                routineStarting = routineStarting,
                onStartClick = onRoutineStartClick,
                onDetailsClick = onRoutineDetailsClick,
                onChooseClick = onChooseRoutineClick,
                onCreateClick = onCreateRoutineClick
            )
        }
    }
}

@Composable
private fun HomePreviewHeader(
    newUiEnabled: Boolean,
    onNewUiEnabledChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                append("Iron")
                withStyle(SpanStyle(color = colors.primary)) { append("Log") }
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NewUiPreviewToggle(newUiEnabled, onNewUiEnabledChange)
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = colors.onSurface
            )
        }
    }
}

@Composable
private fun WorkoutHero(
    workout: Workout?,
    routineStarting: Boolean,
    onWorkoutClick: () -> Unit,
    onPlanWorkoutClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    val startLabel = remember(workout?.startTime) {
        workout?.startTime?.let { "Started ${DateFormat.getTimeInstance(DateFormat.SHORT).format(it)}" }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .drawWithCache {
                    val sweep = Path().apply {
                        moveTo(size.width * 0.5f, size.height)
                        cubicTo(
                            size.width * 0.72f, size.height * 0.46f,
                            size.width * 0.78f, size.height * 0.22f,
                            size.width * 1.1f, -size.height * 0.12f
                        )
                        lineTo(size.width, size.height)
                        close()
                    }
                    val sheen = Brush.linearGradient(
                        colors = listOf(colors.surface.copy(alpha = 0f), colors.surface.copy(alpha = 0.24f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    onDrawBehind { drawPath(path = sweep, brush = sheen) }
                }
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (workout == null) "Your workout. Your way." else "Pick up where you left off.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = workout?.name?.takeIf(String::isNotBlank) ?: if (workout == null) {
                    "Pick an exercise and go."
                } else {
                    "Current workout"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            startLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onWorkoutClick,
                enabled = !routineStarting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
            ) {
                Text(
                    text = when {
                        routineStarting -> "Starting day…"
                        workout == null -> "Start workout"
                        else -> "Resume workout"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (workout == null) {
                TextButton(
                    onClick = onPlanWorkoutClick,
                    enabled = !routineStarting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Plan workout", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HomeMetrics(
    streak: StreakCalculator.StreakResult,
    volume: VolumeOrbState,
    onStreakClick: () -> Unit
) {
    val largeText = LocalDensity.current.fontScale >= 1.35f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StreakMetric(streak, onStreakClick, Modifier.fillMaxWidth())
                VolumeMetric(volume, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreakMetric(streak, onStreakClick, Modifier.weight(1f))
                VolumeMetric(volume, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StreakMetric(
    streak: StreakCalculator.StreakResult,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = streak.streakDays.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSecondaryContainer
            )
            Text("day streak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                text = when {
                    streak.state == StreakCalculator.StreakState.BROKEN -> "Ready for a fresh start"
                    streak.streakDays == 0 -> "Starts with a workout"
                    streak.state == StreakCalculator.StreakState.ACTIVE -> "Trained today"
                    else -> "Rest days left: ${streak.skipsRemaining}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VolumeMetric(state: VolumeOrbState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val formattedVolume = remember(state.currentWeekVolume) {
        NumberFormat.getIntegerInstance().format(state.currentWeekVolume.coerceAtLeast(0f).toLong())
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$formattedVolume lb",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text("Volume this week", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (!state.isFirstWeek && state.lastWeekVolume > 0f) {
                LinearProgressIndicator(
                    progress = { state.progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
                    color = colors.primary,
                    trackColor = colors.outlineVariant
                )
            }
            Text(
                text = when {
                    state.currentWeekVolume <= 0f && state.isFirstWeek -> "No weighted sets yet"
                    state.isFirstWeek || state.lastWeekVolume <= 0f -> "No volume last week"
                    else -> "${(state.progressPercent * 100).toInt()}% of last week"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineFooter(
    routine: RoutineCardState?,
    hasOngoingWorkout: Boolean,
    routineStarting: Boolean,
    onStartClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onChooseClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (routine == null) "Routines · optional" else routine.routineName,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (routine != null) {
                val hasExercises = routine.exercisePreview.isNotEmpty()
                val dayLabel = listOfNotNull(routine.nextDayName, routine.dayPosition).joinToString(" · ")
                Text(
                    text = when {
                        routine.nextDayName == null -> "Add a day to your routine"
                        !hasExercises -> "$dayLabel · No exercises yet"
                        else -> dayLabel
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (routine.nextDayName != null && hasExercises) {
                        OutlinedButton(
                            onClick = onStartClick,
                            enabled = !hasOngoingWorkout && !routineStarting,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(if (routineStarting) "Starting…" else "Start day") }
                    }
                    TextButton(
                        onClick = onDetailsClick,
                        enabled = !routineStarting,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (routine.nextDayName != null && !hasExercises) "Add exercises" else "Manage")
                    }
                    TextButton(
                        onClick = onChooseClick,
                        enabled = !routineStarting,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Choose routine")
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = onChooseClick,
                        enabled = !routineStarting,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Choose routine")
                    }
                    OutlinedButton(
                        onClick = onCreateClick,
                        enabled = !routineStarting,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Create routine")
                    }
                }
            }
        }
    }
}
