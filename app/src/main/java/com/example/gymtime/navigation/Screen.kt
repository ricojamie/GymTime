package com.example.gymtime.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val icon: ImageVector) {
    object Home : Screen("home", Icons.Filled.Home)
    object History : Screen("history", Icons.Filled.History)
    object Library : Screen("library", Icons.Filled.MenuBook) // Using MenuBook for Library
    object Analytics : Screen("analytics", Icons.Filled.ShowChart) // Using ShowChart for Analytics
    object Workout : Screen("workout", Icons.Filled.Home) // Placeholder, not in bottom nav
    object ExerciseSelection : Screen("exercise_selection", Icons.Filled.Home) // Placeholder, not in bottom nav
    object WorkoutResume : Screen("workout_resume", Icons.Filled.Home) // Placeholder, not in bottom nav
    object ExerciseLogging : Screen("exercise_logging/{exerciseId}", Icons.Filled.Home) { // Placeholder, not in bottom nav
        fun createRoute(exerciseId: Long) = "exercise_logging/$exerciseId"
    }
    object ExerciseForm : Screen("exercise_form?exerciseId={exerciseId}", Icons.Filled.Home) { // Create/Edit exercise
        fun createRoute(exerciseId: Long? = null) = if (exerciseId != null) {
            "exercise_form?exerciseId=$exerciseId"
        } else {
            "exercise_form"
        }
    }
}

