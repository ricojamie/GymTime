package com.example.gymtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.navigation.BottomNavigationBar
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.history.HistoryScreen
import com.example.gymtime.ui.home.HomeScreen
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.ThemeColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for stale sessions on app launch
        mainViewModel.checkActiveSession()

        // Request notification permission for Android 13+
        requestNotificationPermission()

        enableEdgeToEdge()
        setContent {
            val themeColorName by userPreferencesRepository.themeColor.collectAsState(initial = "lime")
            val colorScheme = ThemeColors.getScheme(themeColorName)

            IronLogTheme(appColorScheme = colorScheme) {
                val gradientColors = com.example.gymtime.ui.theme.LocalGradientColors.current

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Define which screens should show the bottom bar
                    val bottomBarScreens = listOf(
                        Screen.Home.route,
                        Screen.History.route,
                        Screen.Library.route,
                        Screen.Analytics.route
                    )
                    val showBottomBar = currentRoute in bottomBarScreens

                    // Outer Box to allow BottomNavigationBar to overlay content
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                gradientColors.first,
                                                gradientColors.second
                                            )
                                        )
                                    )
                                    .padding(innerPadding)
                            ) {
                                composable(Screen.Home.route) { HomeScreen(navController = navController) }
                                composable(Screen.History.route) { HistoryScreen(navController = navController) }
                                composable(Screen.Library.route) {
                                    com.example.gymtime.ui.library.LibraryScreen(navController = navController)
                                }
                                composable(Screen.Analytics.route) {
                                    com.example.gymtime.ui.analytics.AnalyticsScreen()
                                }
                                composable(
                                    route = Screen.ExerciseSelection.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("workoutMode") {
                                            type = androidx.navigation.NavType.BoolType
                                            defaultValue = false
                                        },
                                        androidx.navigation.navArgument("supersetMode") {
                                            type = androidx.navigation.NavType.BoolType
                                            defaultValue = false
                                        },
                                        androidx.navigation.navArgument("adHocParentId") {
                                            type = androidx.navigation.NavType.LongType
                                            defaultValue = -1L // Use -1 to indicate null
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.exercise.ExerciseSelectionScreen(navController = navController)
                                }
                                composable(Screen.WorkoutResume.route) {
                                    com.example.gymtime.ui.workout.WorkoutResumeScreen(
                                        onExerciseClick = { exerciseId ->
                                            navController.navigate(Screen.ExerciseLogging.createRoute(exerciseId))
                                        },
                                        onAddExerciseClick = {
                                            navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true))
                                        },
                                        onFinishWorkoutClick = { workoutId ->
                                            navController.navigate(Screen.PostWorkoutSummary.createRoute(workoutId))
                                        }
                                    )
                                }
                                composable(
                                    route = Screen.ExerciseLogging.route,
                                    arguments = listOf(androidx.navigation.navArgument("exerciseId") {
                                        type = androidx.navigation.NavType.LongType
                                    }),
                                ) {
                                    com.example.gymtime.ui.exercise.ExerciseLoggingScreen(navController = navController)
                                }
                                composable(
                                    route = Screen.ExerciseForm.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("exerciseId") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        },
                                        androidx.navigation.navArgument("fromWorkout") {
                                            type = androidx.navigation.NavType.BoolType
                                            defaultValue = false
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.exercise.ExerciseFormScreen(navController = navController)
                                }
                                composable(
                                    route = Screen.PostWorkoutSummary.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("workoutId") {
                                            type = androidx.navigation.NavType.LongType
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.summary.PostWorkoutSummaryScreen(navController = navController)
                                }
                                composable(Screen.Settings.route) {
                                    com.example.gymtime.ui.settings.SettingsScreen(navController = navController)
                                }

                                // Routine Routes
                                composable(Screen.RoutineList.route) {
                                    com.example.gymtime.ui.routine.RoutineListScreen(navController = navController)
                                }
                                composable(
                                    route = Screen.RoutineForm.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("routineId") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.routine.RoutineFormScreen(navController = navController)
                                }
                                composable(
                                    route = Screen.RoutineDayList.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("routineId") {
                                            type = androidx.navigation.NavType.LongType
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.routine.RoutineDayListScreen(navController = navController)
                                }
                                composable(
                                    route = Screen.RoutineDayForm.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("routineId") {
                                            type = androidx.navigation.NavType.LongType
                                        },
                                        androidx.navigation.navArgument("dayId") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.routine.RoutineDayFormScreen(navController = navController)
                                }
                                composable(
                                    route = Screen.RoutineDayStart.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("routineId") {
                                            type = androidx.navigation.NavType.LongType
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.routine.RoutineDayStartScreen(navController = navController)
                                }
                            }
                        }

                        if (showBottomBar) {
                            Box(
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                BottomNavigationBar(navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.d("MainActivity", "Notification permission denied")
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
