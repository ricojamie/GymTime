package com.example.gymtime.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.gymtime.ui.exercise.ExerciseSelectionContent
import com.example.gymtime.ui.theme.*

/**
 * Library screen with tabs for Exercises and Routines.
 * Defaults to Exercises tab.
 */
@Composable
fun LibraryScreen(navController: NavController) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Exercises", "Routines")
    val accentColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = accentColor,
            divider = {
                HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.3f))
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == index) accentColor else LocalAppColors.current.textSecondary
                        )
                    }
                )
            }
        }

        // Content based on selected tab
        when (selectedTabIndex) {
            0 -> ExerciseSelectionContent(navController = navController)
            1 -> RoutineLibraryContent(navController = navController)
        }
    }
}
