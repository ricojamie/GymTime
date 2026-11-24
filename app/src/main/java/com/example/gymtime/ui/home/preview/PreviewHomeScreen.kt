package com.example.gymtime.ui.home.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.navigation.navigateToWorkoutOverview
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.IronLogSheetHeader
import com.example.gymtime.ui.home.HomeViewModel
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.util.StreakCalculator
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate

/** The route owns app flows and navigation; content receives values and callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    navController: NavController
) {
    val userName by viewModel.userName.collectAsStateWithLifecycle(initialValue = "Athlete")
    val ongoingWorkout by viewModel.ongoingWorkout.collectAsStateWithLifecycle()
    val routine by viewModel.routineCardState.collectAsStateWithLifecycle()
    val routineStarting by viewModel.routineStarting.collectAsStateWithLifecycle()
    val strength by viewModel.strengthMomentum.collectAsStateWithLifecycle()
    val strengthLoading by viewModel.strengthMomentumLoading.collectAsStateWithLifecycle()
    val strengthError by viewModel.strengthMomentumError.collectAsStateWithLifecycle()
    val volume by viewModel.volumeOrbState.collectAsStateWithLifecycle()
    val streak by viewModel.streakResult.collectAsStateWithLifecycle()
    val bestStreak by viewModel.bestStreak.collectAsStateWithLifecycle()
    val ytdWorkouts by viewModel.ytdWorkouts.collectAsStateWithLifecycle()
    val ytdVolume by viewModel.ytdVolume.collectAsStateWithLifecycle()
    val lastYearVolume by viewModel.lastYearVolume.collectAsStateWithLifecycle()

    var showStreak by rememberSaveable { mutableStateOf(false) }
    var showStrength by rememberSaveable { mutableStateOf(false) }
    var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var homeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalIronHaptics.current

    // Refresh when returning after pause or a date change without duplicating initial init.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshOnResume()
        onPauseOrDispose {
            viewModel.onScreenPaused()
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, navController) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.startRoutineWorkoutEvent.collect { start ->
                    navController.navigateToWorkoutExercise(start.firstExerciseId)
                }
            }
            launch {
                viewModel.createRoutineEvent.collect {
                    navController.navigateResumed(Screen.RoutineForm.createRoute())
                }
            }
            launch { viewModel.homeMessage.collect { homeMessage = it } }
        }
    }

    PreviewHomeContent(
        modifier = modifier,
        userName = userName,
        ongoingWorkout = ongoingWorkout,
        routine = routine,
        strengthMomentum = strength,
        strengthLoading = strengthLoading,
        strengthError = strengthError,
        volumeState = volume,
        streakResult = streak,
        bestStreak = bestStreak,
        ytdWorkouts = ytdWorkouts,
        onSettingsClick = {
            if (!routineStarting) navController.navigateResumed(Screen.Settings.route)
        },
        onStartWorkoutClick = {
            if (!routineStarting && ongoingWorkout != null) {
                navController.navigateToWorkoutOverview()
            } else if (!routineStarting) {
                navController.navigateResumed(Screen.ExerciseSelection.createRoute(workoutMode = true))
            }
        },
        onPlanWorkoutClick = {
            if (ongoingWorkout == null && !routineStarting) {
                navController.navigateResumed(Screen.WorkoutBuilder.route)
            }
        },
        onRoutineStartClick = {
            if (ongoingWorkout == null && !routineStarting) viewModel.startNextRoutineWorkout()
        },
        onRoutineDetailsClick = {
            if (!routineStarting) {
                routine?.routineId?.let { navController.navigateResumed(Screen.RoutineDetail.createRoute(it)) }
            }
        },
        onChooseRoutineClick = {
            if (!routineStarting) {
                navController.navigateResumed(Screen.RoutineList.route)
            }
        },
        onCreateRoutineClick = viewModel::requestCreateRoutine,
        onStreakClick = { showStreak = true },
        onTrendClick = { muscle -> selectedMuscle = muscle; showStrength = true },
        onTrendInfoClick = { selectedMuscle = null; showStrength = true },
        onRetryTrendsClick = viewModel::retryStrengthMomentum,
        routineStarting = routineStarting
    )

    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    if (showStrength) {
        ModalBottomSheet(
            onDismissRequest = { showStrength = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = IronLogRadius.SheetShape,
            containerColor = surfaces.plateTop,
            contentColor = colors.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) }
        ) {
            PreviewStrengthTrendDetails(
                state = strength,
                selectedMuscle = selectedMuscle,
                onSelectMuscle = { selectedMuscle = it },
                onClose = {
                    haptics.keyTap()
                    showStrength = false
                }
            )
        }
    }
    if (showStreak) {
        ModalBottomSheet(
            onDismissRequest = { showStreak = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = IronLogRadius.SheetShape,
            containerColor = surfaces.plateTop,
            contentColor = colors.onSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) }
        ) {
            PreviewStreakDetails(streak, bestStreak, ytdWorkouts, ytdVolume, lastYearVolume) {
                haptics.keyTap()
                showStreak = false
            }
        }
    }
    homeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { homeMessage = null },
            title = { Text("Routine") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.keyTap()
                        homeMessage = null
                    }
                ) { Text("Got it") }
            },
            containerColor = surfaces.plateTop,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewStreakDetails(
    streak: StreakCalculator.StreakResult,
    bestStreak: Int,
    ytdWorkouts: Int,
    ytdVolume: Float,
    lastYearVolume: Float,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val year = remember { LocalDate.now().year }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val statusText = when {
        streak.state == StreakCalculator.StreakState.BROKEN -> "Your next workout starts a fresh streak."
        streak.streakDays == 0 -> "Your next workout starts your streak."
        streak.state == StreakCalculator.StreakState.ACTIVE -> "You've trained today."
        else -> "Rest days can be part of your streak."
    }

    val rows = listOf(
        "Rest days remaining this week" to "${streak.skipsRemaining}",
        "All-time best" to "$bestStreak days",
        "$year workouts" to "$ytdWorkouts",
        "$year volume" to "${numbers.format(ytdVolume.toLong())} lb",
        "${year - 1} volume" to "${numbers.format(lastYearVolume.toLong())} lb"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
    ) {
        IronLogSheetHeader(
            title = "Your streak",
            subtitle = statusText,
            onClose = onClose
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (streak.streakDays > 0) {
                IronLogLedPip(
                    color = surfaces.ledGlow,
                    size = 6.dp,
                    pulsing = streak.state == StreakCalculator.StreakState.ACTIVE
                )
            }
            OdometerText(
                text = streak.streakDays.toString(),
                style = IronLogType.monumentalNumeral(MaterialTheme.typography.displaySmall).copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = surfaces.accent
            )
            Text(
                text = if (streak.streakDays == 1) "DAY" else "DAYS",
                style = IronLogType.technicalEyebrow(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        IronLogGroupedCard(showBorder = true, shape = IronLogRadius.PlateShape) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    IronLogDivider()
                }
                StreakDetailRow(label = label, value = value)
            }
        }
        Text(
            text = buildString {
                append("Allows ${streak.allowedSkipsPerWeek} rest days per week.")
                if (lastYearVolume > 0f) {
                    append(" ${numbers.format(ytdVolume / lastYearVolume * 100)}% of last year's volume.")
                }
            },
            style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun StreakDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .padding(horizontal = IronLogSpace.CardPadding, vertical = IronLogSpace.RowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium).copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
