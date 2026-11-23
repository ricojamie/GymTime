package com.example.gymtime.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.ui.settings.preview.PreviewMuscleContent
import com.example.gymtime.ui.settings.preview.PreviewMuscleDelete
import com.example.gymtime.ui.settings.preview.PreviewMuscleRow
import com.example.gymtime.ui.settings.preview.PreviewMuscleUiState

@Composable
fun MuscleGroupManagementScreen(
    navController: NavController,
    viewModel: MuscleGroupManagementViewModel = hiltViewModel()
) {
    val muscleGroups by viewModel.muscleGroups.collectAsStateWithLifecycle()
    val editingMuscle by viewModel.editingMuscle.collectAsStateWithLifecycle()
    val muscleNameInput by viewModel.muscleNameInput.collectAsStateWithLifecycle()
    val validationError by viewModel.validationError.collectAsStateWithLifecycle()
    val deleteCheckResult by viewModel.deleteCheckResult.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()

    PreviewMuscleContent(
        state = PreviewMuscleUiState(
            groups = muscleGroups.map { PreviewMuscleRow(it.name, it.name.isWarmupMuscleGroup()) },
            editingOriginalName = editingMuscle,
            nameDraft = muscleNameInput,
            validationError = validationError,
            deletion = deleteCheckResult?.let { (name, result) ->
                when (result) {
                    DeleteCheckResult.CanDelete -> PreviewMuscleDelete(name)
                    is DeleteCheckResult.HasExercises -> PreviewMuscleDelete(name, exerciseCount = result.exerciseCount)
                    is DeleteCheckResult.BlockedByLoggedSets -> PreviewMuscleDelete(name, loggedSetCount = result.setCount)
                }
            },
            saving = isSaving,
            deleting = isDeleting
        ),
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack,
        onAdd = viewModel::startAddNew,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::checkCanDelete,
        onNameChange = viewModel::updateMuscleNameInput,
        onSave = viewModel::saveMuscleGroup,
        onDismissEditor = viewModel::clearDialog,
        onConfirmDelete = {
            deleteCheckResult?.let { (name, result) ->
                when (result) {
                    DeleteCheckResult.CanDelete -> viewModel.deleteMuscleGroup(name)
                    is DeleteCheckResult.HasExercises -> viewModel.deleteWithExercises(name)
                    is DeleteCheckResult.BlockedByLoggedSets -> Unit
                }
            }
        },
        onDismissDelete = viewModel::clearDeleteDialog
    )
}
