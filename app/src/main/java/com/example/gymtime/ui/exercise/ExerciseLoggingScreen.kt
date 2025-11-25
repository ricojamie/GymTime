package com.example.gymtime.ui.exercise

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.ui.theme.GradientStart
import com.example.gymtime.ui.theme.GradientEnd
import com.example.gymtime.ui.theme.GymTimeTheme
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.SurfaceCards
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary
import com.example.gymtime.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLoggingScreen(
    navController: NavController,
    viewModel: ExerciseLoggingViewModel = hiltViewModel()
) {
    val exercise by viewModel.exercise.collectAsState()
    val currentWorkout by viewModel.currentWorkout.collectAsState()
    val loggedSets by viewModel.loggedSets.collectAsState()
    val weight by viewModel.weight.collectAsState()
    val reps by viewModel.reps.collectAsState()
    val rpe by viewModel.rpe.collectAsState()
    val restTime by viewModel.restTime.collectAsState()
    val isWarmup by viewModel.isWarmup.collectAsState()
    val lastWorkoutSets by viewModel.lastWorkoutSets.collectAsState()
    val workoutOverview by viewModel.workoutOverview.collectAsState()

    val isTimerRunning by viewModel.isTimerRunning.collectAsState()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showWorkoutOverview by remember { mutableStateOf(false) }
    var showExerciseHistory by remember { mutableStateOf(false) }
    var personalRecords by remember { mutableStateOf<PersonalRecords?>(null) }
    var exerciseHistory by remember { mutableStateOf<Map<Long, List<com.example.gymtime.data.db.entity.Set>>>(emptyMap()) }

    // Set editing/deletion
    var selectedSetToEdit by remember { mutableStateOf<com.example.gymtime.data.db.entity.Set?>(null) }
    var selectedSetToDelete by remember { mutableStateOf<com.example.gymtime.data.db.entity.Set?>(null) }
    var editWeight by remember { mutableStateOf("") }
    var editReps by remember { mutableStateOf("") }

    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Load workout overview when bottom sheet opens
    LaunchedEffect(showWorkoutOverview) {
        if (showWorkoutOverview) {
            viewModel.loadWorkoutOverview()
        }
    }

    // Load exercise history when bottom sheet opens
    LaunchedEffect(showExerciseHistory) {
        if (showExerciseHistory) {
            personalRecords = viewModel.getPersonalRecords()
            exerciseHistory = viewModel.getExerciseHistory()
        }
    }

    // Get last workout data for inline "Last:" display
    val lastWeight = lastWorkoutSets.firstOrNull()?.weight?.toString()
    val lastReps = lastWorkoutSets.firstOrNull()?.reps?.toString()

    // Timer countdown
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (true) {
                delay(1000)
                if (restTime > 0) {
                    viewModel.updateRestTime(restTime - 1)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            exercise?.let { ex ->
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = ex.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = ex.targetMuscle,
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryAccent,
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.navigate(Screen.ExerciseSelection.route)
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Workout overview icon
                        IconButton(onClick = { showWorkoutOverview = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Workout Overview",
                                tint = PrimaryAccent
                            )
                        }

                        // Vertical divider
                        Divider(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .padding(horizontal = 4.dp),
                            color = TextTertiary.copy(alpha = 0.3f)
                        )

                        // Exercise history icon
                        IconButton(onClick = { showExerciseHistory = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Exercise History",
                                tint = PrimaryAccent
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                )
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            exercise?.let { ex ->
                Spacer(modifier = Modifier.height(8.dp))

            // Timer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%d:%02d", restTime / 60, restTime % 60),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.startTimer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("START", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.updateRestTime(maxOf(0, restTime - 15)) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextTertiary
                        )
                    ) {
                        Text("-15s")
                    }
                    OutlinedButton(
                        onClick = { viewModel.updateRestTime(restTime + 15) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextTertiary
                        )
                    ) {
                        Text("+15s")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Current Set Label
            Text(
                text = "CURRENT SET: ${loggedSets.size + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weight Input
                InputCard(
                    label = "WEIGHT",
                    value = weight,
                    onValueChange = { viewModel.updateWeight(it) },
                    modifier = Modifier.weight(1f),
                    lastValue = lastWeight?.let { "$it lbs" }
                )

                // Reps Input
                InputCard(
                    label = "REPS",
                    value = reps,
                    onValueChange = { viewModel.updateReps(it) },
                    modifier = Modifier.weight(1f),
                    lastValue = lastReps?.let { "$it reps" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Warmup Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isWarmup,
                    onCheckedChange = { viewModel.toggleWarmup() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryAccent,
                        uncheckedColor = TextTertiary
                    )
                )
                Text(
                    text = "Warmup",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Log Set Button
            LogSetButton(
                onClick = {
                    if (weight.isNotBlank() && reps.isNotBlank()) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.logSet()
                        viewModel.updateRestTime(90) // Reset timer
                    }
                },
                enabled = weight.isNotBlank() && reps.isNotBlank()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Session Log Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SESSION LOG",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextTertiary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${loggedSets.size} sets done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryAccent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Logged Sets List
            LazyColumn(
                modifier = Modifier.weight(1.5f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(loggedSets.size) { index ->
                    ExerciseSetLogCard(
                        set = loggedSets[index],
                        setNumber = index + 1,
                        onEdit = { selectedSet ->
                            selectedSetToEdit = selectedSet
                            editWeight = selectedSet.weight?.toString() ?: ""
                            editReps = selectedSet.reps?.toString() ?: ""
                        },
                        onDelete = { selectedSet ->
                            selectedSetToDelete = selectedSet
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate(Screen.ExerciseSelection.route) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryAccent
                    )
                ) {
                    Text("Add Exercise", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showFinishDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent.copy(alpha = 0.2f),
                        contentColor = PrimaryAccent
                    )
                ) {
                    Text("Finish Workout", fontWeight = FontWeight.Bold)
                }
            }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryAccent)
                }
            }
        }
    }

    // Workout Overview Bottom Sheet
    if (showWorkoutOverview) {
        ModalBottomSheet(
            onDismissRequest = { showWorkoutOverview = false },
            containerColor = SurfaceCards
        ) {
            WorkoutOverviewContent(
                exercises = workoutOverview,
                currentExerciseId = exercise?.id,
                workoutStats = viewModel.getWorkoutStats(),
                onExerciseClick = { exerciseId ->
                    showWorkoutOverview = false
                    if (exerciseId != exercise?.id) {
                        // Navigate to the selected exercise, replacing current logging screen
                        navController.navigate(
                            Screen.ExerciseLogging.createRoute(exerciseId)
                        ) {
                            // Pop the current exercise logging screen before navigating
                            popUpTo(Screen.ExerciseLogging.route) {
                                inclusive = true
                            }
                        }
                    }
                    // If clicking the same exercise, just close the sheet
                },
                onAddExercise = {
                    showWorkoutOverview = false
                    navController.navigate(Screen.ExerciseSelection.route)
                }
            )
        }
    }

    // Exercise History Bottom Sheet
    if (showExerciseHistory) {
        ModalBottomSheet(
            onDismissRequest = { showExerciseHistory = false },
            containerColor = SurfaceCards
        ) {
            ExerciseHistoryContent(
                exerciseName = exercise?.name ?: "",
                personalRecords = personalRecords,
                history = exerciseHistory,
                onDismiss = { showExerciseHistory = false }
            )
        }
    }

    // Finish Workout Confirmation Dialog
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish Workout?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to end this workout session?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.finishWorkout()
                        scope.launch {
                            delay(300)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                        showFinishDialog = false
                    }
                ) {
                    Text("Finish", color = PrimaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Cancel", color = TextTertiary)
                }
            }
        )
    }

    // Edit Set Dialog
    if (selectedSetToEdit != null) {
        AlertDialog(
            onDismissRequest = { selectedSetToEdit = null },
            title = { Text("Edit Set", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = editWeight,
                        onValueChange = { editWeight = it },
                        label = { Text("Weight (lbs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editReps,
                        onValueChange = { editReps = it },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedSetToEdit?.let { set ->
                            val updatedSet = set.copy(
                                weight = editWeight.toFloatOrNull(),
                                reps = editReps.toIntOrNull()
                            )
                            viewModel.updateSet(updatedSet)
                            selectedSetToEdit = null
                        }
                    }
                ) {
                    Text("Save", color = PrimaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSetToEdit = null }) {
                    Text("Cancel", color = TextTertiary)
                }
            }
        )
    }

    // Delete Set Confirmation Dialog
    if (selectedSetToDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedSetToDelete = null },
            title = { Text("Delete Set?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this set? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedSetToDelete?.let { set ->
                            viewModel.deleteSet(set)
                            selectedSetToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSetToDelete = null }) {
                    Text("Cancel", color = TextTertiary)
                }
            }
        )
    }
}

@Composable
private fun InputCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lastValue: String? = null
) {
    Card(
        modifier = modifier.height(140.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCards),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header row with label and last value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    letterSpacing = 1.sp
                )

                lastValue?.let {
                    Text(
                        text = "Last: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 48.sp
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LogSetButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            isPressed = true
            scope.launch {
                delay(100)
                isPressed = false
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryAccent,
            disabledContainerColor = PrimaryAccent.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "LOG SET ✓",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun ExerciseSetLogCard(
    set: com.example.gymtime.data.db.entity.Set,
    setNumber: Int,
    onEdit: (com.example.gymtime.data.db.entity.Set) -> Unit = {},
    onDelete: (com.example.gymtime.data.db.entity.Set) -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showContextMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = SurfaceCards),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$setNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTertiary,
                    fontWeight = FontWeight.Bold
                )

                set.weight?.let {
                    Text(
                        text = "$it LBS",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "/",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextTertiary
                    )
                }

                set.reps?.let {
                    Text(
                        text = "$it REPS",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (set.isWarmup) {
                    Text(
                        text = "WU",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryAccent,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(
                                PrimaryAccent.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Three-dot menu icon (affordance hint)
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = TextTertiary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    showContextMenu = false
                    onEdit(set)
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showContextMenu = false
                    onDelete(set)
                }
            )
        }
    }
}

@Composable
private fun WorkoutOverviewContent(
    exercises: List<com.example.gymtime.data.db.dao.WorkoutExerciseSummary>,
    currentExerciseId: Long?,
    workoutStats: WorkoutStats,
    onExerciseClick: (Long) -> Unit,
    onAddExercise: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Current Workout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Duration: ${workoutStats.duration}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
            Text(
                text = "·",
                color = TextTertiary
            )
            Text(
                text = "${workoutStats.totalSets} sets",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exercise list
        exercises.forEach { summary ->
            val isActive = summary.exerciseId == currentExerciseId

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) PrimaryAccent.copy(alpha = 0.1f) else Color(0xFF0D0D0D)
                ),
                shape = RoundedCornerShape(12.dp),
                onClick = { onExerciseClick(summary.exerciseId) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status indicator
                        Text(
                            text = if (isActive) "→" else "✓",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isActive) PrimaryAccent else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Column {
                            Text(
                                text = summary.exerciseName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) PrimaryAccent else TextPrimary
                            )
                            Text(
                                text = summary.targetMuscle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${summary.setCount} sets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                        summary.bestWeight?.let { weight ->
                            Text(
                                text = "${weight.toInt()} lbs",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add Exercise button
        OutlinedButton(
            onClick = onAddExercise,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PrimaryAccent
            )
        ) {
            Text("+ Add Another Exercise", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExerciseHistoryContent(
    exerciseName: String,
    personalRecords: PersonalRecords?,
    history: Map<Long, List<com.example.gymtime.data.db.entity.Set>>,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PERSONAL RECORDS",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PR Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Heaviest Weight PR
            personalRecords?.heaviestWeight?.let { set ->
                PRBadge(
                    title = "Heaviest Weight",
                    value = "${set.weight?.toInt()} lbs",
                    subtitle = "×${set.reps} reps",
                    modifier = Modifier.weight(1f)
                )
            }

            // Best E1RM PR
            personalRecords?.bestE1RM?.let { (set, e1rm) ->
                PRBadge(
                    title = "Best E1RM",
                    value = "${e1rm.toInt()} lbs",
                    subtitle = "from ${set.weight?.toInt()}×${set.reps}",
                    modifier = Modifier.weight(1f)
                )
            }

            // TODO: Best E10RM (premium feature - show lock icon for free users)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "RECENT HISTORY",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // History list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            history.entries.take(10).forEach { (workoutId, sets) ->
                item {
                    WorkoutHistoryCard(sets = sets)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Close button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Close", color = TextTertiary)
        }
    }
}

@Composable
private fun PRBadge(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = PrimaryAccent.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏆",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryAccent
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun WorkoutHistoryCard(
    sets: List<com.example.gymtime.data.db.entity.Set>
) {
    val firstSet = sets.firstOrNull()
    val dateStr = firstSet?.timestamp?.let {
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
        formatter.format(it)
    } ?: "Unknown date"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D0D0D)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            sets.forEachIndexed { index, set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Set ${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Text(
                        text = buildString {
                            set.weight?.let { append("${it.toInt()} lbs") }
                            append(" × ")
                            set.reps?.let { append("$it reps") }
                            if (set.isWarmup) append(" (WU)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
                if (index < sets.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseLoggingScreenPreview() {
    GymTimeTheme {
        // Preview would require mock NavController
    }
}
