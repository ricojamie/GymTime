package com.example.gymtime.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gymtime.data.db.dao.WorkoutExerciseSummary
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@Composable
fun WorkoutResumeScreen(
    viewModel: WorkoutResumeViewModel = hiltViewModel(),
    onExerciseClick: (Long) -> Unit,
    onAddExerciseClick: () -> Unit,
    onFinishWorkoutClick: (Long) -> Unit
) {
    val todaysExercises by viewModel.todaysExercises.collectAsState()
    val currentWorkout by viewModel.currentWorkout.collectAsState()

    // Observe finish workout event
    LaunchedEffect(Unit) {
        viewModel.finishWorkoutEvent.collect { workoutId ->
            onFinishWorkoutClick(workoutId)
        }
    }

    Scaffold(
        bottomBar = {
            if (todaysExercises.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCards,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Add Exercise Button
                        Button(
                            onClick = onAddExerciseClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceCards,
                                contentColor = PrimaryAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PrimaryAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Exercise",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add Exercise",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Finish Workout Button
                        Button(
                            onClick = { viewModel.finishWorkout() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryAccent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Finish Workout",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Text(
                text = "Today's Workout",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )

            Text(
                text = if (todaysExercises.isEmpty()) "No exercises logged yet" else "${todaysExercises.size} exercises",
                fontSize = 14.sp,
                color = TextSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Exercise List
            if (todaysExercises.isEmpty()) {
                EmptyStateCard(onAddExerciseClick = onAddExerciseClick)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(todaysExercises) { exercise ->
                        ExerciseSummaryCard(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise.exerciseId) }
                        )
                    }

                    // Bottom spacing for bottom bar
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSummaryCard(
    exercise: WorkoutExerciseSummary,
    onClick: () -> Unit
) {
    val isUnstarted = exercise.setCount == 0

    GlowCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isUnstarted) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exerciseName,
                    fontSize = if (isUnstarted) 16.sp else 18.sp,
                    fontWeight = if (isUnstarted) FontWeight.Medium else FontWeight.Bold,
                    color = if (isUnstarted) TextSecondary else Color.White
                )
                Text(
                    text = exercise.targetMuscle.uppercase(),
                    fontSize = 12.sp,
                    color = TextTertiary,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isUnstarted) {
                Text(
                    text = "Not started",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextTertiary
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${exercise.setCount} sets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryAccent
                    )
                    exercise.bestWeight?.let { weight ->
                        Text(
                            text = "Best: ${weight.toInt()} lbs",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(onAddExerciseClick: () -> Unit) {
    GlowCard(
        onClick = onAddExerciseClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = PrimaryAccent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No exercises yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "Tap to add your first exercise",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutResumeScreenPreview() {
    GymTimeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Today's Workout",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
                ExerciseSummaryCard(
                    exercise = WorkoutExerciseSummary(
                        exerciseId = 1,
                        exerciseName = "Barbell Bench Press",
                        targetMuscle = "Chest",
                        setCount = 4,
                        bestWeight = 225f,
                        firstSetTimestamp = System.currentTimeMillis()
                    ),
                    onClick = {}
                )
            }
        }
    }
}
