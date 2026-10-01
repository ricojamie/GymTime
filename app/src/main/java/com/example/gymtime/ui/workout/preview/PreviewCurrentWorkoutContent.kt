package com.example.gymtime.ui.workout.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.LoggerPreviewTheme
import com.example.gymtime.ui.workout.ResumeExerciseItem
import kotlinx.coroutines.delay
import java.util.Locale

/** App wiring owns the session and persistence. This value also works in standalone previews. */
data class CurrentWorkoutUiState(
    val workoutId: Long? = null,
    val name: String = "Today's workout",
    val routineName: String? = null,
    val startedAtMs: Long? = null,
    val exercises: List<ResumeExerciseItem> = emptyList(),
    val workingSets: Int = 0,
    val startedExercises: Int = 0,
    val warmupEntries: Int = 0,
    val plannedWorkingSets: Int = 0,
    val completedPlannedSets: Int = 0,
    val completedTargets: Int = 0,
    val targetExercises: Int = 0,
    val continueExercise: ResumeExerciseItem? = null,
    val hasNextTarget: Boolean = false,
    val isLoading: Boolean = true,
    val isFinishing: Boolean = false,
    val finishError: String? = null
)

sealed interface CurrentWorkoutAction {
    data object Back : CurrentWorkoutAction
    data object Home : CurrentWorkoutAction
    data object AddExercise : CurrentWorkoutAction
    data object Finish : CurrentWorkoutAction
    data class OpenExercise(val exerciseId: Long) : CurrentWorkoutAction
    data class SwapExercise(val instanceId: Long) : CurrentWorkoutAction
    data class RemoveExercise(val instanceId: Long) : CurrentWorkoutAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewCurrentWorkoutContent(
    state: CurrentWorkoutUiState,
    onAction: (CurrentWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val actionColors = LocalLoggerActionColors.current
    val supersetIds = remember(state.exercises) { state.exercises.mapNotNull { it.supersetGroupId }.distinct() }
    var showFinish by rememberSaveable(state.workoutId) { mutableStateOf(false) }
    var removeInstanceId by rememberSaveable(state.workoutId) { mutableStateOf<Long?>(null) }
    val removeExercise = state.exercises.firstOrNull { it.instanceId == removeInstanceId && removeInstanceId != null }
    var clockMs by remember(state.startedAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    // Elapsed time is display-only state and follows the visible session, not a VM timer job.
    LaunchedEffect(state.startedAtMs) {
        if (state.startedAtMs != null) {
            while (true) {
                clockMs = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }
    val elapsedMinutes = state.startedAtMs?.let { ((clockMs - it).coerceAtLeast(0) / 60_000).toInt() }
    val progress by animateFloatAsState(
        targetValue = if (state.plannedWorkingSets > 0) {
            (state.completedPlannedSets.toFloat() / state.plannedWorkingSets).coerceIn(0f, 1f)
        } else 0f,
        label = "Workout plan progress"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Current workout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(CurrentWorkoutAction.Back) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onAction(CurrentWorkoutAction.Home) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Home, "Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = {
            Surface(color = colors.surface, tonalElevation = 0.dp, shadowElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onAction(CurrentWorkoutAction.AddExercise) },
                        enabled = !state.isFinishing,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add exercise", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { showFinish = true },
                        enabled = state.workoutId != null && !state.isLoading && !state.isFinishing,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = actionColors.fill, contentColor = actionColors.onFill),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        if (state.isFinishing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = actionColors.onFill)
                        } else Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isFinishing) "Finishing…" else "Finish", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "session-header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            state.routineName?.takeIf { it != state.name }?.let {
                                Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            }
                            Text(state.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        elapsedMinutes?.let { minutes ->
                            Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, null, Modifier.size(16.dp), tint = colors.onSecondaryContainer)
                                    Spacer(Modifier.width(5.dp))
                                    Text(formatElapsed(minutes), style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer)
                                }
                            }
                        }
                    }
                    SessionStats(state)
                    if (state.plannedWorkingSets > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Plan progress", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                                Text("${state.completedPlannedSets} / ${state.plannedWorkingSets} working sets", style = MaterialTheme.typography.labelMedium)
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = colors.primary,
                                trackColor = colors.surfaceContainerHighest
                            )
                        }
                    }
                }
            }
            if (state.isLoading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            } else if (state.exercises.isEmpty()) {
                item(key = "empty") {
                    Surface(color = colors.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(30.dp), tint = colors.onPrimaryContainer)
                            Text("Let's get a set on the board", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Choose your first exercise. You can build the rest as you go.", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onAction(CurrentWorkoutAction.AddExercise) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Choose exercise", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else {
                state.continueExercise?.let { exercise ->
                    item(key = "continue") {
                        ContinueCard(exercise, state.hasNextTarget, !state.isFinishing) {
                            onAction(CurrentWorkoutAction.OpenExercise(exercise.exerciseId))
                        }
                    }
                }
                item(key = "exercise-label") {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Your exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Tap to log", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                itemsIndexed(state.exercises, key = { index, item -> item.instanceId?.let { "plan:$it" } ?: "exercise:${item.exerciseId}:$index" }) { index, exercise ->
                    val previous = state.exercises.getOrNull(index - 1)
                    val next = state.exercises.getOrNull(index + 1)
                    val inSuperset = exercise.supersetGroupId != null
                    val connectedTop = inSuperset && previous?.supersetGroupId == exercise.supersetGroupId
                    val connectedBottom = inSuperset && next?.supersetGroupId == exercise.supersetGroupId
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (inSuperset && !connectedTop) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                                Icon(Icons.Default.Link, null, Modifier.size(16.dp), tint = colors.secondary)
                                Spacer(Modifier.width(5.dp))
                                Text("Superset ${supersetIds.indexOf(exercise.supersetGroupId) + 1}", style = MaterialTheme.typography.labelLarge, color = colors.secondary)
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            if (inSuperset) {
                                Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.width(2.dp).weight(1f).background(if (connectedTop) colors.secondary else colors.background))
                                    Box(Modifier.size(6.dp).background(colors.secondary, CircleShape))
                                    Box(Modifier.width(2.dp).weight(1f).background(if (connectedBottom) colors.secondary else colors.background))
                                }
                            }
                            CurrentExerciseRow(
                                exercise = exercise,
                                number = index + 1,
                                enabled = !state.isFinishing,
                                onClick = { onAction(CurrentWorkoutAction.OpenExercise(exercise.exerciseId)) },
                                onSwap = { exercise.instanceId?.let { onAction(CurrentWorkoutAction.SwapExercise(it)) } },
                                onRemove = { removeInstanceId = exercise.instanceId },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFinish) {
        AlertDialog(
            onDismissRequest = { if (!state.isFinishing) showFinish = false },
            icon = { Icon(Icons.Default.Flag, null, tint = colors.primary) },
            title = { Text("Finish this workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (state.workingSets == 0) {
                        "You haven't logged any working sets yet. Finish this workout anyway?"
                    } else {
                        "${state.workingSets} working sets across ${state.startedExercises} ${if (state.startedExercises == 1) "exercise" else "exercises"}. Your sets will be kept in history."
                    })
                    state.finishError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(CurrentWorkoutAction.Finish) }, enabled = !state.isFinishing) {
                    Text(if (state.isFinishing) "Finishing…" else "Finish workout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinish = false }, enabled = !state.isFinishing) { Text("Keep training") }
            }
        )
    }
    removeExercise?.let { exercise ->
        AlertDialog(
            onDismissRequest = { removeInstanceId = null },
            title = { Text("Remove ${exercise.exerciseName}?") },
            text = { Text("This removes it from today's workout only. Your saved routine will not change.") },
            confirmButton = {
                TextButton(onClick = {
                    exercise.instanceId?.let { onAction(CurrentWorkoutAction.RemoveExercise(it)) }
                    removeInstanceId = null
                }) { Text("Remove", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { removeInstanceId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SessionStats(state: CurrentWorkoutUiState) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SessionStat("${state.workingSets}", "Working sets", Modifier.weight(1f))
            SessionStat("${state.startedExercises}", "Exercises", Modifier.weight(1f))
            if (state.targetExercises > 0) {
                SessionStat("${state.completedTargets}/${state.targetExercises}", "Targets met", Modifier.weight(1f))
            } else {
                SessionStat("${state.warmupEntries}", "Warmup entries", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SessionStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContinueCard(exercise: ResumeExerciseItem, hasNextTarget: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, color = colors.primaryContainer, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (hasNextTarget) "Up next" else "Keep logging", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "${if (hasNextTarget) "Continue" else "Log"} ${exercise.exerciseName}", Modifier.size(24.dp), tint = colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun CurrentExerciseRow(
    exercise: ResumeExerciseItem,
    number: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val warmupActivity = exercise.targetMuscle.isWarmupMuscleGroup()
    val count = if (warmupActivity) exercise.completedEntryCount else exercise.setCount
    val target = exercise.plannedSets?.takeIf { it > 0 }
    val reachedTarget = !exercise.isSkipped && target != null && count >= target
    val status = when {
        exercise.isSkipped -> "Skipped"
        reachedTarget -> "Target met"
        exercise.anySetCount == 0 -> "Not started"
        count == 0 -> "Warming up"
        else -> "In progress"
    }
    val editable = exercise.instanceId != null && exercise.anySetCount == 0
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val countLabel = if (target != null) "$count/$target ${if (warmupActivity) "entries" else "sets"}"
        else "$count ${if (warmupActivity) if (count == 1) "entry" else "entries" else if (count == 1) "set" else "sets"}"
    var showMenu by remember(exercise.instanceId) { mutableStateOf(false) }
    val badgeColor = if (reachedTarget) colors.primaryContainer else colors.surfaceContainerHighest
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (reachedTarget) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = if (editable) 0.dp else 12.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!largeFont) Surface(color = badgeColor, shape = RoundedCornerShape(11.dp)) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    if (reachedTarget) Icon(Icons.Default.Check, null, Modifier.size(19.dp), tint = colors.onPrimaryContainer)
                    else if (warmupActivity) Icon(Icons.Default.Whatshot, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
                    else Text("$number", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(buildString {
                    append(exercise.targetMuscle)
                    exercise.repMin?.let { minimum ->
                        append(" · $minimum")
                        exercise.repMax?.takeIf { it != minimum }?.let { append("–$it") }
                        append(" reps")
                    }
                }, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                exercise.bestWeight?.takeIf { !warmupActivity && count > 0 }?.let { weight ->
                    Text(buildString {
                        append("Heaviest today: ${formatWeight(weight)} lb")
                        exercise.bestWeightReps?.let { append(" × $it") }
                    }, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
                if (largeFont) {
                    Text("$countLabel · $status", style = MaterialTheme.typography.labelLarge,
                        color = if (reachedTarget) colors.primary else colors.onSurfaceVariant)
                }
            }
            if (!largeFont) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    countLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (reachedTarget) colors.primary else colors.onSurface
                )
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (reachedTarget) colors.primary else colors.onSurfaceVariant)
            }
            if (editable) {
                Box {
                    IconButton(onClick = { showMenu = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "Actions for ${exercise.exerciseName}")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Swap exercise") }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }, onClick = { showMenu = false; onSwap() })
                        DropdownMenuItem(text = { Text("Remove from today") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { showMenu = false; onRemove() })
                    }
                }
            }
        }
    }
}

private fun formatWeight(weight: Float): String = if (weight % 1f == 0f) weight.toInt().toString() else String.format(Locale.getDefault(), "%.1f", weight)
private fun formatElapsed(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

@Preview(showBackground = true, widthDp = 393, heightDp = 850)
@Composable
private fun CurrentWorkoutPreview() {
    IronLogTheme {
        LoggerPreviewTheme {
            val exercises = listOf(
                ResumeExerciseItem(exerciseId = 1, exerciseName = "Barbell Bench Press", targetMuscle = "Chest", setCount = 3, bestWeight = 135f, bestWeightReps = 8, supersetGroupId = null, orderIndex = 0, plannedSets = 3, repMin = 8, repMax = 12),
                ResumeExerciseItem(instanceId = 2, exerciseId = 2, exerciseName = "Incline Dumbbell Press", targetMuscle = "Chest", setCount = 1, bestWeight = 45f, bestWeightReps = 10, supersetGroupId = null, orderIndex = 1, plannedSets = 3, repMin = 8, repMax = 12),
                ResumeExerciseItem(instanceId = 3, exerciseId = 3, exerciseName = "Lateral Raise", targetMuscle = "Shoulders", setCount = 0, bestWeight = null, supersetGroupId = "superset", orderIndex = 2, plannedSets = 3, repMin = 12, repMax = 15),
                ResumeExerciseItem(instanceId = 4, exerciseId = 4, exerciseName = "Triceps Pushdown", targetMuscle = "Triceps", setCount = 0, bestWeight = null, supersetGroupId = "superset", orderIndex = 3, plannedSets = 3, repMin = 10, repMax = 15)
            )
            PreviewCurrentWorkoutContent(
                CurrentWorkoutUiState(workoutId = 1, name = "Push day", routineName = "My weekly split", exercises = exercises, startedAtMs = System.currentTimeMillis() - 22 * 60_000, workingSets = 4, startedExercises = 2, plannedWorkingSets = 12, completedPlannedSets = 4, targetExercises = 4, completedTargets = 1, continueExercise = exercises[1], hasNextTarget = true, isLoading = false),
                onAction = {}
            )
        }
    }
}
