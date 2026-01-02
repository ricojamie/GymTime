package com.example.gymtime.ui.exercise

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.R
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.IronLogTheme
import com.example.gymtime.ui.theme.SurfaceCards
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextSecondary
import com.example.gymtime.ui.theme.TextTertiary

private const val TAG = "ExerciseSelectionScreen"

@Composable
fun ExerciseSelectionScreen(
    navController: NavController,
    viewModel: ExerciseSelectionViewModel = hiltViewModel()
) {
    ExerciseSelectionContent(
        navController = navController,
        viewModel = viewModel
    )
}

/**
 * Reusable content for exercise selection.
 * Can be used standalone in ExerciseSelectionScreen or embedded in LibraryScreen.
 */
@Composable
fun ExerciseSelectionContent(
    navController: NavController,
    viewModel: ExerciseSelectionViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.supersetStarted.collect { parentId: Long ->
            navController.popBackStack()
        }
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedMuscles by viewModel.selectedMuscles.collectAsState()
    val availableMuscles by viewModel.availableMuscles.collectAsState(initial = emptyList<String>())
    val filteredExercises by viewModel.filteredExercises.collectAsState(initial = emptyList<com.example.gymtime.data.db.entity.Exercise>())

    // Superset mode state
    val isSupersetMode by viewModel.isSupersetModeEnabled.collectAsState()
    val selectedForSuperset by viewModel.selectedForSuperset.collectAsState()
    val canStartSuperset = selectedForSuperset.size == viewModel.maxSupersetExercises

    // Workout mode state (for navigation after creating exercise)
    val isWorkoutMode by viewModel.isWorkoutMode.collectAsState()

    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }

    Log.d(TAG, "ExerciseSelectionContent recomposed: availableMuscles=${availableMuscles.size}, filteredExercises=${filteredExercises.size}, isSupersetMode=$isSupersetMode, selectedCount=${selectedForSuperset.size}")

    Scaffold(
        floatingActionButton = {
            if (isSupersetMode && canStartSuperset) {
                // Show "Start Superset" button when 2 exercises selected
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 100.dp),
                    onClick = {
                        val firstExerciseId = viewModel.startSuperset()
                        navController.navigate(Screen.ExerciseLogging.createRoute(firstExerciseId))
                    },
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp),
                    text = {
                        Text(
                            text = "Start Superset",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                )
            } else if (!isSupersetMode) {
                // Normal "Add Exercise" FAB
                FloatingActionButton(
                    modifier = Modifier.padding(bottom = 100.dp),
                    onClick = {
                        navController.navigate(Screen.ExerciseForm.createRoute(fromWorkout = isWorkoutMode))
                    },
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Exercise"
                    )
                }
            }
            // When in superset mode but <2 selected, no FAB shown
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Search Box
            ExerciseSearchBox(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Superset Mode Toggle Row
            SupersetModeToggle(
                isSupersetMode = isSupersetMode,
                selectedCount = selectedForSuperset.size,
                maxCount = viewModel.maxSupersetExercises,
                onToggle = { viewModel.toggleSupersetMode() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Pills
            if (availableMuscles.isNotEmpty()) {
                ExerciseFilterPills(
                    muscles = availableMuscles,
                    selectedMuscles = selectedMuscles,
                    onMuscleToggle = { viewModel.toggleMuscleFilter(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Exercise List
            if (filteredExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No exercises found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextTertiary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                ) {
                    items(filteredExercises) { exercise ->
                        val isSelected = selectedForSuperset.any { it.id == exercise.id }
                        val selectionOrder = selectedForSuperset.indexOfFirst { it.id == exercise.id }.let { if (it >= 0) it + 1 else null }

                        ExerciseListItem(
                            exercise = exercise,
                            isSupersetMode = isSupersetMode,
                            isSelected = isSelected,
                            selectionOrder = selectionOrder,
                            onClick = {
                                if (isSupersetMode) {
                                    viewModel.toggleExerciseSelection(exercise)
                                } else {
                                    navController.navigate(Screen.ExerciseLogging.createRoute(exercise.id))
                                }
                            },
                            onEdit = {
                                navController.navigate(Screen.ExerciseForm.createRoute(exercise.id))
                            },
                            onDelete = {
                                exerciseToDelete = exercise
                            },
                            onStarToggle = {
                                viewModel.toggleExerciseStarred(exercise)
                            }
                        )
                    }
                }
            }
        }
    }

    // Deletion Confirmation Dialog
    if (exerciseToDelete != null) {
        AlertDialog(
            onDismissRequest = { exerciseToDelete = null },
            title = {
                Text(text = "Delete Exercise?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${exerciseToDelete?.name}\"?\n\nThis action cannot be undone and will permanently delete all logged history for this exercise.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exerciseToDelete?.let { viewModel.deleteExercise(it.id) }
                        exerciseToDelete = null
                    }
                ) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { exerciseToDelete = null }) {
                    Text(text = "Cancel", color = TextPrimary)
                }
            },
            containerColor = SurfaceCards,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun ExerciseSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = { /* Not clickable as a card, text field is interactive */ }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search exercises...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary
                        )
                    }
                    innerTextField()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true
            )
        }
    }
}

@Composable
private fun ExerciseFilterPills(
    muscles: List<String>,
    selectedMuscles: Set<String>,
    onMuscleToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(muscles) { muscle ->
            FilterChip(
                selected = muscle in selectedMuscles,
                onClick = { onMuscleToggle(muscle) },
                label = {
                    Text(
                        text = muscle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor,
                    selectedLabelColor = Color.Black,
                    containerColor = SurfaceCards,
                    labelColor = TextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = muscle in selectedMuscles,
                    borderColor = if (muscle in selectedMuscles) accentColor else TextTertiary,
                    selectedBorderColor = accentColor
                )
            )
        }
    }
}

@Composable
private fun SupersetModeToggle(
    isSupersetMode: Boolean,
    selectedCount: Int,
    maxCount: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label showing selection count when in superset mode
        Text(
            text = if (isSupersetMode) "SELECT $selectedCount/$maxCount EXERCISES" else "EXERCISES",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSupersetMode) accentColor else TextTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        // Superset toggle pill
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(50),
            color = if (isSupersetMode) accentColor else Color.Transparent,
            border = BorderStroke(1.dp, accentColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sync),
                    contentDescription = "Superset Mode",
                    tint = if (isSupersetMode) Color.Black else accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Superset",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSupersetMode) Color.Black else accentColor
                )
            }
        }
    }
}

@Composable
private fun ExerciseListItem(
    exercise: Exercise,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStarToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isSupersetMode: Boolean = false,
    isSelected: Boolean = false,
    selectionOrder: Int? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    GlowCard(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        onClick = onClick,
        onLongClick = if (!isSupersetMode) ({ showMenu = true }) else null,
        backgroundColor = if (isSelected) accentColor.copy(alpha = 0.15f) else SurfaceCards
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Checkbox in superset mode
                if (isSupersetMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor,
                            uncheckedColor = TextTertiary,
                            checkmarkColor = Color.Black
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Exercise name and muscle group
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) accentColor else TextPrimary
                    )
                    Text(
                        text = exercise.targetMuscle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }

                // Star Button for Trophy Case
                if (!isSupersetMode) {
                    androidx.compose.material3.IconButton(
                        onClick = { onStarToggle() }
                    ) {
                        Icon(
                            imageVector = if (exercise.isStarred) androidx.compose.material.icons.Icons.Default.Star else androidx.compose.material.icons.Icons.Default.StarBorder,
                            contentDescription = "Star Exercise",
                            tint = if (exercise.isStarred) Color(0xFFFFD700) else TextTertiary, // Gold or Gray
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Selection order number badge in superset mode
                if (isSupersetMode && selectionOrder != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$selectionOrder",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            // Context menu (only in normal mode)
            if (!isSupersetMode) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SurfaceCards)
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseSelectionScreenPreview() {
    IronLogTheme {
        // This would be a mock preview without actual navigation
    }
}
