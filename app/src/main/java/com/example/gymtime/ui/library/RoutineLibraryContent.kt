package com.example.gymtime.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

/**
 * Routine library content for the Library screen's Routines tab.
 * Shows list of routines with tap-to-edit navigation.
 */
@Composable
fun RoutineLibraryContent(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val routines by viewModel.routines.collectAsState(initial = emptyList())
    val activeRoutineId by viewModel.activeRoutineId.collectAsState(initial = null)
    val canCreateMore by viewModel.canCreateMoreRoutines.collectAsState()

    var showMaxRoutinesDialog by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (canCreateMore) {
                        navController.navigate(Screen.RoutineForm.createRoute())
                    } else {
                        showMaxRoutinesDialog = true
                    }
                },
                containerColor = if (canCreateMore) PrimaryAccent else TextTertiary,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Routine", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (routines.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No routines yet.\nTap + to create your first routine!",
                        color = TextTertiary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(routines) { routine ->
                        RoutineLibraryItem(
                            routine = routine,
                            isActive = routine.id == activeRoutineId,
                            onTap = { navController.navigate(Screen.RoutineDayList.createRoute(routine.id)) },
                            onDelete = { routineToDelete = routine }
                        )
                    }
                }
            }
        }
    }

    // Max routines dialog
    if (showMaxRoutinesDialog) {
        AlertDialog(
            onDismissRequest = { showMaxRoutinesDialog = false },
            title = { Text("Maximum Routines Reached", color = TextPrimary) },
            text = { Text("You can only create up to 3 routines. Delete an existing routine to create a new one.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showMaxRoutinesDialog = false }) {
                    Text("OK", color = PrimaryAccent)
                }
            },
            containerColor = SurfaceCards
        )
    }

    // Delete confirmation dialog
    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete Routine?", color = TextPrimary) },
            text = { Text("This will permanently delete \"${routine.name}\" and all its days and exercises.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoutine(routine)
                        routineToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = SurfaceCards
        )
    }
}

@Composable
private fun RoutineLibraryItem(
    routine: Routine,
    isActive: Boolean,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = routine.name,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isActive) {
                        Surface(
                            color = PrimaryAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = PrimaryAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Tap to view and edit",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Context menu (delete only)
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
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
