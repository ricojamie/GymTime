package com.example.gymtime.ui.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.dao.RoutineDayWithExercises
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDayStartScreen(
    navController: NavController,
    viewModel: RoutineDayStartViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState(initial = "")
    val daysWithExercises by viewModel.daysWithExercises.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        viewModel.startWorkoutEvent.collect { firstExerciseId ->
            // Navigate to exercise logging for the first exercise
            // Note: In a real app, we might want a 'Workout Overview' screen first
            // or pass the workoutId context.
            // Since I'm limited by the current navigation structure, 
            // I'll assume navigating to ExerciseLogging with the first exercise is the intention.
            // The ExerciseLogging screen should ideally know it's part of an active workout (which it does via global state or VM).
            navController.navigate(Screen.ExerciseLogging.createRoute(firstExerciseId)) {
                // Clear back stack so we don't come back to this start screen easily
                popUpTo(Screen.Home.route)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Start Workout",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            routineName,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "SELECT A DAY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = TextTertiary,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (daysWithExercises.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This routine has no days setup yet.",
                        color = TextTertiary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(daysWithExercises) { dayWithExercises ->
                        RoutineDayStartItem(
                            dayWithExercises = dayWithExercises,
                            onTap = { viewModel.startWorkoutFromDay(dayWithExercises.day.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineDayStartItem(
    dayWithExercises: RoutineDayWithExercises,
    onTap: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    GlowCard(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dayWithExercises.day.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${dayWithExercises.exercises.size} Exercises",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                // List first few exercises as preview
                if (dayWithExercises.exercises.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dayWithExercises.exercises.take(3).joinToString(", ") { it.exercise.name } +
                                if (dayWithExercises.exercises.size > 3) ", ..." else "",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
