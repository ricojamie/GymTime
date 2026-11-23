package com.example.gymtime.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private fun NavController.isDestinationAttachedAndAlive(): Boolean {
    if (currentDestination == null) return false
    val entry = currentBackStackEntry ?: return false
    return entry.lifecycle.currentState != Lifecycle.State.DESTROYED
}

/** True when the current destination is attached and at least [Lifecycle.State.RESUMED]. */
fun NavController.canNavigate(): Boolean {
    val entry = currentBackStackEntry ?: return false
    return currentDestination != null &&
        entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}

/** Guards tap-triggered route navigation against double-taps during transitions. */
fun NavController.navigateResumed(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }
) {
    if (!canNavigate()) return
    navigate(route, builder)
}

fun NavController.navigateHomeAndClearStack() {
    if (!isDestinationAttachedAndAlive()) return
    if (currentDestination?.route == Screen.Home.route && previousBackStackEntry == null) return
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
    if (!isDestinationAttachedAndAlive()) return
    if (!popBackStack()) {
        navigateHomeAndClearStack()
    }
}

fun NavController.navigateToWorkoutOverview() {
    if (!isDestinationAttachedAndAlive()) return
    if (currentDestination?.route == Screen.WorkoutResume.route) return
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
    if (!isDestinationAttachedAndAlive()) return
    val normalizedDraftToken = draftToken?.takeIf { it.isNotBlank() }
    val currentEntry = currentBackStackEntry
    if (currentEntry?.destination?.route == Screen.ExerciseLogging.route) {
        val currentExerciseId = currentEntry.arguments?.let { args ->
            if (args.containsKey("exerciseId")) args.getLong("exerciseId") else null
        }
        val currentDraftToken = currentEntry.arguments?.getString("draftToken")?.takeIf { it.isNotBlank() }
        if (currentExerciseId == exerciseId && currentDraftToken == normalizedDraftToken) {
            return
        }
    }

    val currentRoute = currentDestination?.route
    val overviewReady = currentRoute == Screen.WorkoutResume.route ||
        popBackStack(Screen.WorkoutResume.route, inclusive = false)

    if (!overviewReady) {
        navigateToWorkoutOverview()
    }

    navigate(Screen.ExerciseLogging.createRoute(exerciseId, normalizedDraftToken)) {
        launchSingleTop = true
    }
}

/** A completed workout is terminal: no logger or overview remains underneath. */
fun NavController.navigateToWorkoutSummary(workoutId: Long) {
    if (!isDestinationAttachedAndAlive()) return
    val currentEntry = currentBackStackEntry
    if (currentEntry?.destination?.route == Screen.PostWorkoutSummary.route) {
        val currentWorkoutId = currentEntry.arguments?.let { args ->
            if (args.containsKey("workoutId")) args.getLong("workoutId") else null
        }
        if (currentWorkoutId == workoutId) return
    }
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
    if (!isDestinationAttachedAndAlive()) return
    navigate(Screen.RoutineDetail.createRoute(routineId)) {
        popUpTo(Screen.RoutineForm.route) { inclusive = true }
        launchSingleTop = true
    }
}
