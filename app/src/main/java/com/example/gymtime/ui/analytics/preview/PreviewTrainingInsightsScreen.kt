package com.example.gymtime.ui.analytics.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gymtime.ui.theme.LoggerPreviewTheme

/** App wiring stays outside the immutable, independently previewable content. */
@Composable
fun PreviewTrainingInsightsScreen(
    viewModel: TrainingInsightsViewModel,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onOpenHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LoggerPreviewTheme {
        PreviewTrainingInsightsContent(
            state = state, onWindowChange = viewModel::selectWindow,
            onExerciseChange = viewModel::selectExercise,
            onQueryChange = viewModel::setExerciseQuery, onMuscleFilterChange = viewModel::setMuscleFilter,
            onRetry = viewModel::refresh, onHistory = onHistory, onLibrary = onLibrary, onOpenHome = onOpenHome
        )
    }
}
