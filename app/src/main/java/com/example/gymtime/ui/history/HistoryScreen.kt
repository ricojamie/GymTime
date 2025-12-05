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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Workout History",
                        color = TextPrimary,
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
                .background(BackgroundCanvas)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryAccent)
                }
            } else if (workouts.isEmpty()) {
                EmptyHistoryState(navController)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
            containerColor = SurfaceCards,
            scrimColor = Color.Black.copy(alpha = 0.32f)
        ) {
            WorkoutDetailsSheet(
                workout = selectedWorkout!!,
                sets = selectedWorkoutDetails!!,
                onDismiss = { viewModel.clearSelection() }
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
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Date and time
            Text(
                formatWorkoutDateTime(workout.workout.startTime),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

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
                        color = TextTertiary,
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
    Surface(
        modifier = Modifier
            .height(24.dp)
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(4.dp),
        color = PrimaryAccent.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryAccent)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                muscleGroup.uppercase(),
                color = PrimaryAccent,
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCards)
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
                    color = TextPrimary,
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
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                Text(
                    "$durationMinutes minutes",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(
            color = TextTertiary.copy(alpha = 0.2f),
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            exerciseName,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        sets.forEach { setInfo ->
            SetRow(setInfo.set)
        }
    }
}

@Composable
fun SetRow(set: Set, modifier: Modifier = Modifier) {
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
            .padding(start = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setText,
            color = if (set.isWarmup) TextTertiary else TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        if (set.isWarmup) {
            Text(
                "[Warmup]",
                color = TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCanvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "No Workouts Yet",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Start logging to see your workout history here.",
                color = TextTertiary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Button(
                onClick = { navController.navigate("exercise_selection") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccent
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
