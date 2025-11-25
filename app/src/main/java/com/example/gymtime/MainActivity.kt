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
                    Column(
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
                            modifier = Modifier.weight(1f),
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Home.route) { HomeScreen(navController = navController) }
                                composable(Screen.History.route) { HistoryScreen(navController = navController) }
                                composable(Screen.Library.route) {
                                    com.example.gymtime.ui.exercise.ExerciseSelectionScreen(navController = navController)
                                }
                                composable(Screen.Workout.route) { com.example.gymtime.ui.workout.WorkoutScreen() }
                                composable(Screen.ExerciseSelection.route) {
                                    com.example.gymtime.ui.exercise.ExerciseSelectionScreen(navController = navController)
                                }
                                composable(Screen.WorkoutResume.route) {
                                    com.example.gymtime.ui.workout.WorkoutResumeScreen(
                                        onExerciseClick = { exerciseId ->
                                            navController.navigate(Screen.ExerciseLogging.createRoute(exerciseId))
                                        },
                                        onAddExerciseClick = {
                                            navController.navigate(Screen.ExerciseSelection.route)
                                        }
                                    )
                                }
                                composable(
                                    route = Screen.ExerciseLogging.route,
                                    arguments = listOf(androidx.navigation.navArgument("exerciseId") {
                                        type = androidx.navigation.NavType.LongType
                                    })
                                ) {
                                    com.example.gymtime.ui.exercise.ExerciseLoggingScreen(navController = navController)
                                }
                            }
                        }
                        
                        BottomNavigationBar(navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    navController: androidx.navigation.NavController
) {
    val userName by viewModel.userName.collectAsState(initial = "Athlete")
    val workouts by viewModel.workouts.collectAsState(initial = emptyList())
    val ongoingWorkout by viewModel.ongoingWorkout.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        com.example.gymtime.ui.home.HomeHeader(userName = userName)

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Start button - full width
        com.example.gymtime.ui.QuickStartCard(
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
            com.example.gymtime.ui.components.RoutineCard(
                modifier = Modifier.weight(1f),
                hasActiveRoutine = viewModel.hasActiveRoutine,
                routineName = viewModel.nextWorkoutName,
                onClick = { /* TODO: Handle Routine */ }
            )

            com.example.gymtime.ui.components.WeeklyVolumeCard(
                modifier = Modifier.weight(1f),
                weeklyVolume = viewModel.poundsLifted,
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(24.dp))



        Spacer(modifier = Modifier.height(24.dp))

        // Personal Best Card
        com.example.gymtime.ui.components.PersonalBestCard()
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GymTimeTheme {
        HomeScreen(navController = rememberNavController())
    }
}