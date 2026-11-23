package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSecondaryButton
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType

data class RoutineBrowseItem(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val dayCount: Int,
    val exerciseCount: Int,
    val nextDayName: String?
)

data class RoutineBrowseUiState(
    val routines: List<RoutineBrowseItem> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val loadError: String? = null,
    val actionError: String? = null
) {
    val canCreateMore: Boolean get() = routines.size < 10
}

/** Shared by the standalone route and the embedded Library tab, without navigation or app dependencies. */
@Composable
fun PreviewRoutineBrowseContent(
    state: RoutineBrowseUiState,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onSetActive: (Long?) -> Unit,
    onRename: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    embedded: Boolean = false
) {
    var showLimit by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val create = { if (state.canCreateMore) onCreate() else showLimit = true }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column {
            if (!embedded && onBack != null && onHome != null) {
                RoutineHeader("My routines", onBack, onHome)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = IronLogSpace.ScreenHorizontal,
                    end = IronLogSpace.ScreenHorizontal,
                    top = IronLogSpace.ItemGap,
                    bottom = if (embedded) IronLogSpace.BottomNavClearance else 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
            ) {
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (embedded) "Your routines" else "A plan when you want one",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.routines.size}/10 saved",
                                style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IronLogPrimaryButton(
                            text = "Create",
                            onClick = create,
                            enabled = !state.isWorking && !state.isLoading,
                            leadingIcon = Icons.Default.Add
                        )
                    }
                }
                state.loadError?.let { message ->
                    item(key = "load-error") { RoutineError(message, onDismissError, onRetry) }
                }
                state.actionError?.let { message ->
                    item(key = "action-error") { RoutineError(message, onDismissError) }
                }
                if (state.isLoading) {
                    item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                } else if (state.routines.isEmpty() && state.loadError == null) {
                    item(key = "empty") {
                        RoutineEmpty(
                            title = "No routines yet",
                            message = "Keep training freely, or save a few workout days to make your next session easier.",
                            actionLabel = "Create a routine",
                            onAction = create
                        )
                    }
                }
                if (state.routines.any { it.isActive }) {
                    item(key = "clear-active") {
                        TextButton(
                            onClick = { onSetActive(null) },
                            enabled = !state.isWorking,
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Clear selected routine")
                        }
                    }
                }
                items(state.routines, key = { it.id }) { routine ->
                    RoutineBrowseCard(
                        item = routine,
                        enabled = !state.isWorking,
                        onOpen = { onOpen(routine.id) },
                        onUse = { onSetActive(if (routine.isActive) null else routine.id) },
                        onRename = { onRename(routine.id) },
                        onDuplicate = { if (state.canCreateMore) onDuplicate(routine.id) else showLimit = true },
                        onDelete = { deleteId = routine.id }
                    )
                }
            }
        }
    }

    if (showLimit) {
        AlertDialog(
            onDismissRequest = { showLimit = false },
            title = { Text("10 routines saved") },
            text = { Text("Delete a routine before creating or duplicating another.") },
            confirmButton = { TextButton(onClick = { showLimit = false }) { Text("OK") } }
        )
    }

    state.routines.firstOrNull { it.id == deleteId }?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete ${routine.name}?") },
            text = { Text("Its days and planned exercises will be deleted. Your logged workouts stay saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(routine.id)
                        deleteId = null
                    },
                    enabled = !state.isWorking,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep routine") } }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RoutineBrowseCard(
    item: RoutineBrowseItem,
    enabled: Boolean,
    onOpen: () -> Unit,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val summary = "${item.dayCount} ${if (item.dayCount == 1) "day" else "days"} · ${item.exerciseCount} planned exercises"

    RoutinePanel(highlighted = item.isActive) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.isActive) {
                    IronLogStatusChip(
                        label = "Active",
                        tone = IronLogStatusTone.ACTIVE
                    )
                }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(
                    onClick = { menu = true },
                    enabled = enabled,
                    modifier = Modifier
                        .size(IronLogSpace.TouchTargetMin)
                        .semantics { contentDescription = "Options for ${item.name}" }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() }, enabled = enabled)
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() }, enabled = enabled)
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                        enabled = enabled
                    )
                }
            }
        }
        Text(
            text = summary,
            style = IronLogType.tabular(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.isActive && item.nextDayName != null) {
            Text(
                text = "Up next · ${item.nextDayName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            IronLogPrimaryButton(
                text = "Open routine",
                onClick = onOpen,
                enabled = enabled
            )
            IronLogSecondaryButton(
                text = if (item.isActive) "Clear selection" else "Use routine",
                onClick = onUse,
                enabled = enabled,
                outlined = true
            )
        }
    }
}
