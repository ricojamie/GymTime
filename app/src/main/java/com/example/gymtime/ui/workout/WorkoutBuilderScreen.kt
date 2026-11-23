package com.example.gymtime.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateResumed
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.ui.components.IronLogCard
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBuilderScreen(
    navController: NavController,
    viewModel: WorkoutBuilderViewModel = hiltViewModel()
) {
    val scheme = MaterialTheme.colorScheme
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedMuscle by viewModel.selectedMuscle.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedExerciseIds.collectAsStateWithLifecycle()
    val selectedExercises by viewModel.selectedExercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredExercises by viewModel.filteredExercises.collectAsStateWithLifecycle(initialValue = emptyList())
    val availableMuscles by viewModel.availableMuscles.collectAsStateWithLifecycle(initialValue = emptyList())
    val isStarting by viewModel.isStarting.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val navigationActions = rememberGuardedNavigationActions(
        hasUnsavedChanges = selectedIds.isNotEmpty() && !isStarting,
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack,
        dialogTitle = "Discard workout plan?",
        dialogMessage = "The exercises selected for this one-time workout will be lost."
    )
    BackHandler(enabled = isStarting) { /* Wait for workout creation to finish. */ }

    val lifecycleOwner = LocalLifecycleOwner.current
    val builderBackStackEntry = (lifecycleOwner as? NavBackStackEntry)
        ?: remember(navController) { navController.getBackStackEntry(Screen.WorkoutBuilder.route) }
    val createdExerciseId by builderBackStackEntry.savedStateHandle
        .getStateFlow<Long?>(Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID, null)
        .collectAsStateWithLifecycle()

    LaunchedEffect(createdExerciseId) {
        createdExerciseId?.let { exerciseId ->
            builderBackStackEntry.savedStateHandle[Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID] = null
            viewModel.addExercise(exerciseId)
        }
    }

    val latestOpenWorkout by rememberUpdatedState<(Long) -> Unit> { navController.navigateToWorkoutExercise(it) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.workoutStarted.collect { start ->
                runCatching { latestOpenWorkout(start.firstExerciseId) }
                    .onSuccess { viewModel.onWorkoutOpened() }
                    .onFailure { viewModel.onWorkoutNavigationFailed() }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = scheme.background,
        contentColor = scheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Plan a Workout",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "One-time plan · not saved as a routine",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navigationActions.back,
                        enabled = !isStarting,
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (!isStarting) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = navigationActions.home,
                        enabled = !isStarting,
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (!isStarting) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = scheme.surfaceContainerLow,
                contentColor = scheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IronLogDivider(insetStart = 0.dp)
                    IronLogPrimaryButton(
                        text = when {
                            isStarting -> "Starting…"
                            selectedIds.isEmpty() -> "Choose Exercises"
                            else -> "Start Workout · ${selectedIds.size} ${if (selectedIds.size == 1) "exercise" else "exercises"}"
                        },
                        onClick = viewModel::startWorkout,
                        enabled = selectedIds.isNotEmpty() && !isStarting,
                        loading = isStarting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = IronLogSpace.ScreenHorizontal,
                                vertical = IronLogSpace.ItemGap
                            )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = IronLogSpace.ScreenHorizontal)
        ) {
            IronLogSectionHeader(
                title = "Your plan",
                modifier = Modifier.padding(top = IronLogSpace.CompactGap)
            )
            Spacer(Modifier.height(IronLogSpace.CompactGap))

            if (selectedExercises.isEmpty()) {
                IronLogCard(
                    showBorder = true,
                    shape = IronLogRadius.CardShape,
                    contentPadding = PaddingValues(IronLogSpace.ItemGap),
                    modifier = Modifier.heightIn(min = 72.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap exercises below to build your session.",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                    itemsIndexed(selectedExercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                        SelectedExerciseCard(
                            exercise = exercise,
                            position = index + 1,
                            enabled = !isStarting,
                            canMoveBack = index > 0,
                            canMoveForward = index < selectedExercises.lastIndex,
                            onMoveBack = { viewModel.moveExercise(exercise.id, -1) },
                            onMoveForward = { viewModel.moveExercise(exercise.id, 1) },
                            onRemove = { viewModel.toggleExercise(exercise.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(IronLogSpace.CardPadding))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = IronLogRadius.ControlShape
            )
            Spacer(Modifier.height(IronLogSpace.CompactGap))
            IronLogSecondaryButton(
                text = "Create New Exercise",
                onClick = {
                    navController.navigateResumed(
                        Screen.ExerciseForm.createRoute(
                            fromWorkoutBuilder = true,
                            initialName = searchQuery
                        )
                    )
                },
                enabled = !isStarting,
                outlined = true,
                leadingIcon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(IronLogSpace.CompactGap))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                item(key = "all-muscles") {
                    FilterChip(
                        selected = selectedMuscle == null,
                        onClick = { viewModel.selectMuscle(null) },
                        label = { Text("All") },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                        shape = IronLogRadius.ControlShape
                    )
                }
                items(availableMuscles, key = { it }) { muscle ->
                    FilterChip(
                        selected = selectedMuscle == muscle,
                        onClick = { viewModel.selectMuscle(if (selectedMuscle == muscle) null else muscle) },
                        label = { Text(muscle) },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                        shape = IronLogRadius.ControlShape
                    )
                }
            }

            IronLogSectionHeader(
                title = "Exercises",
                modifier = Modifier.padding(top = IronLogSpace.CompactGap, bottom = IronLogSpace.CompactGap)
            )

            if (filteredExercises.isEmpty()) {
                IronLogCard(
                    showBorder = true,
                    shape = IronLogRadius.CardShape,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    IronLogEmptyState(
                        title = "No matching exercises",
                        body = if (searchQuery.isNotBlank() || selectedMuscle != null) {
                            "Clear your search or muscle filter to see all exercises."
                        } else {
                            "Create an exercise above to add it to this workout."
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                    contentPadding = PaddingValues(bottom = IronLogSpace.ItemGap)
                ) {
                    items(filteredExercises, key = { it.id }) { exercise ->
                        ExercisePickerRow(
                            exercise = exercise,
                            isSelected = exercise.id in selectedIds,
                            enabled = !isStarting,
                            onClick = { viewModel.toggleExercise(exercise.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedExerciseCard(
    exercise: Exercise,
    position: Int,
    enabled: Boolean,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    IronLogCard(
        modifier = Modifier.width(204.dp),
        highlighted = true,
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(horizontal = IronLogSpace.ItemGap, vertical = IronLogSpace.CompactGap),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$position",
                modifier = Modifier.padding(end = IronLogSpace.CompactGap),
                style = IronLogType.tabular(MaterialTheme.typography.labelLarge),
                color = scheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = exercise.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${exercise.name}",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onMoveBack,
                enabled = enabled && canMoveBack,
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier")
            }
            IconButton(
                onClick = onMoveForward,
                enabled = enabled && canMoveForward,
                modifier = Modifier.size(IronLogSpace.TouchTargetMin)
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move later")
            }
        }
    }
}

@Composable
private fun ExercisePickerRow(
    exercise: Exercise,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    IronLogCard(
        onClick = onClick,
        enabled = enabled,
        highlighted = isSelected,
        showBorder = true,
        shape = IronLogRadius.CardShape,
        contentPadding = PaddingValues(horizontal = IronLogSpace.CardPadding, vertical = IronLogSpace.RowVertical),
        modifier = Modifier.heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = exercise.targetMuscle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (isSelected) "Selected" else "Add ${exercise.name}",
                tint = scheme.primary
            )
        }
    }
}
