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
import com.example.gymtime.BuildConfig
import com.example.gymtime.navigation.navigateHomeAndClearStack
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
    val monthlyReportEnabled by viewModel.monthlyReportEnabled.collectAsState(initial = true)
    val keepScreenOn by viewModel.keepScreenOn.collectAsState(initial = false)
    val darkMode by viewModel.darkMode.collectAsState(initial = true)
    val restDaysPerWeek by viewModel.restDaysPerWeek.collectAsState(initial = 2)
    val barWeight by viewModel.barWeight.collectAsState(initial = 45f)
    val loadingSides by viewModel.loadingSides.collectAsState(initial = 2)
    val availablePlates by viewModel.availablePlates.collectAsState(initial = listOf(45f, 35f, 25f, 15f, 10f, 5f, 2.5f))
    val importState by viewModel.importState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val ironLogImportState by viewModel.ironLogImportState.collectAsState()

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

    // File creator for IronLog export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.let { outputStream ->
                viewModel.exportData(outputStream)
            }
        }
    }

    // File picker for IronLog import
    val ironLogImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let { inputStream ->
                viewModel.importIronLog(inputStream)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = LocalAppColors.current.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateHomeAndClearStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LocalAppColors.current.textPrimary
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
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Name", fontSize = 12.sp, color = LocalAppColors.current.textTertiary)
                        Spacer(modifier = Modifier.height(8.dp))

                        BasicTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                viewModel.setUserName(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 18.sp,
                                color = LocalAppColors.current.textPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LocalAppColors.current.inputBackground, RoundedCornerShape(8.dp))
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
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
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
                            Text("Auto-start timer", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Start rest timer after logging set",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
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

                    HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Timer Audio", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Play a tone when timer expires",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
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

                    HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Timer Vibration", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Vibrate when timer expires",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
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

                // Notifications Section
                SectionHeader("Notifications")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monthly Report", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Get a summary of last month on the 1st",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        }

                        Switch(
                            checked = monthlyReportEnabled,
                            onCheckedChange = { viewModel.setMonthlyReportEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(com.example.gymtime.navigation.Screen.MonthlyReport.route) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("View Last Month's Report", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "See the summary right now",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        }
                        Text(
                            text = "Open",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Display Section
                SectionHeader("Display")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
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
                            Text("Keep Screen On", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Prevent screen from dimming during workouts",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        }

                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { viewModel.setKeepScreenOn(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }

                    HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dark Mode", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Switch between dark and light theme",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        }

                        Switch(
                            checked = darkMode,
                            onCheckedChange = { viewModel.setDarkMode(it) },
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

                SectionHeader("Theme")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(com.example.gymtime.navigation.Screen.ThemeSettings.route) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Theme", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Accent color, custom color wheel, and fonts",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        }
                        Text(
                            text = "Open",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Consistency Section
                SectionHeader("Consistency")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Iron Streak Rest Days", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                        Text(
                            "This sets how many misses are allowed each week before the streak breaks.",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textTertiary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (0..7).forEach { dayCount ->
                                val isSelected = restDaysPerWeek == dayCount
                                Button(
                                    onClick = { viewModel.setRestDaysPerWeek(dayCount) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            LocalAppColors.current.inputBackground
                                        },
                                        contentColor = if (isSelected) Color.Black else LocalAppColors.current.textPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = dayCount.toString(),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$restDaysPerWeek rest day${if (restDaysPerWeek == 1) "" else "s"} allowed each week",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textSecondary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

                // Plate Calculator Section
                SectionHeader("Plate Calculator")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Bar Weight
                        Text("Bar Weight", fontSize = 14.sp, color = LocalAppColors.current.textTertiary)
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
                        Text("Loading Sides", fontSize = 14.sp, color = LocalAppColors.current.textTertiary)
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
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Available Plates", fontSize = 14.sp, color = LocalAppColors.current.textTertiary)
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
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
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
                            Text("Manage Muscle Groups", fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
                            Text(
                                "Add, edit, or remove muscle groups",
                                fontSize = 12.sp,
                                color = LocalAppColors.current.textTertiary
                            )
                        }
                        Text("→", fontSize = 18.sp, color = LocalAppColors.current.textTertiary)
                    }
                }
            }

            // Backup & Restore Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Backup & Restore")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Export IronLog Backup", fontSize = 16.sp, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "Save all data to a ZIP file for transfer to another device",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textTertiary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { exportLauncher.launch("ironlog_backup.zip") },
                            enabled = exportState !is SettingsViewModel.ExportState.InProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (exportState is SettingsViewModel.ExportState.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Exporting...")
                            } else {
                                Text("Export Backup")
                            }
                        }
                    }

                    HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Import IronLog Backup", fontSize = 16.sp, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "Restore data from an IronLog backup ZIP file",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textTertiary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { ironLogImportLauncher.launch("application/zip") },
                            enabled = ironLogImportState !is SettingsViewModel.IronLogImportState.InProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (ironLogImportState is SettingsViewModel.IronLogImportState.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importing...")
                            } else {
                                Text("Import Backup")
                            }
                        }
                    }
                }
            }

            // Import Data Section (FitNotes)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Import from Other Apps")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalAppColors.current.surfaceCards),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("FitNotes Import", fontSize = 16.sp, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Medium)
                        Text(
                            "Import workout history from FitNotes CSV export",
                            fontSize = 12.sp,
                            color = LocalAppColors.current.textTertiary
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
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        fontSize = 14.sp,
                        color = LocalAppColors.current.textTertiary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Tap for changelog",
                        fontSize = 10.sp,
                        color = LocalAppColors.current.textTertiary.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Export Result Dialog
    when (val state = exportState) {
        is SettingsViewModel.ExportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearExportState() },
                title = { Text("Export Complete", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text("${state.result.exerciseCount} exercises", color = LocalAppColors.current.textPrimary)
                        Text("${state.result.workoutCount} workouts", color = LocalAppColors.current.textPrimary)
                        Text("${state.result.setCount} sets", color = LocalAppColors.current.textPrimary)
                        if (state.result.routineCount > 0) {
                            Text("${state.result.routineCount} routines", color = LocalAppColors.current.textPrimary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExportState() }) {
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = LocalAppColors.current.surfaceCards
            )
        }
        is SettingsViewModel.ExportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearExportState() },
                title = { Text("Export Failed", color = Color(0xFFEF5350)) },
                text = { Text(state.message, color = LocalAppColors.current.textPrimary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExportState() }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = LocalAppColors.current.surfaceCards
            )
        }
        else -> { /* Idle or InProgress */ }
    }

    // IronLog Import Result Dialog
    when (val state = ironLogImportState) {
        is SettingsViewModel.IronLogImportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearIronLogImportState() },
                title = { Text("Import Complete", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text("${state.result.exercisesImported} exercises imported", color = LocalAppColors.current.textPrimary)
                        Text("${state.result.workoutsImported} workouts imported", color = LocalAppColors.current.textPrimary)
                        Text("${state.result.setsImported} sets imported", color = LocalAppColors.current.textPrimary)
                        if (state.result.routinesImported > 0) {
                            Text("${state.result.routinesImported} routines imported", color = LocalAppColors.current.textPrimary)
                        }
                        if (state.result.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${state.result.errors.size} errors occurred", color = Color(0xFFEF5350))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearIronLogImportState() }) {
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = LocalAppColors.current.surfaceCards
            )
        }
        is SettingsViewModel.IronLogImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearIronLogImportState() },
                title = { Text("Import Failed", color = Color(0xFFEF5350)) },
                text = { Text(state.message, color = LocalAppColors.current.textPrimary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearIronLogImportState() }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = LocalAppColors.current.surfaceCards
            )
        }
        else -> { /* Idle or InProgress */ }
    }

    // FitNotes Import Result Dialog
    when (val state = importState) {
        is SettingsViewModel.ImportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                title = { Text("Import Complete", color = LocalAppColors.current.textPrimary) },
                text = {
                    Column {
                        Text("${state.result.workoutsImported} workouts imported", color = LocalAppColors.current.textPrimary)
                        Text("${state.result.setsImported} sets imported", color = LocalAppColors.current.textPrimary)
                        Text("${state.result.exercisesCreated} new exercises created", color = LocalAppColors.current.textPrimary)
                        if (state.result.duplicatesSkipped > 0) {
                            Text("${state.result.duplicatesSkipped} duplicates skipped", color = LocalAppColors.current.textTertiary)
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
                containerColor = LocalAppColors.current.surfaceCards
            )
        }
        is SettingsViewModel.ImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                title = { Text("Import Failed", color = Color(0xFFEF5350)) },
                text = { Text(state.message, color = LocalAppColors.current.textPrimary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearImportState() }) {
                        Text("OK", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = LocalAppColors.current.surfaceCards
            )
        }
        else -> { /* Idle or InProgress - no dialog */ }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("What's New in v${BuildConfig.VERSION_NAME} 🌞", color = LocalAppColors.current.textPrimary) },
            text = {
                Column {
                    Text(
                        "Summer Shred Update 💪",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "⏱️ New ways to log work\n" +
                        "Track Weight x Time and Calories x Time alongside the classic strength flows.\n\n" +
                        "🏃 Distance your way\n" +
                        "Cardio now supports meters, kilometers, yards, feet, miles, steps, and floors, with live distance-type switching while you log.\n\n" +
                        "🎨 Theme lab unlocked\n" +
                        "Pick from 3 polished presets, spin up a custom accent color, choose from 5 fonts, or upload your own font file.\n\n" +
                        "🔥 Smarter streaks\n" +
                        "Set your own rest days per week so Iron Streak matches how you actually train.\n\n" +
                        "📊 Analytics glow-up\n" +
                        "Radar chart, range filters, better cardio-aware stats, and a much cleaner balance view.\n\n" +
                        "🔧 Smoother updates\n" +
                        "Fixed an issue where the app could occasionally revert to an older version after updating.\n\n" +
                        "Watch logging polish\n" +
                        "Reduced the Wear OS logger size and fixed brief stale exercise flashes when switching exercises.",
                        color = LocalAppColors.current.textPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text("Shred On! ⚡", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = LocalAppColors.current.surfaceCards,
            titleContentColor = LocalAppColors.current.textPrimary,
            textContentColor = LocalAppColors.current.textPrimary
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = LocalAppColors.current.textTertiary,
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
            Text(label, fontSize = 16.sp, color = LocalAppColors.current.textPrimary)
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
                LocalAppColors.current.inputBackground
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
                LocalAppColors.current.inputBackground
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
                LocalAppColors.current.inputBackground
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
