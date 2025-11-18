package com.example.gymtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gymtime.navigation.BottomNavigationBar
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.RecentHistory
import com.example.gymtime.ui.StartWorkout
import com.example.gymtime.ui.Workout
import com.example.gymtime.ui.history.HistoryScreen
import com.example.gymtime.ui.home.Greeting
import com.example.gymtime.ui.home.HomeViewModel
import com.example.gymtime.ui.home.StatsRow
import com.example.gymtime.ui.home.StatsRow
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
                                        com.example.gymtime.ui.theme.BackgroundCanvas
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Greeting()

        Spacer(modifier = Modifier.height(16.dp))

        StatsRow(
            streak = viewModel.streak,
            poundsLifted = viewModel.poundsLifted,
            pbs = viewModel.pbs
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Start Workout
        StartWorkout(
            hasActiveRoutine = viewModel.hasActiveRoutine,
            nextWorkoutName = viewModel.nextWorkoutName
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Recent Workouts", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        // Recent History
        RecentHistory(workouts = viewModel.workouts)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GymTimeTheme {
        HomeScreen()
    }
}