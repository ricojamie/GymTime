package com.example.gymtime.ui.settings.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.gymtime.ui.theme.LocalLoggerActionColors

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
    val saving: Boolean = false
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
    val action = LocalLoggerActionColors.current
    Scaffold(
        containerColor = colors.background,
        contentColor = colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Body parts", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = onHome) { Text("Home") } },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your training, organized", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("These groups organize exercises and strength trends. Renaming also updates assigned exercises.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
                    ) { Text("Add body part") }
                    Text("${state.groups.count { !it.protectedLibrary }} body parts", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
            if (state.groups.isEmpty()) {
                item { Text("Add your first body part to organize your exercise library.", Modifier.padding(vertical = 24.dp), color = colors.onSurfaceVariant) }
            }
            items(state.groups, key = { it.name }) { group ->
                Surface(
                    color = if (group.protectedLibrary) colors.surfaceContainerLow else colors.surface,
                    contentColor = colors.onSurface,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (group.protectedLibrary) {
                                Text("Built-in exercise library · excluded from metrics", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        if (!group.protectedLibrary) {
                            TextButton(onClick = { onEdit(group.name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Rename") }
                            TextButton(onClick = { onDelete(group.name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete", color = colors.error) }
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
            onDismissRequest = onDismissDelete,
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
                    TextButton(onClick = onDismissDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Got it") }
                } else {
                    Button(
                        onClick = onConfirmDelete,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    ) { Text("Delete") }
                }
            },
            dismissButton = {
                if (!blocked) TextButton(onClick = onDismissDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
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
        title = { Text(if (original.isEmpty()) "Add a body part" else "Rename $original") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onNameChange,
                label = { Text("Body part name") },
                singleLine = true,
                isError = error != null,
                enabled = !saving,
                supportingText = { Text(error ?: "At least two characters. Warmups is reserved.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!saving) onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
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
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill)
            ) { Text(if (saving) "Saving…" else if (original.isEmpty()) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } }
    )
}
