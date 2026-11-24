package com.example.gymtime.ui.home.preview

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.gymtime.data.VolumeOrbState
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.domain.analytics.StrengthMomentumState
import com.example.gymtime.ui.components.IronLogCalibrationBar
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogMascotBadge
import com.example.gymtime.ui.components.IronLogMascotStyle
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.home.RoutineCardState
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.staggeredEntranceOnce
import com.example.gymtime.util.StreakCalculator
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private const val HOME_ENTRANCE_KEY = "home_telemetry_cockpit"

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
    routineStarting: Boolean = false,
    bestStreak: Int = 0,
    ytdWorkouts: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = IronLogSpace.ScreenHorizontal,
                    end = IronLogSpace.ScreenHorizontal,
                    top = 10.dp,
                    bottom = IronLogSpace.BottomNavClearance + 8.dp
                ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            HomePreviewHeader(
                userName = userName,
                hasOngoingWorkout = ongoingWorkout != null,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.staggeredEntranceOnce(key = HOME_ENTRANCE_KEY, index = 0)
            )
            WorkoutHero(
                workout = ongoingWorkout,
                routineStarting = routineStarting,
                onWorkoutClick = onStartWorkoutClick,
                onPlanWorkoutClick = onPlanWorkoutClick,
                modifier = Modifier.staggeredEntranceOnce(key = HOME_ENTRANCE_KEY, index = 1)
            )
            PreviewStrengthTrendsCard(
                state = strengthMomentum,
                isLoading = strengthLoading,
                error = strengthError,
                onMuscleClick = onTrendClick,
                onInfoClick = onTrendInfoClick,
                onRetry = onRetryTrendsClick,
                modifier = Modifier.staggeredEntranceOnce(key = HOME_ENTRANCE_KEY, index = 2)
            )
            HomeMetrics(
                streak = streakResult,
                bestStreak = bestStreak,
                ytdWorkouts = ytdWorkouts,
                volume = volumeState,
                onStreakClick = onStreakClick,
                modifier = Modifier.staggeredEntranceOnce(key = HOME_ENTRANCE_KEY, index = 3)
            )
            RoutineFooter(
                routine = routine,
                hasOngoingWorkout = ongoingWorkout != null,
                routineStarting = routineStarting,
                onStartClick = onRoutineStartClick,
                onDetailsClick = onRoutineDetailsClick,
                onChooseClick = onChooseRoutineClick,
                onCreateClick = onCreateRoutineClick,
                modifier = Modifier.staggeredEntranceOnce(key = HOME_ENTRANCE_KEY, index = 4)
            )
        }
    }
}

@Composable
private fun HomePreviewHeader(
    userName: String,
    hasOngoingWorkout: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current

    val eyebrowText = remember(hasOngoingWorkout) {
        val dateStr = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.getDefault()))
            .uppercase(Locale.getDefault())
        if (hasOngoingWorkout) {
            "SESSION LIVE · $dateStr"
        } else {
            val hour = LocalTime.now().hour
            val period = when {
                hour < 12 -> "MORNING BRIEF"
                hour < 17 -> "AFTERNOON BRIEF"
                else -> "EVENING BRIEF"
            }
            "$period · $dateStr"
        }
    }

    val displayName = remember(userName) { userName.trim().ifBlank { "Athlete" } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IronLogMascotBadge(
                    size = 32.dp,
                    style = IronLogMascotStyle.MEDALLION,
                    showGlow = hasOngoingWorkout
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasOngoingWorkout) {
                            IronLogLedPip(
                                color = surfaces.ledGlow,
                                size = 5.dp,
                                pulsing = true
                            )
                        }
                        Text(
                            text = eyebrowText,
                            style = IronLogType.technicalEyebrow(),
                            color = if (hasOngoingWorkout) surfaces.accent else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            append("IRON")
                            withStyle(SpanStyle(color = surfaces.accent)) { append("LOG") }
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.03.em
                        ),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            MachinedCircularSettingsWell(
                onClick = {
                    haptics.keyTap()
                    onSettingsClick()
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Hey, $displayName.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (hasOngoingWorkout) {
                    "Telemetry active. Pick up right where you left off."
                } else {
                    "All systems local. Ready when you are."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MachinedCircularSettingsWell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.92f,
            highlightAlpha = 0.10f,
            cornerRadius = 24.dp
        )
    }
    val rimStroke = remember(surfaces) {
        IronLogBorder.specularRimStroke(
            surfaces = surfaces,
            highlighted = false,
            width = IronLogBorder.Thin
        )
    }

    Box(
        modifier = modifier
            .size(IronLogSpace.TouchTargetMin)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                role = Role.Button,
                onClick = onClick
            )
            .background(surfaces.wellFill)
            .drawWithCache {
                val shadowH = (size.height * 0.28f).coerceAtLeast(1f)
                val topShadow = Brush.verticalGradient(
                    colors = listOf(surfaces.wellInnerShadow, Color.Transparent),
                    startY = 0f,
                    endY = shadowH
                )
                onDrawBehind {
                    drawRect(brush = topShadow, size = Size(size.width, shadowH))
                }
            }
            .border(border = rimStroke, shape = CircleShape)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WorkoutHero(
    workout: Workout?,
    routineStarting: Boolean,
    onWorkoutClick: () -> Unit,
    onPlanWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val startLabel = remember(workout?.startTime) {
        workout?.startTime?.let {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(it).uppercase(Locale.getDefault())
        }
    }

    IronLogCard(
        modifier = modifier,
        highlighted = workout != null,
        spotlight = true,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        if (workout != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IronLogLedPip(
                    color = surfaces.ledGlow,
                    size = 6.dp,
                    pulsing = true
                )
                Text(
                    text = "ACTIVE SESSION",
                    style = IronLogType.technicalEyebrow(),
                    color = surfaces.accent,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (startLabel != null) {
                    Text(
                        text = "STARTED $startLabel",
                        style = IronLogType.telemetryLabel(),
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            val sessionTitle = workout.name?.takeIf(String::isNotBlank)
                ?: workout.routineDayNameSnapshot?.takeIf(String::isNotBlank)
                ?: "Active workout"
            Text(
                text = sessionTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            IronLogRecessedWell(
                modifier = Modifier.fillMaxWidth(),
                active = true,
                accentColor = surfaces.accent,
                shape = IronLogRadius.KeycapShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "ELAPSED",
                            style = IronLogType.technicalEyebrow(),
                            color = colors.onSurfaceVariant
                        )
                        ElapsedSessionOdometer(
                            startTime = workout.startTime,
                            color = colors.onSurface
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (workout.startedFromRoutine) "ROUTINE" else "MODE",
                            style = IronLogType.technicalEyebrow(),
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = workout.routineNameSnapshot?.takeIf(String::isNotBlank) ?: "FREE LOG",
                            style = IronLogType.telemetryLabel(),
                            fontWeight = FontWeight.Bold,
                            color = surfaces.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            IronLogPrimaryButton(
                text = if (routineStarting) "Starting day…" else "Resume workout",
                onClick = {
                    haptics.confirm()
                    onWorkoutClick()
                },
                enabled = !routineStarting,
                loading = routineStarting,
                leadingIcon = Icons.Rounded.PlayArrow,
                shape = IronLogRadius.KeycapShape,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IronLogLedPip(
                    color = surfaces.accent.copy(alpha = 0.72f),
                    size = 5.dp,
                    pulsing = false
                )
                Text(
                    text = "STANDBY · TELEMETRY STAGE",
                    style = IronLogType.technicalEyebrow(),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Your workout. Your way.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onSurface
            )

            Text(
                text = "Pick an exercise and start logging immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            Spacer(Modifier.height(2.dp))

            IronLogPrimaryButton(
                text = if (routineStarting) "Starting day…" else "Start workout",
                onClick = {
                    haptics.confirm()
                    onWorkoutClick()
                },
                enabled = !routineStarting,
                loading = routineStarting,
                leadingIcon = Icons.Rounded.Bolt,
                shape = IronLogRadius.KeycapShape,
                modifier = Modifier.fillMaxWidth()
            )

            PlanWorkoutRecessedSlot(
                enabled = !routineStarting,
                onClick = {
                    haptics.keyTap()
                    onPlanWorkoutClick()
                }
            )
        }
    }
}

@Composable
private fun PlanWorkoutRecessedSlot(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.985f,
            highlightAlpha = 0.06f,
            cornerRadius = IronLogRadius.Keycap
        )
    }

    IronLogRecessedWell(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .clip(IronLogRadius.KeycapShape)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        active = false,
        shape = IronLogRadius.KeycapShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Text(
                text = "Plan workout",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.48f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "BUILDER",
                style = IronLogType.telemetryLabel(),
                color = colors.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) surfaces.accent else colors.onSurfaceVariant.copy(alpha = 0.48f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ElapsedSessionOdometer(
    startTime: Date,
    color: Color
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowMs by remember(startTime) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startTime, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val formattedElapsed = remember(nowMs, startTime) {
        val totalSeconds = ((nowMs - startTime.time).coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    OdometerText(
        text = formattedElapsed,
        style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineMedium).copy(
            fontWeight = FontWeight.ExtraBold
        ),
        color = color
    )
}

@Composable
private fun HomeMetrics(
    streak: StreakCalculator.StreakResult,
    bestStreak: Int,
    ytdWorkouts: Int,
    volume: VolumeOrbState,
    onStreakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val largeText = LocalDensity.current.fontScale >= 1.35f
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                StreakMetric(
                    streak = streak,
                    bestStreak = bestStreak,
                    ytdWorkouts = ytdWorkouts,
                    onClick = onStreakClick,
                    modifier = Modifier.fillMaxWidth()
                )
                VolumeMetric(
                    state = volume,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                StreakMetric(
                    streak = streak,
                    bestStreak = bestStreak,
                    ytdWorkouts = ytdWorkouts,
                    onClick = onStreakClick,
                    modifier = Modifier
                        .weight(0.43f)
                        .fillMaxHeight()
                )
                VolumeMetric(
                    state = volume,
                    modifier = Modifier
                        .weight(0.57f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun StreakMetric(
    streak: StreakCalculator.StreakResult,
    bestStreak: Int,
    ytdWorkouts: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.97f,
            highlightAlpha = 0.08f,
            cornerRadius = IronLogRadius.Plate
        )
    }

    val effectiveBest = maxOf(bestStreak, streak.streakDays)
    val streakColor = if (streak.streakDays > 0) colors.onSurface else colors.onSurfaceVariant

    IronLogCard(
        modifier = modifier
            .clip(IronLogRadius.PlateShape)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                role = Role.Button,
                onClick = {
                    haptics.keyTap()
                    onClick()
                }
            ),
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "STREAK",
                style = IronLogType.technicalEyebrow(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (streak.streakDays > 0) {
                IronLogLedPip(
                    color = surfaces.ledGlow,
                    size = 5.dp,
                    pulsing = streak.state == StreakCalculator.StreakState.ACTIVE
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OdometerText(
                text = streak.streakDays.toString(),
                style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineLarge).copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = streakColor
            )
            Text(
                text = if (streak.streakDays == 1) "DAY" else "DAYS",
                style = IronLogType.technicalEyebrow(),
                color = if (streak.streakDays > 0) surfaces.accent else colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Text(
            text = when {
                streak.state == StreakCalculator.StreakState.BROKEN -> "Ready for a fresh start"
                streak.streakDays == 0 -> "Starts with a workout"
                streak.state == StreakCalculator.StreakState.ACTIVE -> "Trained today"
                else -> "Rest days left: ${streak.skipsRemaining}"
            },
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.weight(1f, fill = false))

        if (streak.allowedSkipsPerWeek in 1..4) {
            RestAllowancePipStrip(
                allowed = streak.allowedSkipsPerWeek,
                remaining = streak.skipsRemaining,
                accentColor = surfaces.accent,
                inactiveColor = colors.outlineVariant.copy(alpha = 0.45f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BEST $effectiveBest",
                style = IronLogType.telemetryLabel(),
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = "YTD $ytdWorkouts",
                style = IronLogType.telemetryLabel(),
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RestAllowancePipStrip(
    allowed: Int,
    remaining: Int,
    accentColor: Color,
    inactiveColor: Color
) {
    val safeRemaining = remaining.coerceIn(0, allowed)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(allowed) { index ->
            val lit = index < safeRemaining
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(IronLogRadius.CapsuleShape)
                    .background(if (lit) accentColor else inactiveColor)
            )
        }
    }
}

@Composable
private fun VolumeMetric(
    state: VolumeOrbState,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val formattedVolume = remember(state.currentWeekVolume) {
        NumberFormat.getIntegerInstance().format(state.currentWeekVolume.coerceAtLeast(0f).toLong())
    }
    val hasPriorWeek = !state.isFirstWeek && state.lastWeekVolume > 0f
    val percentInt = remember(state.progressPercent) {
        (state.progressPercent * 100f).toInt().coerceAtLeast(0)
    }
    val badgeText = when {
        state.hasOverflowed -> "+${percentInt - 100}% OVER"
        hasPriorWeek -> "$percentInt%"
        else -> "BASELINE"
    }

    IronLogCard(
        modifier = modifier,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "WEEKLY VOLUME",
                style = IronLogType.technicalEyebrow(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badgeText,
                style = IronLogType.telemetryLabel(),
                fontWeight = FontWeight.Bold,
                color = if (hasPriorWeek) surfaces.accent else colors.onSurfaceVariant,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OdometerText(
                text = formattedVolume,
                modifier = Modifier.weight(1f, fill = false),
                style = IronLogType.monumentalNumeral(MaterialTheme.typography.headlineLarge).copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = colors.onSurface,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "LB",
                style = IronLogType.technicalEyebrow(),
                color = surfaces.accent,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        IronLogCalibrationBar(
            progress = if (hasPriorWeek) state.progressPercent.coerceIn(0f, 1f) else 0f,
            accentColor = surfaces.accent,
            tickCount = 10,
            height = 14.dp
        )

        Text(
            text = when {
                state.currentWeekVolume <= 0f && state.isFirstWeek -> "No weighted sets yet"
                !hasPriorWeek -> "No volume last week"
                else -> "$percentInt% of last week"
            },
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current

    IronLogCard(
        modifier = modifier,
        showBorder = true,
        shape = IronLogRadius.PlateShape,
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (routine != null) {
                IronLogLedPip(
                    color = surfaces.accent.copy(alpha = 0.72f),
                    size = 5.dp,
                    pulsing = false
                )
            }
            Text(
                text = if (routine == null) "ROUTINES · OPTIONAL" else "ACTIVE ROUTINE",
                style = IronLogType.technicalEyebrow(),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (routine != null) {
            Text(
                text = routine.routineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
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
                style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                color = colors.onSurfaceVariant
            )

            if (hasExercises) {
                IronLogRecessedWell(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IronLogRadius.TightShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = routine.exercisePreview.joinToString(" · "),
                        style = IronLogType.telemetryLabel(),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                if (routine.nextDayName != null && hasExercises) {
                    IronLogSculptedKeycap(
                        onClick = onStartClick,
                        enabled = !hasOngoingWorkout && !routineStarting,
                        accented = true,
                        hapticType = KeycapHaptic.CONFIRM,
                        shape = IronLogRadius.KeycapShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (routineStarting) "Starting…" else "Start day",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IronLogSculptedKeycap(
                    onClick = onDetailsClick,
                    enabled = !routineStarting,
                    accented = false,
                    hapticType = KeycapHaptic.KEY_TAP,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (routine.nextDayName != null && !hasExercises) "Add exercises" else "Manage",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IronLogSculptedKeycap(
                    onClick = onChooseClick,
                    enabled = !routineStarting,
                    accented = false,
                    hapticType = KeycapHaptic.KEY_TAP,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Choose routine",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                IronLogSculptedKeycap(
                    onClick = onChooseClick,
                    enabled = !routineStarting,
                    accented = false,
                    hapticType = KeycapHaptic.KEY_TAP,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Choose routine",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IronLogSculptedKeycap(
                    onClick = onCreateClick,
                    enabled = !routineStarting,
                    accented = false,
                    hapticType = KeycapHaptic.KEY_TAP,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Create routine",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
