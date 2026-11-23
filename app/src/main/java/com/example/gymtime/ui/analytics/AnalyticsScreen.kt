package com.example.gymtime.ui.analytics

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.ui.analytics.preview.PreviewTrainingInsightsScreen
import com.example.gymtime.ui.analytics.preview.TrainingInsightsViewModel

@Composable
fun AnalyticsScreen(
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onOpenHome: () -> Unit,
    previewViewModel: TrainingInsightsViewModel = hiltViewModel()
) {
    PreviewTrainingInsightsScreen(
        viewModel = previewViewModel,
        onHistory = onHistory,
        onLibrary = onLibrary,
        onOpenHome = onOpenHome
    )
}
