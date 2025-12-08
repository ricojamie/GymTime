package com.example.gymtime.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.QuickStartCard
import com.example.gymtime.ui.components.PersonalBestCard
import com.example.gymtime.ui.components.RoutineCard
import com.example.gymtime.ui.components.WeeklyVolumeCard
import com.example.gymtime.ui.home.HomeViewModel
import com.example.gymtime.ui.home.HomeHeader

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
                weeklyVolume = viewModel.poundsLifted,
                onClick = { navController.navigate(Screen.Analytics.route) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Personal Best Card
        PersonalBestCard()
    }
}
