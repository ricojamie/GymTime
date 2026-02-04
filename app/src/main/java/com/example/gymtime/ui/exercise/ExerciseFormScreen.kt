package com.example.gymtime.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseFormScreen(
    navController: NavController,
    viewModel: ExerciseFormViewModel = hiltViewModel()
) {
    val exerciseName by viewModel.exerciseName.collectAsState()
    val targetMuscle by viewModel.targetMuscle.collectAsState()
    val logType by viewModel.logType.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val defaultRestSeconds by viewModel.defaultRestSeconds.collectAsState()
    val availableMuscles by viewModel.availableMuscles.collectAsState(initial = emptyList())
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsState()
    val isFromWorkout by viewModel.isFromWorkout.collectAsState()

    var showMuscleDropdown by remember { mutableStateOf(false) }
    var showLogTypeDropdown by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    // Observe save success event and navigate accordingly
    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect { newExerciseId ->
            if (newExerciseId != null && isFromWorkout) {
                // New exercise created during workout - go to logging screen
                navController.navigate(Screen.ExerciseLogging.createRoute(newExerciseId)) {
                    popUpTo(Screen.ExerciseSelection.route) { inclusive = false }
                }
            } else {
                // Edit mode or not from workout - go back
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Exercise" else "Create Exercise",
                        color = LocalAppColors.current.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LocalAppColors.current.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveExercise() },
                        enabled = isSaveEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (isSaveEnabled) accentColor else LocalAppColors.current.textTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Exercise Name Input
            Text(
                text = "EXERCISE NAME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = LocalAppColors.current.textTertiary
            )

            GlowCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = exerciseName,
                    onValueChange = { viewModel.updateExerciseName(it.titleCase()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 18.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    decorationBox = { innerTextField ->
                        if (exerciseName.isEmpty()) {
                            Text(
                                text = "Enter exercise name...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalAppColors.current.textTertiary
                            )
                        }
                        innerTextField()
                    },
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Target Muscle Dropdown
            Text(
                text = "TARGET MUSCLE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = LocalAppColors.current.textTertiary
            )

            Box {
                GlowCard(
                    onClick = { showMuscleDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = targetMuscle.ifEmpty { "Select muscle group..." },
                            fontSize = 18.sp,
                            color = if (targetMuscle.isEmpty()) LocalAppColors.current.textTertiary else LocalAppColors.current.textPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = LocalAppColors.current.textTertiary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMuscleDropdown,
                    onDismissRequest = { showMuscleDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(LocalAppColors.current.surfaceCards)
                ) {
                    availableMuscles.forEach { muscle ->
                        DropdownMenuItem(
                            text = { Text(muscle, color = LocalAppColors.current.textPrimary) },
                            onClick = {
                                viewModel.updateTargetMuscle(muscle)
                                showMuscleDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log Type Dropdown
            Text(
                text = "LOG TYPE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = LocalAppColors.current.textTertiary
            )

            Box {
                GlowCard(
                    onClick = { showLogTypeDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = logType.displayName,
                                fontSize = 18.sp,
                                color = LocalAppColors.current.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = logType.description,
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = LocalAppColors.current.textTertiary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showLogTypeDropdown,
                    onDismissRequest = { showLogTypeDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(LocalAppColors.current.surfaceCards)
                ) {
                    LogType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(type.displayName, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Medium)
                                    Text(type.description, color = LocalAppColors.current.textSecondary, fontSize = 12.sp)
                                }
                            },
                            onClick = {
                                viewModel.updateLogType(type)
                                showLogTypeDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Default Rest Time Input
            Text(
                text = "DEFAULT REST TIME (SECONDS)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = LocalAppColors.current.textTertiary
            )

            GlowCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = defaultRestSeconds,
                    onValueChange = { viewModel.updateDefaultRestSeconds(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 18.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (defaultRestSeconds.isEmpty()) {
                            Text(
                                text = "90",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalAppColors.current.textTertiary
                            )
                        }
                        innerTextField()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notes Input (Optional)
            Text(
                text = "NOTES (OPTIONAL)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = LocalAppColors.current.textTertiary
            )

            GlowCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = notes,
                    onValueChange = { viewModel.updateNotes(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = LocalAppColors.current.textPrimary
                    ),
                    decorationBox = { innerTextField ->
                        if (notes.isEmpty()) {
                            Text(
                                text = "Add form cues, equipment notes, etc...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalAppColors.current.textTertiary
                            )
                        }
                        innerTextField()
                    },
                    maxLines = 5
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// Extension for LogType display
private val LogType.displayName: String
    get() = when (this) {
        LogType.WEIGHT_REPS -> "Weight + Reps"
        LogType.REPS_ONLY -> "Reps Only"
        LogType.DURATION -> "Duration"
        LogType.WEIGHT_DISTANCE -> "Weight + Distance"
        LogType.DISTANCE_TIME -> "Distance + Time"
    }

private val LogType.description: String
    get() = when (this) {
        LogType.WEIGHT_REPS -> "Barbell, dumbbell exercises"
        LogType.REPS_ONLY -> "Bodyweight exercises"
        LogType.DURATION -> "Planks, cardio time"
        LogType.WEIGHT_DISTANCE -> "Sled push, farmer's carry"
        LogType.DISTANCE_TIME -> "Running, cycling, rowing"
    }

// Extension to capitalize first letter of each word
private fun String.titleCase(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
