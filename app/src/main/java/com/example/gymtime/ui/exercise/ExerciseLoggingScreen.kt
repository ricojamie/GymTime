package com.example.gymtime.ui.exercise

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

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
    val personalBestsByReps by viewModel.personalBestsByReps.collectAsState()

    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val editingSet by viewModel.editingSet.collectAsState()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showWorkoutOverview by remember { mutableStateOf(false) }
    var showExerciseHistory by remember { mutableStateOf(false) }

    var personalRecords by remember { mutableStateOf<PersonalRecords?>(null) }
    var exerciseHistory by remember { mutableStateOf<Map<Long, List<com.example.gymtime.data.db.entity.Set>>>(emptyMap()) }

    // Set deletion
    var selectedSetToDelete by remember { mutableStateOf<com.example.gymtime.data.db.entity.Set?>(null) }

    // Set note editing
    var setToAddNote by remember { mutableStateOf<com.example.gymtime.data.db.entity.Set?>(null) }
    var noteText by remember { mutableStateOf("") }

    // Observe navigation events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { workoutId ->
            navController.navigate(Screen.PostWorkoutSummary.createRoute(workoutId))
        }
    }

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

    // Track if timer just finished for animation
    var timerJustFinished by remember { mutableStateOf(false) }

    // Timer countdown
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (true) {
                delay(1000)
                if (restTime > 0) {
                    viewModel.updateRestTime(restTime - 1)
                    // Check if timer just hit 0
                    if (restTime - 1 == 0) {
                        // Vibrate when timer ends
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        timerJustFinished = true
                        viewModel.stopTimer()
                        // Reset animation flag after a short delay
                        delay(2000)
                        timerJustFinished = false
                    }
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
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Timer finished animation
                        val timerPulseScale by animateFloatAsState(
                            targetValue = if (timerJustFinished) 1.15f else 1f,
                            animationSpec = if (timerJustFinished) {
                                infiniteRepeatable(
                                    animation = tween(300),
                                    repeatMode = RepeatMode.Reverse
                                )
                            } else {
                                tween(300)
                            },
                            label = "timer_pulse"
                        )

                        // Timer Pill Action
                        Surface(
                            onClick = { showTimerDialog = true },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (timerJustFinished) PrimaryAccent else SurfaceCards,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (timerJustFinished) 2.dp else 1.dp,
                                color = PrimaryAccent.copy(alpha = if (timerJustFinished) 1f else 0.5f)
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .scale(timerPulseScale)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_timer),
                                    contentDescription = "Timer",
                                    tint = if (timerJustFinished) Color.Black else PrimaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (timerJustFinished) "GO!" else String.format("%d:%02d", restTime / 60, restTime % 60),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (timerJustFinished) Color.Black else PrimaryAccent
                                )
                            }
                        }

                        // Vertical divider
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp),
                            color = TextTertiary.copy(alpha = 0.3f)
                        )

                        // Workout overview icon
                        IconButton(onClick = { showWorkoutOverview = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Workout Overview",
                                tint = PrimaryAccent
                            )
                        }

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
                // Show exercise notes if available
                ex.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PrimaryAccent.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📝",
                                fontSize = 14.sp
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

            // Removed dedicated Timer Row (Moved to TopAppBar)

            Spacer(modifier = Modifier.height(8.dp))

            // Current Set Label
            Text(
                text = "CURRENT SET: ${loggedSets.size + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryAccent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // Warmup Toggle Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    onClick = { viewModel.toggleWarmup() },
                    shape = RoundedCornerShape(50), // Pill shape
                    color = if (isWarmup) PrimaryAccent else Color.Transparent,
                    border = if (isWarmup) null else androidx.compose.foundation.BorderStroke(1.dp, TextTertiary),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isWarmup) {
                            Text(text = "🔥 ", fontSize = 14.sp)
                        }
                        Text(
                            text = "Warmup",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isWarmup) Color.Black else TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log Set Button (or Save Edit if editing)
            if (editingSet != null) {
                // Editing mode - show Save and Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cancelEditing() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextTertiary
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.saveEditedSet()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = weight.isNotBlank() && reps.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            disabledContainerColor = PrimaryAccent.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "SAVE EDIT ✓",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                            color = Color.Black
                        )
                    }
                }
            } else {
                // Normal mode - show LOG SET button
                LogSetButton(
                    onClick = {
                        if (weight.isNotBlank() && reps.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.logSet()
                            viewModel.startTimer() // Auto-start timer
                            viewModel.resetTimerToDefault() // Reset timer to exercise's default
                        }
                    },
                    enabled = weight.isNotBlank() && reps.isNotBlank()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            // Logged Sets List
            LazyColumn(
                modifier = Modifier.weight(1.5f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = loggedSets,
                    key = { _, item -> item.id }
                ) { index, set ->
                    // Check if this set is a PB for its rep count (must be first occurrence)
                    val isPB = !set.isWarmup &&
                        set.weight != null &&
                        set.reps != null &&
                        personalBestsByReps[set.reps]?.let { pb ->
                            pb.maxWeight == set.weight && pb.firstAchievedAt == set.timestamp.time
                        } == true

                    ExerciseSetLogCard(
                        set = set,
                        setNumber = index + 1,
                        isPersonalBest = isPB,
                        onEdit = { selectedSet ->
                            viewModel.startEditingSet(selectedSet)
                        },
                        onDelete = { selectedSet ->
                            selectedSetToDelete = selectedSet
                        },
                        onAddNote = { selectedSet ->
                            noteText = selectedSet.note ?: ""
                            setToAddNote = selectedSet
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

    // Timer Dialog
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = {
                Text(
                    text = "Rest Timer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = String.format("%d:%02d", restTime / 60, restTime % 60),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryAccent
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.updateRestTime(maxOf(0, restTime - 15)) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTertiary)
                        ) {
                            Text("-15s")
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateRestTime(restTime + 15) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextTertiary)
                        ) {
                            Text("+15s")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startTimer()
                        showTimerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Start Timer", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("Close", color = TextTertiary)
                }
            },
            containerColor = SurfaceCards
        )
    }

    // Delete Confirmation Dialog
    selectedSetToDelete?.let { setToDelete ->
        AlertDialog(
            onDismissRequest = { selectedSetToDelete = null },
            title = {
                Text(
                    text = "Delete Set?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this set? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSet(setToDelete.id)
                        selectedSetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C) // Red for destructive action
                    )
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSetToDelete = null }) {
                    Text("Cancel", color = TextTertiary)
                }
            },
            containerColor = SurfaceCards
        )
    }

    // Add/Edit Note Dialog
    setToAddNote?.let { set ->
        AlertDialog(
            onDismissRequest = {
                setToAddNote = null
                noteText = ""
            },
            title = {
                Text(
                    text = if (set.note.isNullOrBlank()) "Add Note" else "Edit Note",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set ${loggedSets.indexOf(set) + 1}: ${set.weight?.toInt()} lbs × ${set.reps} reps",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Enter note...", color = TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryAccent,
                            unfocusedBorderColor = TextTertiary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryAccent
                        ),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSetNote(set, noteText)
                        setToAddNote = null
                        noteText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    setToAddNote = null
                    noteText = ""
                }) {
                    Text("Cancel", color = TextTertiary)
                }
            },
            containerColor = SurfaceCards
        )
    }

    // Finish Workout Dialog
    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = {
                Text(
                    text = "Finish Workout?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to finish this workout session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishWorkout()
                        showFinishDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Finish", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text("Cancel", color = TextTertiary)
                }
            },
            containerColor = SurfaceCards
        )
    }

    // Workout Overview Bottom Sheet
    if (showWorkoutOverview) {
        ModalBottomSheet(
            onDismissRequest = { showWorkoutOverview = false },
            containerColor = SurfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            WorkoutOverviewContent(
                exercises = workoutOverview,
                currentExerciseId = exercise?.id,
                workoutStats = viewModel.getWorkoutStats(),
                onExerciseClick = { exerciseId ->
                    showWorkoutOverview = false
                    if (exerciseId != exercise?.id) {
                        navController.navigate(Screen.ExerciseLogging.createRoute(exerciseId)) {
                            launchSingleTop = true
                        }
                    }
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
            containerColor = SurfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            exercise?.let { ex ->
                ExerciseHistoryContent(
                    exerciseName = ex.name,
                    personalRecords = personalRecords,
                    history = exerciseHistory,
                    onDismiss = { showExerciseHistory = false }
                )
            }
        }
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
        modifier = modifier.height(100.dp),
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

            // Input Area - Using BasicTextField for better control
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Fill remaining space
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 34.sp,
                        color = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(PrimaryAccent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
    val keyboardController = LocalSoftwareKeyboardController.current

    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            isPressed = true
            keyboardController?.hide()
            scope.launch {
                delay(100)
                isPressed = false
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExerciseSetLogCard(
    set: com.example.gymtime.data.db.entity.Set,
    setNumber: Int,
    isPersonalBest: Boolean = false,
    onEdit: (com.example.gymtime.data.db.entity.Set) -> Unit = {},
    onDelete: (com.example.gymtime.data.db.entity.Set) -> Unit = {},
    onAddNote: (com.example.gymtime.data.db.entity.Set) -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showContextMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isPersonalBest) Color(0xFF2D4A1C) else SurfaceCards
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isPersonalBest) androidx.compose.foundation.BorderStroke(1.dp, PrimaryAccent) else null
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
                // Show trophy for personal best
                if (isPersonalBest) {
                    Text(
                        text = "🏆",
                        fontSize = 18.sp
                    )
                } else {
                    Text(
                        text = "$setNumber",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }

                set.weight?.let {
                    Text(
                        text = "$it LBS",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPersonalBest) PrimaryAccent else TextPrimary,
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
                        color = if (isPersonalBest) PrimaryAccent else TextPrimary,
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

                // PB Badge
                if (isPersonalBest) {
                    Text(
                        text = "PB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(
                                PrimaryAccent,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Note indicator
                set.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "📝",
                        fontSize = 14.sp
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

        // Show note text if present
        set.note?.takeIf { it.isNotBlank() }?.let { noteText ->
            Text(
                text = noteText,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
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
                text = { Text(if (set.note.isNullOrBlank()) "Add Note" else "Edit Note") },
                onClick = {
                    showContextMenu = false
                    onAddNote(set)
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