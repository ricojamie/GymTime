package com.example.gymtime.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Library : Screen("library")
    object Workout : Screen("workout")
    object ExerciseSelection : Screen("exercise_selection")
    object WorkoutResume : Screen("workout_resume")
    object ExerciseLogging : Screen("exercise_logging/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_logging/$exerciseId"
    }
}
