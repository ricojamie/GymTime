package com.example.gymtime.ui.exercise

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.example.gymtime.data.PlateInventorySettings
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.data.db.entity.Set as WorkoutSet
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.navigation.navigateToWorkoutOverview
import com.example.gymtime.navigation.navigateToWorkoutSummary
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogRecessedWell
import com.example.gymtime.ui.components.IronLogSculptedKeycap
import com.example.gymtime.ui.components.IronLogTactileSwitch
import com.example.gymtime.ui.components.KeycapHaptic
import com.example.gymtime.ui.components.PlateCalculatorSheet
import com.example.gymtime.ui.exercise.preview.LoggerAction
import com.example.gymtime.ui.exercise.preview.LoggerField
import com.example.gymtime.ui.exercise.preview.LoggerFields
import com.example.gymtime.ui.exercise.preview.LoggerProgressSheet
import com.example.gymtime.ui.exercise.preview.PreviewLoggerContent
import com.example.gymtime.ui.exercise.preview.PreviewLoggerState
import com.example.gymtime.ui.exercise.preview.formatLoggerNumber
import com.example.gymtime.ui.exercise.preview.formatLoggerSet
import com.example.gymtime.ui.smartlog.SmartLogBottomSheet
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.theme.LocalIronHaptics
import com.example.gymtime.ui.theme.LocalIronLogSurfaces
import com.example.gymtime.ui.theme.OdometerText
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.TimeFormatter
import com.example.gymtime.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NoteEditTarget(
    val set: WorkoutSet,
    val displayLabel: String
)

private fun NavController.replaceExerciseLogger(exerciseId: Long, draftToken: String?) {
    navigateToWorkoutExercise(exerciseId, draftToken)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLoggingScreen(
    navController: NavController,
    viewModel: ExerciseLoggingViewModel = hiltViewModel()
) {
    ExerciseLoggingScreenBody(navController = navController, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseLoggingScreenBody(
    navController: NavController,
    viewModel: ExerciseLoggingViewModel
) {
    val scheme = MaterialTheme.colorScheme
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val currentWorkout by viewModel.currentWorkout.collectAsStateWithLifecycle()
    val loggedSets by viewModel.loggedSets.collectAsStateWithLifecycle()
    val weight by viewModel.weight.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val reps by viewModel.reps.collectAsStateWithLifecycle()
    val rpe by viewModel.rpe.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val distance by viewModel.distance.collectAsStateWithLifecycle()
    val selectedDistanceUnit by viewModel.selectedDistanceUnit.collectAsStateWithLifecycle()
    val setNote by viewModel.setNote.collectAsStateWithLifecycle()
    val restTime by viewModel.restTime.collectAsStateWithLifecycle()
    val isWarmup by viewModel.isWarmup.collectAsStateWithLifecycle()
    val isTimerRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val currentPlanItem by viewModel.currentPlanItem.collectAsStateWithLifecycle()
    val smartLogQueue by viewModel.smartLogQueue.collectAsStateWithLifecycle()
    val isPersistingSet by viewModel.isPersistingSet.collectAsStateWithLifecycle()
    val isCurrentInputValid by viewModel.isCurrentInputValidState.collectAsStateWithLifecycle()
    val inputValidationMessage by viewModel.currentInputValidationMessageState.collectAsStateWithLifecycle()
    val editingSet by viewModel.editingSet.collectAsStateWithLifecycle()
    val timerAutoStart by viewModel.timerAutoStart.collectAsStateWithLifecycle(initialValue = true)
    val isInSupersetMode by viewModel.isInSupersetMode.collectAsStateWithLifecycle()
    val supersetExercises by viewModel.supersetExercises.collectAsStateWithLifecycle()
    val nextExerciseId by viewModel.nextExerciseId.collectAsStateWithLifecycle()
    val previousExerciseId by viewModel.previousExerciseId.collectAsStateWithLifecycle()
    val progress by viewModel.previewProgress.collectAsStateWithLifecycle()
    val celebration by viewModel.prCelebration.collectAsStateWithLifecycle()

    var showFinishDialog by remember { mutableStateOf(false) }
    var finishRequested by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showExerciseHistory by remember { mutableStateOf(false) }
    var showPlateCalculator by remember { mutableStateOf(false) }
    var showExerciseNotes by remember { mutableStateOf(false) }
    var showSmartLog by remember { mutableStateOf(false) }
    var selectedSetToDelete by remember { mutableStateOf<WorkoutSet?>(null) }
    var setToAddNote by remember { mutableStateOf<NoteEditTarget?>(null) }
    var noteText by remember { mutableStateOf("") }
    var showExerciseQuickEdit by remember { mutableStateOf(false) }

    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(viewModel) {
        viewModel.startWearPublishing()
        onDispose {
            viewModel.stopWearPublishing()
        }
    }

    fun navigateToLoggerExercise(
        targetExerciseId: Long,
        draftTokenOverride: String? = viewModel.smartLogDraftToken
    ) {
        if (targetExerciseId == exercise?.id) {
            if (!draftTokenOverride.isNullOrBlank() && draftTokenOverride != viewModel.smartLogDraftToken) {
                viewModel.applySmartLogDraft(draftTokenOverride)
            }
            return
        }
        if (isInSupersetMode) {
            viewModel.selectSupersetExercise(targetExerciseId)
        }
        navController.replaceExerciseLogger(targetExerciseId, draftTokenOverride)
    }

    LaunchedEffect(viewModel, navController, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.navigationEvents.collect { workoutId ->
                    navController.navigateToWorkoutSummary(workoutId)
                }
            }
            launch {
                viewModel.autoSwitchEvent.collect { nextId ->
                    navigateToLoggerExercise(nextId)
                    haptics.confirm()
                }
            }
        }
    }

    LaunchedEffect(celebration?.id, viewModel) {
        val event = celebration ?: return@LaunchedEffect
        val remaining = event.expiresAt - SystemClock.elapsedRealtime()
        if (remaining > 0) {
            if (viewModel.claimPrCelebrationHaptic(event.id)) {
                haptics.confirm()
            }
            delay(remaining)
        }
        viewModel.dismissPrCelebration(event.id)
    }

    val submitSet: () -> Unit = {
        haptics.confirm()
        if (editingSet != null) viewModel.saveEditedSet() else viewModel.logSet(startTimerAfterSave = timerAutoStart)
    }

    val changeField: (LoggerField, String) -> Unit = { field, value ->
        when (field) {
            LoggerField.WEIGHT -> viewModel.updateWeight(value)
            LoggerField.REPS -> viewModel.updateReps(value)
            LoggerField.DURATION -> viewModel.updateDuration(value)
            LoggerField.DISTANCE -> viewModel.updateDistance(value)
            LoggerField.CALORIES -> viewModel.updateCalories(value)
            LoggerField.RPE -> viewModel.updateRpe(value)
            LoggerField.NOTE -> viewModel.updateSetNote(value)
        }
    }

    val fields = remember(weight, reps, duration, distance, calories, rpe, setNote, selectedDistanceUnit) {
        LoggerFields(weight, reps, duration, distance, calories, rpe, setNote, selectedDistanceUnit)
    }
    val bestSummary = remember(progress.isLoading, progress.allTimeBest, exercise?.logType) {
        if (progress.isLoading) "Loading…"
        else progress.allTimeBest?.let { formatLoggerSet(it, exercise?.logType ?: LogType.WEIGHT_REPS) }
            ?: "No working sets yet"
    }
    val lastSummary = remember(progress.isLoading, progress.lastWorkoutBest, exercise?.logType) {
        if (progress.isLoading) "Loading…"
        else progress.lastWorkoutBest?.let { formatLoggerSet(it, exercise?.logType ?: LogType.WEIGHT_REPS) }
            ?: "No prior working set"
    }
    val planSummary = remember(currentPlanItem) {
        currentPlanItem?.let { plan ->
            buildList {
                plan.plannedSets?.let { add("$it planned sets") }
                plan.repMin?.let {
                    add(if (plan.repMax != null && plan.repMax != it) "$it–${plan.repMax} reps" else "$it reps")
                }
                plan.notes?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ").takeIf { it.isNotBlank() }
        }
    }
    val activeSuperset = remember(isInSupersetMode, supersetExercises) {
        if (isInSupersetMode) supersetExercises else emptyList()
    }
    val smartQueueLabel = remember(smartLogQueue) {
        smartLogQueue?.let { "Smart Log ${it.currentSetNumber} of ${it.totalSets}" }
    }
    val activeCelebration = remember(celebration) {
        celebration?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }
    }

    PreviewLoggerContent(
        state = PreviewLoggerState(
            exercise = exercise,
            fields = fields,
            sets = loggedSets,
            best = bestSummary,
            last = lastSummary,
            bestLabel = progress.recordLabel,
            plan = planSummary,
            isWarmup = isWarmup,
            editingSetId = editingSet?.id,
            canLog = isCurrentInputValid,
            saving = isPersistingSet,
            validation = inputValidationMessage,
            restSeconds = restTime,
            remainingSeconds = 0,
            timerRunning = isTimerRunning,
            hasPrevious = previousExerciseId != null,
            hasNext = nextExerciseId != null,
            superset = activeSuperset,
            smartQueue = smartQueueLabel,
            personalBestSetIds = progress.setRecordLabels.keys,
            recordLabels = progress.setRecordLabels,
            workoutId = currentWorkout?.id,
            celebration = activeCelebration
        ),
        remainingSecondsFlow = viewModel.countdownTimer,
        onFieldChange = changeField,
        onDistanceUnitChange = viewModel::updateSelectedDistanceUnit,
        onSelectExercise = { navigateToLoggerExercise(it) },
        onEditSet = viewModel::startEditingSet,
        onDeleteSet = { selectedSetToDelete = it },
        onSetNote = { selectedSet ->
            noteText = selectedSet.note.orEmpty()
            val displayLabel = if (selectedSet.isWarmup) {
                val warmupNumber = loggedSets.asSequence()
                    .filter { it.isWarmup }
                    .indexOfFirst { it.id == selectedSet.id } + 1
                if (warmupNumber > 0) "Warm-up $warmupNumber" else "Warm-up"
            } else {
                val workingNumber = loggedSets.asSequence()
                    .filterNot { it.isWarmup }
                    .indexOfFirst { it.id == selectedSet.id } + 1
                if (workingNumber > 0) "Set $workingNumber" else "Set"
            }
            setToAddNote = NoteEditTarget(selectedSet, displayLabel)
        },
        onAction = { action ->
            when (action) {
                LoggerAction.BACK -> navController.navigateBackOrHome()
                LoggerAction.HOME -> navController.navigateHomeAndClearStack()
                LoggerAction.FINISH -> {
                    finishRequested = false
                    showFinishDialog = true
                }
                LoggerAction.PREVIOUS -> previousExerciseId?.let { navigateToLoggerExercise(it) }
                LoggerAction.NEXT -> nextExerciseId?.let { navigateToLoggerExercise(it) }
                LoggerAction.ADD_EXERCISE -> navController.navigateResumed(
                    Screen.ExerciseSelection.createRoute(workoutMode = true)
                )
                LoggerAction.OVERVIEW -> navController.navigateToWorkoutOverview()
                LoggerAction.HISTORY -> showExerciseHistory = true
                LoggerAction.PLATES -> showPlateCalculator = true
                LoggerAction.NOTES -> showExerciseNotes = true
                LoggerAction.EDIT_EXERCISE -> showExerciseQuickEdit = true
                LoggerAction.THEME -> navController.navigateResumed(Screen.ThemeSettings.route)
                LoggerAction.SUPERSET -> navController.navigateResumed(
                    if (isInSupersetMode) {
                        Screen.ExerciseSelection.createRoute(workoutMode = true, addToSuperset = true)
                    } else {
                        Screen.ExerciseSelection.createRoute(
                            workoutMode = true,
                            supersetMode = true,
                            adHocParentId = exercise?.id
                        )
                    }
                )
                LoggerAction.EXIT_SUPERSET -> viewModel.exitSupersetMode()
                LoggerAction.SMART_LOG -> showSmartLog = true
                LoggerAction.CANCEL_SMART_LOG -> viewModel.cancelSmartLogQueue()
                LoggerAction.TOGGLE_WARMUP -> viewModel.toggleWarmup()
                LoggerAction.LOG -> submitSet()
                LoggerAction.CANCEL_EDIT -> viewModel.cancelEditing()
                LoggerAction.DISMISS_PR -> celebration?.let { viewModel.dismissPrCelebration(it.id) }
                LoggerAction.TIMER -> showTimerDialog = true
                LoggerAction.START_TIMER -> viewModel.startTimer()
                LoggerAction.SKIP_TIMER -> viewModel.stopTimer()
                LoggerAction.ADD_REST -> viewModel.adjustRestTime(30)
            }
        }
    )

    if (showExerciseHistory) {
        LoggerProgressSheet(
            exerciseName = exercise?.name.orEmpty(),
            logType = exercise?.logType ?: LogType.WEIGHT_REPS,
            state = progress,
            onDismiss = { showExerciseHistory = false }
        )
    }

    if (showTimerDialog) {
        RestTimerDialog(
            viewModel = viewModel,
            restTime = restTime,
            isTimerRunning = isTimerRunning,
            onDismiss = { showTimerDialog = false }
        )
    }

    selectedSetToDelete?.let { setToDelete ->
        AlertDialog(
            onDismissRequest = { selectedSetToDelete = null },
            shape = IronLogRadius.PlateShape,
            containerColor = surfaces.plateElevatedTop,
            title = {
                Text(
                    text = "Delete Set?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this set? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSet(setToDelete.id)
                        selectedSetToDelete = null
                    },
                    shape = IronLogRadius.ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSetToDelete = null }) {
                    Text("Cancel", color = scheme.onSurfaceVariant)
                }
            }
        )
    }

    setToAddNote?.let { target ->
        AlertDialog(
            onDismissRequest = {
                setToAddNote = null
                noteText = ""
            },
            shape = IronLogRadius.PlateShape,
            containerColor = surfaces.plateElevatedTop,
            title = {
                Text(
                    text = if (target.set.note.isNullOrBlank()) "Add Note" else "Edit Note",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = buildSetSummary(target.set, selectedDistanceUnit, target.displayLabel),
                        style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Enter note...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = IronLogRadius.ControlShape,
                        colors = loggerTextFieldColors(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSetNote(target.set, noteText)
                        setToAddNote = null
                        noteText = ""
                    },
                    shape = IronLogRadius.ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        setToAddNote = null
                        noteText = ""
                    }
                ) {
                    Text("Cancel", color = scheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showExerciseQuickEdit) {
        exercise?.let { ex ->
            // Seeded on dialog-open (this block leaves composition when the flag
            // flips false); the exercise flow re-emits after save and must not
            // clobber in-progress typing.
            var editName by remember { mutableStateOf(ex.name) }
            var editRest by remember { mutableStateOf(ex.defaultRestSeconds.toString()) }
            var editNotes by remember { mutableStateOf(ex.notes.orEmpty()) }
            val fieldColors = loggerTextFieldColors()

            AlertDialog(
                onDismissRequest = { showExerciseQuickEdit = false },
                shape = IronLogRadius.PlateShape,
                containerColor = surfaces.plateElevatedTop,
                title = {
                    Text(
                        text = "Edit Exercise",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            shape = IronLogRadius.ControlShape,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = editRest,
                            onValueChange = { new -> editRest = new.filter(Char::isDigit).take(4) },
                            label = { Text("Default rest (seconds)") },
                            singleLine = true,
                            shape = IronLogRadius.ControlShape,
                            textStyle = IronLogType.tabular(MaterialTheme.typography.bodyLarge),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = editNotes,
                            onValueChange = { editNotes = it },
                            label = { Text("Notes") },
                            maxLines = 3,
                            shape = IronLogRadius.ControlShape,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateExerciseDetails(
                                name = editName,
                                restSeconds = editRest.toIntOrNull() ?: ex.defaultRestSeconds,
                                notes = editNotes
                            )
                            showExerciseQuickEdit = false
                        },
                        enabled = editName.isNotBlank(),
                        shape = IronLogRadius.ControlShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExerciseQuickEdit = false }) {
                        Text("Cancel", color = scheme.onSurfaceVariant)
                    }
                }
            )
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { if (!finishRequested) showFinishDialog = false },
            shape = IronLogRadius.PlateShape,
            containerColor = surfaces.plateElevatedTop,
            title = {
                Text(
                    text = "Finish Workout?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to finish this workout session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!finishRequested) {
                            finishRequested = true
                            showFinishDialog = false
                            viewModel.finishWorkout()
                        }
                    },
                    enabled = !finishRequested,
                    shape = IronLogRadius.ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text("Finish", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Cancel", color = scheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPlateCalculator) {
        val barWeight by viewModel.barWeight.collectAsStateWithLifecycle(initialValue = 45f)
        val availablePlates by viewModel.availablePlates.collectAsStateWithLifecycle(
            initialValue = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f)
        )
        val loadingSides by viewModel.loadingSides.collectAsStateWithLifecycle(initialValue = 2)
        val plateInventorySettings by viewModel.plateInventorySettings.collectAsStateWithLifecycle(
            initialValue = PlateInventorySettings(counts = emptyMap(), enabled = false)
        )
        PlateCalculatorSheet(
            initialWeight = weight.toFloatOrNull() ?: 0f,
            barWeight = barWeight,
            availablePlates = availablePlates,
            loadingSides = loadingSides,
            plateInventory = plateInventorySettings.counts,
            usePlateInventory = plateInventorySettings.enabled,
            onPlateInventoryCountChange = viewModel::setPlateInventoryCount,
            onUsePlateInventoryChange = viewModel::setUsePlateInventory,
            onPlateInventoryCountDelta = viewModel::adjustPlateInventoryCount,
            onDismiss = { showPlateCalculator = false },
            onNavigateToSettings = {
                showPlateCalculator = false
                navController.navigateResumed(Screen.Settings.route)
            },
            onUseWeight = { newWeight ->
                viewModel.updateWeight(PlateCalculator.formatWeight(newWeight))
                showPlateCalculator = false
            }
        )
    }

    if (showSmartLog) {
        val allowedExerciseIds = remember(isInSupersetMode, supersetExercises) {
            if (isInSupersetMode) supersetExercises.map { it.id }.toSet() else null
        }
        SmartLogBottomSheet(
            currentExercise = exercise,
            allowedExerciseIds = allowedExerciseIds,
            onDismiss = { showSmartLog = false },
            onNavigateToLogger = { targetExerciseId, token ->
                showSmartLog = false
                if (targetExerciseId == exercise?.id) {
                    viewModel.applySmartLogDraft(token)
                } else {
                    navigateToLoggerExercise(targetExerciseId, token)
                }
            },
            onCreateExercise = { name ->
                showSmartLog = false
                viewModel.prepareForSmartLogExerciseCreation(
                    restrictedToSuperset = isInSupersetMode && supersetExercises.isNotEmpty()
                )
                navController.navigateResumed(
                    Screen.ExerciseForm.createRoute(fromWorkout = true, initialName = name)
                )
            }
        )
    }

    if (showExerciseNotes) {
        AlertDialog(
            onDismissRequest = { showExerciseNotes = false },
            shape = IronLogRadius.PlateShape,
            containerColor = surfaces.plateElevatedTop,
            title = {
                Text(
                    text = "Exercise Notes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            },
            text = {
                Text(
                    text = exercise?.notes?.takeIf { it.isNotBlank() }
                        ?: "Add a setup cue or reminder for this exercise.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showExerciseNotes = false }) {
                    Text("Close", color = scheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExerciseNotes = false
                        showExerciseQuickEdit = true
                    }
                ) {
                    Text("Edit notes")
                }
            }
        )
    }
}

@Composable
private fun RestTimerDialog(
    viewModel: ExerciseLoggingViewModel,
    restTime: Int,
    isTimerRunning: Boolean,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = LocalIronLogSurfaces.current
    val haptics = LocalIronHaptics.current
    val countdownTimer by viewModel.countdownTimer.collectAsStateWithLifecycle()
    val timerAudioEnabled by viewModel.timerAudioEnabled.collectAsStateWithLifecycle(initialValue = true)
    val timerVibrateEnabled by viewModel.timerVibrateEnabled.collectAsStateWithLifecycle(initialValue = true)
    val displayedSeconds = if (isTimerRunning) countdownTimer else restTime

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = IronLogRadius.PlateShape,
        containerColor = surfaces.plateElevatedTop,
        title = {
            Text(
                text = "Rest Timer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                IronLogRecessedWell(
                    active = isTimerRunning,
                    accentColor = surfaces.accent,
                    shape = IronLogRadius.KeycapShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    OdometerText(
                        text = TimeFormatter.formatSecondsToMMSS(displayedSeconds),
                        style = IronLogType.monumentalNumeral(MaterialTheme.typography.displayLarge).copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isTimerRunning) surfaces.accent else scheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)) {
                    IronLogSculptedKeycap(
                        onClick = { viewModel.adjustRestTime(-5) },
                        hapticType = KeycapHaptic.TICK,
                        shape = IronLogRadius.KeycapShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "-5s",
                            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge).copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IronLogSculptedKeycap(
                        onClick = { viewModel.adjustRestTime(5) },
                        hapticType = KeycapHaptic.TICK,
                        shape = IronLogRadius.KeycapShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "+5s",
                            style = IronLogType.telemetryLabel(MaterialTheme.typography.labelLarge).copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                IronLogDivider(insetStart = 0.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (timerAudioEnabled) surfaces.accent else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    }
                    IronLogTactileSwitch(
                        checked = timerAudioEnabled,
                        onCheckedChange = viewModel::toggleTimerAudio,
                        accentColor = surfaces.accent
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = null,
                            tint = if (timerVibrateEnabled) surfaces.accent else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vibration", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                    }
                    IronLogTactileSwitch(
                        checked = timerVibrateEnabled,
                        onCheckedChange = viewModel::toggleTimerVibrate,
                        accentColor = surfaces.accent
                    )
                }
            }
        },
        confirmButton = {
            if (isTimerRunning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            viewModel.stopTimer()
                        },
                        shape = IronLogRadius.ControlShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary)
                    ) {
                        Text("Stop")
                    }
                    Button(
                        onClick = {
                            haptics.keyTap()
                            viewModel.stopTimer()
                            viewModel.startTimer()
                            onDismiss()
                        },
                        shape = IronLogRadius.ControlShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary
                        )
                    ) {
                        Text("Restart", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        haptics.keyTap()
                        viewModel.startTimer()
                        onDismiss()
                    },
                    shape = IronLogRadius.ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text("Start Timer", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        viewModel.resetTimerToDefault()
                    }
                ) {
                    Text("Reset", color = scheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = scheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
private fun loggerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
)

@Preview(showBackground = true)
@Composable
private fun ExerciseLoggingScreenPreview() {
    IronLogTheme {
        PreviewLoggerContent(
            state = PreviewLoggerState(
                exercise = null
            ),
            onAction = {},
            onFieldChange = { _, _ -> },
            onDistanceUnitChange = {},
            onSelectExercise = {},
            onEditSet = {},
            onDeleteSet = {},
            onSetNote = {}
        )
    }
}

private val DistanceUnit.shortLabel: String
    get() = when (this) {
        DistanceUnit.METERS -> "M"
        DistanceUnit.KILOMETERS -> "KM"
        DistanceUnit.YARDS -> "YD"
        DistanceUnit.FEET -> "FT"
        DistanceUnit.MILES -> "MI"
        DistanceUnit.STEPS -> "STEPS"
        DistanceUnit.FLOORS -> "FLOORS"
    }

private fun buildSetSummary(
    set: WorkoutSet,
    fallbackDistanceUnit: DistanceUnit,
    displayLabel: String
): String {
    val parts = mutableListOf<String>()

    set.weight?.let { parts += "${formatLoggerNumber(it)} lbs" }
    set.calories?.let { parts += "${formatLoggerNumber(it)} cal" }
    set.reps?.let { parts += "$it reps" }

    val unit = set.distanceUnit ?: fallbackDistanceUnit
    val distanceValue = when {
        set.distanceValue != null -> set.distanceValue
        set.distanceMeters != null && unit.isConvertibleToMeters -> {
            TimeUtils.metersToDistance(set.distanceMeters, unit)
        }
        else -> null
    }
    distanceValue?.let {
        parts += "${TimeUtils.formatDistance(it, unit)} ${unit.shortLabel.lowercase()}"
    }

    set.durationSeconds?.let { parts += TimeUtils.formatSecondsToHMS(it) }

    return "$displayLabel: " + if (parts.isEmpty()) "No data" else parts.joinToString(" × ")
}
