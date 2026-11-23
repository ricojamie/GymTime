package com.example.gymtime.ui.routine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.ui.routine.preview.PreviewRoutineDayStartContent

@Composable
fun RoutineDayStartScreen(
    navController: NavController,
    viewModel: RoutineDayStartViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOpenWorkout by rememberUpdatedState<(Long) -> Unit>({ navController.navigateToWorkoutExercise(it) })
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startWorkoutEvent.collect { firstExerciseId ->
                runCatching { latestOpenWorkout(firstExerciseId) }
                    .onSuccess { viewModel.onWorkoutOpened() }
                    .onFailure { viewModel.onWorkoutNavigationFailed() }
            }
        }
    }

    val state by viewModel.previewState.collectAsStateWithLifecycle()
    PreviewRoutineDayStartContent(
        state = state,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack,
        onStartDay = viewModel::startWorkoutFromDay,
        onEditDay = { navController.navigateResumed(Screen.RoutineDayForm.createRoute(viewModel.routineId, it)) },
        onAddDay = { navController.navigateResumed(Screen.RoutineDayForm.createRoute(viewModel.routineId)) },
        onRetry = viewModel::retryLoad,
        onDismissError = viewModel::dismissError
    )
}
