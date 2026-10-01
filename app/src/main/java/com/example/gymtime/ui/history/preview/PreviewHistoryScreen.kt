package com.example.gymtime.ui.history.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.navigation.navigateToWorkoutOverview
import com.example.gymtime.ui.history.AddToRoutineDialog
import com.example.gymtime.ui.history.HistoryViewModel
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.util.ShareImagePalette

/** App effects and data live here; the timeline and sheet only receive values and callbacks. */
@Composable
fun PreviewHistoryScreen(navController: NavController, viewModel: HistoryViewModel) {
    val workouts by viewModel.allWorkouts.collectAsStateWithLifecycle()
    val selectedWorkout by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val selectedSets by viewModel.selectedWorkoutDetails.collectAsStateWithLifecycle()
    val detailError by viewModel.detailError.collectAsStateWithLifecycle()
    val historyError by viewModel.historyError.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val actionColors = LocalLoggerActionColors.current
    val sharePalette = remember(scheme, actionColors) {
        ShareImagePalette(
            background = scheme.background.toArgb(), card = scheme.surface.toArgb(),
            accent = actionColors.fill.toArgb(), accentSoft = scheme.primaryContainer.toArgb(),
            textPrimary = scheme.onSurface.toArgb(), textMuted = scheme.onSurfaceVariant.toArgb(),
            onAccent = actionColors.onFill.toArgb()
        )
    }
    var addToRoutineWorkoutId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(viewModel, navController) {
        viewModel.resumeWorkoutEvent.collect { navController.navigateToWorkoutOverview() }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.repeatWorkoutEvent.collect { navController.navigateToWorkoutExercise(it) }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.userMessage.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.shareEvent.collect { payload ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, payload.imageUri)
                putExtra(Intent.EXTRA_TEXT, payload.text)
                clipData = ClipData.newUri(context.contentResolver, "Workout summary", payload.imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share workout"))
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.copyEvent.collect { summary ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Workout summary", summary))
            Toast.makeText(context, "Workout copied", Toast.LENGTH_SHORT).show()
        }
    }
    PreviewHistoryContent(
        state = HistoryUiState(workouts, isLoading, historyError),
        onSelectWorkout = viewModel::selectWorkout,
        onRetry = viewModel::retryLoadWorkouts,
        onStartWorkout = { navController.navigate(Screen.ExerciseSelection.createRoute(workoutMode = true)) }
    )
    selectedWorkout?.let { selected ->
        PreviewWorkoutDetailsSheet(
            workout = selected, sets = selectedSets, error = detailError,
            onDismiss = viewModel::clearSelection,
            onRetry = { viewModel.selectWorkout(selected) },
            onResume = { viewModel.resumeWorkout(selected.workout.id) },
            onRepeat = { viewModel.repeatWorkout(selected.workout.id) },
            onCopy = { viewModel.copyWorkout(selected.workout.id) },
            onShare = { viewModel.shareWorkout(selected.workout.id, sharePalette) },
            onAddToRoutine = { addToRoutineWorkoutId = selected.workout.id },
            onDelete = { viewModel.deleteWorkout(selected.workout.id) }
        )
    }
    addToRoutineWorkoutId?.let { workoutId ->
        val routines by viewModel.allRoutines.collectAsStateWithLifecycle()
        AddToRoutineDialog(
            routines = routines, loadDays = viewModel::getDaysForRoutine,
            onConfirm = { routineId, replaceDayId ->
                viewModel.addWorkoutToRoutine(workoutId, routineId, replaceDayId)
                addToRoutineWorkoutId = null
            },
            onDismiss = { addToRoutineWorkoutId = null },
            onAccentColor = scheme.onPrimary
        )
    }
}
