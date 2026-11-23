package com.example.gymtime.ui.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.settings.preview.PreviewThemeSettingsScreen

@Composable
fun ThemeSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    PreviewThemeSettingsScreen(
        viewModel = viewModel,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack
    )
}
