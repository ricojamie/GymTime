package com.example.gymtime.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDayListScreen(
    navController: NavController,
    viewModel: RoutineDayListViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState(initial = "")
    val routineId by viewModel.routineId.collectAsState()
    val days by viewModel.days.collectAsState(initial = emptyList())
    val canAddMoreDays by viewModel.canAddMoreDays.collectAsState()

    var showMaxDaysDialog by remember { mutableStateOf(false) }
    var dayToDelete by remember { mutableStateOf<RoutineDay?>(null) }
    val accentColor = MaterialTheme.colorScheme.primary

    val gradientColors = LocalGradientColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            routineName,
                            color = LocalAppColors.current.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Days",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LocalAppColors.current.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (canAddMoreDays) {
                        navController.navigate(Screen.RoutineDayForm.createRoute(routineId))
                    } else {
                        showMaxDaysDialog = true
                    }
                },
                containerColor = if (canAddMoreDays) accentColor else LocalAppColors.current.textTertiary,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Day", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(gradientColors.first, gradientColors.second)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                if (days.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📅",
                                fontSize = 48.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                text = "No days yet.",
                                color = LocalAppColors.current.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap + to add a workout day!",
                                color = LocalAppColors.current.textTertiary,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(days) {
                            RoutineDayItem(
                                day = it,
                                onTap = { navController.navigate(Screen.RoutineDayForm.createRoute(routineId, it.id)) },
                                onDelete = { dayToDelete = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Max days dialog
    if (showMaxDaysDialog) {
        AlertDialog(
            onDismissRequest = { showMaxDaysDialog = false },
            title = { Text("Maximum Days Reached") },
            text = { Text("You can only create up to 7 days per routine.") },
            confirmButton = {
                TextButton(onClick = { showMaxDaysDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete confirmation dialog
    dayToDelete?.let {
        AlertDialog(
            onDismissRequest = { dayToDelete = null },
            title = { Text("Delete Day?") },
            text = { Text("This will permanently delete \"${it.name}\" and its exercise list.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDay(it)
                        dayToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { dayToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RoutineDayItem(
    day: RoutineDay,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    GlowCard(
        onClick = onTap,
        onLongClick = { showMenu = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = day.name,
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit", color = LocalAppColors.current.textPrimary) },
                    onClick = {
                        showMenu = false
                        onTap()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
