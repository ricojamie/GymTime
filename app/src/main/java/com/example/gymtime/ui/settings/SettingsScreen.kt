package com.example.gymtime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsState(initial = "Athlete")
    val themeColor by viewModel.themeColor.collectAsState(initial = "lime")
    val timerAutoStart by viewModel.timerAutoStart.collectAsState(initial = true)
    val barWeight by viewModel.barWeight.collectAsState(initial = 45f)
    val loadingSides by viewModel.loadingSides.collectAsState(initial = 2)
    val availablePlates by viewModel.availablePlates.collectAsState(initial = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f))

    var nameInput by remember { mutableStateOf(userName) }

    // Update nameInput when userName changes from DataStore
    LaunchedEffect(userName) {
        nameInput = userName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                // User Name Section
                SectionHeader("Profile")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Name", fontSize = 12.sp, color = TextTertiary)
                        Spacer(modifier = Modifier.height(8.dp))

                        BasicTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                viewModel.setUserName(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 18.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Timer Settings Section
                SectionHeader("Timer")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-start timer", fontSize = 16.sp, color = TextPrimary)
                            Text(
                                "Start rest timer after logging set",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }

                        Switch(
                            checked = timerAutoStart,
                            onCheckedChange = { viewModel.setTimerAutoStart(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Theme Section
                SectionHeader("Theme")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Accent Color", fontSize = 14.sp, color = TextTertiary)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Color options
                        ColorOption("Lime Green", "lime", ThemeColors.LimeGreen.primaryAccent, themeColor, viewModel)
                        ColorOption("Electric Blue", "blue", ThemeColors.ElectricBlue.primaryAccent, themeColor, viewModel)
                        ColorOption("Cyber Purple", "purple", ThemeColors.CyberPurple.primaryAccent, themeColor, viewModel)
                        ColorOption("Hot Pink", "pink", ThemeColors.HotPink.primaryAccent, themeColor, viewModel)
                        ColorOption("Gold Amber", "gold", ThemeColors.GoldAmber.primaryAccent, themeColor, viewModel)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Plate Calculator Section
                SectionHeader("Plate Calculator")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Bar Weight
                        Text("Bar Weight", fontSize = 14.sp, color = TextTertiary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BarWeightOption(0f, barWeight, viewModel)
                            BarWeightOption(25f, barWeight, viewModel)
                            BarWeightOption(35f, barWeight, viewModel)
                            BarWeightOption(45f, barWeight, viewModel)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Loading Sides
                        Text("Loading Sides", fontSize = 14.sp, color = TextTertiary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LoadingSidesOption(1, loadingSides, viewModel)
                            LoadingSidesOption(2, loadingSides, viewModel)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Available Plates Section
                SectionHeader("Available Plates")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Available Plates", fontSize = 14.sp, color = TextTertiary)
                        Spacer(modifier = Modifier.height(12.dp))

                        // All available plate options
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PlateToggleOption(45f, availablePlates, viewModel)
                            PlateToggleOption(35f, availablePlates, viewModel)
                            PlateToggleOption(25f, availablePlates, viewModel)
                            PlateToggleOption(15f, availablePlates, viewModel)
                            PlateToggleOption(10f, availablePlates, viewModel)
                            PlateToggleOption(5f, availablePlates, viewModel)
                            PlateToggleOption(2.5f, availablePlates, viewModel)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextTertiary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun ColorOption(
    label: String,
    colorKey: String,
    color: Color,
    selectedColor: String,
    viewModel: SettingsViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setThemeColor(colorKey) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, fontSize = 16.sp, color = TextPrimary)
        }

        RadioButton(
            selected = selectedColor == colorKey,
            onClick = { viewModel.setThemeColor(colorKey) },
            colors = RadioButtonDefaults.colors(selectedColor = color)
        )
    }
}

@Composable
fun RowScope.BarWeightOption(
    weightValue: Float,
    selectedWeight: Float,
    viewModel: SettingsViewModel
) {
    val isSelected = selectedWeight == weightValue
    Button(
        onClick = { viewModel.setBarWeight(weightValue) },
        modifier = Modifier
            .weight(1f)
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFF1A1A1A)
            },
            contentColor = if (isSelected) {
                Color.Black
            } else {
                Color(0xFF9CA3AF)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "${weightValue.toInt()} lbs",
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
fun RowScope.LoadingSidesOption(
    sides: Int,
    selectedSides: Int,
    viewModel: SettingsViewModel
) {
    val isSelected = selectedSides == sides
    Button(
        onClick = { viewModel.setLoadingSides(sides) },
        modifier = Modifier
            .weight(1f)
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFF1A1A1A)
            },
            contentColor = if (isSelected) {
                Color.Black
            } else {
                Color(0xFF9CA3AF)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "$sides side${if (sides > 1) "s" else ""}",
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PlateToggleOption(
    plateWeight: Float,
    enabledPlates: List<Float>,
    viewModel: SettingsViewModel
) {
    val isEnabled = enabledPlates.contains(plateWeight)
    Button(
        onClick = { viewModel.togglePlate(plateWeight, enabledPlates) },
        modifier = Modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                Color(0xFF1A1A1A)
            },
            contentColor = if (isEnabled) {
                Color.Black
            } else {
                Color(0xFF9CA3AF)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = if (plateWeight % 1.0f == 0f) {
                "${plateWeight.toInt()} lbs"
            } else {
                "$plateWeight lbs"
            },
            fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}
