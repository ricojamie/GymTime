package com.example.gymtime.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val icon: ImageVector) {
    object Home : Screen("home", Icons.Filled.Home)
    object History : Screen("history", Icons.Filled.History)
    object Library : Screen("library", Icons.AutoMirrored.Filled.MenuBook)
    object Analytics : Screen("analytics", Icons.AutoMirrored.Filled.ShowChart)
    object Workout : Screen("workout", Icons.Filled.Home) // Placeholder, not in bottom nav
    object ExerciseSelection : Screen("exercise_selection?workoutMode={workoutMode}", Icons.Filled.Home) { // Placeholder, not in bottom nav
        fun createRoute(workoutMode: Boolean = false) = if (workoutMode) {
            "exercise_selection?workoutMode=true"
        } else {
            "exercise_selection"
        }
    }
    object WorkoutResume : Screen("workout_resume", Icons.Filled.Home) // Placeholder, not in bottom nav
    object ExerciseLogging : Screen("exercise_logging/{exerciseId}", Icons.Filled.Home) { // Placeholder, not in bottom nav
        fun createRoute(exerciseId: Long) = "exercise_logging/$exerciseId"
    }
    object ExerciseForm : Screen("exercise_form?exerciseId={exerciseId}&fromWorkout={fromWorkout}", Icons.Filled.Home) { // Create/Edit exercise
        fun createRoute(exerciseId: Long? = null, fromWorkout: Boolean = false) = buildString {
            append("exercise_form")
            val params = mutableListOf<String>()
            exerciseId?.let { params.add("exerciseId=$it") }
            if (fromWorkout) params.add("fromWorkout=true")
            if (params.isNotEmpty()) append("?${params.joinToString("&")}")
        }
    }
    object PostWorkoutSummary : Screen("post_workout_summary/{workoutId}", Icons.Filled.Home) { // Post-workout summary
        fun createRoute(workoutId: Long) = "post_workout_summary/$workoutId"
    }

    object Settings : Screen("settings", Icons.Filled.Settings) // Settings screen

    // Routine Routes
    object RoutineList : Screen("routine_list", Icons.Filled.Home)

    object RoutineForm : Screen("routine_form?routineId={routineId}", Icons.Filled.Home) {
        fun createRoute(routineId: Long? = null) = if (routineId != null) {
            "routine_form?routineId=$routineId"
        } else {
            "routine_form"
        }
    }

    object RoutineDayList : Screen("routine_day_list/{routineId}", Icons.Filled.Home) {
        fun createRoute(routineId: Long) = "routine_day_list/$routineId"
    }

    object RoutineDayForm : Screen("routine_day_form/{routineId}?dayId={dayId}", Icons.Filled.Home) {
        fun createRoute(routineId: Long, dayId: Long? = null) = if (dayId != null) {
            "routine_day_form/$routineId?dayId=$dayId"
        } else {
            "routine_day_form/$routineId"
        }
    }

    object RoutineDayStart : Screen("routine_day_start/{routineId}", Icons.Filled.Home) {
        fun createRoute(routineId: Long) = "routine_day_start/$routineId"
    }
}

