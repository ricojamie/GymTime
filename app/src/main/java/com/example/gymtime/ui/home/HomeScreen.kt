package com.example.gymtime.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.navigation.navigateToWorkoutOverview
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.components.RoutineCard
import com.example.gymtime.ui.smartlog.SmartLogBottomSheet
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.theme.LoggerPreviewTheme
import com.example.gymtime.ui.home.preview.PreviewHomeScreen
import com.example.gymtime.util.StreakCalculator
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController
) {
    val newUiEnabled by viewModel.newUiEnabled.collectAsStateWithLifecycle(initialValue = false)
    if (newUiEnabled) {
        LoggerPreviewTheme {
            PreviewHomeScreen(modifier = modifier, viewModel = viewModel, navController = navController)
        }
    } else {
        LegacyHomeScreen(modifier = modifier, viewModel = viewModel, navController = navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyHomeScreen(
    modifier: Modifier,
    viewModel: HomeViewModel,
    navController: NavController
) {
    val userName by viewModel.userName.collectAsStateWithLifecycle(initialValue = "Athlete")
    val newUiEnabled by viewModel.newUiEnabled.collectAsStateWithLifecycle(initialValue = false)
    val ongoingWorkout by viewModel.ongoingWorkout.collectAsStateWithLifecycle()
    val hasActiveRoutine by viewModel.hasActiveRoutine.collectAsStateWithLifecycle()
    val activeRoutineName by viewModel.activeRoutineName.collectAsStateWithLifecycle()
    val nextRoutineDayName by viewModel.nextRoutineDayName.collectAsStateWithLifecycle()
    val routineCardState by viewModel.routineCardState.collectAsStateWithLifecycle()
    val volumeOrbState by viewModel.volumeOrbState.collectAsStateWithLifecycle()
    val streakResult by viewModel.streakResult.collectAsStateWithLifecycle()
    val bestStreak by viewModel.bestStreak.collectAsStateWithLifecycle()
    val ytdWorkouts by viewModel.ytdWorkouts.collectAsStateWithLifecycle()
    val ytdVolume by viewModel.ytdVolume.collectAsStateWithLifecycle()
    val lastYearVolume by viewModel.lastYearVolume.collectAsStateWithLifecycle()
    val strengthMomentum by viewModel.strengthMomentum.collectAsStateWithLifecycle()

    var showStreakDetail by remember { mutableStateOf(false) }
    var showMomentumDetail by remember { mutableStateOf(false) }
    var showMomentumInfo by remember { mutableStateOf(false) }
    var showWorkoutStartPicker by remember { mutableStateOf(false) }
    var showSmartLog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDataIfDateChanged()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.startRoutineWorkoutEvent.collect { start ->
            navController.navigateToWorkoutExercise(start.firstExerciseId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeHeader(userName = userName, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    IconButton(onClick = { navController.navigate(Screen.ThemeSettings.route) }) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Choose your colors",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                NewUiPreviewToggle(
                    enabled = newUiEnabled,
                    onEnabledChange = viewModel::setNewUiEnabled
                )
            }
        }

        QuickStartCard(
            isOngoing = ongoingWorkout != null,
            hasActiveRoutine = hasActiveRoutine,
            nextRoutineDayName = nextRoutineDayName,
            height = 140.dp,
            onSmartLogClick = { showSmartLog = true },
            onClick = {
                if (ongoingWorkout != null) {
                    navController.navigateToWorkoutOverview()
                } else {
                    showWorkoutStartPicker = true
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoutineCard(
                modifier = Modifier.weight(1f),
                hasActiveRoutine = hasActiveRoutine,
                routineName = activeRoutineName,
                nextDayName = routineCardState?.nextDayName,
                dayPosition = routineCardState?.dayPosition,
                exercisePreview = routineCardState?.exercisePreview ?: emptyList(),
                lastPerformedLabel = routineCardState?.lastPerformedLabel,
                onClick = {
                    val activeId = routineCardState?.routineId
                    if (activeId != null) {
                        navController.navigate(Screen.RoutineDetail.createRoute(activeId))
                    } else {
                        navController.navigate(Screen.RoutineList.route)
                    }
                }
            )

            StreakCardCompact(
                streakResult = streakResult,
                bestStreak = bestStreak,
                modifier = Modifier.weight(1f),
                onClick = { showStreakDetail = true }
            )
        }

        StrengthMomentumMapCard(
            state = strengthMomentum,
            modifier = Modifier.fillMaxWidth(),
            onClick = { showMomentumDetail = true },
            onInfoClick = { showMomentumInfo = true }
        )

        WeeklyLoadBar(
            state = volumeOrbState,
            modifier = Modifier.height(54.dp)
        )
    }

    if (showStreakDetail) {
        ModalBottomSheet(
            onDismissRequest = { showStreakDetail = false },
            sheetState = sheetState,
            containerColor = LocalAppColors.current.surfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            StreakDetailContent(
                streakResult = streakResult,
                bestStreak = bestStreak,
                ytdWorkouts = ytdWorkouts,
                ytdVolume = ytdVolume,
                lastYearVolume = lastYearVolume,
                onClose = { showStreakDetail = false }
            )
        }
    }

    if (showMomentumDetail) {
        ModalBottomSheet(
            onDismissRequest = { showMomentumDetail = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = LocalAppColors.current.surfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            StrengthMomentumDetailSheet(
                state = strengthMomentum,
                onClose = { showMomentumDetail = false }
            )
        }
    }

    if (showMomentumInfo) {
        AlertDialog(
            onDismissRequest = { showMomentumInfo = false },
            title = {
                Text(
                    text = "Strength Momentum",
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                Text(
                    text = "Compares up to your 3 most recent completed strength workouts with the same number of preceding workouts for each exercise. At least 2 workouts per side are required. Each workout uses its best valid working set: estimated one-rep max for positive-weight sets of 1–15 reps, or most repetitions for reps-only exercises. The recent and previous scores are medians of those workout scores. Higher-rep weighted sets stay in Records and History but are excluded from estimated strength. Warmups, unfinished sets and workouts, cardio, timed, distance, and calorie exercises are excluded. Exercise changes are combined equally within each muscle, and Legs remains one combined region. Weekly volume is shown only as context.",
                    color = LocalAppColors.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showMomentumInfo = false }) {
                    Text("Got it", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = LocalAppColors.current.surfaceCards
        )
    }

    if (showWorkoutStartPicker) {
        ModalBottomSheet(
            onDismissRequest = { showWorkoutStartPicker = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = LocalAppColors.current.surfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            WorkoutStartPickerSheet(
                hasActiveRoutine = hasActiveRoutine,
                nextRoutineDayName = nextRoutineDayName,
                exercisePreview = routineCardState?.exercisePreview ?: emptyList(),
                onStartRoutine = {
                    showWorkoutStartPicker = false
                    viewModel.startNextRoutineWorkout()
                },
                onChooseDay = routineCardState?.routineId?.let { activeId ->
                    {
                        showWorkoutStartPicker = false
                        navController.navigate(Screen.RoutineDayStart.createRoute(activeId))
                    }
                },
                onStartBlank = {
                    showWorkoutStartPicker = false
                    navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
                },
                onPlanWorkout = {
                    showWorkoutStartPicker = false
                    navController.navigate(Screen.WorkoutBuilder.route)
                }
            )
        }
    }

    if (showSmartLog) {
        SmartLogBottomSheet(
            onDismiss = { showSmartLog = false },
            onNavigateToLogger = { exerciseId, token ->
                showSmartLog = false
                navController.navigateToWorkoutExercise(exerciseId, token)
            },
            onCreateExercise = { name ->
                showSmartLog = false
                navController.navigate(Screen.ExerciseForm.createRoute(fromWorkout = true, initialName = name))
            }
        )
    }
}

@Composable
internal fun NewUiPreviewToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange
            )
            .semantics { contentDescription = "Try our new UI!" }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "New UI",
            style = MaterialTheme.typography.labelSmall,
            color = appColors.textSecondary,
            maxLines = 1
        )
        Switch(
            checked = enabled,
            onCheckedChange = null,
            modifier = Modifier.scale(0.7f),
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = onAccent
            )
        )
    }
}

/**
 * Quick Start Card
 */
@Composable
private fun QuickStartCard(
    isOngoing: Boolean,
    hasActiveRoutine: Boolean,
    nextRoutineDayName: String?,
    height: Dp,
    onSmartLogClick: () -> Unit,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    GlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = when {
                    isOngoing -> "IN PROGRESS"
                    hasActiveRoutine -> "NEXT UP"
                    else -> "EMPTY SESSION"
                },
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onSmartLogClick,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Smart Log",
                    tint = accentColor
                )
            }

            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_play_arrow),
                    contentDescription = "Quick Start",
                    modifier = Modifier.size(32.dp),
                    tint = accentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when {
                        isOngoing -> "Resume Workout"
                        hasActiveRoutine -> (nextRoutineDayName ?: "Start Next Workout")
                        else -> "Start Workout"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                Text(
                    text = when {
                        isOngoing -> "Continue your session"
                        hasActiveRoutine -> "Routine knows what is next"
                        else -> "Quick start or plan ahead"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textTertiary
                )
            }
        }
    }
}

@Composable
private fun WorkoutStartPickerSheet(
    hasActiveRoutine: Boolean,
    nextRoutineDayName: String?,
    exercisePreview: List<String>,
    onStartRoutine: () -> Unit,
    onChooseDay: (() -> Unit)?,
    onStartBlank: () -> Unit,
    onPlanWorkout: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Start Workout",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )

        Text(
            text = if (hasActiveRoutine) {
                "Continue your routine or start a separate one-off workout."
            } else {
                "Start immediately or choose every exercise before you begin."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textSecondary
        )

        if (hasActiveRoutine) {
        GlowCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartRoutine
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = nextRoutineDayName ?: "Next Routine Workout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (exercisePreview.isNotEmpty()) {
                        exercisePreview.joinToString(" · ")
                    } else {
                        "Continue your active routine in order."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        }

        if (onChooseDay != null) {
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseDay
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Choose a Different Day",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalAppColors.current.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pick any day from your routine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.textSecondary
                    )
                }
            }
        }

        GlowCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onPlanWorkout
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Plan a One-Off Workout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose and order every exercise, then move through them in the logger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary
                )
            }
        }

        GlowCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartBlank
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Quick Start",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pick the first exercise and build as you go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary
                )
            }
        }
    }
}

/**
 * Compact Streak Card with larger icons and skip indicators
 */
@Composable
private fun StreakCardCompact(
    streakResult: StreakCalculator.StreakResult,
    bestStreak: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    // Determine if this is a fresh start (0 days and not broken today)
    val isFreshStart = streakResult.streakDays == 0 &&
        (streakResult.state != StreakCalculator.StreakState.BROKEN || !streakResult.brokeToday)

    // State-based styling
    val (stateIcon, stateColor) = when {
        isFreshStart -> Pair("⭐", accentColor)  // Fresh start
        streakResult.state == StreakCalculator.StreakState.BROKEN -> Pair("\uD83D\uDC80", Color(0xFFEF5350))
        streakResult.state == StreakCalculator.StreakState.ACTIVE -> Pair("\uD83D\uDD25", accentColor)
        else -> Pair("\u2744\uFE0F", Color(0xFF64B5F6))  // Resting
    }

    GlowCard(
        modifier = modifier.fillMaxSize(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Label
            Text(
                text = "IRON STREAK",
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textTertiary,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold
            )

            // Icon
            Text(
                text = stateIcon,
                fontSize = 26.sp
            )

            // Streak count
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isFreshStart) "NEW" else "${streakResult.streakDays}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = stateColor,
                    lineHeight = 22.sp
                )
                if (!isFreshStart) {
                    Text(
                        text = "DAYS",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAppColors.current.textTertiary,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Skip Indicators (Blue circles)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    val isLit = index < streakResult.skipsRemaining
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (isLit) Color(0xFF64B5F6) else Color.DarkGray,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .alpha(if (isLit) 1f else 0.3f)
                            .then(
                                if (isLit) Modifier.background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF64B5F6), Color.Transparent)
                                    ),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                ) else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakDetailContent(
    streakResult: StreakCalculator.StreakResult,
    bestStreak: Int,
    ytdWorkouts: Int,
    ytdVolume: Float,
    lastYearVolume: Float,
    onClose: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val lastYear = currentYear - 1

    // Determine if this is a fresh start (0 days and not broken today)
    val isFreshStart = streakResult.streakDays == 0 &&
        (streakResult.state != StreakCalculator.StreakState.BROKEN || !streakResult.brokeToday)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Iron Streak Overview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large Streak Circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .padding(4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        isFreshStart -> "⭐"
                        streakResult.state == StreakCalculator.StreakState.BROKEN -> "\uD83D\uDC80"
                        streakResult.state == StreakCalculator.StreakState.ACTIVE -> "\uD83D\uDD25"
                        else -> "\u2744\uFE0F"
                    },
                    fontSize = 32.sp
                )
                Text(
                    text = if (isFreshStart) "NEW" else "${streakResult.streakDays}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fresh start motivational message
        if (isFreshStart) {
            Text(
                text = "Your next workout starts a new streak!",
                style = MaterialTheme.typography.bodyMedium,
                color = accentColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Skips remaining section
        Text(
            text = "SKIPS REMAINING THIS WEEK",
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.textTertiary,
            letterSpacing = 1.5.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) { index ->
                val isLit = index < streakResult.skipsRemaining
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = if (isLit) Color(0xFF64B5F6) else LocalAppColors.current.inputBackground,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .then(
                                if (isLit) Modifier.padding(4.dp).background(Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape) else Modifier
                            )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (streakResult.skipsRemaining > 0) 
                "You have ${streakResult.skipsRemaining} free skips left until Sunday." 
                else "No skips left! Workout next to keep the streak alive.",
            style = MaterialTheme.typography.bodySmall,
            color = if (streakResult.skipsRemaining > 0) LocalAppColors.current.textSecondary else Color(0xFFEF5350),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                label = "ALL-TIME BEST",
                value = "$bestStreak Days",
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "$currentYear WORKOUTS",
                value = "$ytdWorkouts",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Year-over-year volume comparison
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                label = "$currentYear VOLUME",
                value = "${numberFormat.format(ytdVolume.toLong())} lbs",
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "$lastYear TOTAL",
                value = "${numberFormat.format(lastYearVolume.toLong())} lbs",
                modifier = Modifier.weight(1f)
            )
        }

        // Progress percentage
        if (lastYearVolume > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val progressPercent = (ytdVolume / lastYearVolume * 100)
            Text(
                text = "${String.format("%.1f", progressPercent)}% of last year's total",
                style = MaterialTheme.typography.bodySmall,
                color = if (progressPercent >= 100) accentColor else LocalAppColors.current.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0D0D0D), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalAppColors.current.textTertiary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )
    }
}
