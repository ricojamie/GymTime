package com.example.gymtime.navigation

import androidx.navigation.NavController

fun NavController.navigateHomeAndClearStack() {
    navigate(Screen.Home.route) {
        popUpTo(Screen.Home.route) {
            inclusive = true
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}
