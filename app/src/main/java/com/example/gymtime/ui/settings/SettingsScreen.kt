package com.example.gymtime.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
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
    val timerAudioEnabled by viewModel.timerAudioEnabled.collectAsState(initial = true)
    val timerVibrateEnabled by viewModel.timerVibrateEnabled.collectAsState(initial = true)
    val barWeight by viewModel.barWeight.collectAsState(initial = 45f)
    val loadingSides by viewModel.loadingSides.collectAsState(initial = 2)
    val availablePlates by viewModel.availablePlates.collectAsState(initial = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f))
    val importState by viewModel.importState.collectAsState()

    var showChangelog by remember { mutableStateOf(false) }

    var nameInput by remember { mutableStateOf(userName) }

    // Update nameInput when userName changes from DataStore
    LaunchedEffect(userName) {
        nameInput = userName
    }

    // File picker for FitNotes import
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let { inputStream ->
                viewModel.importFitNotes(inputStream)
            }
        }
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

                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Timer Audio", fontSize = 16.sp, color = TextPrimary)
                            Text(
                                "Play a tone when timer expires",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }

                        Switch(
                            checked = timerAudioEnabled,
                            onCheckedChange = { viewModel.setTimerAudioEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Timer Vibration", fontSize = 16.sp, color = TextPrimary)
                            Text(
                                "Vibrate when timer expires",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }

                        Switch(
                            checked = timerVibrateEnabled,
                            onCheckedChange = { viewModel.setTimerVibrateEnabled(it) },
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
                        // Color Grid (2 rows of 5)
                        val colorsRow1 = listOf(
                             Triple("lime", ThemeColors.LimeGreen.primaryAccent, "Lime"),
                             Triple("blue", ThemeColors.ElectricBlue.primaryAccent, "Blue"),
                             Triple("purple", ThemeColors.CyberPurple.primaryAccent, "Purple"),
                             Triple("pink", ThemeColors.HotPink.primaryAccent, "Pink"),
                             Triple("gold", ThemeColors.GoldAmber.primaryAccent, "Gold")
                        )
                        val colorsRow2 = listOf(
                             Triple("red", ThemeColors.BloodRed.primaryAccent, "Red"),
                             Triple("orange", ThemeColors.SunsetOrange.primaryAccent, "Orange"),
                             Triple("mint", ThemeColors.MintFresh.primaryAccent, "Mint"),
                             Triple("slate", ThemeColors.SlateGrey.primaryAccent, "Slate"),
                             Triple("lavender", ThemeColors.LavenderFocus.primaryAccent, "Lavender")
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            // Row 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                colorsRow1.forEach { (key, color, label) ->
                                    ColorSwatch(
                                        colorKey = key,
                                        color = color,
                                        selectedColor = themeColor,
                                        onClick = { viewModel.setThemeColor(key) }
                                    )
                                }
                            }
                            // Row 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                colorsRow2.forEach { (key, color, label) ->
                                    ColorSwatch(
                                        colorKey = key,
                                        color = color,
                                        selectedColor = themeColor,
                                        onClick = { viewModel.setThemeColor(key) }
                                    )
                                }
                            }
                        }
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

            // Muscle Groups Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Customization")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(com.example.gymtime.navigation.Screen.MuscleGroupManagement.route) },
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
                            Text("Manage Muscle Groups", fontSize = 16.sp, color = TextPrimary)
                            Text(
                                "Add, edit, or remove muscle groups",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }
                        Text("→", fontSize = 18.sp, color = TextTertiary)
                    }
                }
            }

            // Import Data Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Import Data")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("FitNotes Import", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "Import workout history from FitNotes CSV export",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { filePickerLauncher.launch("text/*") },
                            enabled = importState !is SettingsViewModel.ImportState.InProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (importState is SettingsViewModel.ImportState.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importing...")
                            } else {
                                Text("Select CSV File")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clickable { showChangelog = true },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Version 1.4.0",
                        fontSize = 14.sp,
                        color = TextTertiary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap for changelog",
                        fontSize = 10.sp,
                        color = TextTertiary.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Import Result Dialog
    when (val state = importState) {
        is SettingsViewModel.ImportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                title = { Text("Import Complete", color = TextPrimary) },
                text = {
                    Column {
                        Text("${state.result.workoutsImported} workouts imported", color = TextPrimary)
                        Text("${state.result.setsImported} sets imported", color = TextPrimary)
                        Text("${state.result.exercisesCreated} new exercises created", color = TextPrimary)
                        if (state.result.duplicatesSkipped > 0) {
                            Text("${state.result.duplicatesSkipped} duplicates skipped", color = TextTertiary)
                        }
                        if (state.result.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${state.result.errors.size} errors occurred", color = Color(0xFFEF5350))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearImportState() }) {
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = SurfaceCards
            )
        }
        is SettingsViewModel.ImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                title = { Text("Import Failed", color = Color(0xFFEF5350)) },
                text = { Text(state.message, color = TextPrimary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearImportState() }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = SurfaceCards
            )
        }
        else -> { /* Idle or InProgress - no dialog */ }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("What's New in v1.4 🎉", color = TextPrimary) },
            text = {
                Column {
                    Text("Your Body, Your Rules! 💪", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "🎯 Custom Muscle Groups\n" +
                        "Add, rename, or remove muscle groups to match YOUR training style. Calves? Forearms? Glutes? You decide!\n\n" +
                        "🔄 Smart Renaming\n" +
                        "Rename a muscle group and all your exercises update automatically. Your workout history stays intact.\n\n" +
                        "🛡️ Data Protection\n" +
                        "Can't accidentally delete muscle groups with logged workouts. Your gains are safe!\n\n" +
                        "📊 Dynamic Analytics\n" +
                        "All charts and filters now use your custom muscle groups.",
                        color = TextPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text("Let's Lift! 🏋️", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = SurfaceCards,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
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

@Composable
fun ColorSwatch(
    colorKey: String,
    color: Color,
    selectedColor: String,
    onClick: () -> Unit
) {
    val isSelected = selectedColor == colorKey
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // Selection indicator (ring)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Transparent, CircleShape)
                    .border(2.dp, color, CircleShape)
            )
        }

        // Inner color circle
        Box(
            modifier = Modifier
                .size(if (isSelected) 34.dp else 40.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
