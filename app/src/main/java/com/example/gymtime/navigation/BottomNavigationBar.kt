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
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
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
    }
}
