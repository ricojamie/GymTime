package com.example.gymtime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.MuscleGroup
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupManagementScreen(
    navController: NavController,
    viewModel: MuscleGroupManagementViewModel = hiltViewModel()
) {
    val muscleGroups by viewModel.muscleGroups.collectAsState()
    val editingMuscle by viewModel.editingMuscle.collectAsState()
    val muscleNameInput by viewModel.muscleNameInput.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val deleteCheckResult by viewModel.deleteCheckResult.collectAsState()

    val accentColor = MaterialTheme.colorScheme.primary
    val gradientColors = LocalGradientColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Muscle Groups",
                        color = LocalAppColors.current.textPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
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
                onClick = { viewModel.startAddNew() },
                containerColor = accentColor,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Muscle Group", modifier = Modifier.size(32.dp))
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
                if (muscleGroups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "💪",
                                fontSize = 48.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                text = "No muscle groups yet.",
                                color = LocalAppColors.current.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap + to create your first muscle group!",
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
                        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(muscleGroups, key = { it.name }) { muscleGroup ->
                            MuscleGroupItem(
                                muscleGroup = muscleGroup,
                                onEdit = { viewModel.startEdit(muscleGroup.name) },
                                onDelete = { viewModel.checkCanDelete(muscleGroup.name) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (editingMuscle != null) {
        val isEditing = editingMuscle?.isNotEmpty() == true
        AlertDialog(
            onDismissRequest = { viewModel.clearDialog() },
            title = {
                Text(
                    if (isEditing) "Edit Muscle Group" else "Add Muscle Group",
                    color = LocalAppColors.current.textPrimary
                )
            },
            text = {
                Column {
                    BasicTextField(
                        value = muscleNameInput,
                        onValueChange = { viewModel.updateMuscleNameInput(it) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 18.sp,
                            color = LocalAppColors.current.textPrimary,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LocalAppColors.current.inputBackground, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (muscleNameInput.isEmpty()) {
                                    Text(
                                        "Muscle group name",
                                        color = LocalAppColors.current.textTertiary,
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (validationError != null) {
                        Text(
                            validationError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveMuscleGroup() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black
                    )
                ) {
                    Text(if (isEditing) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDialog() }) {
                    Text("Cancel", color = LocalAppColors.current.textSecondary)
                }
            },
            containerColor = LocalAppColors.current.surfaceCards
        )
    }

    // Delete Dialog
    deleteCheckResult?.let { (name, result) ->
        when (result) {
            is DeleteCheckResult.CanDelete -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearDeleteDialog() },
                    title = { Text("Delete Muscle Group?", color = LocalAppColors.current.textPrimary) },
                    text = { Text("Are you sure you want to delete \"$name\"?", color = LocalAppColors.current.textPrimary) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.deleteMuscleGroup(name) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.clearDeleteDialog() }) {
                            Text("Cancel", color = LocalAppColors.current.textSecondary)
                        }
                    },
                    containerColor = LocalAppColors.current.surfaceCards
                )
            }
            is DeleteCheckResult.HasExercises -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearDeleteDialog() },
                    title = { Text("Delete Muscle Group?", color = LocalAppColors.current.textPrimary) },
                    text = {
                        Text(
                            "\"$name\" has ${result.exerciseCount} exercise(s) assigned to it. " +
                                "Deleting will move these exercises to \"Uncategorized\".",
                            color = LocalAppColors.current.textPrimary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.deleteWithExercises(name) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete Anyway")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.clearDeleteDialog() }) {
                            Text("Cancel", color = LocalAppColors.current.textSecondary)
                        }
                    },
                    containerColor = LocalAppColors.current.surfaceCards
                )
            }
            is DeleteCheckResult.BlockedByLoggedSets -> {
                AlertDialog(
                    onDismissRequest = { viewModel.clearDeleteDialog() },
                    title = { Text("Cannot Delete", color = MaterialTheme.colorScheme.error) },
                    text = {
                        Text(
                            "\"$name\" cannot be deleted because it has ${result.setCount} logged set(s) in your workout history. " +
                                "Rename the muscle group instead if needed.",
                            color = LocalAppColors.current.textPrimary
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearDeleteDialog() }) {
                            Text("OK", color = accentColor)
                        }
                    },
                    containerColor = LocalAppColors.current.surfaceCards
                )
            }
        }
    }
}

@Composable
fun MuscleGroupItem(
    muscleGroup: MuscleGroup,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    GlowCard(
        onClick = onEdit,
        onLongClick = { showMenu = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = muscleGroup.name,
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit", color = LocalAppColors.current.textPrimary) },
                    onClick = {
                        showMenu = false
                        onEdit()
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
