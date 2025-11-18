package com.example.gymtime.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Library : Screen("library")
}
