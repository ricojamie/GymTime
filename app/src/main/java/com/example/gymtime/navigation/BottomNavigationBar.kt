package com.example.gymtime.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gymtime.R
import com.example.gymtime.ui.theme.PrimaryAccent

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.History,
        Screen.Library,
        Screen.Analytics
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Top Neon Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            PrimaryAccent,
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // Handle system gesture bar
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val color = if (isSelected) PrimaryAccent else Color.Gray

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Remove ripple for cleaner look
                        ) {
                            if (screen == Screen.Home) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(16.dp) // Touch target padding
                ) {
                    Icon(
                        painter = painterResource(id = getIconForScreen(screen)),
                        contentDescription = screen.route,
                        tint = color,
                        modifier = Modifier.size(26.dp)
                    )
                    
                    // Removed the dot indicator as requested for "Option 2" clean look
                }
            }
        }
    }
}

fun getIconForScreen(screen: Screen): Int {
    return when (screen) {
        Screen.Home -> R.drawable.ic_home
        Screen.History -> R.drawable.ic_history
        Screen.Library -> R.drawable.ic_library
        Screen.Analytics -> R.drawable.ic_history // TODO: Create ic_analytics icon (trending up)
        Screen.Workout -> R.drawable.ic_home
        Screen.ExerciseSelection -> R.drawable.ic_home
        Screen.WorkoutResume -> R.drawable.ic_home
        Screen.ExerciseLogging -> R.drawable.ic_home
    }
}