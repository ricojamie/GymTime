package com.example.gymtime.ui.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.DistanceUnit
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.navigation.Screen
import com.example.gymtime.navigation.navigateHomeAndClearStack
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
    val defaultDistanceUnit by viewModel.defaultDistanceUnit.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val defaultRestSeconds by viewModel.defaultRestSeconds.collectAsState()
    val repTarget by viewModel.repTarget.collectAsState()
    val availableMuscles by viewModel.availableMuscles.collectAsState(initial = emptyList())
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsState()
    val isFromWorkout by viewModel.isFromWorkout.collectAsState()

    var showMuscleDropdown by remember { mutableStateOf(false) }
    var showLogTypeDropdown by remember { mutableStateOf(false) }
    var showDistanceUnitDropdown by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    // When editing, start with the advanced section open so nothing is hidden.
    LaunchedEffect(isEditMode) {
        if (isEditMode) showMoreOptions = true
    }

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
                    IconButton(onClick = { navController.navigateHomeAndClearStack() }) {
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
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ---------- Essentials ----------
            FieldLabel("EXERCISE NAME")
            FormTextFieldCard(
                value = exerciseName,
                onValueChange = { viewModel.updateExerciseName(it.titleCase()) },
                placeholder = "Enter exercise name...",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            FieldLabel("LOG TYPE")
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
                        val selectedType = logType
                        if (selectedType == null) {
                            Text(
                                text = "Select log type...",
                                fontSize = 18.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        } else {
                            Column {
                                Text(
                                    text = selectedType.displayName,
                                    fontSize = 18.sp,
                                    color = LocalAppColors.current.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = selectedType.description,
                                    fontSize = 12.sp,
                                    color = LocalAppColors.current.textSecondary
                                )
                            }
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

            FieldLabel("TARGET MUSCLE")
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

            // ---------- More options (collapsible) ----------
            val chevronRotation by animateFloatAsState(
                targetValue = if (showMoreOptions) 180f else 0f,
                label = "moreOptionsChevron"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMoreOptions = !showMoreOptions }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORE OPTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = LocalAppColors.current.textTertiary
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (showMoreOptions) "Collapse" else "Expand",
                    tint = LocalAppColors.current.textTertiary,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = showMoreOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FieldLabel("DEFAULT REST TIME (SECONDS)")
                    FormTextFieldCard(
                        value = defaultRestSeconds,
                        onValueChange = { viewModel.updateDefaultRestSeconds(it) },
                        placeholder = "90",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    if (logType?.usesRepTarget == true) {
                        FieldLabel("REP TARGET (OPTIONAL)")
                        FormTextFieldCard(
                            value = repTarget,
                            onValueChange = { value ->
                                viewModel.updateRepTarget(value.filter { it.isDigit() }.take(3))
                            },
                            placeholder = "Example: 10",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    if (logType?.usesDistanceUnit == true) {
                        FieldLabel("DISTANCE TYPE")
                        Box {
                            GlowCard(
                                onClick = { showDistanceUnitDropdown = true },
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
                                            text = defaultDistanceUnit.displayName,
                                            fontSize = 18.sp,
                                            color = LocalAppColors.current.textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = defaultDistanceUnit.description,
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
                                expanded = showDistanceUnitDropdown,
                                onDismissRequest = { showDistanceUnitDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(LocalAppColors.current.surfaceCards)
                            ) {
                                DistanceUnit.entries.forEach { unit ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    unit.displayName,
                                                    color = LocalAppColors.current.textPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    unit.description,
                                                    color = LocalAppColors.current.textSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateDefaultDistanceUnit(unit)
                                            showDistanceUnitDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    FieldLabel("NOTES (OPTIONAL)")
                    FormTextFieldCard(
                        value = notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        placeholder = "Add form cues, equipment notes, etc...",
                        singleLine = false,
                        maxLines = 5,
                        fieldModifier = Modifier.height(120.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = LocalAppColors.current.textTertiary
    )
}

@Composable
private fun FormTextFieldCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    fieldModifier: Modifier = Modifier
) {
    GlowCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .padding(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = LocalAppColors.current.textPrimary,
                fontSize = 18.sp
            ),
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(LocalAppColors.current.cursor),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalAppColors.current.textTertiary,
                        fontSize = 18.sp
                    )
                }
                innerTextField()
            },
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines
        )
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
        LogType.WEIGHT_TIME -> "Weight + Time"
        LogType.CALORIES_TIME -> "Calories + Time"
    }

private val LogType.description: String
    get() = when (this) {
        LogType.WEIGHT_REPS -> "Barbell, dumbbell exercises"
        LogType.REPS_ONLY -> "Bodyweight exercises"
        LogType.DURATION -> "Planks, cardio time"
        LogType.WEIGHT_DISTANCE -> "Sled push, farmer's carry"
        LogType.DISTANCE_TIME -> "Running, cycling, rowing"
        LogType.WEIGHT_TIME -> "Loaded carries, timed holds"
        LogType.CALORIES_TIME -> "Bike, rower, treadmill calorie goal"
    }

private val LogType.usesDistanceUnit: Boolean
    get() = this == LogType.WEIGHT_DISTANCE || this == LogType.DISTANCE_TIME

private val LogType.usesRepTarget: Boolean
    get() = this == LogType.WEIGHT_REPS || this == LogType.REPS_ONLY

private val DistanceUnit.displayName: String
    get() = when (this) {
        DistanceUnit.METERS -> "Meters"
        DistanceUnit.KILOMETERS -> "Kilometers"
        DistanceUnit.YARDS -> "Yards"
        DistanceUnit.FEET -> "Feet"
        DistanceUnit.MILES -> "Miles"
        DistanceUnit.STEPS -> "Steps"
        DistanceUnit.FLOORS -> "Floors"
    }

private val DistanceUnit.description: String
    get() = when (this) {
        DistanceUnit.METERS -> "Track work and short cardio pieces"
        DistanceUnit.KILOMETERS -> "Longer runs and rides"
        DistanceUnit.YARDS -> "Pools, turf, and field work"
        DistanceUnit.FEET -> "Stair machines and short carries"
        DistanceUnit.MILES -> "Road runs and outdoor cardio"
        DistanceUnit.STEPS -> "Pedometer and step-based cardio"
        DistanceUnit.FLOORS -> "Climbers and stair sessions"
    }

// Extension to capitalize first letter of each word
private fun String.titleCase(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
