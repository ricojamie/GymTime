package com.example.gymtime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gymtime.data.UserPreferencesRepository
import com.example.gymtime.data.db.entity.Workout
import com.example.gymtime.data.repository.WorkoutRepository
import com.example.gymtime.navigation.BottomNavigationBar
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.navigation.navigateToWorkoutOverview
import com.example.gymtime.navigation.navigateToWorkoutSummary
import com.example.gymtime.notifications.MonthlyReportNotifier
import com.example.gymtime.ui.components.IronLogLedPip
import com.example.gymtime.ui.components.clearFocusOnTapOutside
import com.example.gymtime.ui.history.HistoryScreen
import com.example.gymtime.ui.home.HomeScreen
import com.example.gymtime.ui.report.MonthlyReportScreen
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.IronMotion
import com.example.gymtime.ui.theme.IronNavTransitions
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.LocalReducedMotion
import com.example.gymtime.ui.theme.MachinedPressIndication
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.ui.theme.ThemeColors
import com.example.gymtime.ui.theme.anodizedMicroGrain
import com.example.gymtime.ui.theme.obsidianAmbientBackdrop
import com.example.gymtime.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Date
import javax.inject.Inject

private val BottomBarRoutes = setOf(
    Screen.Home.route,
    Screen.History.route,
    Screen.Library.route,
    Screen.Analytics.route
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var workoutRepository: WorkoutRepository

    // Holds a deep-link destination delivered via Intent extra (e.g. from a
    // notification tap). Compose collects this to navigate after the NavHost
    // is set up. Cleared once consumed.
    private val pendingDestination = mutableStateOf<String?>(null)

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
            val themeColorName by userPreferencesRepository.themeColor.collectAsStateWithLifecycle(initialValue = "lime")
            val customThemeColor by userPreferencesRepository.customThemeColor.collectAsStateWithLifecycle(initialValue = null)
            val themeFont by userPreferencesRepository.themeFont.collectAsStateWithLifecycle(initialValue = "bebas_neue")
            val customFontUri by userPreferencesRepository.customFontUri.collectAsStateWithLifecycle(initialValue = null)
            val keepScreenOn by userPreferencesRepository.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)
            val darkMode by userPreferencesRepository.darkMode.collectAsStateWithLifecycle(initialValue = true)
            val ongoingWorkout by workoutRepository.getOngoingWorkoutFlow().collectAsStateWithLifecycle(initialValue = null)
            val colorScheme = remember(themeColorName, customThemeColor) {
                ThemeColors.getScheme(themeColorName, customThemeColor)
            }

            // Follow the app's saved appearance instead of the device's appearance.
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { darkMode }
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

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
                val surfaces = LocalIronLogSurfaces.current
                val reducedMotion = LocalReducedMotion.current
                val pushEnter = remember(reducedMotion) { IronNavTransitions.pushEnter(reducedMotion) }
                val pushExit = remember(reducedMotion) { IronNavTransitions.pushExit(reducedMotion) }
                val popEnter = remember(reducedMotion) { IronNavTransitions.popEnter(reducedMotion) }
                val popExit = remember(reducedMotion) { IronNavTransitions.popExit(reducedMotion) }
                val tabEnter = remember(reducedMotion) { IronNavTransitions.tabEnter(reducedMotion) }
                val tabExit = remember(reducedMotion) { IronNavTransitions.tabExit(reducedMotion) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground
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

                    val showBottomBar = currentRoute in BottomBarRoutes
                    var lastDisplayedWorkout by remember { mutableStateOf<Workout?>(null) }
                    if (ongoingWorkout != null) {
                        lastDisplayedWorkout = ongoingWorkout
                    }

                    // Outer Box renders the unified obsidian/ceramic ambient canvas behind transparent screens and overlays.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .obsidianAmbientBackdrop(
                                accent = MaterialTheme.colorScheme.primary,
                                isDark = surfaces.isDark
                            )
                            .clearFocusOnTapOutside()
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                enterTransition = {
                                    val isTabSwitch =
                                        initialState.destination.route in BottomBarRoutes &&
                                            targetState.destination.route in BottomBarRoutes
                                    if (isTabSwitch) tabEnter() else pushEnter()
                                },
                                exitTransition = {
                                    val isTabSwitch =
                                        initialState.destination.route in BottomBarRoutes &&
                                            targetState.destination.route in BottomBarRoutes
                                    if (isTabSwitch) tabExit() else pushExit()
                                },
                                popEnterTransition = {
                                    val isTabSwitch =
                                        initialState.destination.route in BottomBarRoutes &&
                                            targetState.destination.route in BottomBarRoutes
                                    if (isTabSwitch) tabEnter() else popEnter()
                                },
                                popExitTransition = {
                                    val isTabSwitch =
                                        initialState.destination.route in BottomBarRoutes &&
                                            targetState.destination.route in BottomBarRoutes
                                    if (isTabSwitch) tabExit() else popExit()
                                },
                                modifier = Modifier
                                    .fillMaxSize()
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
                                    com.example.gymtime.ui.analytics.AnalyticsScreen(
                                        onHistory = { navController.navigateResumed(Screen.History.route) },
                                        onLibrary = { navController.navigateResumed(Screen.Library.route) },
                                        onOpenHome = navController::navigateHomeAndClearStack
                                    )
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
                                        },
                                        androidx.navigation.navArgument("swapInstanceId") {
                                            type = androidx.navigation.NavType.LongType
                                            defaultValue = -1L
                                        }
                                    )
                                ) {
                                    com.example.gymtime.ui.exercise.ExerciseSelectionScreen(navController = navController)
                                }
                                composable(Screen.WorkoutResume.route) {
                                    com.example.gymtime.ui.workout.WorkoutResumeScreen(
                                        onExerciseClick = { exerciseId ->
                                            navController.navigateToWorkoutExercise(exerciseId)
                                        },
                                        onAddExerciseClick = {
                                            navController.navigateResumed(Screen.ExerciseSelection.createRoute(workoutMode = true))
                                        },
                                        onSwapExerciseClick = { instanceId ->
                                            navController.navigateResumed(
                                                Screen.ExerciseSelection.createRoute(
                                                    workoutMode = true,
                                                    swapInstanceId = instanceId
                                                )
                                            )
                                        },
                                        onFinishWorkoutClick = { workoutId ->
                                            navController.navigateToWorkoutSummary(workoutId)
                                        },
                                        onBackClick = navController::navigateBackOrHome,
                                        onHomeClick = navController::navigateHomeAndClearStack
                                    )
                                }
                                composable(Screen.WorkoutBuilder.route) {
                                    com.example.gymtime.ui.workout.WorkoutBuilderScreen(
                                        navController = navController
                                    )
                                }
                                composable(
                                    route = Screen.ExerciseLogging.route,
                                    arguments = listOf(
                                        androidx.navigation.navArgument("exerciseId") {
                                            type = androidx.navigation.NavType.LongType
                                        },
                                        androidx.navigation.navArgument("draftToken") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        }
                                    ),
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
                                        },
                                        androidx.navigation.navArgument("fromWorkoutBuilder") {
                                            type = androidx.navigation.NavType.BoolType
                                            defaultValue = false
                                        },
                                        androidx.navigation.navArgument("initialName") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        },
                                        androidx.navigation.navArgument("initialMuscle") {
                                            type = androidx.navigation.NavType.StringType
                                            nullable = true
                                            defaultValue = null
                                        },
                                        androidx.navigation.navArgument("returnToPicker") {
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

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(
                                visible = showBottomBar && ongoingWorkout != null,
                                enter = if (reducedMotion) {
                                    fadeIn(tween(durationMillis = 120))
                                } else {
                                    slideInVertically(animationSpec = IronMotion.SpatialLayoutOffset) { it / 2 } +
                                        scaleIn(animationSpec = IronMotion.RewardPop, initialScale = 0.92f) +
                                        fadeIn(animationSpec = IronMotion.EffectDefault)
                                },
                                exit = if (reducedMotion) {
                                    fadeOut(tween(durationMillis = 90))
                                } else {
                                    slideOutVertically(animationSpec = IronMotion.SpatialLayoutOffset) { it / 2 } +
                                        scaleOut(animationSpec = IronMotion.SpatialLayout, targetScale = 0.94f) +
                                        fadeOut(animationSpec = IronMotion.EffectFast)
                                }
                            ) {
                                lastDisplayedWorkout?.let { activeWorkout ->
                                    LiveTelemetryIsland(
                                        workout = activeWorkout,
                                        onResumeClick = { navController.navigateToWorkoutOverview() }
                                    )
                                }
                            }

                            if (showBottomBar) {
                                BottomNavigationBar(
                                    navController = navController,
                                    currentRoute = currentRoute
                                )
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

@Composable
private fun LiveTelemetryIsland(
    workout: Workout,
    onResumeClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current

    val workoutLabel = remember(workout.routineDayNameSnapshot, workout.name, workout.routineNameSnapshot) {
        workout.routineDayNameSnapshot?.takeIf { it.isNotBlank() }
            ?: workout.name?.takeIf { it.isNotBlank() }
            ?: workout.routineNameSnapshot?.takeIf { it.isNotBlank() }
            ?: "ACTIVE SESSION"
    }

    val pillBorder = remember(surfaces) {
        IronLogBorder.specularRimStroke(
            surfaces = surfaces,
            highlighted = true,
            width = IronLogBorder.Thin
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressIndication = remember {
        MachinedPressIndication(
            pressedScale = 0.965f,
            highlightAlpha = 0.08f,
            cornerRadius = IronLogRadius.Capsule
        )
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 360.dp)
            .heightIn(min = IronLogSpace.TouchTargetMin)
            .clickable(
                interactionSource = interactionSource,
                indication = pressIndication,
                role = Role.Button,
                onClickLabel = "Resume active workout",
                onClick = {
                    haptics.segmentTick()
                    onResumeClick()
                }
            )
            .shadow(
                elevation = 14.dp,
                shape = IronLogRadius.CapsuleShape,
                ambientColor = Color.Black.copy(alpha = if (surfaces.isDark) 0.65f else 0.14f),
                spotColor = Color.Black.copy(alpha = if (surfaces.isDark) 0.72f else 0.20f)
            )
            .clip(IronLogRadius.CapsuleShape)
            .background(surfaces.glassDockFill)
            .anodizedMicroGrain(alpha = if (surfaces.isDark) 0.02f else 0.01f)
            .border(border = pillBorder, shape = IronLogRadius.CapsuleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IronLogLedPip(
            color = surfaces.ledGlow,
            size = 6.dp,
            pulsing = true
        )

        Text(
            text = workoutLabel.uppercase(),
            style = IronLogType.technicalEyebrow(),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 14.dp)
                .background(IronLogBorder.dividerColor())
        )

        ElapsedIslandOdometer(
            workoutId = workout.id,
            startTime = workout.startTime,
            color = surfaces.accent
        )

        Row(
            modifier = Modifier
                .clip(IronLogRadius.CapsuleShape)
                .background(surfaces.accent.copy(alpha = if (surfaces.isDark) 0.16f else 0.12f))
                .border(
                    width = IronLogBorder.Thin,
                    color = surfaces.accent.copy(alpha = 0.40f),
                    shape = IronLogRadius.CapsuleShape
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "RESUME",
                style = IronLogType.technicalEyebrow().copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = surfaces.accent,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = surfaces.accent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ElapsedIslandOdometer(
    workoutId: Long,
    startTime: Date,
    color: Color
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsedSeconds by remember(workoutId, startTime) {
        mutableIntStateOf(((System.currentTimeMillis() - startTime.time).coerceAtLeast(0L) / 1000L).toInt())
    }
    LaunchedEffect(workoutId, startTime, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime.time).coerceAtLeast(0L) / 1000L).toInt()
                delay(1_000L)
            }
        }
    }

    OdometerText(
        text = TimeUtils.formatSecondsToHMS(elapsedSeconds),
        style = IronLogType.tabular(MaterialTheme.typography.labelLarge).copy(
            fontWeight = FontWeight.Bold
        ),
        color = color
    )
}
