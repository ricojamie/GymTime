package com.example.gymtime.ui.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.ui.settings.preview.PreviewSettingsScreen

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    PreviewSettingsScreen(
        viewModel = viewModel,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack,
        onTheme = { navController.navigateResumed(Screen.ThemeSettings.route) },
        onMonthlyReport = { navController.navigateResumed(Screen.MonthlyReport.route) },
        onMuscleGroups = { navController.navigateResumed(Screen.MuscleGroupManagement.route) }
    )
}
