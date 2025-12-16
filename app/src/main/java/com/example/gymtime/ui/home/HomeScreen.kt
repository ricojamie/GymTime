package com.example.gymtime.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.components.OrbSize
import com.example.gymtime.ui.components.RoutineCard
import com.example.gymtime.ui.components.VolumeOrb
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextSecondary
import com.example.gymtime.ui.theme.TextTertiary
import com.example.gymtime.util.StreakCalculator
import kotlinx.coroutines.delay

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
    val activeRoutineId by viewModel.activeRoutineId.collectAsState(initial = null)
    val volumeOrbState by viewModel.volumeOrbState.collectAsState()
    val streakResult by viewModel.streakResult.collectAsState()
    val bestStreak by viewModel.bestStreak.collectAsState()
    val ytdWorkouts by viewModel.ytdWorkouts.collectAsState()

    val view = LocalView.current

    // Haptic feedback on overflow
    LaunchedEffect(volumeOrbState.justOverflowed) {
        if (volumeOrbState.justOverflowed) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // Refresh data when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val availableHeight = maxHeight

        // Estimate fixed content heights
        val headerHeight = 90.dp  // Header section
        val spacingTotal = 40.dp  // Total spacing between elements
        val orbLabelHeight = 40.dp // "WEEKLY VOLUME" + percentage text

        // Calculate remaining height for flexible components
        val fixedHeight = headerHeight + spacingTotal + orbLabelHeight
        val flexibleHeight = availableHeight - fixedHeight

        // Distribute flexible space: QuickStart (30%), MiddleRow (30%), Orb (40%)
        val quickStartHeight = (flexibleHeight * 0.28f).coerceIn(100.dp, 180.dp)
        val middleRowHeight = (flexibleHeight * 0.28f).coerceIn(100.dp, 160.dp)
        val orbAreaHeight = (flexibleHeight * 0.44f).coerceIn(120.dp, 220.dp)

        // Calculate orb size from its area (leave room for label)
        val orbSize = (orbAreaHeight - 30.dp).coerceIn(90.dp, 180.dp)

        // Responsive spacers based on available height
        val scale = (availableHeight / 700.dp).coerceIn(0.85f, 1.2f)
        val spacerS = (8.dp * scale).coerceIn(6.dp, 14.dp)
        val spacerM = (12.dp * scale).coerceIn(8.dp, 18.dp)
        val spacerL = (16.dp * scale).coerceIn(10.dp, 24.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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

            Spacer(modifier = Modifier.height(spacerL))

            // Quick Start Card
            QuickStartCard(
                isOngoing = ongoingWorkout != null,
                height = quickStartHeight,
                onClick = {
                    if (ongoingWorkout != null) {
                        navController.navigate(Screen.WorkoutResume.route)
                    } else {
                        navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
                    }
                }
            )

            Spacer(modifier = Modifier.height(spacerM))

            // Middle row: Routines + Streak
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(middleRowHeight),
                horizontalArrangement = Arrangement.spacedBy(spacerM)
            ) {
                // Routines card
                RoutineCard(
                    modifier = Modifier.weight(1f),
                    hasActiveRoutine = hasActiveRoutine,
                    routineName = activeRoutineName,
                    onClick = {
                        if (hasActiveRoutine && activeRoutineId != null) {
                            navController.navigate(Screen.RoutineDayStart.createRoute(activeRoutineId!!))
                        } else {
                            navController.navigate(Screen.RoutineList.route)
                        }
                    }
                )

                // Streak card
                StreakCardCompact(
                    streakResult = streakResult,
                    bestStreak = bestStreak,
                    ytdWorkouts = ytdWorkouts,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Volume Orb at bottom
            VolumeOrbSection(
                volumeOrbState = volumeOrbState,
                orbSize = orbSize,
                onOverflowComplete = { viewModel.clearOrbOverflowAnimation() }
            )

            Spacer(modifier = Modifier.height(spacerS))
        }
    }
}

/**
 * Quick Start Card
 */
@Composable
private fun QuickStartCard(
    isOngoing: Boolean,
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
                text = if (isOngoing) "IN PROGRESS" else "EMPTY SESSION",
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
                    text = if (isOngoing) "Resume Workout" else "Start Workout",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                Text(
                    text = if (isOngoing) "Continue your session" else "Build as you go",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * Compact Streak Card for side-by-side layout
 */
@Composable
private fun StreakCardCompact(
    streakResult: StreakCalculator.StreakResult,
    bestStreak: Int,
    ytdWorkouts: Int,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    // State-based styling
    val (stateIcon, stateColor) = when (streakResult.state) {
        StreakCalculator.StreakState.ACTIVE -> Pair("\uD83D\uDD25", accentColor)
        StreakCalculator.StreakState.RESTING -> Pair("\u2744\uFE0F", Color(0xFF64B5F6))
        StreakCalculator.StreakState.BROKEN -> Pair("\uD83D\uDC80", Color(0xFFEF5350))
    }

    GlowCard(
        modifier = modifier.fillMaxSize(),
        onClick = { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon and streak count
            Text(text = stateIcon, fontSize = 24.sp)

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${streakResult.streakDays}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = stateColor
                )
                Text(
                    text = "day streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Best + YTD in a row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Best: $bestStreak",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Text(
                    text = "YTD: $ytdWorkouts",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * Volume Orb Section at bottom - dynamic orb size with tap-to-show details
 */
@Composable
private fun VolumeOrbSection(
    volumeOrbState: com.example.gymtime.data.VolumeOrbState,
    orbSize: Dp,
    onOverflowComplete: () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    // Auto-dismiss tooltip
    LaunchedEffect(showTooltip) {
        if (showTooltip) {
            delay(2500)
            showTooltip = false
        }
    }

    // Pick the appropriate OrbSize enum based on calculated size
    val orbSizeEnum = when {
        orbSize < 100.dp -> OrbSize.SMALL
        orbSize < 150.dp -> OrbSize.MEDIUM
        else -> OrbSize.LARGE
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WEEKLY VOLUME",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.size(orbSize),
            contentAlignment = Alignment.Center
        ) {
            VolumeOrb(
                state = volumeOrbState,
                size = orbSizeEnum,
                onClick = { showTooltip = !showTooltip },
                onOverflowAnimationComplete = onOverflowComplete
            )

            // Tooltip overlay when tapped
            if (showTooltip && !volumeOrbState.isFirstWeek) {
                Box(
                    modifier = Modifier
                        .size(orbSize)
                        .background(Color.Black.copy(alpha = 0.85f), shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${java.text.NumberFormat.getNumberInstance().format(volumeOrbState.currentWeekVolume.toLong())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "of ${java.text.NumberFormat.getNumberInstance().format(volumeOrbState.lastWeekVolume.toLong())} lbs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Percentage below orb
        val percentText = if (volumeOrbState.isFirstWeek) {
            "Building baseline..."
        } else {
            "${(volumeOrbState.progressPercent * 100).toInt()}% of last week"
        }
        Text(
            text = percentText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (!volumeOrbState.isFirstWeek) FontWeight.Bold else FontWeight.Normal,
            color = if (volumeOrbState.hasOverflowed) MaterialTheme.colorScheme.primary else TextSecondary
        )
    }
}
