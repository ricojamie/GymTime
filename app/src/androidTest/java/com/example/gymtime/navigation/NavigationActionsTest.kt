package com.example.gymtime.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        composeRule.setContent {
            val context = LocalContext.current
            navController = TestNavHostController(context).also { controller ->
                controller.navigatorProvider.addNavigator(ComposeNavigator())
            }

            NavHost(navController = navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) { Text("Home") }
                composable(Screen.Library.route) { Text("Library") }
                composable(Screen.Settings.route) { Text("Settings") }
                composable(Screen.ThemeSettings.route) { Text("Theme") }
                composable(Screen.WorkoutResume.route) { Text("Workout Overview") }
                composable(
                    route = Screen.ExerciseLogging.route,
                    arguments = listOf(
                        navArgument("exerciseId") { type = NavType.LongType },
                        navArgument("draftToken") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { Text("Logger") }
                composable(
                    route = Screen.PostWorkoutSummary.route,
                    arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                ) { Text("Summary") }
                composable(
                    route = Screen.RoutineForm.route,
                    arguments = listOf(
                        navArgument("routineId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { Text("Routine Form") }
                composable(
                    route = Screen.RoutineDetail.route,
                    arguments = listOf(navArgument("routineId") { type = NavType.LongType })
                ) { Text("Routine Detail") }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun repeatedExerciseSwitchesKeepOnlyOverviewAsLoggerParent() {
        composeRule.runOnIdle {
            repeat(12) { index ->
                navController.navigateToWorkoutExercise(index + 1L)
            }

            assertEquals(Screen.ExerciseLogging.route, navController.currentDestination?.route)
            navController.navigateBackOrHome()
            assertEquals(Screen.WorkoutResume.route, navController.currentDestination?.route)
            navController.navigateBackOrHome()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun homeClearsDeepNestedWorkoutNavigation() {
        composeRule.runOnIdle {
            navController.navigateToWorkoutExercise(1L)
            navController.navigate(Screen.Settings.route)
            navController.navigate(Screen.ThemeSettings.route)

            navController.navigateHomeAndClearStack()

            assertEquals(Screen.Home.route, navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun workoutSummaryCannotRevealCompletedLogger() {
        composeRule.runOnIdle {
            navController.navigateToWorkoutExercise(1L)
            navController.navigateToWorkoutSummary(42L)

            assertEquals(Screen.PostWorkoutSummary.route, navController.currentDestination?.route)
            navController.navigateBackOrHome()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }

    @Test
    fun nestedBackReturnsThroughActualCaller() {
        composeRule.runOnIdle {
            navController.navigate(Screen.Settings.route)
            navController.navigate(Screen.ThemeSettings.route)

            navController.navigateBackOrHome()
            assertEquals(Screen.Settings.route, navController.currentDestination?.route)
            navController.navigateBackOrHome()
            assertEquals(Screen.Home.route, navController.currentDestination?.route)
        }
    }

    @Test
    fun newRoutineDetailReplacesFormAndPreservesLibraryCaller() {
        composeRule.runOnIdle {
            navController.navigate(Screen.Library.route)
            navController.navigate(Screen.RoutineForm.createRoute())

            navController.navigateToNewRoutineDetail(9L)

            assertEquals(Screen.RoutineDetail.route, navController.currentDestination?.route)
            navController.navigateBackOrHome()
            assertEquals(Screen.Library.route, navController.currentDestination?.route)
        }
    }
}
