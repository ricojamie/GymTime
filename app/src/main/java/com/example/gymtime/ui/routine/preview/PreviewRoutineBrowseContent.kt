package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class RoutineBrowseItem(val id: Long, val name: String, val isActive: Boolean, val dayCount: Int,
    val exerciseCount: Int, val nextDayName: String?)

data class RoutineBrowseUiState(val routines: List<RoutineBrowseItem> = emptyList(), val isLoading: Boolean = true,
    val isWorking: Boolean = false, val loadError: String? = null, val actionError: String? = null) {
    val canCreateMore: Boolean get() = routines.size < 10
}

/** Shared by the standalone route and the embedded Library tab, without navigation or app dependencies. */
@Composable
fun PreviewRoutineBrowseContent(
    state: RoutineBrowseUiState, onCreate: () -> Unit, onOpen: (Long) -> Unit, onSetActive: (Long?) -> Unit,
    onRename: (Long) -> Unit, onDuplicate: (Long) -> Unit, onDelete: (Long) -> Unit,
    onRetry: () -> Unit, onDismissError: () -> Unit, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null, onHome: (() -> Unit)? = null, embedded: Boolean = false
) {
    var showLimit by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val create = { if (state.canCreateMore) onCreate() else showLimit = true }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column {
            if (!embedded && onBack != null && onHome != null) RoutineHeader("My routines", onBack, onHome)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = if (embedded) 104.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if (embedded) "Your routines" else "A plan when you want one", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${state.routines.size}/10 saved", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RoutinePrimaryAction("Create", create, enabled = !state.isWorking && !state.isLoading)
                    }
                }
                state.loadError?.let { message -> item { RoutineError(message, onDismissError, onRetry) } }
                state.actionError?.let { message -> item { RoutineError(message, onDismissError) } }
                if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                else if (state.routines.isEmpty() && state.loadError == null) item {
                    RoutineEmpty("No routines yet", "Keep training freely, or save a few workout days to make your next session easier.", "Create a routine", create)
                }
                if (state.routines.any { it.isActive }) item {
                    TextButton(onClick = { onSetActive(null) }, enabled = !state.isWorking, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Clear selected routine")
                    }
                }
                items(state.routines, key = { it.id }) { routine ->
                    RoutineBrowseCard(routine, enabled = !state.isWorking,
                        onOpen = { onOpen(routine.id) }, onUse = { onSetActive(if (routine.isActive) null else routine.id) },
                        onRename = { onRename(routine.id) },
                        onDuplicate = { if (state.canCreateMore) onDuplicate(routine.id) else showLimit = true },
                        onDelete = { deleteId = routine.id })
                }
            }
        }
    }
    if (showLimit) AlertDialog(onDismissRequest = { showLimit = false }, title = { Text("10 routines saved") },
        text = { Text("Delete a routine before creating or duplicating another.") },
        confirmButton = { TextButton(onClick = { showLimit = false }) { Text("OK") } })
    state.routines.firstOrNull { it.id == deleteId }?.let { routine ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete ${routine.name}?") },
            text = { Text("Its days and planned exercises will be deleted. Your logged workouts stay saved.") },
            confirmButton = { TextButton(onClick = { onDelete(routine.id); deleteId = null }, enabled = !state.isWorking,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep routine") } })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RoutineBrowseCard(item: RoutineBrowseItem, enabled: Boolean, onOpen: () -> Unit, onUse: () -> Unit,
    onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    RoutinePanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (item.isActive) Text("Selected routine", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box {
                TextButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "Options for ${item.name}" }) { Text("More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() }, enabled = enabled)
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() }, enabled = enabled)
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() }, enabled = enabled)
                }
            }
        }
        Text("${item.dayCount} ${if (item.dayCount == 1) "day" else "days"} · ${item.exerciseCount} planned exercises",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.isActive && item.nextDayName != null) Text("Up next · ${item.nextDayName}", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RoutinePrimaryAction("Open routine", onOpen, enabled = enabled)
            TextButton(onClick = onUse, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (item.isActive) "Clear selection" else "Use routine") }
        }
    }
}
