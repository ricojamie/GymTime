package com.example.gymtime.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
import com.example.gymtime.ui.workout.preview.CurrentWorkoutAction
import com.example.gymtime.ui.workout.preview.PreviewCurrentWorkoutContent
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WorkoutResumeScreen(
    viewModel: WorkoutResumeViewModel = hiltViewModel(),
    onExerciseClick: (Long) -> Unit,
    onAddExerciseClick: () -> Unit,
    onSwapExerciseClick: (Long) -> Unit,
    onFinishWorkoutClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val latestFinishClick by rememberUpdatedState(onFinishWorkoutClick)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.finishWorkoutEvent.collect { workoutId -> latestFinishClick(workoutId) }
            }
            launch {
                viewModel.planEditMessage.collect { message -> snackbarHostState.showSnackbar(message) }
            }
        }
    }

    val state by viewModel.previewState.collectAsStateWithLifecycle()
    PreviewCurrentWorkoutContent(
        state = state,
        onAction = { action ->
            when (action) {
                CurrentWorkoutAction.Back -> onBackClick()
                CurrentWorkoutAction.Home -> onHomeClick()
                CurrentWorkoutAction.AddExercise -> onAddExerciseClick()
                CurrentWorkoutAction.Finish -> viewModel.finishWorkout()
                CurrentWorkoutAction.ClearFinishError -> viewModel.clearFinishError()
                is CurrentWorkoutAction.OpenExercise -> onExerciseClick(action.exerciseId)
                is CurrentWorkoutAction.SwapExercise -> onSwapExerciseClick(action.instanceId)
                is CurrentWorkoutAction.RemoveExercise -> viewModel.removePlannedExercise(action.instanceId)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    )
}

@Composable
internal fun ExerciseSummaryCard(
    exercise: ResumeExerciseItem,
    onClick: () -> Unit,
    onSwap: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isWarmupLibraryExercise = exercise.targetMuscle.isWarmupMuscleGroup()
    val displayedEntryCount = if (isWarmupLibraryExercise) exercise.anySetCount else exercise.setCount
    val isUnstarted = exercise.anySetCount == 0 && !exercise.isSkipped
    val isWarmupOnly = !isWarmupLibraryExercise && exercise.anySetCount > 0 && displayedEntryCount == 0 && !exercise.isSkipped
    var showActions by remember { mutableStateOf(false) }

    IronLogCard(
        onClick = onClick,
        highlighted = !isUnstarted,
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isUnstarted) FontWeight.Medium else FontWeight.Bold,
                    color = if (isUnstarted) scheme.onSurfaceVariant else scheme.onSurface
                )
                Text(
                    text = exercise.targetMuscle.uppercase(),
                    style = IronLogType.sectionEyebrow(),
                    color = scheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                when {
                    isUnstarted -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Not started",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                            exercise.plannedSets?.let { sets ->
                                Text(
                                    text = buildString {
                                        append("$sets sets")
                                        if (exercise.repMin != null) {
                                            append(" · ")
                                            append(exercise.repMin)
                                            if (exercise.repMax != null && exercise.repMax != exercise.repMin) {
                                                append("-${exercise.repMax}")
                                            }
                                            append(" reps")
                                        }
                                    },
                                    style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                                    color = scheme.primary
                                )
                            }
                        }
                    }
                    isWarmupOnly -> {
                        IronLogStatusChip(
                            label = "Warming up",
                            tone = IronLogStatusTone.WARMUP
                        )
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isWarmupLibraryExercise) {
                                    "$displayedEntryCount ${if (displayedEntryCount == 1) "entry" else "entries"}"
                                } else {
                                    "$displayedEntryCount sets"
                                },
                                style = IronLogType.tabular(MaterialTheme.typography.titleSmall),
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.primary
                            )
                            exercise.bestWeight?.let { weight ->
                                val weightLabel = if (weight % 1f == 0f) {
                                    weight.toInt().toString()
                                } else {
                                    String.format(Locale.getDefault(), "%.1f", weight)
                                }
                                Text(
                                    text = "Best: $weightLabel lbs",
                                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (exercise.anySetCount == 0 && onSwap != null && onRemove != null) {
                    Box {
                        IconButton(
                            onClick = { showActions = true },
                            modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Exercise actions for ${exercise.exerciseName}",
                                tint = scheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Swap exercise") },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showActions = false
                                    onSwap()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from today") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showActions = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutResumeScreenPreview() {
    IronLogTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(IronLogSpace.ScreenHorizontal)
        ) {
            ExerciseSummaryCard(
                exercise = ResumeExerciseItem(
                    exerciseId = 1,
                    exerciseName = "Barbell Bench Press",
                    targetMuscle = "Chest",
                    setCount = 4,
                    bestWeight = 225f,
                    supersetGroupId = null,
                    orderIndex = 0
                ),
                onClick = {}
            )
        }
    }
}
