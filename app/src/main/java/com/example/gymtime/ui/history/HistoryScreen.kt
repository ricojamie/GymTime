package com.example.gymtime.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.RoutineRepository
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.history.preview.PreviewHistoryScreen
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.LocalLoggerActionColors

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    PreviewHistoryScreen(navController, viewModel)
}

@Composable
internal fun DeleteWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = IronLogRadius.CardShape,
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = {
            Text(
                text = "Delete Workout?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "This workout will be permanently removed.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.error,
                    contentColor = scheme.onError
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun AddToRoutineDialog(
    routines: List<Routine>,
    loadDays: suspend (Long) -> List<RoutineDay>,
    onConfirm: (routineId: Long, replaceDayId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val actionColors = LocalLoggerActionColors.current
    var pickedRoutine by remember { mutableStateOf<Routine?>(null) }
    var days by remember { mutableStateOf<List<RoutineDay>?>(null) }
    var dayToReplace by remember { mutableStateOf<RoutineDay?>(null) }

    LaunchedEffect(pickedRoutine) {
        days = null
        days = pickedRoutine?.let { loadDays(it.id) }
    }

    dayToReplace?.let { day ->
        AlertDialog(
            onDismissRequest = { dayToReplace = null },
            shape = IronLogRadius.CardShape,
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
            title = {
                Text(
                    text = "Replace '${day.name}'?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "All exercises in this day will be replaced with the ones from this workout.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(day.routineId, day.id) },
                    shape = IronLogRadius.ControlShape,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = actionColors.fill,
                        contentColor = actionColors.onFill
                    )
                ) {
                    Text("Replace", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dayToReplace = null },
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = IronLogRadius.CardShape,
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = {
            Text(
                text = pickedRoutine?.let { "Add to '${it.name}'" } ?: "Add to Routine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val routine = pickedRoutine
            if (routine == null) {
                if (routines.isEmpty()) {
                    Text(
                        text = "No routines yet. Create one from the Library tab first.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(routines, key = { it.id }) { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = IronLogSpace.TouchTargetMin)
                                    .clickable { pickedRoutine = item }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = scheme.onSurface
                                )
                            }
                            IronLogDivider(insetStart = 0.dp)
                        }
                    }
                }
            } else {
                val loadedDays = days
                if (loadedDays == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = scheme.primary)
                    }
                } else {
                    val routineFull = loadedDays.size >= RoutineRepository.MAX_DAYS_PER_ROUTINE
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        item(key = "add-new-day") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = IronLogSpace.TouchTargetMin)
                                    .clickable(enabled = !routineFull) { onConfirm(routine.id, null) }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Add as new day",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (routineFull) scheme.onSurfaceVariant else scheme.primary
                                )
                                if (routineFull) {
                                    Text(
                                        text = "Routine is full — replace a day instead",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            IronLogDivider(insetStart = 0.dp)
                        }
                        items(loadedDays, key = { it.id }) { day ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = IronLogSpace.TouchTargetMin)
                                    .clickable { dayToReplace = day }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Replace: ${day.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = scheme.onSurface
                                )
                            }
                            IronLogDivider(insetStart = 0.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    if (pickedRoutine != null) pickedRoutine = null else onDismiss()
                },
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) {
                Text(if (pickedRoutine != null) "Back" else "Cancel")
            }
        }
    )
}
