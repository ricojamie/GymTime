package com.example.gymtime.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
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
    val supersetLinks by viewModel.supersetLinks.collectAsState()

    var showExercisePicker by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

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
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LocalAppColors.current.textPrimary)
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
                            tint = if (isSaveEnabled) accentColor else LocalAppColors.current.textTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showExercisePicker = true },
                containerColor = accentColor,
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
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 18.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (dayName.isEmpty()) {
                            Text(
                                text = "Day Name (e.g., Push Day)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalAppColors.current.textTertiary,
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
                color = LocalAppColors.current.textTertiary
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
                        color = LocalAppColors.current.textTertiary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp), // Controlled by ExerciseListItem
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(selectedExercises) { index, exercise ->
                        val isLinkedToNext = supersetLinks.contains(index)
                        val isLinkedToPrev = index > 0 && supersetLinks.contains(index - 1)
                        
                        ExerciseListItem(
                            exercise = exercise,
                            onRemove = { viewModel.removeExercise(exercise.id) },
                            isLinkedToNext = isLinkedToNext,
                            isLinkedToPrev = isLinkedToPrev,
                            onToggleLink = { viewModel.toggleSupersetLink(index) },
                            showLinkButton = index < selectedExercises.size - 1
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
                viewModel.toggleExercise(exercise.id)
                // Keep dialog open to allow multiple selections
            }
        )
    }
}

@Composable
fun ExerciseListItem(
    exercise: Exercise,
    onRemove: () -> Unit,
    isLinkedToNext: Boolean = false,
    isLinkedToPrev: Boolean = false,
    onToggleLink: () -> Unit = {},
    showLinkButton: Boolean = false
) {
    val accentColor = MaterialTheme.colorScheme.primary
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Superset Bar indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .padding(vertical = if (isLinkedToPrev && isLinkedToNext) 0.dp else if (isLinkedToPrev) 0.dp else if (isLinkedToNext) 16.dp else 0.dp)
                    .background(
                        color = if (isLinkedToNext || isLinkedToPrev) accentColor else Color.Transparent,
                        shape = when {
                            isLinkedToNext && isLinkedToPrev -> RoundedCornerShape(0.dp)
                            isLinkedToNext -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            isLinkedToPrev -> RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Surface(
                color = LocalAppColors.current.surfaceCards,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
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
                            color = LocalAppColors.current.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = exercise.targetMuscle,
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = LocalAppColors.current.textTertiary)
                    }
                }
            }
        }
        
        // Link toggle button between items
        if (showLinkButton) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Connecting line for the bar
                if (isLinkedToNext) {
                    Box(
                        modifier = Modifier
                            .padding(start = 0.dp) // Aligned with the indicator bar
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(accentColor)
                    )
                }
                
                Row(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .clickable(onClick = onToggleLink),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLinkedToNext) Icons.Default.LinkOff else Icons.Default.Link,
                        contentDescription = "Toggle Superset",
                        tint = if (isLinkedToNext) Color(0xFFEF5350) else accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLinkedToNext) "Unlink Superset" else "Link as Superset",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLinkedToNext) Color(0xFFEF5350) else accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
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
    val accentColor = MaterialTheme.colorScheme.primary

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
            color = LocalAppColors.current.backgroundCanvas
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LocalAppColors.current.textPrimary)
                    }
                    Text(
                        text = "Add Exercises",
                        style = MaterialTheme.typography.titleLarge,
                        color = LocalAppColors.current.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search
                Surface(
                    color = LocalAppColors.current.surfaceCards,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = LocalAppColors.current.textTertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalAppColors.current.textPrimary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search exercises...", color = LocalAppColors.current.textTertiary)
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
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
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
    val accentColor = MaterialTheme.colorScheme.primary
    Surface(
        color = if (isSelected) accentColor.copy(alpha = 0.1f) else LocalAppColors.current.surfaceCards,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, accentColor) else null,
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
                    color = LocalAppColors.current.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = exercise.targetMuscle,
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = accentColor)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = LocalAppColors.current.textTertiary)
            }
        }
    }
}
