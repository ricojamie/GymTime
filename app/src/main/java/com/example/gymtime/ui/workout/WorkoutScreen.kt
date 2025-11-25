package com.example.gymtime.ui.workout

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import com.example.gymtime.ui.theme.BackgroundCanvas
import com.example.gymtime.ui.theme.GradientStart
import com.example.gymtime.ui.theme.GradientEnd
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.GymTimeTheme
import com.example.gymtime.ui.theme.PrimaryAccent
import com.example.gymtime.ui.theme.SurfaceCards
import com.example.gymtime.ui.theme.TextPrimary
import com.example.gymtime.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LoggedSet(
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val isPR: Boolean = false
)

@Composable
fun WorkoutScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Log", "Stats")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = PrimaryAccent
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp
                        )
                    }
                )
            }
        }

        // Content based on selected tab
        when (selectedTab) {
            0 -> LogTab()
            1 -> StatsTab()
        }
    }
}

@Composable
fun LogTab() {
    var weight by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var restTime by remember { mutableIntStateOf(91) } // 1:31 in seconds
    var currentSetNumber by remember { mutableIntStateOf(2) }
    var loggedSets by remember { mutableStateOf(listOf(LoggedSet(1, "185", "6"))) }
    var showFinishDialog by remember { mutableStateOf(false) }

    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Timer countdown
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (restTime > 0) restTime--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Exercise Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Barbell Bench Press",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last: 185 lbs x 6, 6, 5",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PrimaryAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                OutlinedButton(
                    onClick = { restTime = maxOf(0, restTime - 15) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextTertiary
                    )
                ) {
                    Text("-15s")
                }
                OutlinedButton(
                    onClick = { restTime += 15 },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextTertiary
                    )
                ) {
                    Text("+15s")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current Set Label
        Text(
            text = "CURRENT SET: $currentSetNumber",
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
                onValueChange = { weight = it },
                modifier = Modifier.weight(1f)
            )

            // Reps Input
            InputCard(
                label = "REPS",
                value = reps,
                onValueChange = { reps = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log Set Button
        LogSetButton(
            onClick = {
                if (weight.isNotBlank() && reps.isNotBlank()) {
                    // Haptic feedback
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                    // Add set to log
                    val isPR = weight.toIntOrNull()?.let { it > 185 } ?: false
                    loggedSets = loggedSets + LoggedSet(currentSetNumber, weight, reps, isPR)
                    currentSetNumber++

                    // Reset timer
                    restTime = 90
                }
            },
            enabled = weight.isNotBlank() && reps.isNotBlank()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Session Log
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

        // Logged Sets
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(loggedSets) { set ->
                SetLogCard(set)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { /* TODO: Navigate to exercise list */ },
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
                        showFinishDialog = false
                        // TODO: Navigate back and save workout
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
}

@Composable
fun InputCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                letterSpacing = 1.sp
            )

            TextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 40.sp
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
fun LogSetButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var showPRAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    // PR Animation
    val prScale by animateFloatAsState(
        targetValue = if (showPRAnimation) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pr_scale"
    )

    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            isPressed = true
            scope.launch {
                delay(100)
                isPressed = false
                onClick()

                // TODO: Check if PR, then trigger animation
                // For demo, randomly show PR animation
                if (Math.random() > 0.7) {
                    showPRAnimation = true
                    delay(500)
                    showPRAnimation = false
                }
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale * prScale),
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
fun SetLogCard(set: LoggedSet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCards),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${set.setNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTertiary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${set.weight} LBS",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "/",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextTertiary
                )

                Text(
                    text = "${set.reps} REPS",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                if (set.isPR) {
                    Text(
                        text = "PR!",
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
        }
    }
}

@Composable
fun StatsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Stats Coming Soon",
            style = MaterialTheme.typography.headlineMedium,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "PRs, history, and progress tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary.copy(alpha = 0.7f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutScreenPreview() {
    GymTimeTheme {
        WorkoutScreen()
    }
}
