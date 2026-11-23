package com.example.gymtime.ui.workout.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogMetric
import com.example.gymtime.ui.components.IronLogMetricRow
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogBorder
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.IronLogType
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
    data object ClearFinishError : CurrentWorkoutAction
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Current workout",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(CurrentWorkoutAction.Back) },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onAction(CurrentWorkoutAction.Home) },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.Default.Home, "Home", tint = colors.primary)
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = {
            Surface(
                color = colors.surfaceContainerLow,
                contentColor = colors.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IronLogDivider(insetStart = 0.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                horizontal = IronLogSpace.ScreenHorizontal,
                                vertical = IronLogSpace.ItemGap
                            ),
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IronLogSecondaryButton(
                            text = "Add exercise",
                            onClick = { onAction(CurrentWorkoutAction.AddExercise) },
                            enabled = !state.isFinishing,
                            outlined = true,
                            leadingIcon = Icons.Default.Add,
                            modifier = Modifier.weight(1f)
                        )
                        IronLogPrimaryButton(
                            text = if (state.isFinishing) "Finishing…" else "Finish",
                            onClick = {
                                onAction(CurrentWorkoutAction.ClearFinishError)
                                showFinish = true
                            },
                            enabled = state.workoutId != null && !state.isLoading && !state.isFinishing,
                            loading = state.isFinishing,
                            leadingIcon = Icons.Default.Check,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = IronLogSpace.CardPadding
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            item(key = "session-header") {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            state.routineName?.takeIf { it != state.name }?.let { routineName ->
                                Text(
                                    text = routineName.uppercase(),
                                    style = IronLogType.sectionEyebrow(),
                                    color = colors.primary
                                )
                            }
                            Text(
                                text = state.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        elapsedMinutes?.let { minutes ->
                            Surface(
                                color = colors.surfaceContainerHigh,
                                shape = IronLogRadius.ControlShape,
                                border = IronLogBorder.stroke(colors.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colors.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatElapsed(minutes),
                                        style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                    SessionStats(state)
                    if (state.plannedWorkingSets > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Plan progress",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = "${state.completedPlannedSets} / ${state.plannedWorkingSets} working sets",
                                    style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(IronLogRadius.MicroShape),
                                color = colors.primary,
                                trackColor = colors.surfaceContainerHighest
                            )
                        }
                    }
                }
            }
            if (state.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            } else if (state.exercises.isEmpty()) {
                item(key = "empty") {
                    IronLogCard(
                        highlighted = true,
                        showBorder = true,
                        shape = IronLogRadius.CardShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        IronLogEmptyState(
                            icon = Icons.Default.FitnessCenter,
                            title = "Let's get a set on the board",
                            body = "Choose your first exercise. You can build the rest as you go.",
                            actionLabel = "Choose exercise",
                            onAction = { onAction(CurrentWorkoutAction.AddExercise) }
                        )
                    }
                }
            } else {
                state.continueExercise?.let { exercise ->
                    item(key = "continue") {
                        ContinueCard(
                            exercise = exercise,
                            hasNextTarget = state.hasNextTarget,
                            enabled = !state.isFinishing,
                            onClick = { onAction(CurrentWorkoutAction.OpenExercise(exercise.exerciseId)) }
                        )
                    }
                }
                item(key = "exercise-label") {
                    IronLogSectionHeader(
                        title = "Your exercises",
                        trailingAction = {
                            Text(
                                text = "Tap to log",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    )
                }
                itemsIndexed(
                    state.exercises,
                    key = { index, item -> item.instanceId?.let { "plan:$it" } ?: "exercise:${item.exerciseId}:$index" }
                ) { index, exercise ->
                    val previous = state.exercises.getOrNull(index - 1)
                    val next = state.exercises.getOrNull(index + 1)
                    val inSuperset = exercise.supersetGroupId != null
                    val connectedTop = inSuperset && previous?.supersetGroupId == exercise.supersetGroupId
                    val connectedBottom = inSuperset && next?.supersetGroupId == exercise.supersetGroupId
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (inSuperset && !connectedTop) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                IronLogStatusChip(
                                    label = "Superset ${supersetIds.indexOf(exercise.supersetGroupId) + 1}",
                                    tone = IronLogStatusTone.SUPERSET,
                                    icon = Icons.Default.Link
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (inSuperset) {
                                Column(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .weight(1f)
                                            .background(if (connectedTop) colors.secondary else colors.background)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(colors.secondary, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .weight(1f)
                                            .background(if (connectedBottom) colors.secondary else colors.background)
                                    )
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
            onDismissRequest = {
                if (!state.isFinishing) {
                    showFinish = false
                    onAction(CurrentWorkoutAction.ClearFinishError)
                }
            },
            icon = { Icon(Icons.Default.Flag, null, tint = colors.primary) },
            title = { Text("Finish this workout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.workingSets == 0) {
                            "You haven't logged any working sets yet. Finish this workout anyway?"
                        } else {
                            "${state.workingSets} working sets across ${state.startedExercises} ${if (state.startedExercises == 1) "exercise" else "exercises"}. Your sets will be kept in history."
                        }
                    )
                    state.finishError?.let {
                        Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(CurrentWorkoutAction.Finish) },
                    enabled = !state.isFinishing
                ) {
                    Text(
                        text = if (state.isFinishing) "Finishing…" else "Finish workout",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFinish = false
                        onAction(CurrentWorkoutAction.ClearFinishError)
                    },
                    enabled = !state.isFinishing
                ) {
                    Text("Keep training")
                }
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
                }) {
                    Text("Remove", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeInstanceId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionStats(state: CurrentWorkoutUiState) {
    val metrics = if (state.targetExercises > 0) {
        listOf(
            IronLogMetric("Working sets", "${state.workingSets}", emphasized = true),
            IronLogMetric("Exercises", "${state.startedExercises}"),
            IronLogMetric("Targets met", "${state.completedTargets}/${state.targetExercises}")
        )
    } else {
        listOf(
            IronLogMetric("Working sets", "${state.workingSets}", emphasized = true),
            IronLogMetric("Exercises", "${state.startedExercises}"),
            IronLogMetric("Warmups", "${state.warmupEntries}")
        )
    }

    IronLogGroupedCard(showBorder = true) {
        IronLogMetricRow(
            metrics = metrics,
            modifier = Modifier.padding(IronLogSpace.CardPadding)
        )
    }
}

@Composable
private fun ContinueCard(
    exercise: ResumeExerciseItem,
    hasNextTarget: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    IronLogCard(
        onClick = onClick,
        enabled = enabled,
        highlighted = true,
        showBorder = true,
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(IronLogSpace.CardPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = (if (hasNextTarget) "Up next" else "Keep logging").uppercase(),
                    style = IronLogType.sectionEyebrow(),
                    color = colors.onPrimaryContainer
                )
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "${if (hasNextTarget) "Continue" else "Log"} ${exercise.exerciseName}",
                modifier = Modifier.size(22.dp),
                tint = colors.onPrimaryContainer
            )
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
    val editable = exercise.instanceId != null && exercise.anySetCount == 0
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val countLabel = if (target != null) {
        "$count/$target ${if (warmupActivity) "entries" else "sets"}"
    } else {
        "$count ${if (warmupActivity) if (count == 1) "entry" else "entries" else if (count == 1) "set" else "sets"}"
    }
    var showMenu by remember(exercise.instanceId) { mutableStateOf(false) }

    IronLogCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        highlighted = reachedTarget,
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(
            start = IronLogSpace.ItemGap,
            end = if (editable) 4.dp else IronLogSpace.ItemGap,
            top = IronLogSpace.ItemGap,
            bottom = IronLogSpace.ItemGap
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!largeFont) {
                Surface(
                    color = if (reachedTarget) colors.primaryContainer else colors.surfaceContainerHighest,
                    shape = IronLogRadius.MicroShape
                ) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        when {
                            reachedTarget -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.onPrimaryContainer
                            )
                            warmupActivity -> Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.onSurfaceVariant
                            )
                            else -> Text(
                                text = "$number",
                                style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(exercise.targetMuscle)
                        exercise.repMin?.let { minimum ->
                            append(" · $minimum")
                            exercise.repMax?.takeIf { it != minimum }?.let { append("–$it") }
                            append(" reps")
                        }
                    },
                    style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                    color = colors.onSurfaceVariant
                )
                exercise.bestWeight?.takeIf { !warmupActivity && count > 0 }?.let { weight ->
                    Text(
                        text = buildString {
                            append("Heaviest today: ${formatWeight(weight)} lb")
                            exercise.bestWeightReps?.let { append(" × $it") }
                        },
                        style = IronLogType.tabular(MaterialTheme.typography.labelMedium),
                        color = colors.onSurfaceVariant
                    )
                }
                if (largeFont) {
                    Text(
                        text = countLabel,
                        style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                        color = if (reachedTarget) colors.primary else colors.onSurfaceVariant
                    )
                }
            }
            if (!largeFont) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = countLabel,
                        style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                        fontWeight = FontWeight.Bold,
                        color = if (reachedTarget) colors.primary else colors.onSurface
                    )
                    ExerciseProgressTag(
                        isSkipped = exercise.isSkipped,
                        reachedTarget = reachedTarget,
                        anySetCount = exercise.anySetCount,
                        workingCount = count
                    )
                }
            }
            if (editable) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        enabled = enabled,
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.Default.MoreVert, "Actions for ${exercise.exerciseName}")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Swap exercise") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                            onClick = {
                                showMenu = false
                                onSwap()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from today") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = {
                                showMenu = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseProgressTag(
    isSkipped: Boolean,
    reachedTarget: Boolean,
    anySetCount: Int,
    workingCount: Int
) {
    when {
        isSkipped -> IronLogStatusChip(label = "Skipped", tone = IronLogStatusTone.NEUTRAL)
        reachedTarget -> IronLogStatusChip(label = "Done", tone = IronLogStatusTone.ACCENT)
        anySetCount == 0 -> Text(
            text = "Not started",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        workingCount == 0 -> IronLogStatusChip(label = "Warming up", tone = IronLogStatusTone.WARMUP)
        else -> IronLogStatusChip(label = "Active", tone = IronLogStatusTone.ACTIVE)
    }
}

private fun formatWeight(weight: Float): String =
    if (weight % 1f == 0f) weight.toInt().toString() else String.format(Locale.getDefault(), "%.1f", weight)

private fun formatElapsed(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

@Preview(showBackground = true, widthDp = 393, heightDp = 850)
@Composable
private fun CurrentWorkoutPreview() {
    IronLogTheme {
        val exercises = listOf(
            ResumeExerciseItem(
                exerciseId = 1,
                exerciseName = "Barbell Bench Press",
                targetMuscle = "Chest",
                setCount = 3,
                bestWeight = 135f,
                bestWeightReps = 8,
                supersetGroupId = null,
                orderIndex = 0,
                plannedSets = 3,
                repMin = 8,
                repMax = 12
            ),
            ResumeExerciseItem(
                instanceId = 2,
                exerciseId = 2,
                exerciseName = "Incline Dumbbell Press",
                targetMuscle = "Chest",
                setCount = 1,
                bestWeight = 45f,
                bestWeightReps = 10,
                supersetGroupId = null,
                orderIndex = 1,
                plannedSets = 3,
                repMin = 8,
                repMax = 12
            ),
            ResumeExerciseItem(
                instanceId = 3,
                exerciseId = 3,
                exerciseName = "Lateral Raise",
                targetMuscle = "Shoulders",
                setCount = 0,
                bestWeight = null,
                supersetGroupId = "superset",
                orderIndex = 2,
                plannedSets = 3,
                repMin = 12,
                repMax = 15
            ),
            ResumeExerciseItem(
                instanceId = 4,
                exerciseId = 4,
                exerciseName = "Triceps Pushdown",
                targetMuscle = "Triceps",
                setCount = 0,
                bestWeight = null,
                supersetGroupId = "superset",
                orderIndex = 3,
                plannedSets = 3,
                repMin = 10,
                repMax = 15
            )
        )
        PreviewCurrentWorkoutContent(
            state = CurrentWorkoutUiState(
                workoutId = 1,
                name = "Push day",
                routineName = "My weekly split",
                exercises = exercises,
                startedAtMs = System.currentTimeMillis() - 22 * 60_000,
                workingSets = 4,
                startedExercises = 2,
                plannedWorkingSets = 12,
                completedPlannedSets = 4,
                targetExercises = 4,
                completedTargets = 1,
                continueExercise = exercises[1],
                hasNextTarget = true,
                isLoading = false
            ),
            onAction = {}
        )
    }
}
