package com.example.gymtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gymtime.navigation.BottomNavigationBar
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.history.HistoryScreen
import com.example.gymtime.ui.home.HomeViewModel
import com.example.gymtime.ui.library.LibraryScreen
import com.example.gymtime.ui.theme.GymTimeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        com.example.gymtime.ui.theme.GradientStart,
                                        com.example.gymtime.ui.theme.GradientEnd
                                    )
                                )
                            )
                    ) {
                        val navController = rememberNavController()
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = { BottomNavigationBar(navController = navController) },
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Home.route) { HomeScreen() }
                                composable(Screen.History.route) { HistoryScreen() }
                                composable(Screen.Library.route) { LibraryScreen() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val userName by viewModel.userName.collectAsState(initial = "Athlete")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        com.example.gymtime.ui.home.HomeHeader(userName = userName)

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Start button - full width
        com.example.gymtime.ui.QuickStartCard(
            onClick = { /* TODO: Handle Quick Start */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Routine and Volume Graph Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            com.example.gymtime.ui.components.RoutineCard(
                modifier = Modifier.weight(1f),
                hasActiveRoutine = viewModel.hasActiveRoutine,
                routineName = viewModel.nextWorkoutName,
                onClick = { /* TODO: Handle Routine */ }
            )

            com.example.gymtime.ui.components.WeeklyVolumeCard(
                modifier = Modifier.weight(1f),
                weeklyVolume = viewModel.poundsLifted
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Workout section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${viewModel.workouts.firstOrNull()?.name ?: "No workouts"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Single recent workout card
        com.example.gymtime.ui.RecentWorkoutCard(
            workout = viewModel.workouts.firstOrNull()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GymTimeTheme {
        HomeScreen()
    }
}