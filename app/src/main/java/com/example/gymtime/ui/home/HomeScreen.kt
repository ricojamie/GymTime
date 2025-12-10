package com.example.gymtime.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.QuickStartCard
import com.example.gymtime.ui.components.OrbSize
import com.example.gymtime.ui.components.RoutineCard
import com.example.gymtime.ui.components.VolumeOrb
import com.example.gymtime.ui.components.WeeklyVolumeCard
import com.example.gymtime.ui.home.HomeViewModel
import com.example.gymtime.ui.home.HomeHeader
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.SurfaceCards
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

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
    val weeklyVolume by viewModel.weeklyVolume.collectAsState()
    val weeklyVolumeTrend by viewModel.weeklyVolumeTrend.collectAsState()
    val volumeOrbState by viewModel.volumeOrbState.collectAsState()

    // Tooltip state
    var showTooltip by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Auto-dismiss tooltip after 2 seconds
    LaunchedEffect(showTooltip) {
        if (showTooltip) {
            delay(2000)
            showTooltip = false
        }
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with Settings icon
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

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Start button - full width
        QuickStartCard(
            isOngoing = ongoingWorkout != null,
            onClick = {
                if (ongoingWorkout != null) {
                    navController.navigate(Screen.WorkoutResume.route)
                } else {
                    navController.navigate(Screen.ExerciseSelection.route)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Routine and Volume Graph Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            WeeklyVolumeCard(
                modifier = Modifier.weight(1f),
                weeklyVolume = weeklyVolume,
                volumeTrend = weeklyVolumeTrend,
                onClick = { navController.navigate(Screen.Analytics.route) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Volume Orb Section
        VolumeOrbSection(
            volumeOrbState = volumeOrbState,
            showTooltip = showTooltip,
            onOrbClick = { showTooltip = !showTooltip },
            onOverflowComplete = { viewModel.clearOrbOverflowAnimation() }
        )
    }
}

/**
 * Volume Orb section with title, orb, and tooltip.
 */
@Composable
private fun VolumeOrbSection(
    volumeOrbState: com.example.gymtime.data.VolumeOrbState,
    showTooltip: Boolean,
    onOrbClick: () -> Unit,
    onOverflowComplete: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Section title
        Text(
            text = "WEEKLY VOLUME",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Orb with tooltip
        Box(
            contentAlignment = Alignment.Center
        ) {
            VolumeOrb(
                state = volumeOrbState,
                size = OrbSize.LARGE,
                onClick = onOrbClick,
                onOverflowAnimationComplete = onOverflowComplete
            )

            // Tooltip overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showTooltip,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = SurfaceCards.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (volumeOrbState.isFirstWeek) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Getting baseline",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                            Text(
                                text = "${numberFormat.format(volumeOrbState.currentWeekVolume.toLong())} lbs",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${numberFormat.format(volumeOrbState.currentWeekVolume.toLong())} / ${numberFormat.format(volumeOrbState.lastWeekVolume.toLong())} lbs",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (volumeOrbState.hasOverflowed) {
                                    "Goal crushed! ${(volumeOrbState.progressPercent * 100).toInt()}%"
                                } else {
                                    "${(volumeOrbState.progressPercent * 100).toInt()}% of last week"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (volumeOrbState.hasOverflowed) PrimaryAccent else TextTertiary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress text below orb
        if (!volumeOrbState.isFirstWeek) {
            Text(
                text = "${(volumeOrbState.progressPercent * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = if (volumeOrbState.hasOverflowed) PrimaryAccent else TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "of last week's volume",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        } else {
            Text(
                text = "Building baseline...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${numberFormat.format(volumeOrbState.currentWeekVolume.toLong())} lbs this week",
                style = MaterialTheme.typography.titleSmall,
                color = PrimaryAccent,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
