package com.example.gymtime.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Set
import com.example.gymtime.data.db.entity.WorkoutWithMuscles
import com.example.gymtime.data.db.dao.SetWithExerciseInfo
import com.example.gymtime.ui.components.ExerciseIcons
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val workouts by viewModel.allWorkouts.collectAsState()
    val selectedWorkout by viewModel.selectedWorkout.collectAsState()
    val selectedWorkoutDetails by viewModel.selectedWorkoutDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    // Handle resume workout navigation
    LaunchedEffect(Unit) {
        viewModel.resumeWorkoutEvent.collect { workoutId ->
            navController.navigate("workout_resume")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Workout History",
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(LocalAppColors.current.backgroundCanvas)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else if (workouts.isEmpty()) {
                EmptyHistoryState(navController)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workouts) { workout ->
                        WorkoutCard(
                            workout = workout,
                            onTap = { viewModel.selectWorkout(workout) },
                            onDelete = { viewModel.deleteWorkout(workout.workout.id) }
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet for workout details
    if (selectedWorkout != null && selectedWorkoutDetails != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelection() },
            containerColor = LocalAppColors.current.surfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.32f)
        ) {
            WorkoutDetailsSheet(
                workout = selectedWorkout!!,
                sets = selectedWorkoutDetails!!,
                onDismiss = { viewModel.clearSelection() },
                onResumeWorkout = { viewModel.resumeWorkout(selectedWorkout!!.workout.id) }
            )
        }
    }
}

@Composable
fun WorkoutCard(
    workout: WorkoutWithMuscles,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    GlowCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onTap,
        onLongClick = { showDeleteDialog = true }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Muscle groups
            if (workout.muscleGroups.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    workout.muscleGroups.take(3).forEach { muscle ->
                        MuscleGroupTag(muscle)
                    }
                    if (workout.muscleGroups.size > 3) {
                        Text(
                            "+${workout.muscleGroups.size - 3}",
                            color = LocalAppColors.current.textTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Date and time
            Text(
                formatWorkoutDateTime(workout.workout.startTime),
                color = LocalAppColors.current.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            // Volume and Sets row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume
                val volume = workout.totalVolume?.toInt() ?: 0
                if (volume > 0) {
                    Text(
                        text = "${formatVolume(volume)} lbs",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Working sets
                val setCount = workout.workingSetCount ?: 0
                if (setCount > 0) {
                    Text(
                        text = "$setCount sets",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Duration and rating row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration if available
                if (workout.workout.endTime != null) {
                    val durationMinutes = ((workout.workout.endTime!!.time - workout.workout.startTime.time) / 1000 / 60).toInt()
                    Text(
                        "$durationMinutes min",
                        color = LocalAppColors.current.textTertiary,
                        fontSize = 12.sp
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Flame rating if available
                workout.workout.rating?.let { rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(rating) {
                            Text(
                                text = "\uD83D\uDD25",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteWorkoutDialog(
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun MuscleGroupTag(muscleGroup: String) {
    val themeColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .height(24.dp)
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(4.dp),
        color = themeColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, themeColor)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                muscleGroup.uppercase(),
                color = themeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun WorkoutDetailsSheet(
    workout: WorkoutWithMuscles,
    sets: List<SetWithExerciseInfo>,
    onDismiss: () -> Unit,
    onResumeWorkout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.surfaceCards)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatWorkoutDateTime(workout.workout.startTime),
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Flame rating if available
                workout.workout.rating?.let { rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(rating) {
                            Text(
                                text = "\uD83D\uDD25",
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            if (workout.workout.endTime != null) {
                val durationMinutes = ((workout.workout.endTime!!.time - workout.workout.startTime.time) / 1000 / 60).toInt()
                val timeRange = "${SimpleDateFormat("h:mm a", Locale.getDefault()).format(workout.workout.startTime)} - ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(workout.workout.endTime!!)}"
                Text(
                    timeRange,
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 12.sp
                )
                Text(
                    "$durationMinutes minutes",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 12.sp
                )
            }

            // Resume Workout button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onResumeWorkout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Resume This Workout",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(
            color = LocalAppColors.current.textTertiary.copy(alpha = 0.2f),
            thickness = 1.dp
        )

        // Exercises list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group sets by exercise
            val groupedByExercise = sets.groupBy { it.exerciseName }

            items(groupedByExercise.size) { index ->
                val (exerciseName, exerciseSets) = groupedByExercise.entries.toList()[index]
                ExerciseSetGroup(exerciseName, exerciseSets)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ExerciseSetGroup(
    exerciseName: String,
    sets: List<SetWithExerciseInfo>,
    modifier: Modifier = Modifier
) {
    val muscleGroup = sets.firstOrNull()?.targetMuscle ?: "Other"
    val accentColor = MaterialTheme.colorScheme.primary

    // Find the best (heaviest) working set for this exercise in this workout
    val bestSet = sets
        .filter { !it.set.isWarmup && it.set.weight != null }
        .maxByOrNull { it.set.weight ?: 0f }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0D0D0D)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Muscle group icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            accentColor.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ExerciseIcons.getIconForMuscle(muscleGroup),
                        contentDescription = muscleGroup,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        exerciseName,
                        color = LocalAppColors.current.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        muscleGroup,
                        color = LocalAppColors.current.textTertiary,
                        fontSize = 11.sp
                    )
                }

                // Set count badge
                Text(
                    text = "${sets.count { !it.set.isWarmup }} sets",
                    color = LocalAppColors.current.textTertiary,
                    fontSize = 11.sp
                )
            }

            // Sets list
            sets.forEachIndexed { index, setInfo ->
                val isBestSet = setInfo == bestSet
                SetRow(
                    set = setInfo.set,
                    setNumber = index + 1,
                    isBestSet = isBestSet
                )
            }
        }
    }
}

@Composable
fun SetRow(
    set: Set,
    setNumber: Int = 0,
    isBestSet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val setText = buildString {
        if (set.weight != null) append("${set.weight.toInt()} lbs")
        if (set.reps != null) {
            if (set.weight != null) append(" × ")
            append("${set.reps} reps")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isBestSet && !set.isWarmup) {
                    Modifier
                        .background(
                            accentColor.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$setNumber.",
                color = LocalAppColors.current.textTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = setText,
                color = if (set.isWarmup) LocalAppColors.current.textTertiary else if (isBestSet) accentColor else LocalAppColors.current.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (isBestSet && !set.isWarmup) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBestSet && !set.isWarmup) {
                Text(
                    text = "BEST",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (set.isWarmup) {
                Text(
                    "WU",
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            accentColor.copy(alpha = 0.2f),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun DeleteWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Workout?") },
        text = { Text("This workout will be permanently removed.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyHistoryState(navController: NavController) {
    val accentColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.backgroundCanvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "No Workouts Yet",
                color = LocalAppColors.current.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Start logging to see your workout history here.",
                color = LocalAppColors.current.textTertiary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Button(
                onClick = { navController.navigate("exercise_selection") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    "Start Your First Workout",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatWorkoutDateTime(date: Date): String {
    val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${dateFormat.format(date)} • ${timeFormat.format(date)}"
}

fun formatVolume(volume: Int): String {
    return java.text.NumberFormat.getNumberInstance(Locale.US).format(volume)
}
