package com.example.gymtime.ui.exercise.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.ui.exercise.ExerciseSelectionViewModel
import com.example.gymtime.ui.smartlog.SmartLogBottomSheet
import kotlinx.coroutines.flow.filterNotNull

/** App wiring shared with the existing selection flows; the preview owns only presentation. */
@Composable
fun PreviewExerciseSelectionScreen(
    navController: NavController,
    viewModel: ExerciseSelectionViewModel,
    showNavigationChrome: Boolean
) {
    val picker by viewModel.previewPickerState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val muscles by viewModel.availableMuscles.collectAsStateWithLifecycle(initialValue = emptyList())
    val isSuperset by viewModel.isSupersetModeEnabled.collectAsStateWithLifecycle()
    val selected by viewModel.selectedForSuperset.collectAsStateWithLifecycle()
    val isWorkout by viewModel.isWorkoutMode.collectAsStateWithLifecycle()
    val mode = when {
        viewModel.isSwapMode -> PickerMode.SWAP
        viewModel.isImmediateSupersetSelection -> PickerMode.ADD_TO_SUPERSET
        isSuperset -> PickerMode.BUILD_SUPERSET
        else -> PickerMode.BROWSE
    }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    var showSmartLog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val pickerBackStackEntry = remember(navController) { navController.currentBackStackEntry }

    LaunchedEffect(viewModel, pickerBackStackEntry) {
        val entry = pickerBackStackEntry ?: return@LaunchedEffect
        entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            entry.savedStateHandle
                .getStateFlow<Long?>(Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID, null)
                .filterNotNull()
                .collect { exerciseId ->
                    viewModel.handleCreatedExercise(exerciseId)
                    entry.savedStateHandle[Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID] = null
                }
        }
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.supersetStarted.collect { navController.popBackStack() }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.exerciseAddedToSuperset.collect { navController.popBackStack() }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.swapExerciseEvent.collect { event ->
            when (event) {
                is ExerciseSelectionViewModel.SwapExerciseEvent.Success -> navController.navigateToWorkoutExercise(event.exerciseId)
                is ExerciseSelectionViewModel.SwapExerciseEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.selectionMessages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = mode == PickerMode.BUILD_SUPERSET) { viewModel.toggleSupersetMode() }

    Box(Modifier.fillMaxSize()) {
        PreviewExercisePicker(
            state = PickerUiState(
                rows = picker.rows,
                lastSets = picker.lastSets,
                isLoading = picker.isLoading,
                query = query,
                muscles = muscles,
                selectedMuscles = picker.selectedMuscles,
                sortMode = picker.sortMode,
                selectedExercises = selected,
                mode = mode,
                showBack = showNavigationChrome
            ),
            onQueryChange = viewModel::updateSearchQuery,
            onMuscleToggle = viewModel::togglePreviewMuscleFilter,
            onSortChange = viewModel::updatePreviewSortMode,
            onSelectExercise = { exercise ->
                when (mode) {
                    PickerMode.SWAP -> viewModel.swapPlannedExercise(exercise.id)
                    PickerMode.ADD_TO_SUPERSET, PickerMode.BUILD_SUPERSET -> viewModel.toggleExerciseSelection(exercise)
                    PickerMode.BROWSE -> navController.navigateToWorkoutExercise(exercise.id)
                }
            },
            onAction = { action ->
                when (action) {
                    PickerAction.Back -> if (mode == PickerMode.BUILD_SUPERSET) viewModel.toggleSupersetMode() else navController.navigateBackOrHome()
                    PickerAction.ToggleSuperset -> viewModel.toggleSupersetMode()
                    PickerAction.StartSuperset -> if (viewModel.canStartSuperset()) navController.navigateToWorkoutExercise(viewModel.startSuperset())
                    PickerAction.ClearFilters -> viewModel.clearPreviewMuscleFilters()
                    PickerAction.SmartLog -> showSmartLog = true
                    is PickerAction.Create -> navController.navigate(
                        Screen.ExerciseForm.createRoute(
                            fromWorkout = isWorkout && mode == PickerMode.BROWSE,
                            initialName = action.name,
                            initialMuscle = picker.selectedMuscles.singleOrNull(),
                            returnToPicker = mode != PickerMode.BROWSE || !isWorkout
                        )
                    )
                    is PickerAction.Edit -> navController.navigate(Screen.ExerciseForm.createRoute(action.exercise.id))
                    is PickerAction.Delete -> exerciseToDelete = action.exercise
                    is PickerAction.TogglePr -> viewModel.toggleExerciseStarred(action.exercise)
                }
            }
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (showNavigationChrome) 8.dp else 104.dp))
    }
    exerciseToDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = { Text("Delete exercise?") },
            text = { Text("Delete “${exercise.name}” and all its logged history? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteExercise(exercise.id); exerciseToDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { exerciseToDelete = null }) { Text("Cancel") } }
        )
    }
    if (showSmartLog) {
        SmartLogBottomSheet(
            onDismiss = { showSmartLog = false },
            onNavigateToLogger = { id, token -> showSmartLog = false; navController.navigateToWorkoutExercise(id, token) },
            onCreateExercise = { name ->
                showSmartLog = false
                navController.navigate(
                    Screen.ExerciseForm.createRoute(
                        fromWorkout = true,
                        initialName = name,
                        initialMuscle = picker.selectedMuscles.singleOrNull()
                    )
                )
            },
            useThemeForegrounds = true
        )
    }
}
