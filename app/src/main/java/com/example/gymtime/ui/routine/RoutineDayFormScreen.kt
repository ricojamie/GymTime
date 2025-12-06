package com.example.gymtime.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDayFormScreen(
    navController: NavController,
    viewModel: RoutineDayFormViewModel = hiltViewModel()
) {
    val dayName by viewModel.dayName.collectAsState()
    val selectedExercises by viewModel.selectedExercises.collectAsState(initial = emptyList())
    val availableExercises by viewModel.availableExercises.collectAsState(initial = emptyList())
    val selectedExerciseIds by viewModel.selectedExerciseIds.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsState()

    var showExercisePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect {
            navController.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Edit Day" else "New Day",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveDay() },
                        enabled = isSaveEnabled
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (isSaveEnabled) PrimaryAccent else TextTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showExercisePicker = true },
                containerColor = PrimaryAccent,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name Input
            GlowCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = dayName,
                    onValueChange = { viewModel.updateDayName(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontSize = 18.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (dayName.isEmpty()) {
                            Text(
                                text = "Day Name (e.g., Push Day)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextTertiary,
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    },
                    singleLine = true
                )
            }

            Text(
                text = "EXERCISES (${selectedExercises.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = TextTertiary
            )

            if (selectedExercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No exercises added yet.",
                        color = TextTertiary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(selectedExercises) { exercise ->
                        ExerciseListItem(
                            exercise = exercise,
                            onRemove = { viewModel.removeExercise(exercise.id) }
                        )
                    }
                }
            }
        }
    }

    if (showExercisePicker) {
        ExercisePickerDialog(
            availableExercises = availableExercises,
            selectedExerciseIds = selectedExerciseIds,
            onDismiss = { showExercisePicker = false },
            onExerciseSelected = { exercise ->
                viewModel.addExercise(exercise.id)
                // Keep dialog open to allow multiple selections
            }
        )
    }
}

@Composable
fun ExerciseListItem(
    exercise: Exercise,
    onRemove: () -> Unit
) {
    Surface(
        color = SurfaceCards,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = exercise.targetMuscle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextTertiary)
            }
        }
    }
}

@Composable
fun ExercisePickerDialog(
    availableExercises: List<Exercise>,
    selectedExerciseIds: Set<Long>,
    onDismiss: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredExercises = remember(searchQuery, availableExercises) {
        if (searchQuery.isBlank()) availableExercises
        else availableExercises.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.targetMuscle.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundCanvas
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Add Exercises",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search
                Surface(
                    color = SurfaceCards,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search exercises...", color = TextTertiary)
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                // List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredExercises) { exercise ->
                        ExercisePickerItem(
                            exercise = exercise,
                            isSelected = exercise.id in selectedExerciseIds,
                            onClick = { onExerciseSelected(exercise) }
                        )
                    }
                }
                
                // Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 32.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ExercisePickerItem(
    exercise: Exercise,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) PrimaryAccent.copy(alpha = 0.1f) else SurfaceCards,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, PrimaryAccent) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = exercise.targetMuscle,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = PrimaryAccent)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = TextTertiary)
            }
        }
    }
}
