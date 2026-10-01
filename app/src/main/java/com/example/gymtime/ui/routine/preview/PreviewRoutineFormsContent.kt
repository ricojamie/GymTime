package com.example.gymtime.ui.routine.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.LogType
import com.example.gymtime.ui.components.BackNavigationIcon
import com.example.gymtime.ui.theme.LocalLoggerActionColors

/** The app shell owns the theme; keep this form's content at one stable composition location. */
@Composable
fun RoutineFormsTheme(content: @Composable () -> Unit) {
    content()
}

data class RoutineFormUiState(
    val name: String = "", val editing: Boolean = false,
    val loading: Boolean = false, val saving: Boolean = false,
    val canSave: Boolean = false, val error: String? = null
)

data class RoutineDayFormUiState(
    val name: String = "", val editing: Boolean = false,
    val loading: Boolean = false, val saving: Boolean = false,
    val canSave: Boolean = false, val error: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val sets: Map<Long, String> = emptyMap(),
    val repMin: Map<Long, String> = emptyMap(),
    val repMax: Map<Long, String> = emptyMap(),
    val rest: Map<Long, String> = emptyMap(),
    val notes: Map<Long, String> = emptyMap(),
    val links: Set<Int> = emptySet(),
    val pickerOpen: Boolean = false, val pickerQuery: String = "", val pickerMuscle: String = ""
)

data class RoutineDayFormActions(
    val name: (String) -> Unit,
    val sets: (Long, String) -> Unit,
    val repMin: (Long, String) -> Unit,
    val repMax: (Long, String) -> Unit,
    val rest: (Long, String) -> Unit,
    val notes: (Long, String) -> Unit,
    val move: (Long, Int) -> Unit,
    val remove: (Long) -> Unit,
    val link: (Int) -> Unit,
    val toggleExercise: (Long) -> Unit,
    val pickerQuery: (String) -> Unit,
    val pickerMuscle: (String) -> Unit,
    val openPicker: () -> Unit, val closePicker: () -> Unit,
    val save: () -> Unit, val retry: () -> Unit, val back: () -> Unit, val home: () -> Unit
)

/** Plain form rendering; navigation, persistence and lifecycle belong to the route wrapper. */
@Composable
fun PreviewRoutineFormContent(
    state: RoutineFormUiState,
    onName: (String) -> Unit, onSave: () -> Unit, onRetry: () -> Unit,
    onBack: () -> Unit, onHome: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.background, contentColor = colors.onBackground,
        topBar = { RoutineFormHeader(if (state.editing) "Edit routine" else "New routine", onBack, onHome, !state.saving) },
        bottomBar = { RoutineFormFooter(state.error, state.canSave, state.saving,
            if (state.editing) "Save routine" else "Create routine", onSave) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(if (state.editing) "Make it yours" else "A plan you can come back to",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Give your routine a name. Add its training days next.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Surface(color = colors.surface, contentColor = colors.onSurface,
                    shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, colors.outlineVariant)) {
                    OutlinedTextField(state.name, onName, label = { Text("Routine name") },
                        placeholder = { Text("Push / Pull / Legs") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        enabled = !state.loading && !state.saving,
                        modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null && !state.canSave && state.editing && !state.loading) item {
                TextButton(onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reload routine") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreviewRoutineDayFormContent(state: RoutineDayFormUiState, actions: RoutineDayFormActions) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colors.background, contentColor = colors.onBackground,
        topBar = { RoutineFormHeader(if (state.editing) "Edit day" else "New training day", actions.back, actions.home, !state.saving) },
        bottomBar = { RoutineFormFooter(state.error, state.canSave, state.saving, "Save day", actions.save) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(state.name, actions.name, label = { Text("Day name") },
                    placeholder = { Text("Push day") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    enabled = !state.loading && !state.saving, modifier = Modifier.fillMaxWidth())
            }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f).widthIn(min = 160.dp)) {
                        Text("Your exercise order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${state.exercises.size} exercises · linked pairs rotate together",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    TextButton(actions.openPicker, enabled = !state.loading && !state.saving,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Add exercises") }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null && state.loading.not() && state.exercises.isEmpty() && state.editing) item {
                TextButton(actions.retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reload day") }
            }
            if (state.exercises.isEmpty() && !state.loading) item {
                Surface(color = colors.surfaceContainerLow, contentColor = colors.onSurface,
                    shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Build the workout you want", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Choose exercises, set your targets, and put them in the order you'll lift.",
                            color = colors.onSurfaceVariant)
                        TextButton(actions.openPicker, enabled = !state.loading && !state.saving,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Choose exercises") }
                    }
                }
            }
            itemsIndexed(state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                RoutineExerciseEditor(exercise, index, state, actions)
            }
        }
    }
    if (state.pickerOpen) {
        ModalBottomSheet(onDismissRequest = actions.closePicker,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface, contentColor = colors.onSurface) {
            RoutineFormExercisePicker(state, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineExerciseEditor(exercise: Exercise, index: Int, state: RoutineDayFormUiState, actions: RoutineDayFormActions) {
    val colors = MaterialTheme.colorScheme
    val id = exercise.id
    val linkedBefore = index > 0 && (index - 1) in state.links
    val linkedAfter = index in state.links
    val hasReps = exercise.logType == LogType.WEIGHT_REPS || exercise.logType == LogType.REPS_ONLY
    var details by rememberSaveable(id) { mutableStateOf(state.notes[id].orEmpty().isNotEmpty()) }
    val enabled = !state.loading && !state.saving
    Column {
        Surface(color = if (linkedBefore || linkedAfter) colors.primaryContainer else colors.surface,
            contentColor = colors.onSurface, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp,
            border = BorderStroke(1.dp, if (linkedBefore || linkedAfter) colors.primary else colors.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${exercise.targetMuscle}${if (linkedBefore || linkedAfter) " · Superset" else ""}",
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineTargetField("Sets", state.sets[id].orEmpty(), { actions.sets(id, it) }, enabled,
                        Modifier.weight(1f).widthIn(min = 128.dp), "3")
                    if (hasReps) {
                        RoutineTargetField("Min reps", state.repMin[id].orEmpty(), { actions.repMin(id, it) }, enabled,
                            Modifier.weight(1f).widthIn(min = 128.dp), "Optional")
                        RoutineTargetField("Max reps", state.repMax[id].orEmpty(), { actions.repMax(id, it) }, enabled,
                            Modifier.weight(1f).widthIn(min = 128.dp), "Optional")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ details = !details }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (details) "Hide rest & notes" else "Rest & notes")
                    }
                    TextButton({ actions.move(id, -1) }, enabled = enabled && index > 0,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Up") }
                    TextButton({ actions.move(id, 1) }, enabled = enabled && index < state.exercises.lastIndex,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Down") }
                    TextButton({ actions.remove(id) }, enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove", color = colors.error) }
                }
                if (details) {
                    RoutineTargetField("Rest in seconds", state.rest[id].orEmpty(), { actions.rest(id, it) }, enabled,
                        Modifier.fillMaxWidth(), exercise.defaultRestSeconds.toString())
                    Text("Leave rest blank to use this exercise's default (${exercise.defaultRestSeconds}s).",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(state.notes[id].orEmpty(), { actions.notes(id, it) },
                        label = { Text("Exercise notes") }, placeholder = { Text("Cues, setup, or your plan") },
                        enabled = enabled, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
                }
            }
        }
        if (index < state.exercises.lastIndex) {
            val otherLinked = (index - 1) in state.links || (index + 1) in state.links
            TextButton({ actions.link(index) }, enabled = enabled && (linkedAfter || !otherLinked),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (linkedAfter) "Unlink superset" else if (otherLinked) "An exercise is already paired" else "Pair with next exercise")
            }
        }
    }
}

@Composable
private fun RoutineTargetField(label: String, value: String, change: (String) -> Unit,
    enabled: Boolean, modifier: Modifier, placeholder: String) {
    OutlinedTextField(value, change, modifier = modifier, enabled = enabled, label = { Text(label) },
        placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun RoutineFormExercisePicker(state: RoutineDayFormUiState, actions: RoutineDayFormActions) {
    val colors = MaterialTheme.colorScheme
    val action = LocalLoggerActionColors.current
    val muscles = remember(state.availableExercises) { state.availableExercises.map { it.targetMuscle }.distinct().sorted() }
    val filtered = remember(state.availableExercises, state.pickerQuery, state.pickerMuscle) {
        state.availableExercises.filter { exercise ->
            (state.pickerMuscle.isBlank() || exercise.targetMuscle == state.pickerMuscle) &&
                (exercise.name.contains(state.pickerQuery.trim(), true) || exercise.targetMuscle.contains(state.pickerQuery.trim(), true))
        }
    }
    // Material 3's sheet dialog owns IME and system-bar insets for this separate window.
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(horizontal = 16.dp)) {
        Text("Add exercises", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${state.selectedIds.size} selected · tap to add or remove",
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(state.pickerQuery, actions.pickerQuery, singleLine = true,
            label = { Text("Search exercises") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            item { FilterChip(state.pickerMuscle.isBlank(), { actions.pickerMuscle("") }, label = { Text("All") },
                modifier = Modifier.heightIn(min = 48.dp)) }
            items(muscles) { muscle ->
                FilterChip(state.pickerMuscle == muscle, { actions.pickerMuscle(if (state.pickerMuscle == muscle) "" else muscle) },
                    label = { Text(muscle) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)) {
            if (filtered.isEmpty()) item { Text("No exercises match. Try another body part or search.", Modifier.padding(16.dp)) }
            items(filtered, key = { it.id }) { exercise ->
                val selected = exercise.id in state.selectedIds
                Surface(onClick = { actions.toggleExercise(exercise.id) }, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { this.selected = selected },
                    color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                    contentColor = colors.onSurface, border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, fontWeight = FontWeight.SemiBold)
                            Text(exercise.targetMuscle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Checkbox(selected, onCheckedChange = null)
                    }
                }
            }
        }
        Button(actions.closePicker, Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill),
            shape = RoundedCornerShape(16.dp)) { Text("Done · ${state.selectedIds.size} selected") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineFormHeader(title: String, back: () -> Unit, home: () -> Unit, enabled: Boolean) {
    TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        navigationIcon = { if (enabled) BackNavigationIcon(back) },
        actions = { TextButton(home, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("Home") } },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}

@Composable
private fun RoutineFormFooter(error: String?, enabled: Boolean, saving: Boolean, label: String, save: () -> Unit) {
    val action = LocalLoggerActionColors.current
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(save, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = action.fill, contentColor = action.onFill), shape = RoundedCornerShape(16.dp)) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = action.onFill, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Saving…" else label, fontWeight = FontWeight.Bold)
            }
        }
    }
}
