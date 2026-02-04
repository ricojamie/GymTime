package com.example.gymtime.ui.summary

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.components.VolumeOrb
import com.example.gymtime.ui.components.OrbSize
import com.example.gymtime.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PostWorkoutSummaryScreen(
    navController: NavController,
    viewModel: PostWorkoutSummaryViewModel = hiltViewModel()
) {
    val workoutStats by viewModel.workoutStats.collectAsState()
    val selectedRating by viewModel.selectedRating.collectAsState()
    val ratingNote by viewModel.ratingNote.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val volumeOrbState by viewModel.volumeOrbState.collectAsState()
    val sessionContribution by viewModel.sessionContribution.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    // Observe navigation event
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Celebration Header
            Text(
                text = "Workout Complete!",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "YOU CRUSHED IT",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = LocalAppColors.current.textTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Hero Summary Card
            GlowCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Duration
                    StatRow(
                        label = "DURATION",
                        value = workoutStats?.duration ?: "0m",
                        emphasize = true
                    )

                    HorizontalDivider(
                        color = LocalAppColors.current.textTertiary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 20.dp)
                    )

                    // Volume and Exercises Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn(
                            label = "VOLUME",
                            value = "${workoutStats?.totalVolume?.toInt() ?: 0} lbs"
                        )

                        StatColumn(
                            label = "EXERCISES",
                            value = "${workoutStats?.exerciseCount ?: 0}"
                        )

                        StatColumn(
                            label = "SETS",
                            value = "${workoutStats?.totalSets ?: 0}"
                        )
                    }

                    HorizontalDivider(
                        color = LocalAppColors.current.textTertiary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 20.dp)
                    )

                    // Muscle Groups
                    Text(
                        text = "MUSCLES TRAINED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = LocalAppColors.current.textTertiary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = workoutStats?.muscleGroups?.joinToString(" • ") ?: "None",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = LocalAppColors.current.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(
                        color = LocalAppColors.current.textTertiary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 20.dp)
                    )

                    // Weekly Volume Progress Section
                    Text(
                        text = "WEEKLY PROGRESS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = LocalAppColors.current.textTertiary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Medium Volume Orb
                    VolumeOrb(
                        state = volumeOrbState,
                        size = OrbSize.MEDIUM,
                        onOverflowAnimationComplete = { viewModel.clearOrbOverflowAnimation() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Session contribution text
                    if (sessionContribution > 0) {
                        Text(
                            text = "+${numberFormat.format(sessionContribution.toLong())} lbs this session",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                    }

                    // Progress percentage
                    if (!volumeOrbState.isFirstWeek) {
                        Text(
                            text = if (volumeOrbState.hasOverflowed) {
                                "Goal crushed! ${(volumeOrbState.progressPercent * 100).toInt()}% of last week"
                            } else {
                                "${(volumeOrbState.progressPercent * 100).toInt()}% of last week's volume"
                            },
                            fontSize = 12.sp,
                            color = if (volumeOrbState.hasOverflowed) accentColor else LocalAppColors.current.textTertiary
                        )
                    } else {
                        Text(
                            text = "Building your baseline...",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Rating Section
                    Text(
                        text = "HOW WAS YOUR WORKOUT?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = LocalAppColors.current.textTertiary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Flame Rating Selector
                    FlameRatingSelector(
                        selectedRating = selectedRating,
                        onRatingSelected = { viewModel.updateRating(it) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Optional Note
                    BasicTextField(
                        value = ratingNote,
                        onValueChange = { viewModel.updateRatingNote(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = LocalAppColors.current.backgroundCanvas,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = LocalAppColors.current.textPrimary,
                            textAlign = TextAlign.Center
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ratingNote.isEmpty()) {
                                    Text(
                                        text = "Add a note (optional)...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalAppColors.current.textTertiary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        },
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Done Button
            Button(
                onClick = { viewModel.saveAndFinish() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Done",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip Button
            TextButton(
                onClick = { viewModel.skipAndFinish() },
                enabled = !isSaving
            ) {
                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    color = LocalAppColors.current.textSecondary
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    emphasize: Boolean = false
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = LocalAppColors.current.textTertiary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            fontSize = if (emphasize) 48.sp else 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (emphasize) accentColor else LocalAppColors.current.textPrimary
        )
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = LocalAppColors.current.textTertiary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.textPrimary
        )
    }
}

@Composable
private fun FlameRatingSelector(
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit
) {
    // Pulsing animation for high ratings (4-5)
    val infiniteTransition = rememberInfiniteTransition(label = "flamePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        (1..5).forEach { rating ->
            val isSelected = selectedRating != null && rating <= selectedRating

            // Base scale increases with rating: 1/5 = 1.05, 5/5 = 1.25
            val baseScale = if (isSelected) {
                1f + ((selectedRating ?: 0) * 0.05f)
            } else {
                1f
            }

            // Add pulse for high ratings (4-5)
            val shouldPulse = isSelected && (selectedRating ?: 0) >= 4

            val scale by animateFloatAsState(
                targetValue = baseScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "FlameScale"
            )

            val finalScale = if (shouldPulse) scale * pulseScale else scale

            Text(
                text = "\uD83D\uDD25", // Fire emoji
                fontSize = 32.sp,
                modifier = Modifier
                    .scale(finalScale)
                    .alpha(if (isSelected) 1f else 0.3f)
                    .clickable { onRatingSelected(rating) }
                    .padding(8.dp)
            )
        }
    }
}
