package com.example.gymtime.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    val accentColor = MaterialTheme.colorScheme.primary

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
                    color = LocalAppColors.current.surfaceCards,
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
                                containerColor = LocalAppColors.current.surfaceCards,
                                contentColor = accentColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accentColor)
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
                                containerColor = accentColor,
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
                color = LocalAppColors.current.textSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Exercise List
            if (todaysExercises.isEmpty()) {
                EmptyStateCard(onAddExerciseClick = onAddExerciseClick)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp), // Removed spacing to make connector continuous
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(todaysExercises.size) { index ->
                        val exercise = todaysExercises[index]
                        val isSuperset = exercise.supersetGroupId != null
                        
                        val prevExercise = todaysExercises.getOrNull(index - 1)
                        val nextExercise = todaysExercises.getOrNull(index + 1)
                        
                        val isConnectedTop = isSuperset && prevExercise?.supersetGroupId == exercise.supersetGroupId
                        val isConnectedBottom = isSuperset && nextExercise?.supersetGroupId == exercise.supersetGroupId

                        Box(modifier = Modifier.padding(bottom = 12.dp)) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                // Connector Column
                                if (isSuperset) {
                                    Column(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .fillMaxHeight(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top Line
                                        if (isConnectedTop) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                        
                                        // Icon
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.example.gymtime.R.drawable.ic_sync),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        
                                        // Bottom Line
                                        if (isConnectedBottom) {
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp))
                                }

                                ExerciseSummaryCard(
                                    exercise = exercise,
                                    onClick = { onExerciseClick(exercise.exerciseId) }
                                )
                            }
                        }
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
    val accentColor = MaterialTheme.colorScheme.primary

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
                    color = if (isUnstarted) LocalAppColors.current.textSecondary else Color.White
                )
                Text(
                    text = exercise.targetMuscle.uppercase(),
                    fontSize = 12.sp,
                    color = LocalAppColors.current.textTertiary,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isUnstarted) {
                Text(
                    text = "Not started",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = LocalAppColors.current.textTertiary
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${exercise.setCount} sets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor
                    )
                    exercise.bestWeight?.let { weight ->
                        Text(
                            text = "Best: ${weight.toInt()} lbs",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textSecondary,
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
    val accentColor = MaterialTheme.colorScheme.primary
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
                tint = accentColor,
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
                color = LocalAppColors.current.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun WorkoutResumeScreenPreview() {
    IronLogTheme {
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
                        firstSetTimestamp = System.currentTimeMillis(),
                        supersetGroupId = null
                    ),
                    onClick = {}
                )
            }
        }
    }
}
