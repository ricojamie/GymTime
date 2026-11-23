package com.example.gymtime.ui.exercise

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.exercise.preview.ExerciseFormAction
import com.example.gymtime.ui.exercise.preview.ExerciseFormUiState
import com.example.gymtime.ui.exercise.preview.PreviewExerciseForm
import com.example.gymtime.ui.theme.IronLogTheme

@Composable
fun ExerciseFormScreen(
    navController: NavController,
    viewModel: ExerciseFormViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, navController, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.saveSuccessEvent.collect { newExerciseId ->
                when {
                    newExerciseId != null &&
                        (viewModel.isFromWorkoutBuilder.value || viewModel.isReturningToPicker.value) -> {
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID, newExerciseId)
                        navController.navigateBackOrHome()
                    }
                    newExerciseId != null && viewModel.isFromWorkout.value ->
                        navController.navigateToWorkoutExercise(newExerciseId)
                    else -> navController.navigateBackOrHome()
                }
            }
        }
    }

    PreviewExerciseFormScreen(navController = navController, viewModel = viewModel)
}

@Composable
private fun PreviewExerciseFormScreen(
    navController: NavController,
    viewModel: ExerciseFormViewModel
) {
    val name by viewModel.exerciseName.collectAsStateWithLifecycle()
    val muscle by viewModel.targetMuscle.collectAsStateWithLifecycle()
    val type by viewModel.logType.collectAsStateWithLifecycle()
    val distanceUnit by viewModel.defaultDistanceUnit.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val rest by viewModel.defaultRestSeconds.collectAsStateWithLifecycle()
    val repTarget by viewModel.repTarget.collectAsStateWithLifecycle()
    val muscles by viewModel.availableMuscles.collectAsStateWithLifecycle(initialValue = emptyList())
    val similar by viewModel.similarExercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val canSave by viewModel.isSaveEnabled.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.saveError.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val navigation = rememberGuardedNavigationActions(
        hasUnsavedChanges = hasChanges,
        onBack = { if (!viewModel.isSaving.value) navController.navigateBackOrHome() },
        onHome = { if (!viewModel.isSaving.value) navController.navigateHomeAndClearStack() }
    )
    BackHandler(enabled = isSaving) { /* Save owns the next navigation. */ }
    LaunchedEffect(viewModel) { viewModel.preparePreviewDefaults() }

    PreviewExerciseForm(
        state = ExerciseFormUiState(
            name = name,
            muscle = muscle,
            logType = type,
            distanceUnit = distanceUnit,
            notes = notes,
            restSeconds = rest,
            repTarget = repTarget,
            muscles = muscles,
            similarExercises = similar,
            isEditMode = isEditMode,
            isLoading = isLoading,
            isSaving = isSaving,
            canSave = canSave,
            error = error,
            loadError = loadError,
            saveLabel = when {
                isEditMode -> "Save changes"
                viewModel.isFromWorkoutBuilder.value -> "Create & add to workout"
                viewModel.isReturningToPicker.value -> "Create & continue"
                viewModel.isFromWorkout.value -> "Create & log"
                else -> "Create exercise"
            }
        ),
        onAction = { action ->
            when (action) {
                is ExerciseFormAction.Name -> viewModel.updateExerciseName(action.value)
                is ExerciseFormAction.Muscle -> viewModel.updateTargetMuscle(action.value)
                is ExerciseFormAction.Tracking -> viewModel.updateLogType(action.value)
                is ExerciseFormAction.Distance -> viewModel.updateDefaultDistanceUnit(action.value)
                is ExerciseFormAction.Notes -> viewModel.updateNotes(action.value)
                is ExerciseFormAction.Rest -> viewModel.updateDefaultRestSeconds(action.value)
                is ExerciseFormAction.RepTarget -> viewModel.updateRepTarget(action.value)
                is ExerciseFormAction.UseExisting -> viewModel.useExistingExercise(action.exercise)
                ExerciseFormAction.Save -> viewModel.saveExercise()
                ExerciseFormAction.Back -> if (!isSaving) navigation.back()
                ExerciseFormAction.DismissError -> viewModel.dismissSaveError()
                ExerciseFormAction.RetryLoad -> viewModel.retryLoad()
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ExerciseFormScreenPreview() {
    IronLogTheme {
        PreviewExerciseForm(
            state = ExerciseFormUiState(
                muscles = listOf("Chest", "Back", "Legs")
            ),
            onAction = {}
        )
    }
}
