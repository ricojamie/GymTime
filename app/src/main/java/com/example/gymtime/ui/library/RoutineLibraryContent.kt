package com.example.gymtime.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.ui.routine.preview.PreviewRoutineBrowseContent

/**
 * Routine library content for the Library screen's Routines tab.
 * Shows list of routines with tap-to-open navigation.
 */
@Composable
fun RoutineLibraryContent(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.routinePreviewState.collectAsStateWithLifecycle()
    PreviewRoutineBrowseContent(
        state = state,
        embedded = true,
        onCreate = { navController.navigateResumed(Screen.RoutineForm.createRoute()) },
        onOpen = { navController.navigateResumed(Screen.RoutineDetail.createRoute(it)) },
        onSetActive = viewModel::setActiveRoutine,
        onRename = { navController.navigateResumed(Screen.RoutineForm.createRoute(it)) },
        onDuplicate = viewModel::duplicateRoutine,
        onDelete = viewModel::deleteRoutineById,
        onRetry = viewModel::retryRoutines,
        onDismissError = viewModel::dismissRoutineError
    )
}
