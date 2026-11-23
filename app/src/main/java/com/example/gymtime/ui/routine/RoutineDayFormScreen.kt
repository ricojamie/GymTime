package com.example.gymtime.ui.routine

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.routine.preview.PreviewRoutineDayFormContent
import com.example.gymtime.ui.routine.preview.RoutineDayFormActions
import com.example.gymtime.ui.routine.preview.RoutineDayFormUiState

@Composable
fun RoutineDayFormScreen(
    navController: NavController,
    viewModel: RoutineDayFormViewModel = hiltViewModel()
) {
    val dayName by viewModel.dayName.collectAsStateWithLifecycle()
    val selectedExercises by viewModel.selectedExercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val availableExercises by viewModel.availableExercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedExerciseIds by viewModel.selectedExerciseIds.collectAsStateWithLifecycle()
    val targetSets by viewModel.targetSets.collectAsStateWithLifecycle()
    val targetRepMin by viewModel.targetRepMin.collectAsStateWithLifecycle()
    val targetRepMax by viewModel.targetRepMax.collectAsStateWithLifecycle()
    val targetRestSeconds by viewModel.targetRestSeconds.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val supersetLinks by viewModel.supersetLinks.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val pickerMuscle by viewModel.pickerMuscle.collectAsStateWithLifecycle()

    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    val navigationActions = rememberGuardedNavigationActions(
        hasUnsavedChanges = hasUnsavedChanges,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack
    )

    BackHandler(enabled = isSaving) { /* Prevent leaving while the complete day is being saved. */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, navController, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.saveSuccessEvent.collect {
                navController.navigateUp()
            }
        }
    }

    PreviewRoutineDayFormContent(
        state = RoutineDayFormUiState(
            name = dayName,
            editing = isEditMode,
            loading = isLoading,
            saving = isSaving,
            canEdit = canEdit,
            canSave = isSaveEnabled,
            error = error,
            exercises = selectedExercises,
            availableExercises = availableExercises,
            selectedIds = selectedExerciseIds,
            sets = targetSets,
            repMin = targetRepMin,
            repMax = targetRepMax,
            rest = targetRestSeconds,
            notes = notes,
            links = supersetLinks,
            pickerOpen = showExercisePicker,
            pickerQuery = pickerQuery,
            pickerMuscle = pickerMuscle
        ),
        actions = RoutineDayFormActions(
            name = viewModel::updateDayName,
            sets = viewModel::updateTargetSets,
            repMin = viewModel::updateTargetRepMin,
            repMax = viewModel::updateTargetRepMax,
            rest = viewModel::updateTargetRestSeconds,
            notes = viewModel::updateExerciseNotes,
            move = viewModel::moveExercise,
            remove = viewModel::removeExercise,
            link = viewModel::toggleSupersetLink,
            toggleExercise = viewModel::toggleExercise,
            pickerQuery = viewModel::updatePickerQuery,
            pickerMuscle = viewModel::updatePickerMuscle,
            openPicker = { showExercisePicker = true },
            closePicker = { showExercisePicker = false },
            save = viewModel::saveDay,
            retry = viewModel::retryLoad,
            back = navigationActions.back,
            home = navigationActions.home
        )
    )
}
