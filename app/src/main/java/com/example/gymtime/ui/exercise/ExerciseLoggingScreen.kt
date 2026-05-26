package com.example.gymtime.ui.exercise

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import com.example.gymtime.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.domain.recommendation.ExerciseAttemptRecommendation
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.components.PlateCalculatorSheet
import com.example.gymtime.ui.components.VolumeProgressBar
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.util.TimeUtils
import com.example.gymtime.util.TimeFormatter
import com.example.gymtime.ui.components.InputCard
import com.example.gymtime.ui.components.TimeInputCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLoggingScreen(
    navController: NavController,
    viewModel: ExerciseLoggingViewModel = hiltViewModel()
) {
    val exercise by viewModel.exercise.collectAsState()
    val currentWorkout by viewModel.currentWorkout.collectAsState()
    val loggedSets by viewModel.loggedSets.collectAsState()
    val weight by viewModel.weight.collectAsState()
    val calories by viewModel.calories.collectAsState()
    val reps by viewModel.reps.collectAsState()
    val rpe by viewModel.rpe.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val distance by viewModel.distance.collectAsState()
    val selectedDistanceUnit by viewModel.selectedDistanceUnit.collectAsState()
    val restTime by viewModel.restTime.collectAsState()
    val countdownTimer by viewModel.countdownTimer.collectAsState()
    val isWarmup by viewModel.isWarmup.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val timerAudioEnabled by viewModel.timerAudioEnabled.collectAsState(initial = true)
    val timerVibrateEnabled by viewModel.timerVibrateEnabled.collectAsState(initial = true)
    val lastWorkoutSets by viewModel.lastWorkoutSets.collectAsState()
    val workoutOverview by viewModel.workoutOverview.collectAsState()
    val currentPlanItem by viewModel.currentPlanItem.collectAsState()
    val personalBestsByReps by viewModel.personalBestsByReps.collectAsState()
    val attemptRecommendation by viewModel.attemptRecommendation.collectAsState()
    val volumeOrbState by viewModel.volumeOrbState.collectAsState()


    val editingSet by viewModel.editingSet.collectAsState()
    val timerAutoStart by viewModel.timerAutoStart.collectAsState(initial = true)
    val barWeight by viewModel.barWeight.collectAsState(initial = 45f)
    val availablePlates by viewModel.availablePlates.collectAsState(initial = listOf(45f, 35f, 25f, 10f, 5f, 2.5f))
    val loadingSides by viewModel.loadingSides.collectAsState(initial = 2)
    var showDistanceUnitMenu by remember { mutableStateOf(false) }

    // Superset state
    val isInSupersetMode by viewModel.isInSupersetMode.collectAsState()
    val supersetExercises by viewModel.supersetExercises.collectAsState()
    val currentSupersetIndex by viewModel.currentSupersetIndex.collectAsState()

    val nextExerciseId by viewModel.nextExerciseId.collectAsState()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showWorkoutOverview by remember { mutableStateOf(false) }
    var showExerciseHistory by remember { mutableStateOf(false) }
    var showPlateCalculator by remember { mutableStateOf(false) }

    var personalRecords by remember { mutableStateOf<PersonalRecords?>(null) }
    var exerciseHistory by remember { mutableStateOf<Map<Long, List<com.example.gymtime.data.db.entity.Set>>>(emptyMap()) }

    // Set deletion
    var selectedSetToDelete by remember { mutableStateOf<com.example.gymtime.data.db.entity.Set?>(null) }

    // Set note editing
    var setToAddNote by remember { mutableStateOf<com.example.gymtime.data.db.entity.Set?>(null) }
    var noteText by remember { mutableStateOf("") }

    val view = LocalView.current
    
    // Observe navigation events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { workoutId ->
            navController.navigate(Screen.PostWorkoutSummary.createRoute(workoutId))
        }
    }

    // Observe auto-switch events for superset mode
    LaunchedEffect(Unit) {
        viewModel.autoSwitchEvent.collect { nextExerciseId ->
            navController.navigate(Screen.ExerciseLogging.createRoute(nextExerciseId)) {
                // Pop the current logging screen off so we don't stack A -> B -> A -> B
                popUpTo(navController.currentBackStackEntry?.destination?.route ?: return@navigate) {
                    inclusive = true
                }
                launchSingleTop = true
            }
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    // Keep system back behavior consistent even while superset mode is active.
    BackHandler(enabled = isInSupersetMode) {
        navController.navigateHomeAndClearStack()
    }

    val scope = rememberCoroutineScope()
    val gradientColors = com.example.gymtime.ui.theme.LocalGradientColors.current

    // Load workout overview when bottom sheet opens
    LaunchedEffect(showWorkoutOverview) {
        if (showWorkoutOverview) {
            viewModel.loadWorkoutOverview()
        }
    }

    // Load exercise history when bottom sheet opens
    LaunchedEffect(showExerciseHistory) {
        if (showExerciseHistory) {
            personalRecords = viewModel.getPersonalRecords()
            exerciseHistory = viewModel.getExerciseHistory()
        }
    }

    // Get best set data for inline "Best:" display
    val bestWeight by viewModel.bestWeight.collectAsState()
    val bestReps by viewModel.bestReps.collectAsState()

    // Track if timer just finished for animation
    var timerJustFinished by remember { mutableStateOf(false) }

    // Timer countdown is now handled by RestTimerService (persistent notification)
    // No need for LaunchedEffect - service manages countdown and vibration

    Scaffold(
        topBar = {
            exercise?.let { ex ->
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = ex.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                fontWeight = FontWeight.Bold,
                                color = LocalAppColors.current.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ex.targetMuscle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.navigateHomeAndClearStack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = LocalAppColors.current.textPrimary
                            )
                        }
                    },
                    actions = {
                        // Timer finished animation
                        val timerPulseScale by animateFloatAsState(
                            targetValue = if (timerJustFinished) 1.15f else 1f,
                            animationSpec = if (timerJustFinished) {
                                infiniteRepeatable(
                                    animation = tween(300),
                                    repeatMode = RepeatMode.Reverse
                                )
                            } else {
                                tween(300)
                            },
                            label = "timer_pulse"
                        )

                        // Timer Pill Action
                        Surface(
                            onClick = { showTimerDialog = true },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (timerJustFinished) MaterialTheme.colorScheme.primary else LocalAppColors.current.surfaceCards,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (timerJustFinished) 2.dp else 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (timerJustFinished) 1f else 0.5f)
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .scale(timerPulseScale)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_timer),
                                    contentDescription = "Timer",
                                    tint = if (timerJustFinished) Color.Black else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (timerJustFinished) "GO!" else if (isTimerRunning) {
                                        TimeFormatter.formatSecondsToMMSS(countdownTimer)
                                    } else {
                                        TimeFormatter.formatSecondsToMMSS(restTime)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (timerJustFinished) Color.Black else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Vertical divider
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp),
                            color = LocalAppColors.current.textTertiary.copy(alpha = 0.3f)
                        )

                        // Workout overview icon
                        IconButton(onClick = { showWorkoutOverview = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Workout Overview",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Exercise history icon
                        IconButton(onClick = { showExerciseHistory = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Exercise History",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(gradientColors.first, gradientColors.second)
                    )
                )
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            exercise?.let { ex ->
                // Show exercise notes if available
                ex.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📝",
                                fontSize = 14.sp
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                currentPlanItem?.let { plan ->
                    WorkoutPrescriptionCard(
                        plannedSets = plan.plannedSets,
                        repMin = plan.repMin,
                        repMax = plan.repMax,
                        restSeconds = plan.restSeconds,
                        notes = plan.notes
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (exercise?.logType.supportsRepTarget && exercise?.repTarget != null) {
                    if (attemptRecommendation != null) {
                        AttemptRecommendationCard(
                            recommendation = attemptRecommendation!!,
                            repTarget = exercise!!.repTarget!!,
                            onUse = { viewModel.applyAttemptRecommendation() }
                        )
                    } else {
                        RepTargetHintCard(repTarget = exercise!!.repTarget!!)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

            // Removed dedicated Timer Row (Moved to TopAppBar)

            Spacer(modifier = Modifier.height(8.dp))

            // Volume Progress Bar - shows weekly progress (compact horizontal bar)
            VolumeProgressBar(
                state = volumeOrbState,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Superset Indicator Pills (only shown when in superset mode)
            if (isInSupersetMode && supersetExercises.isNotEmpty()) {
                SupersetIndicatorPills(
                    exercises = supersetExercises,
                    currentExerciseIndex = currentSupersetIndex,
                    currentExerciseId = exercise?.id,
                    onExerciseClick = { exerciseId ->
                        if (exerciseId != exercise?.id) {
                            navController.navigate(Screen.ExerciseLogging.createRoute(exerciseId)) {
                                popUpTo("exercise_logging/{exerciseId}") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Current Set Label
            Text(
                text = "CURRENT SET: ${loggedSets.size + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Input Fields - Dynamic based on exercise LogType
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (exercise?.logType) {
                    LogType.WEIGHT_REPS -> {
                        // Weight Input
                        InputCard(
                            label = "WEIGHT",
                            value = weight,
                            onValueChange = { viewModel.updateWeight(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestWeight?.let { "$it lbs" },
                            lastLabel = "BEST"
                        )
                        // Reps Input
                        InputCard(
                            label = "REPS",
                            value = reps,
                            onValueChange = { viewModel.updateReps(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestReps?.let { "$it reps" },
                            lastLabel = "BEST"
                        )
                    }
                    LogType.REPS_ONLY -> {
                        // Just Reps Input (centered, wider)
                        InputCard(
                            label = "REPS",
                            value = reps,
                            onValueChange = { viewModel.updateReps(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestReps?.let { "$it reps" },
                            lastLabel = "BEST"
                        )
                    }
                    LogType.DURATION -> {
                        // Duration Input (in seconds)
                        TimeInputCard(
                            label = "DURATION (HH:MM:SS)",
                            value = duration,
                            onValueChange = { viewModel.updateDuration(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                    }
                    LogType.WEIGHT_DISTANCE -> {
                        // Weight Input
                        InputCard(
                            label = "WEIGHT",
                            value = weight,
                            onValueChange = { viewModel.updateWeight(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestWeight?.let { "$it lbs" },
                            lastLabel = "BEST"
                        )
                        // Distance Input
                        InputCard(
                            label = "DISTANCE (${selectedDistanceUnit.shortLabel})",
                            value = distance,
                            onValueChange = { viewModel.updateDistance(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                    }
                    LogType.DISTANCE_TIME -> {
                        // Distance Input
                        InputCard(
                            label = "DISTANCE (${selectedDistanceUnit.shortLabel})",
                            value = distance,
                            onValueChange = { viewModel.updateDistance(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                        // Time Input
                        TimeInputCard(
                            label = "TIME (HH:MM:SS)",
                            value = duration,
                            onValueChange = { viewModel.updateDuration(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                    }
                    LogType.WEIGHT_TIME -> {
                        InputCard(
                            label = "WEIGHT",
                            value = weight,
                            onValueChange = { viewModel.updateWeight(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestWeight?.let { "$it lbs" },
                            lastLabel = "BEST"
                        )
                        TimeInputCard(
                            label = "TIME (HH:MM:SS)",
                            value = duration,
                            onValueChange = { viewModel.updateDuration(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                    }
                    LogType.CALORIES_TIME -> {
                        InputCard(
                            label = "CALORIES",
                            value = calories,
                            onValueChange = { viewModel.updateCalories(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                        TimeInputCard(
                            label = "TIME (HH:MM:SS)",
                            value = duration,
                            onValueChange = { viewModel.updateDuration(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = null,
                            lastLabel = "BEST"
                        )
                    }
                    null -> {
                        // Default fallback - Weight + Reps
                        InputCard(
                            label = "WEIGHT",
                            value = weight,
                            onValueChange = { viewModel.updateWeight(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestWeight?.let { "$it lbs" },
                            lastLabel = "BEST"
                        )
                        InputCard(
                            label = "REPS",
                            value = reps,
                            onValueChange = { viewModel.updateReps(it) },
                            modifier = Modifier.weight(1f),
                            lastValue = bestReps?.let { "$it reps" },
                            lastLabel = "BEST"
                        )
                    }
                }
            }

            if (exercise?.logType.usesDistanceUnit) {
                Spacer(modifier = Modifier.height(12.dp))

                Box {
                    OutlinedButton(
                        onClick = { showDistanceUnitMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Distance Type: ${selectedDistanceUnit.displayName}",
                            color = LocalAppColors.current.textPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showDistanceUnitMenu,
                        onDismissRequest = { showDistanceUnitMenu = false },
                        modifier = Modifier.background(LocalAppColors.current.surfaceCards)
                    ) {
                        DistanceUnit.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(unit.displayName, color = LocalAppColors.current.textPrimary)
                                        Text(
                                            text = unit.loggingDescription,
                                            color = LocalAppColors.current.textTertiary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateSelectedDistanceUnit(unit)
                                    showDistanceUnitMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Warmup Toggle and Plate Calculator Row - show based on LogType
            val showWarmupToggle = exercise?.logType in listOf(
                LogType.WEIGHT_REPS,
                LogType.REPS_ONLY,
                LogType.WEIGHT_DISTANCE,
                LogType.WEIGHT_TIME,
                null
            )
            val showPlateCalculatorButton = exercise?.logType in listOf(
                LogType.WEIGHT_REPS,
                LogType.WEIGHT_DISTANCE,
                LogType.WEIGHT_TIME,
                null
            )

            if (showWarmupToggle || showPlateCalculatorButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Warmup Toggle Pill
                    if (showWarmupToggle) {
                        Surface(
                            onClick = { viewModel.toggleWarmup() },
                            shape = RoundedCornerShape(50),
                            color = if (isWarmup) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (isWarmup) null else androidx.compose.foundation.BorderStroke(1.dp, LocalAppColors.current.textTertiary),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isWarmup) {
                                    Text(text = "🔥 ", fontSize = 14.sp)
                                }
                                Text(
                                    text = "Warmup",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWarmup) Color.Black else LocalAppColors.current.textTertiary
                                )
                            }
                        }
                    }

                    // Plate Calculator Button - only for weight-based exercises
                    if (showPlateCalculatorButton) {
                        Surface(
                            onClick = { showPlateCalculator = true },
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(text = "🏋️ ", fontSize = 14.sp)
                                Text(
                                    text = "Plates",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Superset Toggle Pill / Add Exercise to Superset
                    val isInSupersetMode by viewModel.isInSupersetMode.collectAsState()
                    if (isInSupersetMode) {
                        // Already in superset - show "Add" button to add more exercises
                        Surface(
                            onClick = {
                                navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true, addToSuperset = true))
                            },
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = {
                                exercise?.id?.let { id ->
                                    navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true, supersetMode = true, adHocParentId = id))
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_sync),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Superset",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Determine if Log Set button should be enabled based on LogType
            val isLogSetEnabled = when (exercise?.logType) {
                LogType.WEIGHT_REPS -> weight.isNotBlank() && reps.isNotBlank()
                LogType.REPS_ONLY -> reps.isNotBlank()
                LogType.DURATION -> duration.isNotBlank()
                LogType.WEIGHT_DISTANCE -> weight.isNotBlank() && distance.isNotBlank()
                LogType.DISTANCE_TIME -> distance.isNotBlank() && duration.isNotBlank()
                LogType.WEIGHT_TIME -> weight.isNotBlank() && duration.isNotBlank()
                LogType.CALORIES_TIME -> calories.isNotBlank() && duration.isNotBlank()
                null -> weight.isNotBlank() && reps.isNotBlank()
            }

            // Log Set Button (or Save Edit if editing)
            if (editingSet != null) {
                // Editing mode - show Save and Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cancelEditing() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalAppColors.current.textTertiary
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.saveEditedSet()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = isLogSetEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "SAVE EDIT ✓",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                            color = Color.Black
                        )
                    }
                }
            } else {
                // Normal mode - show LOG SET button
                LogSetButton(
                    onClick = {
                        if (isLogSetEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.logSet()
                            if (timerAutoStart) {
                                viewModel.startTimer() // Auto-start timer only if enabled
                            }
                            viewModel.resetTimerToDefault() // Reset timer to exercise's default
                        }
                    },
                    enabled = isLogSetEnabled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Session Log Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESSION LOG",
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalAppColors.current.textTertiary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${loggedSets.size} sets done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logged Sets List
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            
            // Auto-scroll to bottom when a new set is added
            LaunchedEffect(loggedSets.size) {
                if (loggedSets.isNotEmpty()) {
                    listState.animateScrollToItem(loggedSets.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1.5f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = loggedSets,
                    key = { _, item -> item.id }
                ) { index, set ->
                    // Check if this set is a PB for its rep count (must be first occurrence)
                    val isPB = !set.isWarmup &&
                        set.weight != null &&
                        set.reps != null &&
                        personalBestsByReps[set.reps]?.let { pb ->
                            pb.maxWeight == set.weight && pb.firstAchievedAt == set.timestamp.time
                        } == true

                    ExerciseSetLogCard(
                        set = set,
                        setNumber = index + 1,
                        isPersonalBest = isPB,
                        onEdit = { selectedSet ->
                            viewModel.startEditingSet(selectedSet)
                        },
                        onDelete = { selectedSet ->
                            selectedSetToDelete = selectedSet
                        },
                        onAddNote = { selectedSet ->
                            noteText = selectedSet.note ?: ""
                            setToAddNote = selectedSet
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (isInSupersetMode) {
                            // Exit superset mode before navigating to add exercise
                            viewModel.exitSupersetMode()
                            navController.popBackStack()
                        } else {
                            navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isInSupersetMode) "Exit Superset" else "Add Exercise",
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { 
                        if (nextExerciseId != null) {
                            navController.navigate(Screen.ExerciseLogging.createRoute(nextExerciseId!!)) {
                                popUpTo("exercise_logging/{exerciseId}") { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            showFinishDialog = true 
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (nextExerciseId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = if (nextExerciseId != null) Color.Black else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (nextExerciseId != null) "Next Exercise →" else "Finish Workout",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // Timer Dialog
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = {
                Text(
                    text = "Rest Timer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isTimerRunning) {
                            String.format(java.util.Locale.US, "%d:%02d", countdownTimer / 60, countdownTimer % 60)
                        } else {
                            String.format(java.util.Locale.US, "%d:%02d", restTime / 60, restTime % 60)
                        },
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.adjustRestTime(-5) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.textTertiary)
                        ) {
                            Text("-5s")
                        }
                        OutlinedButton(
                            onClick = { viewModel.adjustRestTime(5) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAppColors.current.textTertiary)
                        ) {
                            Text("+5s")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = if (timerAudioEnabled) MaterialTheme.colorScheme.primary else LocalAppColors.current.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audio", style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
                        }
                        Switch(
                            checked = timerAudioEnabled,
                            onCheckedChange = { viewModel.toggleTimerAudio(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Vibration,
                                contentDescription = null,
                                tint = if (timerVibrateEnabled) MaterialTheme.colorScheme.primary else LocalAppColors.current.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vibration", style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.textPrimary)
                        }
                        Switch(
                            checked = timerVibrateEnabled,
                            onCheckedChange = { viewModel.toggleTimerVibrate(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            },
            confirmButton = {
                if (isTimerRunning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.stopTimer() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Stop")
                        }
                        Button(
                            onClick = {
                                viewModel.stopTimer()
                                viewModel.startTimer()
                                showTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Restart", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startTimer()
                            showTimerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Start Timer", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.resetTimerToDefault() }) {
                        Text("Reset", color = LocalAppColors.current.textTertiary)
                    }
                    TextButton(onClick = { showTimerDialog = false }) {
                        Text("Close", color = LocalAppColors.current.textTertiary)
                    }
                }
            },
            containerColor = LocalAppColors.current.surfaceCards
        )
    }

    // Delete Confirmation Dialog
    selectedSetToDelete?.let { setToDelete ->
        AlertDialog(
            onDismissRequest = { selectedSetToDelete = null },
            title = {
                Text(
                    text = "Delete Set?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this set? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textTertiary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSet(setToDelete.id)
                        selectedSetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C) // Red for destructive action
                    )
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSetToDelete = null }) {
                    Text("Cancel", color = LocalAppColors.current.textTertiary)
                }
            },
            containerColor = LocalAppColors.current.surfaceCards
        )
    }

    // Add/Edit Note Dialog
    setToAddNote?.let { set ->
        AlertDialog(
            onDismissRequest = {
                setToAddNote = null
                noteText = ""
            },
            title = {
                Text(
                    text = if (set.note.isNullOrBlank()) "Add Note" else "Edit Note",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = buildSetSummary(set, selectedDistanceUnit, loggedSets.indexOf(set) + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textTertiary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Enter note...", color = LocalAppColors.current.textTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = LocalAppColors.current.textTertiary,
                            focusedTextColor = LocalAppColors.current.textPrimary,
                            unfocusedTextColor = LocalAppColors.current.textPrimary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSetNote(set, noteText)
                        setToAddNote = null
                        noteText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    setToAddNote = null
                    noteText = ""
                }) {
                    Text("Cancel", color = LocalAppColors.current.textTertiary)
                }
            },
            containerColor = LocalAppColors.current.surfaceCards
        )
    }

    // Finish Workout Dialog
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    text = "Finish Workout?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to finish this workout session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.textTertiary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishWorkout()
                        showFinishDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Finish", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Cancel", color = LocalAppColors.current.textTertiary)
                }
            },
            containerColor = LocalAppColors.current.surfaceCards
        )
    }

    // Plate Calculator Sheet
    if (showPlateCalculator) {
        PlateCalculatorSheet(
            initialWeight = weight.toFloatOrNull() ?: 0f,
            barWeight = barWeight,
            availablePlates = availablePlates,
            loadingSides = loadingSides,
            onDismiss = { showPlateCalculator = false },
            onNavigateToSettings = {
                showPlateCalculator = false
                navController.navigate(Screen.Settings.route)
            },
            onUseWeight = { newWeight ->
                viewModel.updateWeight(newWeight.toString())
                showPlateCalculator = false
            }
        )
    }

    // Workout Overview Bottom Sheet
    if (showWorkoutOverview) {
        ModalBottomSheet(
            onDismissRequest = { showWorkoutOverview = false },
            containerColor = LocalAppColors.current.surfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            WorkoutOverviewCommandPanel(
                exercises = workoutOverview,
                currentExerciseId = exercise?.id,
                workoutStats = viewModel.getWorkoutStats(),
                onExerciseClick = { exerciseId ->
                    showWorkoutOverview = false
                    if (exerciseId != exercise?.id) {
                        navController.navigate(Screen.ExerciseLogging.createRoute(exerciseId)) {
                            popUpTo(navController.currentBackStackEntry?.destination?.route ?: Screen.ExerciseLogging.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                onAddExercise = {
                    showWorkoutOverview = false
                    navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
                }
            )
        }
    }

    // Exercise History Bottom Sheet
    if (showExerciseHistory) {
        ModalBottomSheet(
            onDismissRequest = { showExerciseHistory = false },
            containerColor = LocalAppColors.current.surfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            exercise?.let { ex ->
                ExerciseHistoryContent(
                    exerciseName = ex.name,
                    personalRecords = personalRecords,
                    history = exerciseHistory,
                    onDismiss = { showExerciseHistory = false }
                )
            }
        }
    }
}

@Composable
private fun WorkoutPrescriptionCard(
    plannedSets: Int?,
    repMin: Int?,
    repMax: Int?,
    restSeconds: Int?,
    notes: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "PLANNED WORK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = buildString {
                    plannedSets?.let { append("$it sets") }
                    if (repMin != null) {
                        if (isNotEmpty()) append(" • ")
                        append(repMin)
                        if (repMax != null && repMax != repMin) append("-$repMax")
                        append(" reps")
                    }
                    restSeconds?.let {
                        if (isNotEmpty()) append(" • ")
                        append("${it}s rest")
                    }
                }.ifBlank { "Session-only exercise" },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textPrimary
            )
            notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AttemptRecommendationCard(
    recommendation: ExerciseAttemptRecommendation,
    repTarget: Int,
    onUse: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recommendation.title.uppercase(java.util.Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = recommendation.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textPrimary
                )
                Text(
                    text = "Rep target: $repTarget",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppColors.current.textTertiary
                )
            }

            if (recommendation.canApply) {
                TextButton(onClick = onUse) {
                    Text(
                        text = "Use",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RepTargetHintCard(repTarget: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "REP TARGET",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAppColors.current.textTertiary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$repTarget reps before increasing weight",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textPrimary
                )
            }
        }
    }
}





@Preview(showBackground = true)
@Composable
private fun ExerciseLoggingScreenPreview() {
    IronLogTheme {
        // Preview would require mock NavController
    }
}

private val DistanceUnit.shortLabel: String
    get() = when (this) {
        DistanceUnit.METERS -> "M"
        DistanceUnit.KILOMETERS -> "KM"
        DistanceUnit.YARDS -> "YD"
        DistanceUnit.FEET -> "FT"
        DistanceUnit.MILES -> "MI"
        DistanceUnit.STEPS -> "STEPS"
        DistanceUnit.FLOORS -> "FLOORS"
    }

private val DistanceUnit.displayName: String
    get() = when (this) {
        DistanceUnit.METERS -> "Meters"
        DistanceUnit.KILOMETERS -> "Kilometers"
        DistanceUnit.YARDS -> "Yards"
        DistanceUnit.FEET -> "Feet"
        DistanceUnit.MILES -> "Miles"
        DistanceUnit.STEPS -> "Steps"
        DistanceUnit.FLOORS -> "Floors"
    }

private val DistanceUnit.loggingDescription: String
    get() = when (this) {
        DistanceUnit.METERS -> "Track work and short intervals"
        DistanceUnit.KILOMETERS -> "Longer runs and rides"
        DistanceUnit.YARDS -> "Pools, turf, and field work"
        DistanceUnit.FEET -> "Carries and stair machines"
        DistanceUnit.MILES -> "Road runs and outdoor cardio"
        DistanceUnit.STEPS -> "Pedometer and step-based cardio"
        DistanceUnit.FLOORS -> "Stair climbers and floor goals"
    }

private val LogType?.usesDistanceUnit: Boolean
    get() = this == LogType.WEIGHT_DISTANCE || this == LogType.DISTANCE_TIME

private val LogType?.supportsRepTarget: Boolean
    get() = this == LogType.WEIGHT_REPS || this == LogType.REPS_ONLY

private fun buildSetSummary(
    set: com.example.gymtime.data.db.entity.Set,
    fallbackDistanceUnit: DistanceUnit,
    setNumber: Int
): String {
    val parts = mutableListOf<String>()

    set.weight?.let { parts += "${it.toInt()} lbs" }
    set.calories?.let { parts += "${it.toInt()} cal" }
    set.reps?.let { parts += "$it reps" }

    val unit = set.distanceUnit ?: fallbackDistanceUnit
    val distanceValue = when {
        set.distanceValue != null -> set.distanceValue
        set.distanceMeters != null && unit.isConvertibleToMeters -> {
            com.example.gymtime.util.TimeUtils.metersToDistance(set.distanceMeters, unit)
        }
        else -> null
    }
    distanceValue?.let {
        parts += "${com.example.gymtime.util.TimeUtils.formatDistance(it, unit)} ${unit.shortLabel.lowercase()}"
    }

    set.durationSeconds?.let { parts += com.example.gymtime.util.TimeUtils.formatSecondsToHMS(it) }

    return "Set $setNumber: " + if (parts.isEmpty()) "No data" else parts.joinToString(" × ")
}
