package com.example.gymtime.ui.exercise

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.ui.exercise.preview.PickerUiState
import com.example.gymtime.ui.exercise.preview.PreviewExercisePicker
import com.example.gymtime.ui.exercise.preview.PreviewExerciseSelectionScreen
import com.example.gymtime.ui.theme.IronLogTheme

@Composable
fun ExerciseSelectionScreen(
    navController: NavController,
    viewModel: ExerciseSelectionViewModel = hiltViewModel()
) {
    ExerciseSelectionContent(
        navController = navController,
        viewModel = viewModel,
        showNavigationChrome = true
    )
}

/**
 * Reusable content for exercise selection.
 * Can be used standalone in ExerciseSelectionScreen or embedded in LibraryScreen.
 */
@Composable
fun ExerciseSelectionContent(
    navController: NavController,
    viewModel: ExerciseSelectionViewModel = hiltViewModel(),
    showNavigationChrome: Boolean = false
) {
    PreviewExerciseSelectionScreen(
        navController = navController,
        viewModel = viewModel,
        showNavigationChrome = showNavigationChrome
    )
}

@Preview(showBackground = true)
@Composable
private fun ExerciseSelectionScreenPreview() {
    IronLogTheme {
        PreviewExercisePicker(
            state = PickerUiState(showBack = false),
            onAction = {},
            onSelectExercise = {},
            onQueryChange = {},
            onMuscleToggle = {},
            onSortChange = {}
        )
    }
}
