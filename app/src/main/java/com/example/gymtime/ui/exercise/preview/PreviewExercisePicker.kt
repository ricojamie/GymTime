package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.ui.exercise.ExerciseSortMode
import com.example.gymtime.ui.theme.LocalLoggerActionColors

enum class PickerMode { BROWSE, BUILD_SUPERSET, ADD_TO_SUPERSET, SWAP }

data class PickerUiState(
    val rows: List<ExerciseUsageRow> = emptyList(),
    val lastSets: Map<Long, ExerciseLastSetRow> = emptyMap(),
    val isLoading: Boolean = false,
    val query: String = "",
    val muscles: List<String> = emptyList(),
    val selectedMuscles: Set<String> = emptySet(),
    val sortMode: ExerciseSortMode = ExerciseSortMode.RECENTLY_USED,
    val selectedExercises: List<Exercise> = emptyList(),
    val mode: PickerMode = PickerMode.BROWSE,
    val showBack: Boolean = true
)

sealed interface PickerAction {
    data object Back : PickerAction
    data object ToggleSuperset : PickerAction
    data object StartSuperset : PickerAction
    data object ClearFilters : PickerAction
    data object SmartLog : PickerAction
    data class Create(val name: String = "") : PickerAction
    data class Edit(val exercise: Exercise) : PickerAction
    data class Delete(val exercise: Exercise) : PickerAction
    data class TogglePr(val exercise: Exercise) : PickerAction
}

/** Plain rendering boundary; navigation, preferences and database work live in the screen/VM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewExercisePicker(
    state: PickerUiState,
    onAction: (PickerAction) -> Unit,
    onSelectExercise: (Exercise) -> Unit,
    onQueryChange: (String) -> Unit,
    onMuscleToggle: (String) -> Unit,
    onSortChange: (ExerciseSortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(state.query, state.selectedMuscles, state.sortMode) {
        listState.scrollToItem(0)
    }
    val muscles = remember(state.muscles) {
        val usualOrder = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Abs", "Legs", "Cardio")
        state.muscles.sortedWith(compareBy<String> { muscle ->
            usualOrder.indexOfFirst { it.equals(muscle, ignoreCase = true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE
        }.thenBy { it.lowercase() })
    }
    val muscleListState = rememberLazyListState()
    // A newly opened picker reveals the remembered muscle without moving chips after each tap.
    LaunchedEffect(state.isLoading, muscles) {
        if (!state.isLoading) {
            val selectedIndex = muscles.indexOfFirst { it in state.selectedMuscles }
            if (selectedIndex >= 0) muscleListState.scrollToItem(selectedIndex + 1)
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.showBack) {
                IconButton(onClick = { onAction(PickerAction.Back) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
            Text(
                text = when (state.mode) {
                    PickerMode.BUILD_SUPERSET -> "Build superset"
                    PickerMode.ADD_TO_SUPERSET -> "Add to superset"
                    PickerMode.SWAP -> "Swap exercise"
                    PickerMode.BROWSE -> if (state.showBack) "Choose exercise" else "Exercises"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = if (state.showBack) 0.dp else 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.mode == PickerMode.BROWSE || state.mode == PickerMode.BUILD_SUPERSET) {
                TextButton(onClick = { onAction(PickerAction.ToggleSuperset) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state.mode == PickerMode.BUILD_SUPERSET) "Cancel" else "Superset")
                }
            } else {
                TextButton(onClick = { onAction(PickerAction.Back) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "Picker options") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Sort exercises") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                        onClick = { showMenu = false; showSort = true },
                        enabled = !state.isLoading
                    )
                    DropdownMenuItem(
                        text = { Text("Create exercise") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { showMenu = false; onAction(PickerAction.Create(state.query.trim())) }
                    )
                    if (state.mode == PickerMode.BROWSE) {
                        DropdownMenuItem(
                            text = { Text("Smart Log") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                            onClick = { showMenu = false; onAction(PickerAction.SmartLog) }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search all exercises") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear search") } }
            } else null,
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.surface,
                focusedContainerColor = colors.surface,
                unfocusedBorderColor = colors.outlineVariant
            )
        )
        LazyRow(
            state = muscleListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "all-muscles") {
                FilterChip(
                    selected = state.selectedMuscles.isEmpty(),
                    onClick = { onAction(PickerAction.ClearFilters) },
                    label = { Text("All") },
                    enabled = !state.isLoading
                )
            }
            items(muscles, key = { "muscle:$it" }) { muscle ->
                FilterChip(
                    selected = muscle in state.selectedMuscles,
                    onClick = { onMuscleToggle(muscle) },
                    label = { Text(muscle) },
                    enabled = !state.isLoading
                )
            }
        }
        if (state.isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else if (state.rows.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No exercises found", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Try a different name or clear your filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (state.selectedMuscles.isNotEmpty()) {
                    TextButton(onClick = { onAction(PickerAction.ClearFilters) }) {
                        Text("Clear filters")
                    }
                }
                TextButton(onClick = { onAction(PickerAction.Create(state.query.trim())) }) {
                    Text(if (state.query.isBlank()) "Create exercise" else "Create “${state.query.trim()}”")
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)
            ) {
                items(state.rows, key = { it.exercise.id }) { row ->
                    val index = state.selectedExercises.indexOfFirst { it.id == row.exercise.id }
                    PickerExerciseRow(
                        exercise = row.exercise,
                        lastSet = state.lastSets[row.exercise.id],
                        selectionOrder = if (index >= 0) index + 1 else null,
                        mode = state.mode,
                        onClick = { onSelectExercise(row.exercise) },
                        onAction = onAction
                    )
                }
            }
        }
        if (state.mode == PickerMode.BUILD_SUPERSET) {
            SupersetSelectionFooter(state.selectedExercises, onSelectExercise) { onAction(PickerAction.StartSuperset) }
        }
        // Library's navigation bar overlays its content; the standalone picker has no bottom bar.
        if (!state.showBack) Spacer(Modifier.height(104.dp))
    }
    }
    if (showSort) {
        PickerSortSheet(
            state = state,
            onSortChange = onSortChange,
            onDismiss = { showSort = false }
        )
    }
}

@Composable
private fun PickerExerciseRow(
    exercise: Exercise,
    lastSet: ExerciseLastSetRow?,
    selectionOrder: Int?,
    mode: PickerMode,
    onClick: () -> Unit,
    onAction: (PickerAction) -> Unit
) {
    var showMenu by remember(exercise.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val muscleColor = when (Math.floorMod(exercise.targetMuscle.hashCode(), 3)) {
        0 -> colors.primary
        1 -> colors.secondary
        else -> colors.tertiary
    }
    Surface(
        onClick = onClick,
        color = if (selectionOrder != null) colors.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().semantics { selected = selectionOrder != null }
    ) {
        Row(
            Modifier.heightIn(min = 68.dp).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mode == PickerMode.BUILD_SUPERSET) {
                Surface(
                    modifier = Modifier.size(28.dp), shape = CircleShape,
                    color = if (selectionOrder != null) colors.primary else colors.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectionOrder != null) Text("$selectionOrder", color = colors.onPrimary)
                        else Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
            } else {
                Box(Modifier.size(8.dp).background(muscleColor, CircleShape))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildString {
                        append(exercise.targetMuscle)
                        lastSet?.let { append(" · Last: ${formatLoggerSet(it.set, exercise.logType)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (mode == PickerMode.BROWSE) {
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "${exercise.name} options") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit exercise") }, onClick = { showMenu = false; onAction(PickerAction.Edit(exercise)) })
                        DropdownMenuItem(
                            text = { Text(if (exercise.isStarred) "Stop tracking PR" else "Track PR") },
                            onClick = { showMenu = false; onAction(PickerAction.TogglePr(exercise)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete exercise", color = colors.error) },
                            onClick = { showMenu = false; onAction(PickerAction.Delete(exercise)) }
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 40.dp, end = 12.dp), color = colors.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun SupersetSelectionFooter(selected: List<Exercise>, onRemove: (Exercise) -> Unit, onStart: () -> Unit) {
    val actionColors = LocalLoggerActionColors.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (selected.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selected, key = { it.id }) { exercise ->
                        InputChip(
                            selected = true,
                            onClick = { onRemove(exercise) },
                            label = { Text(exercise.name, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove ${exercise.name}", Modifier.size(16.dp)) }
                        )
                    }
                }
            }
            Button(
                enabled = selected.size >= 2,
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionColors.fill, contentColor = actionColors.onFill)
            ) { Text(if (selected.size < 2) "Select at least 2 exercises" else "Start superset (${selected.size})") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSortSheet(
    state: PickerUiState,
    onSortChange: (ExerciseSortMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sort exercises", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            ExerciseSortMode.entries.forEach { sort ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = state.sortMode == sort, role = Role.RadioButton, onClick = { onSortChange(sort) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = state.sortMode == sort, onClick = null)
                    Text(sort.label, Modifier.padding(start = 12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
