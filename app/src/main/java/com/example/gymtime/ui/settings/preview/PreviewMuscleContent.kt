package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogEmptyState
import com.example.gymtime.ui.components.IronLogGroupedCard
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.components.IronLogSectionHeader
import com.example.gymtime.ui.components.IronLogStatusChip
import com.example.gymtime.ui.components.IronLogStatusTone
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.LocalLoggerActionColors
import com.example.gymtime.ui.theme.tabular

@Immutable
data class PreviewMuscleRow(val name: String, val protectedLibrary: Boolean)

@Immutable
data class PreviewMuscleDelete(val name: String, val exerciseCount: Int = 0, val loggedSetCount: Int = 0)

@Immutable
data class PreviewMuscleUiState(
    val groups: List<PreviewMuscleRow>,
    val editingOriginalName: String?,
    val nameDraft: String,
    val validationError: String?,
    val deletion: PreviewMuscleDelete?,
    val saving: Boolean = false,
    val deleting: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewMuscleContent(
    state: PreviewMuscleUiState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val editableCount = remember(state.groups) { state.groups.count { !it.protectedLibrary } }

    Scaffold(
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Body parts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onHome,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) { Text("Home") }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = IronLogSpace.ScreenHorizontal,
                end = IronLogSpace.ScreenHorizontal,
                top = IronLogSpace.CompactGap,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(IronLogSpace.SectionGap)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.ItemGap)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Your training, organized",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "These groups organize exercises and strength trends. Renaming also updates assigned exercises.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IronLogPrimaryButton(
                        text = "Add body part",
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.groups.isEmpty()) {
                item(key = "empty") {
                    IronLogEmptyState(
                        title = "No body parts yet",
                        body = "Add your first body part to organize your exercise library."
                    )
                }
            } else {
                item(key = "groups-list") {
                    Column(verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                        IronLogSectionHeader(
                            title = "Body parts",
                            trailingAction = {
                                Text(
                                    text = "$editableCount ${if (editableCount == 1) "group" else "groups"}",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = colors.onSurfaceVariant
                                )
                            }
                        )
                        IronLogGroupedCard(
                            showBorder = true,
                            containerColor = colors.surfaceContainerLow
                        ) {
                            state.groups.forEachIndexed { index, group ->
                                if (index > 0) {
                                    IronLogDivider()
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .padding(
                                            start = IronLogSpace.CardPadding,
                                            end = if (group.protectedLibrary) IronLogSpace.CardPadding else 8.dp,
                                            top = 10.dp,
                                            bottom = 10.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
                                        ) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (group.protectedLibrary) {
                                                IronLogStatusChip(
                                                    label = "Built-in",
                                                    tone = IronLogStatusTone.NEUTRAL
                                                )
                                            }
                                        }
                                        if (group.protectedLibrary) {
                                            Text(
                                                text = "Exercise library · excluded from metrics",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (!group.protectedLibrary) {
                                        TextButton(
                                            onClick = { onEdit(group.name) },
                                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                        ) { Text("Rename") }
                                        TextButton(
                                            onClick = { onDelete(group.name) },
                                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                                        ) { Text("Delete", color = colors.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.editingOriginalName?.let { original ->
        PreviewMuscleEditor(
            original = original,
            draft = state.nameDraft,
            error = state.validationError,
            saving = state.saving,
            onNameChange = onNameChange,
            onSave = onSave,
            onDismiss = onDismissEditor
        )
    }

    state.deletion?.let { deletion ->
        val blocked = deletion.loggedSetCount > 0
        AlertDialog(
            onDismissRequest = { if (!state.deleting) onDismissDelete() },
            shape = IronLogRadius.CardShape,
            title = { Text(if (blocked) "Keep your history intact" else "Delete ${deletion.name}?") },
            text = {
                Text(
                    when {
                        blocked -> "${deletion.name} has ${deletion.loggedSetCount} logged sets and cannot be deleted. Rename it instead."
                        deletion.exerciseCount > 0 -> "${deletion.exerciseCount} assigned exercises will move to Uncategorized. Your exercises will stay in the library."
                        else -> "This body part will be removed from your library."
                    }
                )
            },
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            confirmButton = {
                if (blocked) {
                    TextButton(
                        onClick = onDismissDelete,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) { Text("Got it") }
                } else {
                    Button(
                        onClick = onConfirmDelete,
                        enabled = !state.deleting,
                        shape = IronLogRadius.ControlShape,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    ) { Text(if (state.deleting) "Deleting…" else "Delete") }
                }
            },
            dismissButton = {
                if (!blocked) {
                    TextButton(
                        onClick = onDismissDelete,
                        enabled = !state.deleting,
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun PreviewMuscleEditor(
    original: String,
    draft: String,
    error: String?,
    saving: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val action = LocalLoggerActionColors.current
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        shape = IronLogRadius.CardShape,
        title = { Text(if (original.isEmpty()) "Add a body part" else "Rename $original") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onNameChange,
                label = { Text("Body part name") },
                singleLine = true,
                shape = IronLogRadius.ControlShape,
                isError = error != null,
                enabled = !saving,
                supportingText = { Text(error ?: "At least two characters. Warmups is reserved.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!saving) onSave() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            LaunchedEffect(original) { focus.requestFocus() }
            LaunchedEffect(error) { if (error != null && !saving) focus.requestFocus() }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !saving,
                shape = IronLogRadius.ControlShape,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
            ) { Text(if (saving) "Saving…" else if (original.isEmpty()) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !saving,
                modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
            ) { Text("Cancel") }
        }
    )
}
