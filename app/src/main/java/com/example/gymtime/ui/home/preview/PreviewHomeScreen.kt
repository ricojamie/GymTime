package com.example.gymtime.ui.home.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.navigation.navigateToWorkoutOverview
import com.example.gymtime.ui.home.HomeViewModel
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

    // Returning from a completed workout must refresh trends even on the same day.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshData()
        onPauseOrDispose { }
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
                    navController.navigate(Screen.RoutineForm.createRoute()) { launchSingleTop = true }
                }
            }
            launch { viewModel.homeMessage.collect { homeMessage = it } }
        }
    }

    PreviewHomeContent(
        modifier = modifier,
        userName = userName,
        newUiEnabled = true,
        ongoingWorkout = ongoingWorkout,
        routine = routine,
        routineStarting = routineStarting,
        strengthMomentum = strength,
        strengthLoading = strengthLoading,
        strengthError = strengthError,
        volumeState = volume,
        streakResult = streak,
        onNewUiEnabledChange = viewModel::setNewUiEnabled,
        onSettingsClick = {
            if (!routineStarting) navController.navigate(Screen.Settings.route)
        },
        onStartWorkoutClick = {
            if (!routineStarting && ongoingWorkout != null) {
                navController.navigateToWorkoutOverview()
            } else if (!routineStarting) {
                navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true)) {
                    launchSingleTop = true
                }
            }
        },
        onPlanWorkoutClick = {
            if (ongoingWorkout == null && !routineStarting) {
                navController.navigate(Screen.WorkoutBuilder.route) { launchSingleTop = true }
            }
        },
        onRoutineStartClick = {
            if (ongoingWorkout == null && !routineStarting) viewModel.startNextRoutineWorkout()
        },
        onRoutineDetailsClick = {
            if (!routineStarting) {
                routine?.routineId?.let { navController.navigate(Screen.RoutineDetail.createRoute(it)) }
            }
        },
        onChooseRoutineClick = {
            if (!routineStarting) {
                navController.navigate(Screen.RoutineList.route) { launchSingleTop = true }
            }
        },
        onCreateRoutineClick = viewModel::requestCreateRoutine,
        onStreakClick = { showStreak = true },
        onTrendClick = { muscle -> selectedMuscle = muscle; showStrength = true },
        onTrendInfoClick = { selectedMuscle = null; showStrength = true },
        onRetryTrendsClick = viewModel::retryStrengthMomentum
    )

    val colors = MaterialTheme.colorScheme
    if (showStrength) {
        ModalBottomSheet(
            onDismissRequest = { showStrength = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ) {
            PreviewStrengthTrendDetails(
                state = strength,
                selectedMuscle = selectedMuscle,
                onSelectMuscle = { selectedMuscle = it },
                onClose = { showStrength = false }
            )
        }
    }
    if (showStreak) {
        ModalBottomSheet(
            onDismissRequest = { showStreak = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ) {
            PreviewStreakDetails(streak, bestStreak, ytdWorkouts, ytdVolume, lastYearVolume) {
                showStreak = false
            }
        }
    }
    homeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { homeMessage = null },
            title = { Text("Routine") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { homeMessage = null }) { Text("Got it") } },
            containerColor = colors.surface,
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
    val year = LocalDate.now().year
    val numbers = NumberFormat.getIntegerInstance()
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Your streak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("Done") }
        }
        Text(
            "${streak.streakDays} days",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = when {
                streak.state == StreakCalculator.StreakState.BROKEN -> "Your next workout starts a fresh streak."
                streak.streakDays == 0 -> "Your next workout starts your streak."
                streak.state == StreakCalculator.StreakState.ACTIVE -> "You've trained today."
                else -> "Rest days can be part of your streak."
            },
            color = colors.onSurfaceVariant
        )
        StreakDetailRow("Rest days remaining this week", "${streak.skipsRemaining}")
        Text(
            "Your setting allows ${streak.allowedSkipsPerWeek} rest days per week.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        StreakDetailRow("All-time best", "$bestStreak days")
        StreakDetailRow("$year workouts", "$ytdWorkouts")
        StreakDetailRow("$year volume", "${numbers.format(ytdVolume.toLong())} lb")
        StreakDetailRow("${year - 1} volume", "${numbers.format(lastYearVolume.toLong())} lb")
        if (lastYearVolume > 0f) {
            Text(
                "${numbers.format(ytdVolume / lastYearVolume * 100)}% of last year's volume",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreakDetailRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
