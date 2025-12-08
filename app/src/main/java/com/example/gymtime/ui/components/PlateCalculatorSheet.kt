package com.example.gymtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorSheet(
    targetWeight: Float,
    loadout: PlateLoadout,
    barWeight: Float,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Color(0xFF0D0D0D),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with target weight and settings icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plate Calculator",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Target weight display
            Text(
                text = "Target: ${formatWeight(targetWeight)} lbs",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Barbell visual representation
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Left plates
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    loadout.platesPerSide.reversed().forEach { plate ->
                        PlateRectangle(plate)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bar
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(12.dp)
                        .background(Color(0xFF95A5A6), RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Right plates
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    loadout.platesPerSide.forEach { plate ->
                        PlateRectangle(plate)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Plate breakdown text
            if (loadout.platesPerSide.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Per Side:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = PlateCalculator.formatPlateLoadout(loadout),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = "Bar only (${formatWeight(barWeight)} lbs)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9CA3AF)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total weight achieved
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Weight:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    text = "${formatWeight(loadout.totalWeight)} lbs",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (loadout.isExact) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color(0xFFF39C12) // Orange for approximation
                    }
                )
            }

            // Warning if not exact match
            if (!loadout.isExact) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF39C12).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 20.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Closest approximation with available plates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF39C12)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Close button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlateRectangle(weight: Float) {
    val height = when {
        weight >= 45f -> 80.dp
        weight >= 35f -> 70.dp
        weight >= 25f -> 60.dp
        weight >= 10f -> 50.dp
        else -> 40.dp
    }

    val width = 16.dp

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(horizontal = 2.dp)
            .background(
                Color(PlateCalculator.getPlateColor(weight)),
                RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (weight % 1.0f == 0f) {
                weight.toInt().toString()
            } else {
                weight.toString()
            },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(2.dp)
        )
    }
}

private fun formatWeight(weight: Float): String {
    return if (weight % 1.0f == 0f) {
        weight.toInt().toString()
    } else {
        weight.toString()
    }
}
