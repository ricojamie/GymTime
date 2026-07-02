package com.example.gymtime

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.util.Log
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.navigation.BottomNavigationBar
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.notifications.MonthlyReportNotifier
import com.example.gymtime.ui.history.HistoryScreen
import com.example.gymtime.ui.home.HomeScreen
import com.example.gymtime.ui.report.MonthlyReportScreen
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.ThemeColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    // Holds a deep-link destination delivered via Intent extra (e.g. from a
    // notification tap). Compose collects this to navigate after the NavHost
    // is set up. Cleared once consumed.
    private val pendingDestination = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Check for stale sessions on app launch
        mainViewModel.checkActiveSession()

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Capture any deep-link destination from the launching intent.
        consumeDestinationExtra(intent)

        enableEdgeToEdge()
        setContent {
            val themeColorName by userPreferencesRepository.themeColor.collectAsState(initial = "lime")
            val customThemeColor by userPreferencesRepository.customThemeColor.collectAsState(initial = null)
            val themeFont by userPreferencesRepository.themeFont.collectAsState(initial = "bebas_neue")
            val customFontUri by userPreferencesRepository.customFontUri.collectAsState(initial = null)
            val keepScreenOn by userPreferencesRepository.keepScreenOn.collectAsState(initial = false)
            val darkMode by userPreferencesRepository.darkMode.collectAsState(initial = true)
            val colorScheme = ThemeColors.getScheme(themeColorName, customThemeColor)

            DisposableEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            IronLogTheme(
                appColorScheme = colorScheme,
                darkMode = darkMode,
                themeFontKey = themeFont,
                customFontUri = customFontUri
            ) {
                val gradientColors = com.example.gymtime.ui.theme.LocalGradientColors.current

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Handle deep-link destination delivered via Intent extras
                    // (e.g. notification tap). The state holder is updated from
                    // onCreate / onNewIntent; we consume and clear it here.
                    val pending = pendingDestination.value
                    LaunchedEffect(pending) {
                        if (pending == MonthlyReportNotifier.DESTINATION_MONTHLY_REPORT) {
                            navController.navigate(Screen.MonthlyReport.route) {
                                launchSingleTop = true
                            }
                            pendingDestination.value = null
                        }
                    }

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
                                    .consumeWindowInsets(innerPadding)
                                    .imePadding()
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
                                        },
                                        androidx.navigation.navArgument("addToSuperset") {
                                            type = androidx.navigation.NavType.BoolType
                                            defaultValue = false
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
                                composable(Screen.MonthlyReport.route) {
                                    MonthlyReportScreen(navController = navController)
                                }
                                composable(Screen.Settings.route) {
                                    com.example.gymtime.ui.settings.SettingsScreen(navController = navController)
                                }
                                composable(Screen.ThemeSettings.route) {
                                    com.example.gymtime.ui.settings.ThemeSettingsScreen(navController = navController)
                                }
                                composable(Screen.MuscleGroupManagement.route) {
                                    com.example.gymtime.ui.settings.MuscleGroupManagementScreen(navController = navController)
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
                                    route = Screen.RoutineDetail.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("routineId") {
                                            type = androidx.navigation.NavType.LongType
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.routine.RoutineDetailScreen(navController = navController)
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

                        // Re-key on currentRoute so this callback is re-added to the
                        // dispatcher on every nav, winning LIFO over NavController's.
                        androidx.compose.runtime.key(currentRoute) {
                            BackHandler(enabled = currentRoute != null && currentRoute != Screen.Home.route) {
                                navController.navigateHomeAndClearStack()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDestinationExtra(intent)
    }

    private fun consumeDestinationExtra(intent: Intent?) {
        val destination = intent?.getStringExtra(MonthlyReportNotifier.EXTRA_DESTINATION)
            ?: return
        pendingDestination.value = destination
        // Strip the extra so back navigation / config changes don't re-fire.
        intent.removeExtra(MonthlyReportNotifier.EXTRA_DESTINATION)
    }
}
