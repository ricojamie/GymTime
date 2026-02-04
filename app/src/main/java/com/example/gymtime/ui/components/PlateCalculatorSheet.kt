package com.example.gymtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtime.ui.theme.LocalAppColors
import com.example.gymtime.util.PlateCalculator
import com.example.gymtime.util.PlateLoadout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorSheet(
    initialWeight: Float,
    barWeight: Float,
    availablePlates: List<Float>,
    loadingSides: Int,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onUseWeight: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var weightInput by remember { mutableStateOf(if (initialWeight > 0) initialWeight.toString() else "") }

    val loadout = remember(weightInput, barWeight, availablePlates, loadingSides) {
        val targetWeight = weightInput.toFloatOrNull()
        if (targetWeight != null && targetWeight > 0) {
            PlateCalculator.calculatePlates(
                targetWeight = targetWeight,
                availablePlates = availablePlates,
                barWeight = barWeight,
                loadingSides = loadingSides
            )
        } else {
            PlateLoadout(emptyList(), barWeight, true)
        }
    }

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
            // Header with settings icon
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

            Spacer(modifier = Modifier.height(16.dp))

            // Weight Input
            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                label = { Text("Target Weight (lbs)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (loadout.platesPerSide.isNotEmpty()) {
                // Per Side Breakdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = LocalAppColors.current.inputBackground
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Per Side",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Plate breakdown as colored circles with text
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 6
                        ) {
                            loadout.platesPerSide.forEach { plate ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(PlateCalculator.getPlateColor(plate))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (plate % 1.0f == 0f) {
                                                plate.toInt().toString()
                                            } else {
                                                plate.toString()
                                            },
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "lbs",
                                        fontSize = 10.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Text breakdown
                        Text(
                            text = PlateCalculator.formatPlateLoadout(loadout),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Total weight display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (loadout.isExact) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        } else {
                            Color(0xFFF39C12).copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Weight:",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF9CA3AF)
                        )
                        Text(
                            text = "${formatWeight(loadout.totalWeight)} lbs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (loadout.isExact) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFFF39C12)
                            }
                        )
                    }
                }

                // Warning if not exact
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
            } else {
                // No plates needed
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = LocalAppColors.current.inputBackground
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (barWeight == 0f) "Bodyweight only (0 lbs)" else "Bar only (${formatWeight(barWeight)} lbs)",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Close button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Use This Weight button (only show if callback provided and weight is valid)
                if (onUseWeight != null && loadout.totalWeight > 0) {
                    Button(
                        onClick = {
                            onUseWeight(loadout.totalWeight)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Use This Weight",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatWeight(weight: Float): String {
    return if (weight % 1.0f == 0f) {
        weight.toInt().toString()
    } else {
        weight.toString()
    }
}
