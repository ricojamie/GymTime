package com.example.gymtime.ui.exercise.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.dao.ExerciseLastSetRow
import com.example.gymtime.data.db.dao.ExerciseUsageRow
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.ui.components.IronLogDivider
import com.example.gymtime.ui.components.IronLogPrimaryButton
import com.example.gymtime.ui.exercise.ExerciseSortMode
import com.example.gymtime.ui.theme.IronLogRadius
import com.example.gymtime.ui.theme.IronLogSpace
import com.example.gymtime.ui.theme.IronLogType

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
    var lastFilterKey by remember { mutableStateOf<Triple<String, Set<String>, ExerciseSortMode>?>(null) }
    val currentFilterKey = Triple(state.query, state.selectedMuscles, state.sortMode)
    LaunchedEffect(state.isLoading, currentFilterKey) {
        if (state.isLoading) return@LaunchedEffect
        val previous = lastFilterKey
        lastFilterKey = currentFilterKey
        if (previous != null && previous != currentFilterKey) {
            listState.scrollToItem(0)
        }
    }
    val muscles = remember(state.muscles) {
        val usualOrder = listOf(
            "Chest", "Back", "Shoulders", "Biceps", "Triceps",
            "Quads", "Hamstrings", "Glutes", "Calves", "Core", "Abs", "Legs", "Cardio"
        )
        state.muscles.sortedWith(
            compareBy<String> { muscle ->
                usualOrder.indexOfFirst { it.equals(muscle, ignoreCase = true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.lowercase() }
        )
    }
    val muscleListState = rememberLazyListState()
    var revealedInitialMuscle by remember { mutableStateOf(false) }
    // A newly opened picker reveals the remembered muscle once without moving chips after each tap.
    LaunchedEffect(state.isLoading, muscles) {
        if (!state.isLoading && !revealedInitialMuscle) {
            revealedInitialMuscle = true
            val selectedIndex = muscles.indexOfFirst { it in state.selectedMuscles }
            if (selectedIndex >= 0) muscleListState.scrollToItem(selectedIndex + 1)
        }
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.showBack) {
                    IconButton(
                        onClick = { onAction(PickerAction.Back) },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (state.showBack) 0.dp else 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.mode == PickerMode.BROWSE || state.mode == PickerMode.BUILD_SUPERSET) {
                    TextButton(
                        onClick = { onAction(PickerAction.ToggleSuperset) },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text(if (state.mode == PickerMode.BUILD_SUPERSET) "Cancel" else "Superset")
                    }
                } else {
                    TextButton(
                        onClick = { onAction(PickerAction.Back) },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text("Cancel")
                    }
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.Default.MoreVert, "Picker options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Sort exercises") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                            onClick = { showMenu = false; showSort = true },
                            enabled = !state.isLoading
                        )
                        DropdownMenuItem(
                            text = { Text("Create exercise") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { showMenu = false; onAction(PickerAction.Create(state.query.trim())) }
                        )
                        if (state.mode == PickerMode.BROWSE) {
                            DropdownMenuItem(
                                text = { Text("Smart Log") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                onClick = { showMenu = false; onAction(PickerAction.SmartLog) }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IronLogSpace.ScreenHorizontal),
                placeholder = { Text("Search all exercises") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                        ) {
                            Icon(Icons.Default.Close, "Clear search")
                        }
                    }
                } else null,
                shape = IronLogRadius.ControlShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = colors.surfaceContainerLow,
                    focusedContainerColor = colors.surfaceContainerLow,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedBorderColor = colors.primary
                )
            )
            LazyRow(
                state = muscleListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = IronLogSpace.ScreenHorizontal, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "all-muscles") {
                    FilterChip(
                        selected = state.selectedMuscles.isEmpty(),
                        onClick = { onAction(PickerAction.ClearFilters) },
                        label = { Text("All") },
                        enabled = !state.isLoading,
                        shape = IronLogRadius.MicroShape
                    )
                }
                items(muscles, key = { "muscle:$it" }) { muscle ->
                    FilterChip(
                        selected = muscle in state.selectedMuscles,
                        onClick = { onMuscleToggle(muscle) },
                        label = { Text(muscle) },
                        enabled = !state.isLoading,
                        shape = IronLogRadius.MicroShape
                    )
                }
            }
            if (state.isLoading) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            } else if (state.rows.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No exercises found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Try a different name or clear your filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    if (state.selectedMuscles.isNotEmpty()) {
                        TextButton(
                            onClick = { onAction(PickerAction.ClearFilters) },
                            modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                        ) {
                            Text("Clear filters")
                        }
                    }
                    TextButton(
                        onClick = { onAction(PickerAction.Create(state.query.trim())) },
                        modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                    ) {
                        Text(if (state.query.isBlank()) "Create exercise" else "Create “${state.query.trim()}”")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                SupersetSelectionFooter(state.selectedExercises, onSelectExercise) {
                    onAction(PickerAction.StartSuperset)
                }
            }
            // Library's navigation bar overlays its content; the standalone picker has no bottom bar.
            if (!state.showBack) Spacer(Modifier.height(IronLogSpace.BottomNavClearance))
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
        shape = IronLogRadius.ControlShape,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = selectionOrder != null }
    ) {
        Row(
            Modifier
                .heightIn(min = 68.dp)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mode == PickerMode.BUILD_SUPERSET) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (selectionOrder != null) colors.primary else colors.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selectionOrder != null) {
                            Text(
                                text = "$selectionOrder",
                                color = colors.onPrimary,
                                style = IronLogType.tabular(MaterialTheme.typography.labelMedium).copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(muscleColor, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        append(exercise.targetMuscle)
                        lastSet?.let { append(" · Last: ${formatLoggerSet(it.set, exercise.logType)}") }
                    },
                    style = IronLogType.tabular(MaterialTheme.typography.bodySmall),
                    color = colors.onSurfaceVariant
                )
            }
            if (mode == PickerMode.BROWSE) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(IronLogSpace.TouchTargetMin)
                    ) {
                        Icon(Icons.Default.MoreVert, "${exercise.name} options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit exercise") },
                            onClick = { showMenu = false; onAction(PickerAction.Edit(exercise)) }
                        )
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
    IronLogDivider(insetStart = 36.dp, insetEnd = 12.dp)
}

@Composable
private fun SupersetSelectionFooter(
    selected: List<Exercise>,
    onRemove: (Exercise) -> Unit,
    onStart: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            IronLogDivider(insetStart = 0.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IronLogSpace.ScreenHorizontal, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)
            ) {
                if (selected.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(IronLogSpace.CompactGap)) {
                        items(selected, key = { it.id }) { exercise ->
                            InputChip(
                                selected = true,
                                onClick = { onRemove(exercise) },
                                shape = IronLogRadius.MicroShape,
                                label = { Text(exercise.name, maxLines = 1) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, "Remove ${exercise.name}", Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
                IronLogPrimaryButton(
                    text = if (selected.size < 2) "Select at least 2 exercises" else "Start superset (${selected.size})",
                    onClick = onStart,
                    enabled = selected.size >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin),
                    shape = IronLogRadius.ControlShape
                )
            }
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
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = IronLogRadius.Sheet, topEnd = IronLogRadius.Sheet),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort exercises",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = IronLogSpace.TouchTargetMin)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(IronLogSpace.CompactGap))
            ExerciseSortMode.entries.forEach { sort ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = IronLogSpace.TouchTargetMin)
                        .selectable(
                            selected = state.sortMode == sort,
                            role = Role.RadioButton,
                            onClick = { onSortChange(sort) }
                        ),
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
