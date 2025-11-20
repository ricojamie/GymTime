package com.example.gymtime.navigation

import com.example.gymtime.R
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.compose.material3.NavigationBarItemDefaults
import com.example.gymtime.ui.theme.PrimaryAccent

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.History,
        Screen.Library
    )
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(painterResource(id = getIconForScreen(screen)), contentDescription = screen.route) },
                label = { Text(text = screen.route.replaceFirstChar { it.uppercase() }) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        if (screen == Screen.Home) {
                            // For Home button: clear the entire back stack and go to Home
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        } else {
                            // For other buttons: use standard navigation
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryAccent,
                    selectedTextColor = PrimaryAccent,
                )
            )
        }
    }
}

fun getIconForScreen(screen: Screen): Int {
    return when (screen) {
        Screen.Home -> R.drawable.ic_home
        Screen.History -> R.drawable.ic_history
        Screen.Library -> R.drawable.ic_library
        Screen.Workout -> R.drawable.ic_home // Not in bottom bar, but needs to be exhaustive
        Screen.ExerciseSelection -> R.drawable.ic_home // Not in bottom bar
        Screen.ExerciseLogging -> R.drawable.ic_home // Not in bottom bar
    }
}
