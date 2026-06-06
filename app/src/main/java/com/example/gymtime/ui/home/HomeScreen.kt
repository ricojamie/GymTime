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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.components.RoutineCard
import com.example.gymtime.ui.theme.LocalAppColors
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
    val userName by viewModel.userName.collectAsState(initial = "Athlete")
    val ongoingWorkout by viewModel.ongoingWorkout.collectAsState()
    val hasActiveRoutine by viewModel.hasActiveRoutine.collectAsState()
    val activeRoutineName by viewModel.activeRoutineName.collectAsState()
    val nextRoutineDayName by viewModel.nextRoutineDayName.collectAsState()
    val volumeOrbState by viewModel.volumeOrbState.collectAsState()
    val streakResult by viewModel.streakResult.collectAsState()
    val bestStreak by viewModel.bestStreak.collectAsState()
    val ytdWorkouts by viewModel.ytdWorkouts.collectAsState()
    val ytdVolume by viewModel.ytdVolume.collectAsState()
    val lastYearVolume by viewModel.lastYearVolume.collectAsState()
    val strengthMomentum by viewModel.strengthMomentum.collectAsState()

    var showStreakDetail by remember { mutableStateOf(false) }
    var showMomentumDetail by remember { mutableStateOf(false) }
    var showMomentumInfo by remember { mutableStateOf(false) }
    var showWorkoutStartPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Refresh data when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    LaunchedEffect(Unit) {
        viewModel.startRoutineWorkoutEvent.collect { start ->
            navController.navigate(Screen.ExerciseLogging.createRoute(start.firstExerciseId))
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
            IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        QuickStartCard(
            isOngoing = ongoingWorkout != null,
            hasActiveRoutine = hasActiveRoutine,
            nextRoutineDayName = nextRoutineDayName,
            height = 140.dp,
            onClick = {
                if (ongoingWorkout != null) {
                    navController.navigate(Screen.WorkoutResume.route)
                } else if (hasActiveRoutine) {
                    showWorkoutStartPicker = true
                } else {
                    navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
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
                onClick = {
                    navController.navigate(Screen.RoutineList.route)
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
                    text = "Compares your best recent performances from the last 28 days against the previous 28 days for exercises with data in both windows. Warmups and cardio-style exercises are excluded. Weighted lifts use estimated strength and reps-only uses reps. Muscle scores are weighted by exercise workout count, and mixed means at least one exercise improved while another declined. Weekly muscle volume is shown as context in the detail sheet, but it does not drive the body chart color by itself.",
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
                nextRoutineDayName = nextRoutineDayName,
                onStartRoutine = {
                    showWorkoutStartPicker = false
                    viewModel.startNextRoutineWorkout()
                },
                onStartBlank = {
                    showWorkoutStartPicker = false
                    navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
                }
            )
        }
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
                        else -> "Build as you go"
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
    nextRoutineDayName: String?,
    onStartRoutine: () -> Unit,
    onStartBlank: () -> Unit
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
            text = "Choose the next routine day or start a blank session.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textSecondary
        )

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
                    text = "Continue your active routine in order.",
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
                    text = "Blank Workout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Log freely without advancing the routine.",
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
