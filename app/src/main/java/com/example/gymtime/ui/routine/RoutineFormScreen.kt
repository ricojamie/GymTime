package com.example.gymtime.ui.routine

import androidx.activity.compose.BackHandler
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
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateToNewRoutineDetail
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.routine.preview.PreviewRoutineFormContent
import com.example.gymtime.ui.routine.preview.RoutineFormUiState

@Composable
fun RoutineFormScreen(
    navController: NavController,
    viewModel: RoutineFormViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val navigationActions = rememberGuardedNavigationActions(
        hasUnsavedChanges = hasUnsavedChanges,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack
    )

    BackHandler(enabled = isSaving) { /* Keep the draft on screen until its save finishes. */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestEditMode by rememberUpdatedState(isEditMode)
    LaunchedEffect(viewModel, navController, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.saveSuccessEvent.collect { routineId ->
                if (latestEditMode) {
                    navController.navigateUp()
                } else {
                    navController.navigateToNewRoutineDetail(routineId)
                }
            }
        }
    }

    PreviewRoutineFormContent(
        state = RoutineFormUiState(
            name = routineName,
            editing = isEditMode,
            loading = isLoading,
            saving = isSaving,
            canEdit = canEdit,
            canSave = isSaveEnabled,
            error = error
        ),
        onName = viewModel::updateRoutineName,
        onSave = viewModel::saveRoutine,
        onRetry = viewModel::retryLoad,
        onBack = navigationActions.back,
        onHome = navigationActions.home
    )
}
