package com.example.gymtime.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            routineName,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Days",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (canAddMoreDays) {
                        navController.navigate(Screen.RoutineDayForm.createRoute(routineId))
                    } else {
                        showMaxDaysDialog = true
                    }
                },
                containerColor = if (canAddMoreDays) PrimaryAccent else TextTertiary,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Day", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = 16.dp)
        ) {
            if (days.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No days in this routine yet.\nTap + to add a workout day!",
                        color = TextTertiary,
                        fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    color = TextPrimary,
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
                    text = { Text("Edit", color = TextPrimary) },
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
