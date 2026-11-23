package com.example.gymtime.ui.routine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.ui.routine.preview.PreviewRoutineBrowseContent

@Composable
fun RoutineListScreen(
    navController: NavController,
    viewModel: RoutineListViewModel = hiltViewModel()
) {
    val state by viewModel.previewState.collectAsStateWithLifecycle()
    PreviewRoutineBrowseContent(
        state = state,
        onCreate = { navController.navigateResumed(Screen.RoutineForm.createRoute()) },
        onOpen = { navController.navigateResumed(Screen.RoutineDetail.createRoute(it)) },
        onSetActive = viewModel::setActiveRoutine,
        onRename = { navController.navigateResumed(Screen.RoutineForm.createRoute(it)) },
        onDuplicate = viewModel::duplicateRoutine,
        onDelete = viewModel::deleteRoutineById,
        onRetry = viewModel::retryLoad,
        onDismissError = viewModel::dismissError,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack
    )
}
