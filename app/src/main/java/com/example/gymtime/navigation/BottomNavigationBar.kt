package com.example.gymtime.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.theme.LocalLoggerPreviewThemeActive
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavigationBar(navController: NavController) {
    if (LocalLoggerPreviewThemeActive.current) {
        PreviewBottomNavigationBar(navController)
        return
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val items = listOf(
        Screen.Home to "Home",
        Screen.History to "History",
        Screen.Library to "Library",
        Screen.Analytics to "Stats"
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp) // Making it float
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = LocalAppColors.current.surfaceCards.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = accentColor.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (screen, label) ->
                    val isSelected = currentRoute == screen.route
                    val color = if (isSelected) accentColor else Color.Gray

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null 
                            ) {
                                if (screen == Screen.Home) {
                                    navController.navigateHomeAndClearStack()
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            inclusive = false
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = label,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBottomNavigationBar(navController: NavController) {
    val scheme = MaterialTheme.colorScheme
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = listOf(
        Screen.Home to "Home",
        Screen.History to "History",
        Screen.Library to "Library",
        Screen.Analytics to "Insights"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = scheme.surfaceContainer,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (screen, label) ->
                    val selected = currentRoute == screen.route
                    val color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) scheme.primaryContainer else Color.Transparent)
                            .selectable(selected = selected, role = Role.Tab) {
                                if (screen == Screen.Home) {
                                    navController.navigateHomeAndClearStack()
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) { inclusive = false; saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(screen.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
