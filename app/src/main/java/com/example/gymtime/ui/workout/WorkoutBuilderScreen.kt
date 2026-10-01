package com.example.gymtime.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateBackOrHome
import com.example.gymtime.navigation.navigateHomeAndClearStack
import com.example.gymtime.navigation.navigateToWorkoutExercise
import com.example.gymtime.ui.components.BackNavigationIcon
import com.example.gymtime.ui.components.HomeNavigationAction
import com.example.gymtime.ui.components.rememberGuardedNavigationActions
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.LocalLoggerPreviewThemeActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutBuilderScreen(
    navController: NavController,
    viewModel: WorkoutBuilderViewModel = hiltViewModel()
) {
    val previewActive = LocalLoggerPreviewThemeActive.current
    val action = LocalLoggerActionColors.current
    val actionFill = if (previewActive) action.fill else MaterialTheme.colorScheme.primary
    val actionForeground = if (previewActive) action.onFill else Color.Black
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
        hasUnsavedChanges = selectedIds.isNotEmpty(),
        onBack = navController::navigateBackOrHome,
        onHome = navController::navigateHomeAndClearStack,
        dialogTitle = "Discard workout plan?",
        dialogMessage = "The exercises selected for this one-time workout will be lost."
    )
    val builderBackStackEntry = remember(navController) {
        navController.getBackStackEntry(Screen.WorkoutBuilder.route)
    }
    val createdExerciseId by builderBackStackEntry.savedStateHandle
        .getStateFlow<Long?>(Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID, null)
        .collectAsStateWithLifecycle()

    LaunchedEffect(createdExerciseId) {
        createdExerciseId?.let { exerciseId ->
            viewModel.addExercise(exerciseId)
            builderBackStackEntry.savedStateHandle[Screen.ExerciseForm.RESULT_CREATED_EXERCISE_ID] = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.workoutStarted.collect { start ->
            navController.navigateToWorkoutExercise(start.firstExerciseId)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Plan a Workout",
                            fontWeight = FontWeight.Bold,
                            style = if (previewActive) MaterialTheme.typography.titleLarge else LocalTextStyle.current
                        )
                        Text(
                            "One-time plan · not saved as a routine",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalAppColors.current.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    BackNavigationIcon(navigationActions.back)
                },
                actions = {
                    HomeNavigationAction(navigationActions.home)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (previewActive) MaterialTheme.colorScheme.surfaceContainerLow else LocalAppColors.current.surfaceCards,
                shadowElevation = if (previewActive) 1.dp else 12.dp
            ) {
                Button(
                    onClick = viewModel::startWorkout,
                    enabled = selectedIds.isNotEmpty() && !isStarting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .then(if (previewActive) Modifier.heightIn(min = 56.dp) else Modifier.height(56.dp)),
                    shape = RoundedCornerShape(if (previewActive) 18.dp else 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = actionFill,
                        contentColor = actionForeground
                    )
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = if (previewActive) LocalContentColor.current else actionForeground
                        )
                    } else {
                        Text(
                            if (selectedIds.isEmpty()) "Choose Exercises" else "Start Workout · ${selectedIds.size} exercises",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "YOUR PLAN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (selectedExercises.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (previewActive) Modifier.heightIn(min = 76.dp) else Modifier.height(76.dp)),
                    shape = RoundedCornerShape(if (previewActive) 20.dp else 12.dp),
                    color = LocalAppColors.current.surfaceCards,
                    border = BorderStroke(1.dp, if (previewActive) MaterialTheme.colorScheme.outlineVariant else LocalAppColors.current.textTertiary.copy(alpha = 0.25f)),
                    shadowElevation = if (previewActive) 1.dp else 0.dp
                ) {
                    Box(modifier = if (previewActive) Modifier.padding(12.dp) else Modifier, contentAlignment = Alignment.Center) {
                        Text(
                            "Tap exercises below to build your session.",
                            color = LocalAppColors.current.textSecondary
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(selectedExercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                        SelectedExerciseCard(
                            exercise = exercise,
                            position = index + 1,
                            canMoveBack = index > 0,
                            canMoveForward = index < selectedExercises.lastIndex,
                            onMoveBack = { viewModel.moveExercise(exercise.id, -1) },
                            onMoveForward = { viewModel.moveExercise(exercise.id, 1) },
                            onRemove = { viewModel.toggleExercise(exercise.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = if (previewActive) RoundedCornerShape(18.dp) else OutlinedTextFieldDefaults.shape
            )
            OutlinedButton(
                onClick = {
                    navController.navigate(
                        Screen.ExerciseForm.createRoute(
                            fromWorkoutBuilder = true,
                            initialName = searchQuery
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .then(if (previewActive) Modifier.heightIn(min = 48.dp) else Modifier),
                shape = RoundedCornerShape(if (previewActive) 18.dp else 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create New Exercise", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedMuscle == null,
                        onClick = { viewModel.selectMuscle(null) },
                        label = { Text("All") },
                        modifier = if (previewActive) Modifier.heightIn(min = 48.dp) else Modifier,
                        shape = if (previewActive) RoundedCornerShape(16.dp) else FilterChipDefaults.shape
                    )
                }
                items(availableMuscles) { muscle ->
                    FilterChip(
                        selected = selectedMuscle == muscle,
                        onClick = { viewModel.selectMuscle(muscle) },
                        label = { Text(muscle) },
                        modifier = if (previewActive) Modifier.heightIn(min = 48.dp) else Modifier,
                        shape = if (previewActive) RoundedCornerShape(16.dp) else FilterChipDefaults.shape
                    )
                }
            }

            Text(
                "EXERCISES",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = LocalAppColors.current.textTertiary,
                fontWeight = FontWeight.Bold
            )

            if (filteredExercises.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (previewActive) Modifier.heightIn(min = 120.dp) else Modifier.height(120.dp)),
                    shape = RoundedCornerShape(if (previewActive) 20.dp else 12.dp),
                    color = LocalAppColors.current.surfaceCards,
                    border = BorderStroke(1.dp, if (previewActive) MaterialTheme.colorScheme.outlineVariant else LocalAppColors.current.textTertiary.copy(alpha = 0.25f)),
                    shadowElevation = if (previewActive) 1.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedMuscle != null) {
                                "No exercises match this filter. Clear it or try a different search."
                            } else {
                                "No exercises available yet. Create one above to add it to this workout."
                            },
                            color = LocalAppColors.current.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredExercises, key = { it.id }) { exercise ->
                        val isSelected = exercise.id in selectedIds
                        ExercisePickerRow(
                            exercise = exercise,
                            isSelected = isSelected,
                            onClick = { viewModel.toggleExercise(exercise.id) }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SelectedExerciseCard(
    exercise: Exercise,
    position: Int,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onRemove: () -> Unit
) {
    val previewActive = LocalLoggerPreviewThemeActive.current
    Surface(
        modifier = Modifier.width(190.dp),
        shape = RoundedCornerShape(if (previewActive) 18.dp else 12.dp),
        color = if (previewActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (previewActive) 0.4f else 0.65f)),
        shadowElevation = if (previewActive) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$position",
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    exercise.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalAppColors.current.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(if (previewActive) 48.dp else 30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${exercise.name}", modifier = Modifier.size(18.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onMoveBack, enabled = canMoveBack, modifier = Modifier.size(if (previewActive) 48.dp else 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier")
                }
                IconButton(onClick = onMoveForward, enabled = canMoveForward, modifier = Modifier.size(if (previewActive) 48.dp else 32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move later")
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerRow(
    exercise: Exercise,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val previewActive = LocalLoggerPreviewThemeActive.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(if (previewActive) Modifier.heightIn(min = 64.dp) else Modifier),
        shape = RoundedCornerShape(if (previewActive) 18.dp else 12.dp),
        color = if (previewActive && isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            LocalAppColors.current.surfaceCards
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else if (previewActive) MaterialTheme.colorScheme.outlineVariant else Color.Transparent
        ),
        shadowElevation = if (previewActive) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold)
                Text(
                    exercise.targetMuscle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary
                )
            }
            Icon(
                if (isSelected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = if (isSelected) "Selected" else "Add ${exercise.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
