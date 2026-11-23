package com.example.gymtime.ui.home.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.home.RoutineCardState
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.util.StreakCalculator
import java.text.DateFormat
import java.text.NumberFormat

/** App wiring owns data and navigation; this surface owns only its scrolling runtime. */
@Composable
fun PreviewHomeContent(
    modifier: Modifier = Modifier,
    userName: String,
    ongoingWorkout: Workout?,
    routine: RoutineCardState?,
    strengthMomentum: StrengthMomentumState,
    strengthLoading: Boolean,
    strengthError: String?,
    volumeState: VolumeOrbState,
    streakResult: StreakCalculator.StreakResult,
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
                .padding(
                    start = IronLogSpace.ScreenHorizontal,
                    end = IronLogSpace.ScreenHorizontal,
                    top = 8.dp,
                    bottom = IronLogSpace.BottomNavClearance
                ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            HomePreviewHeader(onSettingsClick = onSettingsClick)
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
private fun HomePreviewHeader(onSettingsClick: () -> Unit) {
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
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.sizeIn(
                minWidth = IronLogSpace.TouchTargetMin,
                minHeight = IronLogSpace.TouchTargetMin
            )
        ) {
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
    val startLabel = remember(workout?.startTime) {
        workout?.startTime?.let { "Started ${DateFormat.getTimeInstance(DateFormat.SHORT).format(it)}" }
    }
    IronLogCard(
        highlighted = workout != null,
        showBorder = true,
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        borderColor = IronLogBorder.borderColor(colors, highlighted = workout != null),
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                        colors = listOf(colors.surface.copy(alpha = 0f), colors.surface.copy(alpha = 0.22f)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    onDrawBehind { drawPath(path = sweep, brush = sheen) }
                }
                .padding(
                    start = IronLogSpace.CardPadding,
                    end = IronLogSpace.CardPadding,
                    top = IronLogSpace.CardPadding,
                    bottom = if (workout == null) IronLogSpace.CompactGap else IronLogSpace.CardPadding
                ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            if (workout != null) {
                IronLogStatusChip(
                    label = "Active",
                    tone = IronLogStatusTone.ACTIVE
                )
            }
            Text(
                text = if (workout == null) "Your workout. Your way." else "Pick up where you left off.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            val subtitle = listOfNotNull(
                workout?.name?.takeIf(String::isNotBlank) ?: if (workout == null) {
                    "Pick an exercise and go."
                } else {
                    "Current workout"
                },
                startLabel
            ).joinToString(" · ")
            Text(
                text = subtitle,
                style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                color = colors.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            IronLogPrimaryButton(
                text = when {
                    routineStarting -> "Starting day…"
                    workout == null -> "Start workout"
                    else -> "Resume workout"
                },
                onClick = onWorkoutClick,
                enabled = !routineStarting,
                loading = routineStarting,
                modifier = Modifier.fillMaxWidth()
            )
            if (workout == null) {
                TextButton(
                    onClick = onPlanWorkoutClick,
                    enabled = !routineStarting,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                    shape = IronLogRadius.ControlShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Plan workout", fontWeight = FontWeight.SemiBold)
                }
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
            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                StreakMetric(streak, onStreakClick, Modifier.fillMaxWidth())
                VolumeMetric(volume, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                StreakMetric(streak, onStreakClick, Modifier.weight(1f).fillMaxHeight())
                VolumeMetric(volume, Modifier.weight(1f).fillMaxHeight())
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
    IronLogCard(
        modifier = modifier,
        onClick = onClick,
        showBorder = true,
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        borderColor = IronLogBorder.borderColor(colors),
        shape = IronLogRadius.CardShape,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "DAY STREAK",
            style = IronLogType.sectionEyebrow(),
            color = colors.onSecondaryContainer
        )
        Text(
            text = streak.streakDays.toString(),
            style = IronLogType.tabular(MaterialTheme.typography.headlineMedium).copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = colors.onSecondaryContainer
        )
        Text(
            text = when {
                streak.state == StreakCalculator.StreakState.BROKEN -> "Ready for a fresh start"
                streak.streakDays == 0 -> "Starts with a workout"
                streak.state == StreakCalculator.StreakState.ACTIVE -> "Trained today"
                else -> "Rest days left: ${streak.skipsRemaining}"
            },
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = colors.onSecondaryContainer
        )
    }
}

@Composable
private fun VolumeMetric(state: VolumeOrbState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val formattedVolume = remember(state.currentWeekVolume) {
        NumberFormat.getIntegerInstance().format(state.currentWeekVolume.coerceAtLeast(0f).toLong())
    }
    IronLogCard(
        modifier = modifier,
        showBorder = true,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        borderColor = IronLogBorder.borderColor(colors),
        shape = IronLogRadius.CardShape,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "THIS WEEK",
            style = IronLogType.sectionEyebrow(),
            color = colors.onSurfaceVariant
        )
        Text(
            text = "$formattedVolume lb",
            style = IronLogType.tabular(MaterialTheme.typography.headlineMedium).copy(
                fontWeight = FontWeight.ExtraBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!state.isFirstWeek && state.lastWeekVolume > 0f) {
            LinearProgressIndicator(
                progress = { state.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(6.dp)
                    .clip(IronLogRadius.MicroShape),
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
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = colors.onSurfaceVariant
        )
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
    IronLogCard(
        showBorder = true,
        containerColor = colors.surfaceContainerLow,
        borderColor = IronLogBorder.borderColor(colors),
        shape = IronLogRadius.CardShape,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        IronLogSectionHeader(
            title = if (routine == null) "Routines · Optional" else "Active routine"
        )
        if (routine != null) {
            Text(
                text = routine.routineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val hasExercises = routine.exercisePreview.isNotEmpty()
            val dayLabel = listOfNotNull(routine.nextDayName, routine.dayPosition, routine.lastPerformedLabel)
                .joinToString(" · ")
            Text(
                text = when {
                    routine.nextDayName == null -> "Add a day to your routine"
                    !hasExercises -> "$dayLabel · No exercises yet"
                    else -> dayLabel
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (routine.nextDayName != null && hasExercises) {
                    IronLogSecondaryButton(
                        text = if (routineStarting) "Starting…" else "Start day",
                        onClick = onStartClick,
                        enabled = !hasOngoingWorkout && !routineStarting,
                        outlined = true
                    )
                }
                TextButton(
                    onClick = onDetailsClick,
                    enabled = !routineStarting,
                    shape = IronLogRadius.ControlShape,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text(if (routine.nextDayName != null && !hasExercises) "Add exercises" else "Manage")
                }
                TextButton(
                    onClick = onChooseClick,
                    enabled = !routineStarting,
                    shape = IronLogRadius.ControlShape,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Choose routine")
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IronLogSecondaryButton(
                    text = "Choose routine",
                    onClick = onChooseClick,
                    enabled = !routineStarting,
                    outlined = true
                )
                IronLogSecondaryButton(
                    text = "Create routine",
                    onClick = onCreateClick,
                    enabled = !routineStarting,
                    outlined = true
                )
            }
        }
    }
}
