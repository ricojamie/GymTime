package com.example.gymtime.navigation

import androidx.navigation.NavController

fun NavController.navigateHomeAndClearStack() {
    navigate(Screen.Home.route) {
        popUpTo(Screen.Home.route) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

fun NavController.navigateBackOrHome() {
    if (!popBackStack()) {
        navigateHomeAndClearStack()
    }
}

fun NavController.navigateToWorkoutOverview() {
    navigate(Screen.WorkoutResume.route) {
        popUpTo(Screen.Home.route) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

/**
 * Opens an exercise logger with one stable workout parent:
 * Home -> WorkoutResume -> ExerciseLogging.
 *
 * Returning from pickers/forms and switching exercises first removes everything
 * above WorkoutResume, preventing logger/picker entries from accumulating.
 */
fun NavController.navigateToWorkoutExercise(exerciseId: Long, draftToken: String? = null) {
    val currentRoute = currentDestination?.route
    val overviewReady = currentRoute == Screen.WorkoutResume.route ||
        popBackStack(Screen.WorkoutResume.route, inclusive = false)

    if (!overviewReady) {
        navigateToWorkoutOverview()
    }

    navigate(Screen.ExerciseLogging.createRoute(exerciseId, draftToken)) {
        launchSingleTop = true
    }
}

/** A completed workout is terminal: no logger or overview remains underneath. */
fun NavController.navigateToWorkoutSummary(workoutId: Long) {
    navigate(Screen.PostWorkoutSummary.createRoute(workoutId)) {
        popUpTo(Screen.Home.route) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

/** Replaces a newly completed routine form while preserving its real caller. */
fun NavController.navigateToNewRoutineDetail(routineId: Long) {
    navigate(Screen.RoutineDetail.createRoute(routineId)) {
        popUpTo(Screen.RoutineForm.route) { inclusive = true }
        launchSingleTop = true
    }
}
