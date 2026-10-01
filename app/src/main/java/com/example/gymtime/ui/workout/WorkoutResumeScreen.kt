package com.example.gymtime.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.data.db.entity.isWarmupMuscleGroup
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*
import com.example.gymtime.ui.workout.preview.CurrentWorkoutAction
import com.example.gymtime.ui.workout.preview.PreviewCurrentWorkoutContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val newUiEnabled by viewModel.newUiEnabled.collectAsStateWithLifecycle(initialValue = false)
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
    if (newUiEnabled) {
        val state by viewModel.previewState.collectAsStateWithLifecycle()
        LoggerPreviewTheme {
            PreviewCurrentWorkoutContent(
                state = state,
                onAction = { action ->
                    when (action) {
                        CurrentWorkoutAction.Back -> onBackClick()
                        CurrentWorkoutAction.Home -> onHomeClick()
                        CurrentWorkoutAction.AddExercise -> onAddExerciseClick()
                        CurrentWorkoutAction.Finish -> viewModel.finishWorkout()
                        is CurrentWorkoutAction.OpenExercise -> onExerciseClick(action.exerciseId)
                        is CurrentWorkoutAction.SwapExercise -> onSwapExerciseClick(action.instanceId)
                        is CurrentWorkoutAction.RemoveExercise -> viewModel.removePlannedExercise(action.instanceId)
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            )
        }
    } else {
        LegacyWorkoutResumeScreen(viewModel, onExerciseClick, onAddExerciseClick, onSwapExerciseClick, onBackClick, onHomeClick, snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyWorkoutResumeScreen(
    viewModel: WorkoutResumeViewModel,
    onExerciseClick: (Long) -> Unit,
    onAddExerciseClick: () -> Unit,
    onSwapExerciseClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val todaysExercises by viewModel.todaysExercises.collectAsStateWithLifecycle()
    val currentWorkout by viewModel.currentWorkout.collectAsStateWithLifecycle()
    val isFinishing by viewModel.isFinishing.collectAsStateWithLifecycle()
    val accentColor = MaterialTheme.colorScheme.primary
    var exerciseToRemove by remember { mutableStateOf<ResumeExerciseItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Workout Overview",
                        color = LocalAppColors.current.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LocalAppColors.current.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onHomeClick) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Home",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (todaysExercises.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LocalAppColors.current.surfaceCards,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Add Exercise Button
                        Button(
                            onClick = onAddExerciseClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalAppColors.current.surfaceCards,
                                contentColor = accentColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accentColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Exercise",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Exercise",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Finish Workout Button
                        Button(
                            onClick = { viewModel.finishWorkout() },
                            enabled = !isFinishing,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Finish Workout",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Text(
                text = currentWorkout?.routineDayNameSnapshot ?: "Today's Workout",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Text(
                text = if (todaysExercises.isEmpty()) "No exercises logged yet" else "${todaysExercises.size} exercises",
                fontSize = 14.sp,
                color = LocalAppColors.current.textSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Exercise List
            if (todaysExercises.isEmpty()) {
                EmptyStateCard(onAddExerciseClick = onAddExerciseClick)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp), // Removed spacing to make connector continuous
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(todaysExercises.size) { index ->
                        val exercise = todaysExercises[index]
                        val isSuperset = exercise.supersetGroupId != null
                        
                        val prevExercise = todaysExercises.getOrNull(index - 1)
                        val nextExercise = todaysExercises.getOrNull(index + 1)
                        
                        val isConnectedTop = isSuperset && prevExercise?.supersetGroupId == exercise.supersetGroupId
                        val isConnectedBottom = isSuperset && nextExercise?.supersetGroupId == exercise.supersetGroupId

                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                // Connector Column
                                if (isSuperset) {
                                    Column(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .fillMaxHeight(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top Line
                                        if (isConnectedTop) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                        
                                        // Icon
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_sync),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        
                                        // Bottom Line
                                        if (isConnectedBottom) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp))
                                }

                                ExerciseSummaryCard(
                                    exercise = exercise,
                                    onClick = { onExerciseClick(exercise.exerciseId) },
                                    onSwap = exercise.instanceId
                                        ?.takeIf { exercise.anySetCount == 0 }
                                        ?.let { instanceId -> { onSwapExerciseClick(instanceId) } },
                                    onRemove = exercise.instanceId
                                        ?.takeIf { exercise.anySetCount == 0 }
                                        ?.let { { exerciseToRemove = exercise } }
                                )
                            }
                        }
                    }

                    // Bottom spacing for bottom bar
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    exerciseToRemove?.let { exercise ->
        AlertDialog(
            onDismissRequest = { exerciseToRemove = null },
            title = { Text("Remove ${exercise.exerciseName}?") },
            text = {
                Text("This removes it from today's workout only. Your saved routine will not change.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exercise.instanceId?.let(viewModel::removePlannedExercise)
                        exerciseToRemove = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun ExerciseSummaryCard(
    exercise: ResumeExerciseItem,
    onClick: () -> Unit,
    onSwap: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val isWarmupLibraryExercise = exercise.targetMuscle.isWarmupMuscleGroup()
    val displayedEntryCount = if (isWarmupLibraryExercise) exercise.anySetCount else exercise.setCount
    val isUnstarted = displayedEntryCount == 0 && !exercise.isSkipped
    val accentColor = MaterialTheme.colorScheme.primary
    var showActions by remember { mutableStateOf(false) }

    GlowCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isUnstarted) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exerciseName,
                    fontSize = if (isUnstarted) 16.sp else 18.sp,
                    fontWeight = if (isUnstarted) FontWeight.Medium else FontWeight.Bold,
                    color = if (isUnstarted) LocalAppColors.current.textSecondary else Color.White
                )
                Text(
                    text = exercise.targetMuscle.uppercase(),
                    fontSize = 12.sp,
                    color = LocalAppColors.current.textTertiary,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUnstarted) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Not started",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = LocalAppColors.current.textTertiary
                        )
                        exercise.plannedSets?.let { sets ->
                            Text(
                                text = buildString {
                                    append("$sets sets")
                                    if (exercise.repMin != null) {
                                        append(" • ")
                                        append(exercise.repMin)
                                        if (exercise.repMax != null && exercise.repMax != exercise.repMin) {
                                            append("-${exercise.repMax}")
                                        }
                                        append(" reps")
                                    }
                                },
                                fontSize = 12.sp,
                                color = accentColor
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isWarmupLibraryExercise) {
                                "$displayedEntryCount ${if (displayedEntryCount == 1) "entry" else "entries"}"
                            } else {
                                "$displayedEntryCount sets"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                        exercise.bestWeight?.let { weight ->
                            Text(
                                text = "Best: ${weight.toInt()} lbs",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                if (exercise.anySetCount == 0 && onSwap != null && onRemove != null) {
                    Box {
                        IconButton(onClick = { showActions = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Exercise actions for ${exercise.exerciseName}",
                                tint = LocalAppColors.current.textSecondary
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

@Composable
private fun EmptyStateCard(onAddExerciseClick: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    GlowCard(
        onClick = onAddExerciseClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No exercises yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "Tap to add your first exercise",
                fontSize = 14.sp,
                color = LocalAppColors.current.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutResumeScreenPreview() {
    IronLogTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Today's Workout",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
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
}
